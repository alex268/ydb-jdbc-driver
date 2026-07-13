package tech.ydb.jdbc.settings;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;

import tech.ydb.auth.AuthProvider;
import tech.ydb.auth.TokenAuthProvider;
import tech.ydb.auth.iam.CloudAuthHelper;
import tech.ydb.core.auth.StaticCredentials;
import tech.ydb.core.grpc.BalancingSettings;
import tech.ydb.core.grpc.GrpcCompression;
import tech.ydb.core.grpc.GrpcTransportBuilder;
import tech.ydb.core.metrics.Meter;
import tech.ydb.core.tracing.Tracer;
import tech.ydb.jdbc.YdbDriver;
import tech.ydb.jdbc.common.JdbcDriverVersion;
import tech.ydb.query.QueryClient;
import tech.ydb.table.impl.PooledTableClient;


public class YdbConnectionProperties {
    private static final Logger LOGGER = Logger.getLogger(YdbDriver.class.getName());
    private static final String ID_PATTERN = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private static final Pattern FQCN = Pattern.compile(ID_PATTERN + "(\\." + ID_PATTERN + ")*");
    private static final String OPENTELEMETRY_TRACER_CLASS = "tech.ydb.core.tracing.OpenTelemetryTracer";
    private static final String OPENTELEMETRY_METER_CLASS = "tech.ydb.core.metrics.OpenTelemetryMeter";

    static final YdbProperty<String> TOKEN = YdbProperty.content(YdbConfig.TOKEN_KEY, "Authentication token");

    static final YdbProperty<String> TOKEN_FILE = YdbProperty.file("tokenFile",
            "Path to token file for the token-based authentication");

    static final YdbProperty<String> LOCAL_DATACENTER = YdbProperty.string("localDatacenter",
            "Local Datacenter");

    static final YdbProperty<Boolean> USE_SECURE_CONNECTION = YdbProperty.bool("secureConnection",
            "Use TLS connection");

    static final YdbProperty<byte[]> SECURE_CONNECTION_CERTIFICATE = YdbProperty.fileBytes(
            "secureConnectionCertificate", "Use TLS connection with certificate from provided path"
    );

    @Deprecated
    static final YdbProperty<String> SERVICE_ACCOUNT_FILE = YdbProperty.content("saFile",
            "Service account file based authentication");

    static final YdbProperty<String> SA_KEY_FILE = YdbProperty.file("saKeyFile",
            "Path to key file for the service account authentication");

    static final YdbProperty<Boolean> USE_METADATA = YdbProperty.bool("useMetadata",
            "Use metadata service for authentication");

    static final YdbProperty<String> IAM_ENDPOINT = YdbProperty.content("iamEndpoint",
            "Custom IAM endpoint for the service account authentication");

    static final YdbProperty<String> METADATA_URL = YdbProperty.content("metadataURL",
            "Custom URL for the metadata service authentication");

    static final YdbProperty<Object> CHANNEL_INITIALIZER = YdbProperty.object("channelInitializer",
            "Custom GRPC channel initilizer, use object instance or class full name impementing"
                    + " Consumer<ManagedChannelBuilder>");

    static final YdbProperty<Object> TOKEN_PROVIDER = YdbProperty.object("tokenProvider",
            "Custom token provider, use object instance or class full name impementing Supplier<String>");

    static final YdbProperty<String> GRPC_COMPRESSION = YdbProperty.string(
            "grpcCompression", "Use specified GRPC compressor (supported only none and gzip)"
    );

    static final YdbProperty<Object> WITH_TRACER = YdbProperty.object("withTracer",
            "Enable tracing for YDB client operations, object instance or class full name implementing "
                    + "Tracer or Supplier<Tracer>. Can be 'true' for the default global OpenTelemetry tracer");

    static final YdbProperty<Object> WITH_METER = YdbProperty.object("withMeter",
            "Enable metric collection for YDB SDK operations, object instance or class full name implementing "
                    + "Meter or Supplier<Meter>. Can be 'true' for the default global OpenTelemetry meter");

    static final YdbProperty<String> METER_POOL_NAME = YdbProperty.string("meterPoolName",
            "The value of the metric's attribute 'poolName'. By default is 'jdbc'", "jdbc");

    private final String username;
    private final String password;

    private final YdbValue<String> localDatacenter;
    private final YdbValue<Boolean> useSecureConnection;
    private final YdbValue<byte[]> secureConnectionCertificate;
    private final YdbValue<String> token;
    private final YdbValue<String> tokenFile;
    private final YdbValue<String> serviceAccountFile;
    private final YdbValue<String> saKeyFile;
    private final YdbValue<Boolean> useMetadata;
    private final YdbValue<String> iamEndpoint;
    private final YdbValue<String> metadataUrl;
    private final YdbValue<Object> tokenProvider;
    private final YdbValue<Object> channelInitializer;
    private final YdbValue<Object> withTracer;
    private final YdbValue<Object> withMeter;
    private final YdbValue<String> meterPoolName;
    private final YdbValue<String> grpcCompression;

    public YdbConnectionProperties(String username, String password, Properties props) throws SQLException {
        this.username = username;
        this.password = password;

        this.localDatacenter = LOCAL_DATACENTER.readValue(props);
        this.useSecureConnection = USE_SECURE_CONNECTION.readValue(props);
        this.secureConnectionCertificate = SECURE_CONNECTION_CERTIFICATE.readValue(props);
        this.token = TOKEN.readValue(props);
        this.tokenFile = TOKEN_FILE.readValue(props);
        this.serviceAccountFile = SERVICE_ACCOUNT_FILE.readValue(props);
        this.saKeyFile = SA_KEY_FILE.readValue(props);
        this.useMetadata = USE_METADATA.readValue(props);
        this.iamEndpoint = IAM_ENDPOINT.readValue(props);
        this.metadataUrl = METADATA_URL.readValue(props);
        this.tokenProvider = TOKEN_PROVIDER.readValue(props);
        this.channelInitializer = CHANNEL_INITIALIZER.readValue(props);
        this.withTracer = WITH_TRACER.readValue(props);
        this.withMeter = WITH_METER.readValue(props);
        this.meterPoolName = METER_POOL_NAME.readValue(props);
        this.grpcCompression = GRPC_COMPRESSION.readValue(props);
    }

    public YdbConnectionProperties(YdbConfig config) throws SQLException {
        this(config.getUsername(), config.getPassword(), config.getProperties());
    }

    String getLocalDataCenter() {
        return localDatacenter.getValue();
    }

    String getToken() {
        return token.getValue();
    }

    byte[] getSecureConnectionCert() {
        return secureConnectionCertificate.getValue();
    }

    public GrpcTransportBuilder applyToGrpcTransport(GrpcTransportBuilder builder) throws SQLException {
        if (localDatacenter.hasValue()) {
            builder = builder.withBalancingSettings(BalancingSettings.fromLocation(localDatacenter.getValue()));
        }

        if (useSecureConnection.hasValue() && useSecureConnection.getValue()) {
            builder = builder.withSecureConnection();
        }

        if (secureConnectionCertificate.hasValue()) {
            builder = builder.withSecureConnection(secureConnectionCertificate.getValue());
        }

        String usedProvider = null;

        if (username != null && !username.isEmpty()) {
            builder = builder.withAuthProvider(new StaticCredentials(username, password));
            usedProvider = "username & password credentials";
        }

        if (useMetadata.hasValue()) {
            if (usedProvider != null) {
                LOGGER.log(Level.WARNING, "Dublicate authentication config! Metadata credentials replaces {0}",
                        usedProvider);
            }

            if (metadataUrl.hasValue()) {
                String url = metadataUrl.getValue();
                builder =  builder.withAuthProvider(CloudAuthHelper.getMetadataAuthProvider(url));
            } else {
                builder =  builder.withAuthProvider(CloudAuthHelper.getMetadataAuthProvider());
            }
            usedProvider = "metadata credentials";
        }

        if (tokenFile.hasValue()) {
            if (usedProvider != null) {
                LOGGER.log(Level.WARNING, "Dublicate authentication config! Token credentials replaces {0}",
                        usedProvider);
            }
            builder = builder.withAuthProvider(new TokenAuthProvider(tokenFile.getValue()));
            usedProvider = "token file credentitals";
        }

        if (token.hasValue()) {
            if (usedProvider != null) {
                LOGGER.log(Level.WARNING, "Dublicate authentication config! Token credentials replaces {0}",
                        usedProvider);
            }
            builder = builder.withAuthProvider(new TokenAuthProvider(token.getValue()));
            usedProvider = "token value credentitals";
        }

        if (saKeyFile.hasValue()) {
            if (usedProvider != null) {
                LOGGER.log(Level.WARNING, "Dublicate authentication config! Token credentials replaces {0}",
                        usedProvider);
            }
            String json = saKeyFile.getValue();
            if (iamEndpoint.hasValue()) {
                String endpoint = iamEndpoint.getValue();
                builder = builder.withAuthProvider(CloudAuthHelper.getServiceAccountJsonAuthProvider(json, endpoint));
            } else {
                builder = builder.withAuthProvider(CloudAuthHelper.getServiceAccountJsonAuthProvider(json));
            }
            usedProvider = "service account credentitals";
        }

        if (serviceAccountFile.hasValue()) {
            LOGGER.warning("Option 'saFile' is deprecated and will be removed in next versions. "
                    + "Use options 'saKeyFile' instead");
            if (usedProvider != null) {
                LOGGER.log(Level.WARNING, "Dublicate authentication config! Token credentials replaces {0}",
                        usedProvider);
            }
            String json = serviceAccountFile.getValue();
            if (iamEndpoint.hasValue()) {
                String endpoint = iamEndpoint.getValue();
                builder = builder.withAuthProvider(CloudAuthHelper.getServiceAccountJsonAuthProvider(json, endpoint));
            } else {
                builder = builder.withAuthProvider(CloudAuthHelper.getServiceAccountJsonAuthProvider(json));
            }
            usedProvider = "service account file credentitals";
        }

        if (tokenProvider.hasValue()) {
            if (usedProvider != null) {
                LOGGER.log(Level.WARNING, "Dublicate authentication config! Token Provider credentials replaces {0}",
                        usedProvider);
            }

            Object provider = tokenProvider.getValue();
            builder = applyTokenProvider(builder, provider);
        }

        if (channelInitializer.hasValue()) {
            Object initializer = channelInitializer.getValue();
            builder = applyChannelInitializer(builder, initializer);
        }

        if (withTracer.hasValue()) {
            JdbcDriverVersion version = JdbcDriverVersion.getInstance();
            if (version.isSdkVersion(2, 4, 6)) {
                builder = builder.withTracer(getTracer());
            } else {
                LOGGER.log(Level.WARNING, "Option 'withTracer' was ignored because SDK version {0} is too old",
                        version.getSdkVersion());
            }
        }

        if (grpcCompression.hasValue()) {
            String value = grpcCompression.getValue();
            if ("none".equalsIgnoreCase(value)) {
                builder = builder.withGrpcCompression(GrpcCompression.NO_COMPRESSION);
            } else if ("gzip".equalsIgnoreCase(value)) {
                builder = builder.withGrpcCompression(GrpcCompression.GZIP);
            } else {
                LOGGER.log(Level.WARNING, "Unknown value for option 'grpcCompression' : {0}", value);
            }
        }

        return builder;
    }

    public void applyToClients(PooledTableClient.Builder table, QueryClient.Builder query) throws SQLException {
        if (!withMeter.hasValue()) {
            return;
        }

        JdbcDriverVersion version = JdbcDriverVersion.getInstance();
        if (!version.isSdkVersion(2, 4, 6)) {
            LOGGER.log(Level.WARNING, "Option 'withMeter' was ignored because SDK version {0} is too old",
                    version.getSdkVersion());
            return;
        }

        Meter meter = getMeter();
        String poolName = meterPoolName.getValue();
        table.withMeter(meter, poolName);
        query.withMeter(meter, poolName);
    }

    private GrpcTransportBuilder applyTokenProvider(GrpcTransportBuilder builder, Object provider) throws SQLException {
        if (provider instanceof Supplier) {
            Supplier<?> prov = (Supplier<?>) provider;
            builder = builder.withAuthProvider((rpc) -> () -> prov.get().toString());
        } else if (provider instanceof AuthProvider) {
            AuthProvider prov = (AuthProvider) provider;
            builder = builder.withAuthProvider(prov);
        } else if (provider instanceof String) {
            String className = (String) provider;
            if (!FQCN.matcher(className).matches()) {
                throw new SQLException("tokenProvider must be full class name or instance of Supplier<String>");
            }

            try {
                Class<?> clazz = Class.forName(className);
                if (!Supplier.class.isAssignableFrom(clazz)) {
                    throw new SQLException("tokenProvider " + className + " does not implement Supplier<String>");
                }
                Supplier<?> prov = clazz.asSubclass(Supplier.class)
                        .getConstructor(new Class<?>[0])
                        .newInstance(new Object[0]);
                builder = builder.withAuthProvider((rpc) -> () -> prov.get().toString());
            } catch (ClassNotFoundException ex) {
                throw new SQLException("tokenProvider " + className + " not found", ex);
            } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException ex) {
                throw new SQLException("Cannot construct tokenProvider " + className, ex);
            }
        } else if (provider != null) {
            throw new SQLException("Cannot parse tokenProvider " + provider.getClass().getName());
        }
        return builder;
    }

    private GrpcTransportBuilder applyChannelInitializer(GrpcTransportBuilder builder, Object initializer)
            throws SQLException {
        if (initializer instanceof Consumer) {
            @SuppressWarnings("unchecked")
            Consumer<Object> prov = (Consumer<Object>) initializer;
            builder = builder.addChannelInitializer(prov);
        } else if (initializer instanceof String) {
            String className = (String) initializer;

            if (FQCN.matcher(className.trim()).matches()) {
                builder.addChannelInitializer(newInitializerInstance(className.trim()));
            } else {
                String[] classNames = className.split(",");
                if (classNames.length < 2) {
                    throw new SQLException("channelInitializer must be full class name or instance of "
                            + "Consumer<ManagedChannelBuilder>");
                }

                for (String name: classNames) {
                    if (!FQCN.matcher(name.trim()).matches()) {
                        throw new SQLException("channelInitializer must be full class name or instance of "
                                + "Consumer<ManagedChannelBuilder>");
                    }
                    builder.addChannelInitializer(newInitializerInstance(name.trim()));
                }
            }
        } else if (initializer != null) {
            throw new SQLException("Cannot parse channelInitializer " + initializer.getClass().getName());
        }
        return builder;
    }

    @SuppressWarnings("unchecked")
    private Consumer<Object> newInitializerInstance(String className) throws SQLException {
        try {
            Class<?> clazz = Class.forName(className);
            if (!Consumer.class.isAssignableFrom(clazz)) {
                throw new SQLException("channelInitializer " + className + " does not implement "
                        + "Consumer<ManagedChannelBuilder>");
            }
            return clazz.asSubclass(Consumer.class)
                    .getConstructor(new Class<?>[0])
                    .newInstance(new Object[0]);
        } catch (ClassNotFoundException ex) {
            throw new SQLException("channelInitializer " + className + " not found", ex);
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException ex) {
            throw new SQLException("Cannot construct channelInitializer " + className, ex);
        }
    }

    @VisibleForTesting
    Tracer getTracer() throws SQLException {
        Object obj = withTracer.getValue();
        if (obj == null) {
            return null;
        }

        if (obj instanceof Tracer) {
            return (Tracer) obj;
        }

        if (obj instanceof Supplier) {
            Object impl = ((Supplier) obj).get();
            if (impl instanceof Tracer) {
                return (Tracer) impl;
            } else {
                String className = obj.getClass().getName();
                throw new SQLException("Tracer supplier " + className + " did not return a Tracer instance");
            }
        }

        if (obj instanceof String) {
            String className = (String) obj;

            if ("true".equalsIgnoreCase(className)) {
                try {
                    Class<?> clazz = Class.forName(OPENTELEMETRY_TRACER_CLASS);
                    Method createGlobal = clazz.getMethod("createGlobal");
                    Object global = createGlobal.invoke(null);
                    if (global instanceof Tracer) {
                        return (Tracer) global;
                    }
                    throw new SQLException("OpenTelemetryTracer.createGlobal() did not return a Tracer");
                } catch (ClassNotFoundException | NoClassDefFoundError  e) {
                    throw new SQLException("withTracer requires io.opentelemetry:opentelemetry-api on the classpath",
                            e);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    throw new SQLException("Cannot invoke OpenTelemetryTracer.createGlobal()", e);
                }
            }

            if (!FQCN.matcher(className).matches()) {
                throw new SQLException("withTracer option must be full class name or object instance implementing "
                        + "tech.ydb.core.tracing.Tracer");
            }

            try {
                Class<?> clazz = Class.forName(className);

                if (Tracer.class.isAssignableFrom(clazz)) {
                    Tracer tracer = clazz.asSubclass(Tracer.class)
                            .getConstructor(new Class<?>[0])
                            .newInstance(new Object[0]);
                    return tracer;
                }

                if (Supplier.class.isAssignableFrom(clazz)) {
                    Supplier<?> prov = clazz.asSubclass(Supplier.class)
                            .getConstructor(new Class<?>[0])
                            .newInstance(new Object[0]);
                    Object tracer = prov.get();
                    if (tracer instanceof Tracer) {
                        return (Tracer) tracer;
                    }

                    throw new SQLException("Tracer " + className + " supplied object is not a Tracer instance");
                }

                throw new SQLException("Tracer " + className + " does not implement tech.ydb.core.tracing.Tracer");
            } catch (ClassNotFoundException ex) {
                throw new SQLException("Tracer class " + className + " not found", ex);
            } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException ex) {
                throw new SQLException("Cannot construct tracer " + className, ex);
            }
        }

        throw new SQLException("Cannot parse tracer " + obj.getClass().getName());
    }

    @VisibleForTesting
    Meter getMeter() throws SQLException {
        Object obj = withMeter.getValue();
        if (obj == null) {
            return null;
        }
        if (obj instanceof Meter) {
            return (Meter) obj;
        }
        if (obj instanceof Supplier) {
            Object impl = ((Supplier) obj).get();
            if (impl instanceof Meter) {
                return (Meter) impl;
            } else {
                String className = obj.getClass().getName();
                throw new SQLException("Meter supplier " + className + " did not return a Meter instance");
            }
        }

        if (obj instanceof String) {
            String className = (String) obj;

            if ("true".equalsIgnoreCase(className)) {
                try {
                    Class<?> clazz = Class.forName(OPENTELEMETRY_METER_CLASS);
                    Method createGlobal = clazz.getMethod("createGlobal");
                    Object global = createGlobal.invoke(null);
                    if (global instanceof Meter) {
                        return (Meter) global;
                    }
                    throw new SQLException("OpenTelemetryMeter.createGlobal() did not return a Meter");
                } catch (ClassNotFoundException | NoClassDefFoundError  e) {
                    throw new SQLException("withMeter requires io.opentelemetry:opentelemetry-api on the classpath",
                            e);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    throw new SQLException("Cannot invoke OpenTelemetryMeter.createGlobal()", e);
                }
            }

            if (!FQCN.matcher(className).matches()) {
                throw new SQLException("withMeter option must be full class name or object instance implementing "
                        + "tech.ydb.core.metrics.Meter");
            }

            try {
                Class<?> clazz = Class.forName(className);

                if (Meter.class.isAssignableFrom(clazz)) {
                    Meter meter = clazz.asSubclass(Meter.class)
                            .getConstructor(new Class<?>[0])
                            .newInstance(new Object[0]);
                    return meter;
                }

                if (Supplier.class.isAssignableFrom(clazz)) {
                    Supplier<?> prov = clazz.asSubclass(Supplier.class)
                            .getConstructor(new Class<?>[0])
                            .newInstance(new Object[0]);
                    Object meter = prov.get();
                    if (meter instanceof Meter) {
                        return (Meter) meter;
                    }
                    throw new SQLException("Meter " + className + " supplied object is not a Meter instance");
                }

                throw new SQLException("Meter " + className + " does not implement tech.ydb.core.metrics.Meter");
            } catch (ClassNotFoundException ex) {
                throw new SQLException("Meter class " + className + " not found", ex);
            } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException ex) {
                throw new SQLException("Cannot construct meter " + className, ex);
            }
        }

        throw new SQLException("Cannot parse meter " + obj.getClass().getName());
    }
}
