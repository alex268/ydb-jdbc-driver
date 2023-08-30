package tech.ydb.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import javax.annotation.Nullable;

import tech.ydb.jdbc.context.YdbQuery;
import tech.ydb.jdbc.context.YdbContext;
import tech.ydb.jdbc.context.YdbExecutor;
import tech.ydb.table.query.DataQueryResult;
import tech.ydb.table.query.ExplainDataQueryResult;
import tech.ydb.table.query.Params;
import tech.ydb.table.result.ResultSetReader;
import tech.ydb.table.settings.ExecuteDataQuerySettings;

public interface YdbConnection extends Connection {

    /**
     * Returns class with some type conversion capabilities
     *
     * @return ydb types converter
     */
    YdbTypes getYdbTypes();

    /**
     * Return current YDB transaction, if exists
     *
     * @return YDB transaction ID or null, if no transaction started
     */
    @Nullable
    String getYdbTxId();


    YdbContext getCtx();

    /**
     * Explicitly execute query as a schema query
     *
     * @param query query (DDL) to execute
     * @param executor executor for logging and warnings
     * @throws SQLException if query cannot be executed
     */
    void executeSchemeQuery(YdbQuery query, YdbExecutor executor) throws SQLException;

    /**
     * Explicitly execute query as a data query
     *
     * @param query query to execute
     * @param params parameters for query
     * @param settings settings of execution
     * @param executor executor for logging and warnings
     * @return list of result set
     * @throws SQLException if query cannot be executed
     */
    DataQueryResult executeDataQuery(YdbQuery query, YdbExecutor executor, ExecuteDataQuerySettings settings, Params params) throws SQLException;

    /**
     * Explicitly execute query as a scan query
     *
     * @param query query to execute
     * @param params parameters for query
     * @param executor executor for logging and warnings
     * @return single result set with rows
     * @throws SQLException if query cannot be executed
     */
    ResultSetReader executeScanQuery(YdbQuery query, YdbExecutor executor, Params params) throws SQLException;

    /**
     * Explicitly explain this query
     *
     * @param query query to explain
     * @param executor executor for logging and warnings
     * @return list of result set of two string columns: {@link YdbConst#EXPLAIN_COLUMN_AST}
     * and {@link YdbConst#EXPLAIN_COLUMN_PLAN}
     * @throws SQLException if query cannot be explained
     */
    ExplainDataQueryResult executeExplainQuery(YdbQuery query, YdbExecutor executor) throws SQLException;

    @Override
    YdbDatabaseMetaData getMetaData() throws SQLException;

    @Override
    YdbStatement createStatement() throws SQLException;

    @Override
    YdbStatement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException;

    @Override
    YdbPreparedStatement prepareStatement(String sql, int resultSetType,
                                          int resultSetConcurrency) throws SQLException;

    @Override
    YdbStatement createStatement(int resultSetType, int resultSetConcurrency,
                                 int resultSetHoldability) throws SQLException;

    @Override
    YdbPreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
                                          int resultSetHoldability) throws SQLException;

    @Override
    YdbPreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException;

    @Override
    YdbPreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException;

    @Override
    YdbPreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException;


    /**
     * Prepares statement depending on driver settings
     *
     * @param sql sql to execute
     * @return statement
     * @throws SQLException in case of any internal error
     */
    @Override
    YdbPreparedStatement prepareStatement(String sql) throws SQLException;

    /**
     * Prepares statement with explicit configuration
     *
     * @param sql  sql to prepare
     * @param mode prepare mode
     * @return prepared statement
     * @throws SQLException in case of any internal error
     */
    YdbPreparedStatement prepareStatement(String sql, YdbPrepareMode mode) throws SQLException;
}
