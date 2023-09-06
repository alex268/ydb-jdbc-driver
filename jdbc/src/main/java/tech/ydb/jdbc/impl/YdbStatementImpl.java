package tech.ydb.jdbc.impl;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import tech.ydb.jdbc.YdbConnection;
import tech.ydb.jdbc.YdbConst;
import tech.ydb.jdbc.YdbResultSet;
import tech.ydb.jdbc.query.YdbQuery;
import tech.ydb.table.query.Params;

public class YdbStatementImpl extends BaseYdbStatement {
    private static final Logger LOGGER = Logger.getLogger(YdbStatementImpl.class.getName());

    private final List<String> batch = new ArrayList<>();

    public YdbStatementImpl(YdbConnection connection, int resultSetType) {
        super(LOGGER, connection, resultSetType, false); // is not poolable by default
    }

    @Override
    public void executeSchemeQuery(String sql) throws SQLException {
        cleanState();
        clearBatch();

        YdbQuery query = parseYdbQuery(sql);
        executeSchemeQuery(query);
    }

    @Override
    public YdbResultSet executeScanQuery(String sql) throws SQLException {
        cleanState();
        clearBatch();

        YdbQuery query = parseYdbQuery(sql);
        ResultState results = executeScanQuery(query, Params.empty());
        if (!updateState(results)) {
            throw new SQLException(YdbConst.QUERY_EXPECT_RESULT_SET);
        }
        return getResultSet();
    }

    @Override
    public YdbResultSet executeExplainQuery(String sql) throws SQLException {
        cleanState();
        clearBatch();

        YdbQuery query = parseYdbQuery(sql);
        ResultState newState = executeExplainQuery(query);
        if (!updateState(newState)) {
            throw new SQLException(YdbConst.QUERY_EXPECT_RESULT_SET);
        }
        return getResultSet();
    }

    @Override
    public YdbResultSet executeQuery(String sql) throws SQLException {
        if (!execute(sql)) {
            throw new SQLException(YdbConst.QUERY_EXPECT_RESULT_SET);
        }
        return getResultSet();
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        if (execute(sql)) {
            throw new SQLException(YdbConst.QUERY_EXPECT_UPDATE);
        }
        return getUpdateCount();
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        cleanState();

        YdbQuery query = parseYdbQuery(sql);
        ResultState newState = EMPTY_STATE;
        switch (query.type()) {
            case SCHEME_QUERY:
                executeSchemeQuery(query);
                break;
            case DATA_QUERY:
                newState = executeDataQuery(query, Params.empty());
                break;
            case SCAN_QUERY:
                newState = executeScanQuery(query, Params.empty());
                break;
            case EXPLAIN_QUERY:
                newState = executeExplainQuery(query);
                break;
            default:
                throw new IllegalStateException("Internal error. Unsupported query type " + query.type());
        }

        return updateState(newState);
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        cleanState();
        batch.add(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        batch.clear();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        cleanState();

        if (batch.isEmpty()) {
            LOGGER.log(Level.FINE, "Batch is empty, nothing to execute");
            return new int[0];
        }

        try {
            LOGGER.log(Level.FINE, "Executing batch of {0} item(s)", batch.size());

            String sql = String.join(";\n", batch);
            execute(sql);

            int[] ret = new int[batch.size()];
            Arrays.fill(ret, SUCCESS_NO_INFO);
            return ret;
        } finally {
            clearBatch();
        }
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        if (autoGeneratedKeys != Statement.NO_GENERATED_KEYS) {
            throw new SQLFeatureNotSupportedException(YdbConst.AUTO_GENERATED_KEYS_UNSUPPORTED);
        }
        return executeUpdate(sql);
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        if (autoGeneratedKeys != Statement.NO_GENERATED_KEYS) {
            throw new SQLFeatureNotSupportedException(YdbConst.AUTO_GENERATED_KEYS_UNSUPPORTED);
        }
        return execute(sql);
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        throw new SQLFeatureNotSupportedException(YdbConst.AUTO_GENERATED_KEYS_UNSUPPORTED);
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        throw new SQLFeatureNotSupportedException(YdbConst.AUTO_GENERATED_KEYS_UNSUPPORTED);
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        throw new SQLFeatureNotSupportedException(YdbConst.AUTO_GENERATED_KEYS_UNSUPPORTED);
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        throw new SQLFeatureNotSupportedException(YdbConst.AUTO_GENERATED_KEYS_UNSUPPORTED);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        throw new SQLException(YdbConst.CANNOT_UNWRAP_TO + iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isAssignableFrom(getClass());
    }
}
