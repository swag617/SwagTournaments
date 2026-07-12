package com.swag.tournaments.engine;

import java.util.Map;

public record ScoringContext(double delta, Map<String, Object> metadata) {

    public ScoringContext(double delta) {
        this(delta, Map.of());
    }

    public double getDouble(String key, double defaultValue) {
        Object val = metadata.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return defaultValue;
    }

    public String getString(String key, String defaultValue) {
        Object val = metadata.get(key);
        if (val instanceof String s) return s;
        return defaultValue;
    }
}
