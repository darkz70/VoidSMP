package ru.voidsmp.spawnrates.spawn;

import java.util.Locale;

/**
 * Что делать, если точка попала сразу в несколько зон.
 */
public enum OverlapPolicy {

    /** Действует только самая приоритетная зона (остальные игнорируются). */
    PRIORITY,
    /** Множители пересекающихся зон перемножаются. */
    MULTIPLY,
    /** Берётся наименьший множитель — «выигрывает запрет». */
    STRICTEST;

    public static OverlapPolicy parse(String raw, OverlapPolicy fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        for (OverlapPolicy policy : values()) {
            if (policy.name().equals(key)) {
                return policy;
            }
        }
        return fallback;
    }
}
