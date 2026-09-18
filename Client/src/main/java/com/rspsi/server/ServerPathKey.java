package com.rspsi.server;

/** Well-known OpenRune project paths that may be overridden per connection. */
public enum ServerPathKey {
    LIVE_CACHE("live_cache"),
    SERVER_CACHE("server_cache"),
    RAW_CACHE("raw_cache"),
    GAMEVALS("gamevals"),
    GAMEVALS_BINARY("gamevals_binary"),
    CONTENT("content"),
    PACKS("packs"),
    RUNTIME_PLUGINS("runtime_plugins"),
    CS2("cs2");

    private final String configName;

    ServerPathKey(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }

    public static ServerPathKey fromConfigName(String value) {
        String normalized = value.trim().toLowerCase();
        for (ServerPathKey key : values()) {
            if (key.configName.equals(normalized)) return key;
        }
        throw new IllegalArgumentException("Unknown server path key: " + value);
    }
}
