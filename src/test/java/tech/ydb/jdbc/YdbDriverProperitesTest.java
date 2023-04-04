package tech.ydb.jdbc;

import java.io.File;
import java.io.IOException;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tech.ydb.auth.AuthProvider;
import tech.ydb.jdbc.exception.YdbConfigurationException;
import tech.ydb.jdbc.settings.ParsedProperty;
import tech.ydb.jdbc.settings.YdbClientProperty;
import tech.ydb.jdbc.settings.YdbConnectionProperties;
import tech.ydb.jdbc.settings.YdbConnectionProperty;
import tech.ydb.jdbc.settings.YdbJdbcTools;
import tech.ydb.jdbc.settings.YdbOperationProperties;
import tech.ydb.jdbc.settings.YdbOperationProperty;
import tech.ydb.jdbc.settings.YdbProperties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.ydb.jdbc.impl.helper.TestHelper.assertThrowsMsg;
import static tech.ydb.jdbc.impl.helper.TestHelper.assertThrowsMsgLike;

public class YdbDriverProperitesTest {
    private static final Logger logger = Logger.getLogger(YdbDriverProperitesTest.class.getName());
    public static final String TOKEN_FROM_FILE = "token-from-file";
    public static final String CERTIFICATE_FROM_FILE = "certificate-from-file";

    private static File TOKEN_FILE;
    private static File CERTIFICATE_FILE;

    private YdbDriver driver;

    @BeforeAll
    public static void beforeAll() throws YdbConfigurationException, IOException {
        TOKEN_FILE = safeCreateFile(TOKEN_FROM_FILE);
        CERTIFICATE_FILE = safeCreateFile(CERTIFICATE_FROM_FILE);
    }

    @AfterAll
    public static void afterAll() throws SQLException {
        safeDeleteFile(TOKEN_FILE);
        safeDeleteFile(CERTIFICATE_FILE);
    }

    @BeforeEach
    public void beforeEach() {
        driver = new YdbDriver();
    }

    @AfterEach
    public void afterEach() {
        driver.close();
    }

    @Test
    public void connectToUnsupportedUrl() throws SQLException {
        assertNull(driver.connect("jdbc:clickhouse:localhost:123", new Properties()));
    }

    @SuppressWarnings("UnstableApiUsage")
    @ParameterizedTest
    @MethodSource("urlsToParse")
    public void parseURL(String url, HostAndPort hostAndPort,
                  @Nullable String database,
                  @Nullable String localDatacenter) throws SQLException {
        YdbProperties props = YdbJdbcTools.from(url, new Properties());
        YdbConnectionProperties connectionProperties = props.getConnectionProperties();
        assertEquals(hostAndPort, connectionProperties.getAddress());
        assertEquals(database, connectionProperties.getDatabase());

        ParsedProperty dcProperty = connectionProperties.getParams().get(YdbConnectionProperty.LOCAL_DATACENTER);
        assertEquals(localDatacenter, Optional.ofNullable(dcProperty)
                .map(ParsedProperty::getParsedValue)
                .orElse(null));

    }

    @ParameterizedTest
    @MethodSource("urlsToCheck")
    public void acceptsURL(String url, boolean accept, String expectDatabase) throws SQLException {
        assertEquals(accept, driver.acceptsURL(url));
        DriverPropertyInfo[] properties = driver.getPropertyInfo(url, new Properties());
        assertNotNull(properties);

        if (accept) {
            YdbProperties ydbProperties = YdbJdbcTools.from(url, new Properties());
            assertNotNull(ydbProperties);
            assertEquals(expectDatabase, ydbProperties.getConnectionProperties().getDatabase());
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    @Test
    public void getPropertyInfoDefault() throws SQLException {
        String url = "jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci";

        Properties properties = new Properties();
        DriverPropertyInfo[] propertyInfo = driver.getPropertyInfo(url, properties);
        assertEquals(new Properties(), properties);

        List<String> actual = convertPropertyInfo(propertyInfo);
        actual.forEach(logger::info);

        List<String> expect = convertPropertyInfo(defaultPropertyInfo());
        assertEquals(expect, actual);

        YdbProperties ydbProperties = YdbJdbcTools.from(url, properties);
        assertEquals(HostAndPort.fromParts("ydb-ru-prestable.yandex.net", 2135),
                ydbProperties.getConnectionProperties().getAddress());
        assertEquals("/ru-prestable/ci/testing/ci",
                ydbProperties.getConnectionProperties().getDatabase());
    }

    @Test
    public void getPropertyInfoAllFromUrl() throws SQLException {
        String url = "jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci?" +
                customizedProperties().entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining("&"));

        Properties properties = new Properties();
        DriverPropertyInfo[] propertyInfo = driver.getPropertyInfo(url, properties);
        assertEquals(new Properties(), properties);

        List<String> actual = convertPropertyInfo(propertyInfo);
        actual.forEach(logger::info);

        List<String> expect = convertPropertyInfo(customizedPropertyInfo());
        assertEquals(expect, actual);

        YdbProperties ydbProperties = YdbJdbcTools.from(url, properties);
        checkCustomizedProperties(ydbProperties);
    }

    @Test
    public void getPropertyInfoFromProperties() throws SQLException {
        String url = "jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci";

        Properties properties = customizedProperties();
        Properties copy = new Properties();
        copy.putAll(properties);

        DriverPropertyInfo[] propertyInfo = driver.getPropertyInfo(url, properties);
        assertEquals(copy, properties);

        List<String> actual = convertPropertyInfo(propertyInfo);
        actual.forEach(logger::info);

        List<String> expect = convertPropertyInfo(customizedPropertyInfo());
        assertEquals(expect, actual);

        YdbProperties ydbProperties = YdbJdbcTools.from(url, properties);
        checkCustomizedProperties(ydbProperties);
    }

    @SuppressWarnings("UnstableApiUsage")
    @Test
    public void getPropertyInfoOverwrite() throws SQLException {
        String url = "jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci?localDatacenter=sas";
        Properties properties = new Properties();
        properties.put("localDatacenter", "vla");

        Properties copy = new Properties();
        copy.putAll(properties);

        DriverPropertyInfo[] propertyInfo = driver.getPropertyInfo(url, properties);
        assertEquals(copy, properties);

        List<String> actual = convertPropertyInfo(propertyInfo);
        actual.forEach(logger::info);

        // URL will always overwrite properties
        List<String> expect = convertPropertyInfo(defaultPropertyInfo("sas"));
        assertEquals(expect, actual);

        YdbProperties ydbProperties = YdbJdbcTools.from(url, properties);
        assertEquals(HostAndPort.fromParts("ydb-ru-prestable.yandex.net", 2135),
                ydbProperties.getConnectionProperties().getAddress());
        assertEquals("/ru-prestable/ci/testing/ci",
                ydbProperties.getConnectionProperties().getDatabase());
    }

    @Test
    public void getPropertyInfoAuthProvider() throws SQLException {
        AuthProvider customAuthProvider = () -> () -> "any";

        Properties properties = new Properties();
        properties.put(YdbConnectionProperty.AUTH_PROVIDER.getName(), customAuthProvider);

        Properties copy = new Properties();
        copy.putAll(properties);

        String url = "jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci";
        DriverPropertyInfo[] propertyInfo = driver.getPropertyInfo(url, properties);

        assertEquals(copy, properties); // Provided properties were not changed

        List<String> actual = convertPropertyInfo(propertyInfo);
        actual.forEach(logger::info);

        List<String> expect = convertPropertyInfo(defaultPropertyInfo());
        assertEquals(expect, actual);


        YdbProperties ydbProperties = YdbJdbcTools.from(url, properties);
        ParsedProperty auth = ydbProperties.getConnectionProperties().getProperty(YdbConnectionProperty.AUTH_PROVIDER);
        assertNotNull(auth);
        assertEquals(customAuthProvider, auth.getParsedValue());
    }


    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("tokensToCheck")
    public void getTokenAs(String token, String expectValue) throws SQLException {
        if ("file:".equals(token)) {
            token += TOKEN_FILE.getAbsolutePath();
        }

        String url = "jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci?token=" + token;
        Properties properties = new Properties();
        YdbProperties ydbProperties = YdbJdbcTools.from(url, properties);

        YdbConnectionProperties props = ydbProperties.getConnectionProperties();
        assertEquals(expectValue, ((AuthProvider)props.getProperty(YdbConnectionProperty.TOKEN).getParsedValue())
                .createAuthIdentity(null).getToken());
    }

    @ParameterizedTest
    @MethodSource("unknownFiles")
    public void getTokenAsInvalid(String token, String expectException) {
        String url = "jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci?token=" + token;

        assertThrowsMsgLike(YdbConfigurationException.class,
                () -> YdbJdbcTools.from(url, new Properties()),
                expectException);
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("certificatesToCheck")
    public void getCaCertificateAs(String certificate, String expectValue) throws SQLException {
        if ("file:".equals(certificate)) {
            certificate += CERTIFICATE_FILE.getAbsolutePath();
        }
        String url = "jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci" +
                "?secureConnectionCertificate=" + certificate;
        Properties properties = new Properties();
        YdbProperties ydbProperties = YdbJdbcTools.from(url, properties);

        YdbConnectionProperties props = ydbProperties.getConnectionProperties();
        assertArrayEquals(expectValue.getBytes(),
                props.getProperty(YdbConnectionProperty.SECURE_CONNECTION_CERTIFICATE).getParsedValue());
    }

    @ParameterizedTest
    @MethodSource("unknownFiles")
    public void getCaCertificateAsInvalid(String certificate, String expectException) {
        String url = "jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci" +
                "?secureConnectionCertificate=" + certificate;
        assertThrowsMsgLike(YdbConfigurationException.class,
                () -> YdbJdbcTools.from(url, new Properties()),
                expectException);
    }

    @ParameterizedTest
    @MethodSource("invalidDurationParams")
    public void invalidDuration(String param) {
        String url = "jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci?" + param + "=1bc";
        assertThrowsMsg(YdbConfigurationException.class,
                () -> YdbJdbcTools.from(url, new Properties()),
                "Unable to convert property " + param +
                        ": Unable to parse value [1bc] -> [PT1BC] as Duration: Text cannot be parsed to a Duration");
    }

    @ParameterizedTest
    @MethodSource("invalidIntegerParams")
    public void invalidInteger(String param) {
        String url = "jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci?" + param + "=1bc";
        assertThrowsMsg(YdbConfigurationException.class,
                () -> YdbJdbcTools.from(url, new Properties()),
                "Unable to convert property " + param +
                        ": Unable to parse value [1bc] as Integer: For input string: \"1bc\"");
    }

    @Test
    public void invalidAuthProviderProperty() {
        String url = "jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci?authProvider=test";
        assertThrowsMsg(YdbConfigurationException.class,
                () -> YdbJdbcTools.from(url, new Properties()),
                "Unable to convert property authProvider: " +
                        "Property authProvider must be configured with object, not a string");
    }

    @Test
    public void getMajorVersion() {
        assertEquals(2, driver.getMajorVersion());
    }

    @Test
    public void getMinorVersion() {
        assertTrue(driver.getMinorVersion() >= 0);
    }

    @Test
    public void jdbcCompliant() {
        assertFalse(driver.jdbcCompliant());
    }

    @Test
    public void getParentLogger() throws SQLFeatureNotSupportedException {
        Assertions.assertNotNull(driver.getParentLogger());
    }

    static List<String> convertPropertyInfo(DriverPropertyInfo[] propertyInfo) {
        return Stream.of(propertyInfo)
                .map(YdbDriverProperitesTest::asString)
                .collect(Collectors.toList());
    }

    static DriverPropertyInfo[] defaultPropertyInfo() {
        return defaultPropertyInfo(null);
    }

    static DriverPropertyInfo[] defaultPropertyInfo(@Nullable String localDatacenter) {
        return new DriverPropertyInfo[]{
                YdbConnectionProperty.DATABASE.toDriverPropertyInfo("/ru-prestable/ci/testing/ci"),
                YdbConnectionProperty.LOCAL_DATACENTER.toDriverPropertyInfo(localDatacenter),
                YdbConnectionProperty.SECURE_CONNECTION.toDriverPropertyInfo(null),
                YdbConnectionProperty.SECURE_CONNECTION_CERTIFICATE.toDriverPropertyInfo(null),
                YdbConnectionProperty.READ_TIMEOUT.toDriverPropertyInfo(null),
                YdbConnectionProperty.TOKEN.toDriverPropertyInfo(null),
                YdbConnectionProperty.AUTH_PROVIDER.toDriverPropertyInfo(null),

                YdbClientProperty.KEEP_QUERY_TEXT.toDriverPropertyInfo(null),
                YdbClientProperty.SESSION_KEEP_ALIVE_TIME.toDriverPropertyInfo(null),
                YdbClientProperty.SESSION_MAX_IDLE_TIME.toDriverPropertyInfo(null),
                YdbClientProperty.SESSION_POOL_SIZE_MIN.toDriverPropertyInfo(null),
                YdbClientProperty.SESSION_POOL_SIZE_MAX.toDriverPropertyInfo(null),

                YdbOperationProperty.JOIN_DURATION.toDriverPropertyInfo("5m"),
                YdbOperationProperty.KEEP_IN_QUERY_CACHE.toDriverPropertyInfo("false"),
                YdbOperationProperty.QUERY_TIMEOUT.toDriverPropertyInfo("0s"),
                YdbOperationProperty.SCAN_QUERY_TIMEOUT.toDriverPropertyInfo("1m"),
                YdbOperationProperty.FAIL_ON_TRUNCATED_RESULT.toDriverPropertyInfo("true"),
                YdbOperationProperty.SESSION_TIMEOUT.toDriverPropertyInfo("5s"),
                YdbOperationProperty.DEADLINE_TIMEOUT.toDriverPropertyInfo("0s"),
                YdbOperationProperty.AUTOCOMMIT.toDriverPropertyInfo("false"),
                YdbOperationProperty.TRANSACTION_LEVEL.toDriverPropertyInfo("8"),

                YdbOperationProperty.AUTO_PREPARED_BATCHES.toDriverPropertyInfo("true"),
                YdbOperationProperty.ENFORCE_SQL_V1.toDriverPropertyInfo("true"),
                YdbOperationProperty.ENFORCE_VARIABLE_PREFIX.toDriverPropertyInfo("true"),
                YdbOperationProperty.CACHE_CONNECTIONS_IN_DRIVER.toDriverPropertyInfo("true"),
                YdbOperationProperty.DETECT_SQL_OPERATIONS.toDriverPropertyInfo("true"),
                YdbOperationProperty.ALWAYS_PREPARE_DATAQUERY.toDriverPropertyInfo("true"),
                YdbOperationProperty.TRANSFORM_STANDARD_JDBC_QUERIES.toDriverPropertyInfo("false"),
                YdbOperationProperty.TRANSFORMED_JDBC_QUERIES_CACHE.toDriverPropertyInfo("0")
        };
    }

    static Properties customizedProperties() {
        Properties properties = new Properties();
        properties.setProperty("localDatacenter", "sas");
        properties.setProperty("secureConnection", "true");
        properties.setProperty("readTimeout", "2m");
        properties.setProperty("token", "x-secured-token");

        properties.setProperty("keepQueryText", "true");
        properties.setProperty("sessionKeepAliveTime", "15m");
        properties.setProperty("sessionMaxIdleTime", "5m");
        properties.setProperty("sessionPoolSizeMin", "3");
        properties.setProperty("sessionPoolSizeMax", "4");

        properties.setProperty("joinDuration", "6m");
        properties.setProperty("keepInQueryCache", "true");
        properties.setProperty("queryTimeout", "2m");
        properties.setProperty("scanQueryTimeout", "3m");
        properties.setProperty("failOnTruncatedResult", "false");
        properties.setProperty("sessionTimeout", "6s");
        properties.setProperty("deadlineTimeout", "1s");
        properties.setProperty("autoCommit", "true");
        properties.setProperty("transactionLevel", "4");

        properties.setProperty("autoPreparedBatches", "false");
        properties.setProperty("enforceSqlV1", "false");
        properties.setProperty("enforceVariablePrefix", "false");
        properties.setProperty("cacheConnectionsInDriver", "false");
        properties.setProperty("detectSqlOperations", "false");
        properties.setProperty("alwaysPrepareDataQuery", "false");
        properties.setProperty("transformStandardJdbcQueries", "true");
        properties.setProperty("transformedJdbcQueriesCache", "1000");
        return properties;
    }

    static DriverPropertyInfo[] customizedPropertyInfo() {
        return new DriverPropertyInfo[]{
                YdbConnectionProperty.DATABASE.toDriverPropertyInfo("/ru-prestable/ci/testing/ci"),
                YdbConnectionProperty.LOCAL_DATACENTER.toDriverPropertyInfo("sas"),
                YdbConnectionProperty.SECURE_CONNECTION.toDriverPropertyInfo("true"),
                YdbConnectionProperty.SECURE_CONNECTION_CERTIFICATE.toDriverPropertyInfo(null),
                YdbConnectionProperty.READ_TIMEOUT.toDriverPropertyInfo("2m"),
                YdbConnectionProperty.TOKEN.toDriverPropertyInfo("x-secured-token"),
                YdbConnectionProperty.AUTH_PROVIDER.toDriverPropertyInfo(null),

                YdbClientProperty.KEEP_QUERY_TEXT.toDriverPropertyInfo("true"),
                YdbClientProperty.SESSION_KEEP_ALIVE_TIME.toDriverPropertyInfo("15m"),
                YdbClientProperty.SESSION_MAX_IDLE_TIME.toDriverPropertyInfo("5m"),
                YdbClientProperty.SESSION_POOL_SIZE_MIN.toDriverPropertyInfo("3"),
                YdbClientProperty.SESSION_POOL_SIZE_MAX.toDriverPropertyInfo("4"),

                YdbOperationProperty.JOIN_DURATION.toDriverPropertyInfo("6m"),
                YdbOperationProperty.KEEP_IN_QUERY_CACHE.toDriverPropertyInfo("true"),
                YdbOperationProperty.QUERY_TIMEOUT.toDriverPropertyInfo("2m"),
                YdbOperationProperty.SCAN_QUERY_TIMEOUT.toDriverPropertyInfo("3m"),
                YdbOperationProperty.FAIL_ON_TRUNCATED_RESULT.toDriverPropertyInfo("false"),
                YdbOperationProperty.SESSION_TIMEOUT.toDriverPropertyInfo("6s"),
                YdbOperationProperty.DEADLINE_TIMEOUT.toDriverPropertyInfo("1s"),
                YdbOperationProperty.AUTOCOMMIT.toDriverPropertyInfo("true"),
                YdbOperationProperty.TRANSACTION_LEVEL.toDriverPropertyInfo("4"),

                YdbOperationProperty.AUTO_PREPARED_BATCHES.toDriverPropertyInfo("false"),
                YdbOperationProperty.ENFORCE_SQL_V1.toDriverPropertyInfo("false"),
                YdbOperationProperty.ENFORCE_VARIABLE_PREFIX.toDriverPropertyInfo("false"),
                YdbOperationProperty.CACHE_CONNECTIONS_IN_DRIVER.toDriverPropertyInfo("false"),
                YdbOperationProperty.DETECT_SQL_OPERATIONS.toDriverPropertyInfo("false"),
                YdbOperationProperty.ALWAYS_PREPARE_DATAQUERY.toDriverPropertyInfo("false"),
                YdbOperationProperty.TRANSFORM_STANDARD_JDBC_QUERIES.toDriverPropertyInfo("true"),
                YdbOperationProperty.TRANSFORMED_JDBC_QUERIES_CACHE.toDriverPropertyInfo("1000")
        };
    }

    @SuppressWarnings("UnstableApiUsage")
    static void checkCustomizedProperties(YdbProperties properties) {
        YdbConnectionProperties conn = properties.getConnectionProperties();
        assertEquals(HostAndPort.fromParts("ydb-ru-prestable.yandex.net", 2135),
                conn.getAddress());
        assertEquals("/ru-prestable/ci/testing/ci",
                conn.getDatabase());

        YdbOperationProperties ops = properties.getOperationProperties();
        assertEquals(Duration.ofMinutes(6), ops.getJoinDuration());
        assertTrue(ops.isKeepInQueryCache());
        assertEquals(Duration.ofMinutes(2), ops.getQueryTimeout());
        assertEquals(Duration.ofMinutes(3), ops.getScanQueryTimeout());
        assertFalse(ops.isFailOnTruncatedResult());
        assertEquals(Duration.ofSeconds(6), ops.getSessionTimeout());
        assertTrue(ops.isAutoCommit());
        assertEquals(YdbConst.ONLINE_CONSISTENT_READ_ONLY, ops.getTransactionLevel());
        assertFalse(ops.isAutoPreparedBatches());
        assertFalse(ops.isEnforceSqlV1());
        assertFalse(ops.isEnforceVariablePrefix());
        assertFalse(ops.isCacheConnectionsInDriver());
        assertFalse(ops.isDetectSqlOperations());
    }

    static String asString(DriverPropertyInfo info) {
        assertNull(info.choices);
        return String.format("%s=%s (%s, required = %s)", info.name, info.value, info.description, info.required);
    }

    @SuppressWarnings("UnstableApiUsage")
    static Collection<Arguments> urlsToParse() {
        return Arrays.asList(
                Arguments.of("jdbc:ydb:ydb-ru-prestable.yandex.net:2135",
                        HostAndPort.fromParts("ydb-ru-prestable.yandex.net", 2135),
                        null,
                        null),
                Arguments.of("jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci",
                        HostAndPort.fromParts("ydb-ru-prestable.yandex.net", 2135),
                        "/ru-prestable/ci/testing/ci",
                        null),
                Arguments.of("jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci?localDatacenter=man",
                        HostAndPort.fromParts("ydb-ru-prestable.yandex.net", 2135),
                        "/ru-prestable/ci/testing/ci",
                        "man"),
                Arguments.of("jdbc:ydb:ydb-ru-prestable.yandex.net:2135?localDatacenter=man",
                        HostAndPort.fromParts("ydb-ru-prestable.yandex.net", 2135),
                        null,
                        "man")
        );
    }

    static Collection<Arguments> urlsToCheck() {
        return Arrays.asList(
                Arguments.of("jdbc:ydb:",
                        true, null),
                Arguments.of("jdbc:ydb:ydb-ru-prestable.yandex.net:2135",
                        true, null),
                Arguments.of("jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci",
                        true, "/ru-prestable/ci/testing/ci"),
                Arguments.of("jdbc:ydb:ydb-ru-prestable.yandex.net:2135?database=ru-prestable/ci/testing/ci",
                        true, "/ru-prestable/ci/testing/ci"),
                Arguments.of("jdbc:ydb:ydb-ru-prestable.yandex.net:2135?database=/ru-prestable/ci/testing/ci",
                        true, "/ru-prestable/ci/testing/ci"),
                Arguments.of("jdbc:ydb:ydb-ru-prestable.yandex.net:2135/ru-prestable/ci/testing/ci?dc=man",
                        true, "/ru-prestable/ci/testing/ci"),
                Arguments.of("jdbc:ydb:ydb-ru-prestable.yandex.net:2135?database=ru-prestable/ci/testing/ci&dc=man",
                        true, "/ru-prestable/ci/testing/ci"),
                Arguments.of("jdbc:ydb:ydb-ru-prestable.yandex.net:2135?dc=man&database=ru-prestable/ci/testing/ci",
                        true, "/ru-prestable/ci/testing/ci"),
                Arguments.of("ydb:",
                        false, null),
                Arguments.of("jdbc:ydb",
                        false, null),
                Arguments.of("jdbc:clickhouse://man",
                        false, null)
        );
    }

    static Collection<Arguments> tokensToCheck() {
        return Arrays.asList(
                Arguments.of("classpath:data/token.txt", "token-from-classpath"),
                Arguments.of("file:", TOKEN_FROM_FILE));
    }

    static Collection<Arguments> certificatesToCheck() {
        return Arrays.asList(
                Arguments.of("classpath:data/certificate.txt", "certificate-from-classpath"),
                Arguments.of("file:", CERTIFICATE_FROM_FILE));
    }

    static Collection<Arguments> unknownFiles() {
        return Arrays.asList(
                Arguments.of("classpath:data/unknown-file.txt",
                        "Unable to find classpath resource: classpath:data/unknown-file.txt"),
                Arguments.of("file:data/unknown-file.txt",
                        "Unable to read resource from file:data/unknown-file.txt"));
    }

    static Collection<Arguments> invalidDurationParams() {
        return Arrays.asList(
                Arguments.of("readTimeout"),
                Arguments.of("sessionKeepAliveTime"),
                Arguments.of("sessionMaxIdleTime"),
                Arguments.of("joinDuration"),
                Arguments.of("queryTimeout"),
                Arguments.of("scanQueryTimeout"),
                Arguments.of("sessionTimeout"),
                Arguments.of("deadlineTimeout")
        );
    }

    static Collection<Arguments> invalidIntegerParams() {
        return Arrays.asList(
                Arguments.of("sessionPoolSizeMin"),
                Arguments.of("sessionPoolSizeMax"),
                Arguments.of("transactionLevel")
        );
    }

    @SuppressWarnings("UnstableApiUsage")
    private static File safeCreateFile(String content) throws IOException {
        File file = File.createTempFile("junit", "ydb");
        Files.write(content.getBytes(), file);
        return file;
    }

    private static void safeDeleteFile(@Nullable File file) {
        if (file != null) {
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
    }
}