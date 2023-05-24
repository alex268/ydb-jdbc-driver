package tech.ydb.jdbc.statement;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;

import tech.ydb.jdbc.YdbResultSet;
import tech.ydb.jdbc.common.TypeDescription;
import tech.ydb.jdbc.common.YdbQuery;
import tech.ydb.jdbc.connection.YdbConnectionImpl;
import tech.ydb.jdbc.exception.YdbExecutionException;
import tech.ydb.table.query.DataQuery;
import tech.ydb.table.query.Params;
import tech.ydb.table.values.ListType;
import tech.ydb.table.values.StructType;
import tech.ydb.table.values.Type;
import tech.ydb.table.values.Value;

import static tech.ydb.jdbc.YdbConst.MISSING_VALUE_FOR_PARAMETER;
import static tech.ydb.jdbc.YdbConst.PARAMETER_NOT_FOUND;

public class YdbPreparedStatementWithDataQueryBatchedImpl extends AbstractYdbDataQueryPreparedStatementImpl {
    private static final Logger LOGGER = Logger.getLogger(YdbPreparedStatementWithDataQueryBatchedImpl.class.getName());

    private final StructBatchConfiguration cfg;
    private final StructMutableState state;

    public YdbPreparedStatementWithDataQueryBatchedImpl(
            YdbConnectionImpl connection,
            int resultSetType,
            YdbQuery query,
            DataQuery dataQuery,
            StructBatchConfiguration cfg) throws SQLException {
        super(connection, resultSetType, query, dataQuery);
        this.cfg = Objects.requireNonNull(cfg);
        this.state = new StructMutableState(cfg);
        this.clearParameters();
    }

    @Override
    public void clearParameters() {
        state.clear();
    }

    @Override
    protected void afterExecute() {
        clearParameters();
        clearBatch();
    }

    @Override
    public void addBatch() throws SQLException {
        state.flush();
    }

    @Override
    public void clearBatch() {
        state.batch.clear();
        state.clear();
    }

    @Override
    public boolean execute() throws SQLException {
        addBatch();
        return super.execute();
    }

    @Override
    public int executeUpdate() throws SQLException {
        addBatch();
        return super.executeUpdate();
    }

    @Override
    public YdbResultSet executeQuery() throws SQLException {
        addBatch();
        return super.executeQuery();
    }

    /**
     * There is a difference between this method and all other 'execute' methods.
     * All methods except 'executeBatch' will be executed unconditionally, i.e. calling other 'execute' methods
     * without settings parameter first could cause an exception
     */
    @Override
    public int[] executeBatch() throws SQLException {
        int batchSize = state.batch.size();
        if (batchSize == 0) {
            LOGGER.log(Level.FINE, "Batch is empty, nothing to execute");
            return new int[0];
        }
        super.execute();
        int[] ret = new int[batchSize];
        Arrays.fill(ret, SUCCESS_NO_INFO);
        return ret;
    }

    @Override
    protected Params getParams() {
        // Do not flush parameters
        Params params = getDataQuery().newParams();
        params.put(cfg.paramName, cfg.listType.newValue(state.batch)); // The fastest way to prepare list
        return params;
    }

    @Override
    protected Map<String, TypeDescription> getParameterTypes() {
        return cfg.types;
    }

    @Override
    protected void setImpl(String parameterName, @Nullable Object x,
                           int sqlType, @Nullable String typeName, @Nullable Type type)
            throws SQLException {
        int index = cfg.getIndex(parameterName);
        TypeDescription description = cfg.descriptions[index];
        Value<?> value = getValue(parameterName, description, x);
        state.addParam(index, value);
    }

    @Override
    protected void setImpl(int parameterIndex, @Nullable Object x,
                           int sqlType, @Nullable String typeName, @Nullable Type type)
            throws SQLException {
        setImpl(query.getParameterName(parameterIndex), x, sqlType, typeName, type);
    }

    //

    public static Optional<StructBatchConfiguration> asColumns(Map<String, Type> types) {
        if (types.size() != 1) {
            return Optional.empty(); // ---
        }
        // Only single parameter

        Map.Entry<String, Type> entry = types.entrySet().iterator().next();

        String paramName = entry.getKey();

        // Only list of values
        Type paramType = entry.getValue();
        if (paramType.getKind() != Type.Kind.LIST) {
            return Optional.empty(); // ---
        }

        ListType listType = (ListType) paramType;
        Type itemType = listType.getItemType();
        // Component - must be struct (i.e. list of structs)
        if (itemType.getKind() == Type.Kind.STRUCT) {
            return Optional.of(fromStruct(paramName, listType, (StructType) itemType));
        }
        return Optional.empty(); // ---
    }

    private static StructBatchConfiguration fromStruct(String paramName, ListType listType, StructType structType) {
        int membersCount = structType.getMembersCount();

        Map<String, TypeDescription> types = new LinkedHashMap<>(membersCount);
        Map<String, Integer> indexes = new HashMap<>(membersCount);
        String[] names = new String[membersCount];
        TypeDescription[] descriptions = new TypeDescription[membersCount];
        for (int i = 0; i < membersCount; i++) {
            String name = structType.getMemberName(i);
            Type type = structType.getMemberType(i);
            TypeDescription description = TypeDescription.of(type);
            if (indexes.put(name, i) != null) {
                throw new IllegalStateException("Internal error. YDB must not bypass this struct " +
                        "with duplicate member " + paramName);
            }
            types.put(name, description);
            names[i] = name;
            descriptions[i] = description;
        }
        return new StructBatchConfiguration(paramName, listType, structType, types, indexes, names, descriptions);
    }

    public static class StructBatchConfiguration {
        private final String paramName;
        private final ListType listType;
        private final StructType structType;
        private final Map<String, TypeDescription> types;
        private final Map<String, Integer> indexes;
        private final String[] names;
        private final TypeDescription[] descriptions;

        private StructBatchConfiguration(String paramName,
                                         ListType listType,
                                         StructType structType,
                                         Map<String, TypeDescription> types,
                                         Map<String, Integer> indexes,
                                         String[] names,
                                         TypeDescription[] descriptions) {
            this.paramName = Objects.requireNonNull(paramName);
            this.listType = Objects.requireNonNull(listType);
            this.structType = Objects.requireNonNull(structType);
            this.types = Objects.requireNonNull(types);
            this.indexes = Objects.requireNonNull(indexes);
            this.names = Objects.requireNonNull(names);
            this.descriptions = Objects.requireNonNull(descriptions);
            Preconditions.checkState(descriptions.length == names.length);
            Preconditions.checkState(descriptions.length == indexes.size());
            Preconditions.checkState(descriptions.length == types.size());
        }

        int getIndex(String name) throws SQLException {
            Integer index = indexes.get(name);
            if (index == null) {
                throw new YdbExecutionException(PARAMETER_NOT_FOUND + name);
            }
            return index;
        }
    }

    @SuppressWarnings("rawtypes")
    private static class StructMutableState {
        private final List<Value<?>> batch = new ArrayList<>();
        private final StructBatchConfiguration cfg;
        private Value<?>[] members;
        private boolean modified;

        StructMutableState(StructBatchConfiguration cfg) {
            this.cfg = cfg;
            this.members = new Value[cfg.descriptions.length];
        }

        void addParam(int index, Value<?> value) {
            members[index] = value;
            modified = true;
        }

        void flush() throws SQLException {
            if (modified) {
                for (int i = 0; i < members.length; i++) {
                    if (members[i] == null) {
                        throw new SQLException(MISSING_VALUE_FOR_PARAMETER + cfg.names[i]);
                    }
                }
                batch.add(cfg.structType.newValueUnsafe(members)); // The fastest way to prepare struct
                clear();
            }
        }

        void clear() {
            members = new Value<?>[cfg.descriptions.length];
            modified = false;
        }
    }
}
