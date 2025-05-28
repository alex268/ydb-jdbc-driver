package tech.ydb.jdbc.query.params;


import java.sql.SQLDataException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import tech.ydb.jdbc.YdbConst;
import tech.ydb.jdbc.common.TypeDescription;
import tech.ydb.jdbc.common.YdbTypes;
import tech.ydb.jdbc.query.ParamDescription;
import tech.ydb.jdbc.query.YdbPreparedQuery;
import tech.ydb.jdbc.query.YdbQuery;
import tech.ydb.table.query.Params;
import tech.ydb.table.values.Type;
import tech.ydb.table.values.Value;

/**
 *
 * @author Aleksandr Gorshenin
 */
public class PreparedQuery implements YdbPreparedQuery {
    private final String yql;
    private final Map<String, ParamDescription> params;
    private final String[] paramNames;

    private final Map<String, Value<?>> paramValues = new HashMap<>();
    private final List<Params> batchList = new ArrayList<>();

    public PreparedQuery(YdbTypes types, YdbQuery query, Map<String, Type> paramTypes) {
        yql = query.getPreparedYql();
        params = new HashMap<>();
        paramNames = new String[paramTypes.size()];

        // Firstly put all indexed params (p1, p2, ...,  pN) in correct places of paramNames
        Set<String> indexedNames = new HashSet<>();
        for (int idx = 0; idx < paramNames.length; idx += 1) {
            String indexedName = YdbConst.VARIABLE_PARAMETER_PREFIX + YdbConst.INDEXED_PARAMETER_PREFIX + (1 + idx);
            if (paramTypes.containsKey(indexedName)) {
                TypeDescription typeDesc = types.find(paramTypes.get(indexedName));
                ParamDescription paramDesc = new ParamDescription(indexedName, typeDesc);

                params.put(indexedName, paramDesc);
                paramNames[idx] = indexedName;
                indexedNames.add(indexedName);
            }
        }

        // Then put all others params in free places of paramNames in alphabetic order
        Iterator<String> sortedIter = new TreeSet<>(paramTypes.keySet()).iterator();
        for (int idx = 0; idx < paramNames.length; idx += 1) {
            if (paramNames[idx] != null) {
                continue;
            }

            String param = sortedIter.next();
            while (indexedNames.contains(param)) {
                param = sortedIter.next();
            }

            TypeDescription typeDesc = types.find(paramTypes.get(param));
            ParamDescription paramDesc = new ParamDescription(param, typeDesc);

            params.put(param, paramDesc);
            paramNames[idx] = param;
        }
    }

    @Override
    public String getQueryText(Params prms) {
        return yql;
    }

    @Override
    public String getBatchText(Params prms) {
        return yql;
    }

    @Override
    public void setParam(int index, Object obj, int sqlType) throws SQLException {
        if (index <= 0 || index > paramNames.length) {
            throw new SQLException(YdbConst.PARAMETER_NUMBER_NOT_FOUND + index);
        }
        String varName = paramNames[index - 1];
        ParamDescription desc = params.get(varName);
        paramValues.put(varName, ValueFactory.readValue(desc.name(), obj, desc.type()));
    }

    @Override
    public void setParam(String name, Object obj, int sqlType) throws SQLException {
        String varName = YdbConst.VARIABLE_PARAMETER_PREFIX + name;
        if (!params.containsKey(varName)) {
            throw new SQLException(YdbConst.PARAMETER_NOT_FOUND + name);
        }

        ParamDescription desc = params.get(varName);
        paramValues.put(varName, ValueFactory.readValue(desc.name(), obj, desc.type()));
    }

    @Override
    public void clearParameters() {
        paramValues.clear();
    }

    @Override
    public void addBatch() throws SQLException {
        batchList.add(getCurrentParams());
        clearParameters();
    }

    @Override
    public void clearBatch() {
        batchList.clear();
    }

    @Override
    public int parametersCount() {
        return params.size();
    }

    @Override
    public int batchSize() {
        return batchList.size();
    }

    private Params validateParams(Map<String, Value<?>> values) throws SQLException {
        for (String key: this.params.keySet()) {
            if (!values.containsKey(key)) {
                throw new SQLDataException(YdbConst.MISSING_VALUE_FOR_PARAMETER + key);
            }
        }
        return Params.copyOf(values);
    }

    @Override
    public List<Params> getBatchParams() {
        return batchList;
    }

    @Override
    public Params getCurrentParams() throws SQLException {
        return validateParams(paramValues);
    }

    @Override
    public String getNameByIndex(int index) throws SQLException {
        if (index <= 0 || index > paramNames.length) {
            throw new SQLException(YdbConst.PARAMETER_NUMBER_NOT_FOUND + index);
        }
        return paramNames[index - 1].substring(YdbConst.VARIABLE_PARAMETER_PREFIX.length());
    }

    @Override
    public TypeDescription getDescription(int index) throws SQLException {
        if (index <= 0 || index > paramNames.length) {
            throw new SQLException(YdbConst.PARAMETER_NUMBER_NOT_FOUND + index);
        }
        String name = paramNames[index - 1];
        return params.get(name).type();
    }
}
