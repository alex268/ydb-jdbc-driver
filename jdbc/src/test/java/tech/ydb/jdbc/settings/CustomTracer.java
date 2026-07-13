package tech.ydb.jdbc.settings;

import java.util.function.Supplier;

import tech.ydb.core.tracing.Span;
import tech.ydb.core.tracing.SpanKind;
import tech.ydb.core.tracing.Tracer;

/**
 *
 * @author Aleksandr Gorshenin {@literal <alexandr268@ydb.tech>}
 */
public class CustomTracer implements Tracer {
    public static class Generator implements Supplier<Tracer> {
        @Override
        public Tracer get() {
            return new CustomTracer();
        }
    }

    @Override
    public Span startSpan(String spanName, SpanKind spanKind) {
        return Span.NOOP;
    }

    @Override
    public Span currentSpan() {
        return Span.NOOP;
    }
}
