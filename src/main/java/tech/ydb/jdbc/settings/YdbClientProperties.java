package tech.ydb.jdbc.settings;

import java.util.Map;

import javax.annotation.Nullable;
import tech.ydb.core.grpc.GrpcTransport;

import tech.ydb.table.TableClient;

public class YdbClientProperties {
    private final Map<YdbClientProperty<?>, ParsedProperty> params;

    public YdbClientProperties(Map<YdbClientProperty<?>, ParsedProperty> params) {
        this.params = params;
    }

    @Nullable
    public ParsedProperty getProperty(YdbClientProperty<?> property) {
        return params.get(property);
    }

    public Map<YdbClientProperty<?>, ParsedProperty> getParams() {
        return params;
    }

    public TableClient toTableClient(GrpcTransport grpc) {
        TableClient.Builder builder = TableClient.newClient(grpc);
        for (Map.Entry<YdbClientProperty<?>, ParsedProperty> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                entry.getKey().getSetter().accept(builder, entry.getValue().getParsedValue());
            }
        }
        ParsedProperty minSize = params.get(YdbClientProperty.SESSION_POOL_SIZE_MIN);
        ParsedProperty maxSize = params.get(YdbClientProperty.SESSION_POOL_SIZE_MAX);
        if (minSize != null && maxSize != null) {
            builder.sessionPoolSize(minSize.getParsedValue(), maxSize.getParsedValue());
        }
        return builder.build();
    }
}
