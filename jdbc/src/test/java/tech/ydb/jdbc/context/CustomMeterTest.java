package tech.ydb.jdbc.context;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import tech.ydb.core.metrics.Attr;
import tech.ydb.core.metrics.LongCounter;
import tech.ydb.core.metrics.Meter;
import tech.ydb.jdbc.impl.helper.JdbcUrlHelper;
import tech.ydb.test.junit5.YdbHelperExtension;

public class CustomMeterTest {
    @RegisterExtension
    private static final YdbHelperExtension ydb = new YdbHelperExtension();

    private static final JdbcUrlHelper jdbcUrl = new JdbcUrlHelper(ydb);

    @Test
    public void customMeterTest() throws SQLException {
        TestMeter custom = new TestMeter();
        Properties props = new Properties();
        props.put("withMeter", custom);
        try (Connection conn = DriverManager.getConnection(jdbcUrl.build(), props)) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 + 2")) {
                try (ResultSet rs = ps.executeQuery()) {
                    Assertions.assertTrue(rs.next());
                }
            }
        }
        Assertions.assertTrue(custom.countersCreated > 0);
        Assertions.assertTrue(custom.attributes.containsKey("ydb.table.session.pool.name"));
        Assertions.assertTrue(custom.attributes.containsKey("ydb.query.session.pool.name"));
        Assertions.assertEquals("jdbc", custom.attributes.get("ydb.table.session.pool.name"));
        Assertions.assertEquals("jdbc", custom.attributes.get("ydb.query.session.pool.name"));
    }

    @Test
    public void customMeterSupplierTest() throws SQLException {
        TestMeter impl = new TestMeter();
        Supplier<Meter> generator = () -> impl;
        Properties props = new Properties();
        props.put("withMeter", generator);
        props.put("meterPoolName", "driver1");
        try (Connection conn = DriverManager.getConnection(jdbcUrl.build(), props)) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 + 2")) {
                try (ResultSet rs = ps.executeQuery()) {
                    Assertions.assertTrue(rs.next());
                }
            }
        }
        Assertions.assertTrue(impl.countersCreated > 0);
        Assertions.assertTrue(impl.attributes.containsKey("ydb.table.session.pool.name"));
        Assertions.assertTrue(impl.attributes.containsKey("ydb.query.session.pool.name"));
        Assertions.assertEquals("driver1", impl.attributes.get("ydb.table.session.pool.name"));
        Assertions.assertEquals("driver1", impl.attributes.get("ydb.query.session.pool.name"));
    }

    private class TestMeter implements Meter {
        private int countersCreated = 0;
        private final Map<String, String> attributes = new HashMap<>();

        @Override
        public LongCounter createCounter(String name, String unit, String description) {
            countersCreated++;
            return (long value, Attr... attrs) -> {
                for (Attr attr: attrs) {
                    attributes.put(attr.getKey(), attr.getValue());
                }
            };
        }
    }
}
