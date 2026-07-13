package tech.ydb.jdbc.context;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT 1 + 2")) {
                Assertions.assertTrue(rs.next());
            }
        }
        Assertions.assertTrue(custom.countersCreated > 0);
    }

    @Test
    public void customMeterSupplierTest() throws SQLException {
        TestMeter impl = new TestMeter();
        Supplier<Meter> generator = () -> impl;
        Properties props = new Properties();
        props.put("withMeter", generator);
        try (Connection conn = DriverManager.getConnection(jdbcUrl.build(), props)) {
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT 1 + 2")) {
                Assertions.assertTrue(rs.next());
            }
        }
        Assertions.assertTrue(impl.countersCreated > 0);
    }

    private class TestMeter implements Meter {
        private int countersCreated = 0;

        @Override
        public LongCounter createCounter(String name, String unit, String description) {
            countersCreated++;
            return LongCounter.NOOP;
        }
    }
}
