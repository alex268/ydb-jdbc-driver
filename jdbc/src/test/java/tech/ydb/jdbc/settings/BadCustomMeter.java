package tech.ydb.jdbc.settings;

import java.util.function.Consumer;

import tech.ydb.core.metrics.DoubleHistogram;
import tech.ydb.core.metrics.LongCounter;
import tech.ydb.core.metrics.LongMeasurement;
import tech.ydb.core.metrics.Meter;

/**
 *
 * @author Aleksandr Gorshenin {@literal <alexandr268@ydb.tech>}
 */
public class BadCustomMeter implements Meter {
    private final Meter delegate;

    public BadCustomMeter(Meter delegate) {
        this.delegate = delegate;
    }

    @Override
    public LongCounter createCounter(String name, String unit, String description) {
        return delegate.createCounter(name, unit, description);
    }

    @Override
    public void createLongGauge(String name, String unit, String description, Consumer<LongMeasurement> callback) {
        delegate.createLongGauge(name, unit, description, callback);
    }

    @Override
    public DoubleHistogram createHistogram(String name, String unit, String description) {
        return delegate.createHistogram(name, unit, description);
    }
}
