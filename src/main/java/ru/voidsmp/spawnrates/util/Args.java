package ru.voidsmp.spawnrates.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Плейсхолдеры для сообщений: {name}, {value} и т.п. Собран отдельным
 * классом только ради читаемости кода команд — Map.of упирается в 10 пар.
 */
public final class Args {

    private final Map<String, String> values = new LinkedHashMap<>();

    private Args() {
    }

    public static Args of() {
        return new Args();
    }

    public Args put(String key, Object value) {
        values.put(key, value == null ? "" : String.valueOf(value));
        return this;
    }

    public Map<String, String> asMap() {
        return values;
    }
}
