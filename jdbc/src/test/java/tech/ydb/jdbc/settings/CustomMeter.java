package tech.ydb.jdbc.settings;

import java.util.function.Supplier;

import tech.ydb.core.metrics.Meter;

/**
 *
 * @author Aleksandr Gorshenin {@literal <alexandr268@ydb.tech>}
 */
public class CustomMeter implements Meter {
    public static class Generator implements Supplier<Meter> {
        @Override
        public Meter get() {
            return new CustomMeter();
        }
    }
}
