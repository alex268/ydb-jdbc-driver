package tech.ydb.jdbc.query.params;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import tech.ydb.jdbc.YdbConst;
import tech.ydb.jdbc.common.TypeDescription;
import tech.ydb.jdbc.common.YdbTypes;
import tech.ydb.table.query.Params;
import tech.ydb.table.values.ListType;
import tech.ydb.table.values.OptionalType;
import tech.ydb.table.values.Type;
import tech.ydb.table.values.Value;
import tech.ydb.table.values.VoidValue;

/**
 *
 * @author Aleksandr Gorshenin
 */
public class InListJdbcPrm {
    private static final Value<?> NULL = VoidValue.of();
    private final YdbTypes types;
    private final String listName;
    private final List<Item> items = new ArrayList<>();
    private TypeDescription type;

    public InListJdbcPrm(YdbTypes types, String listName, int listSize) {
        this.types = types;
        this.listName = listName;
        for (int idx = 0; idx < listSize; idx++) {
            items.add(new Item(listName, idx));
        }
    }

    public List<? extends JdbcPrm> toJdbcPrmList() {
        return items;
    }

    private Value<?> buildList() throws SQLException {
        if (type == null) {
            throw new SQLException(YdbConst.PARAMETER_TYPE_UNKNOWN);
        }

        boolean hasNull = false;
        for (Item item: items) {
            if (item.value == null) {
                throw new SQLException(YdbConst.MISSING_VALUE_FOR_PARAMETER + item.name);
            }
            hasNull = hasNull || item.value == NULL;
        }

        List<Value<?>> values = new ArrayList<>();
        if (!hasNull) {
            for (Item item: items) {
                values.add(item.value);
            }
            return ListType.of(type.ydbType()).newValue(values);
        }

        OptionalType optional = type.ydbType().makeOptional();
        for (Item item: items) {
            if (item.value == NULL) {
                values.add(optional.emptyValue());
            } else {
                values.add(item.value.makeOptional());
            }
        }

        return ListType.of(optional).newValue(values);

    }

    private class Item implements JdbcPrm {
        private final String name;
        private final int index;
        private Value<?> value = null;

        Item(String listName, int index) {
            this.name = listName + "[" + index + "]";
            this.index = index;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public TypeDescription getType() {
            return type;
        }

        @Override
        public void setValue(Object obj, int sqlType) throws SQLException {
            if (type == null) {
                Type ydbType = types.findType(obj, sqlType);
                if (ydbType == null) {
                    if (obj == null) {
                        value = NULL;
                        return;
                    } else {
                        throw new SQLException(String.format(YdbConst.PARAMETER_TYPE_UNKNOWN, sqlType, obj));
                    }
                }

                type = types.find(ydbType);
            }

            if (obj == null) {
                value = NULL;
                return;
            }

            value = type.toYdbValue(obj);
        }

        @Override
        public void copyToParams(Params params) throws SQLException {
            if (index == 0) { // first prm
                params.put(listName, buildList());
            }
        }

        @Override
        public void reset() {
            value = null;
            if (index == items.size() - 1) { // last prm reset type
                type = null;
            }
        }
    }
}
