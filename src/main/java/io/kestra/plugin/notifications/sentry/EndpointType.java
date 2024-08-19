package io.kestra.plugin.notifications.sentry;

public enum EndpointType {
    ENVELOP {
        public String getEnvelopeUrl(String dsn) {
            String protocol = getProtocol(dsn);

            return SENTRY_ENVELOPE_URL_TEMPLATE.formatted(protocol, getHost(dsn), getProjectId(dsn), SENTRY_VERSION, SENTRY_CLIENT, getPublicKey(dsn, protocol));
        }
    },
    STORE {
        public String getEnvelopeUrl(String dsn) {
            String protocol = getProtocol(dsn);

            return SENTRY_STORE_URL_TEMPLATE.formatted(protocol, getHost(dsn), getProjectId(dsn), SENTRY_VERSION, SENTRY_CLIENT, getPublicKey(dsn, protocol));
        }
    };

    public static final String SYMBOL_AT = "@";
    public static final String SYMBOL_FORWARD_SLASH = "/";
    public static final String SYMBOLS_COLON_DOUBLE_FORWARD_SLASH = "://";
    public static final String SENTRY_VERSION = "7";
    public static final String SENTRY_CLIENT = "java";
    public static final String SENTRY_STORE_URL_TEMPLATE = "%s://%s/api/%s/store/?sentry_version=%s&sentry_client=%s&sentry_key=%s";
    public static final String SENTRY_ENVELOPE_URL_TEMPLATE = "%s://%s/api/%s/envelope/?sentry_version=%s&sentry_client=%s&sentry_key=%s";

    public abstract String getEnvelopeUrl(String dsn);

    private static String getProtocol(String dsn) {
        return dsn.split(SYMBOLS_COLON_DOUBLE_FORWARD_SLASH)[0];
    }

    private static String getHost(String dsn) {
        return dsn.split(SYMBOL_AT)[1].split(SYMBOL_FORWARD_SLASH)[0];
    }

    private static String getProjectId(String dsn) {
        return dsn.split(SYMBOL_AT)[1].split(SYMBOL_FORWARD_SLASH)[1];
    }

    private static String getPublicKey(String dsn, String protocol) {
        return dsn.split(SYMBOL_AT)[0].replace(protocol + SYMBOLS_COLON_DOUBLE_FORWARD_SLASH, "");
    }
}