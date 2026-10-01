package me.darkz70.quirks;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Данные игрока: НАБОР причуд (несколько штук с уровнями),
 * кулдауны и личные настройки оповещений.
 */
public final class PlayerData {

    private final Map<Quirk, Integer> quirks = new EnumMap<>(Quirk.class);
    private final Map<String, Long> cooldowns = new HashMap<>();
    private boolean notifications = true;

    public boolean has(Quirk quirk) {
        return quirks.containsKey(quirk);
    }

    /** Уровень причуды; 0, если её нет. */
    public int levelOf(Quirk quirk) {
        return quirks.getOrDefault(quirk, 0);
    }

    public void put(Quirk quirk, int level) {
        quirks.put(quirk, Math.max(1, Math.min(3, level)));
    }

    public void remove(Quirk quirk) {
        quirks.remove(quirk);
    }

    public boolean isEmpty() {
        return quirks.isEmpty();
    }

    public Set<Map.Entry<Quirk, Integer>> entries() {
        return quirks.entrySet();
    }

    public boolean notifications() {
        return notifications;
    }

    public void notifications(boolean notifications) {
        this.notifications = notifications;
    }

    public long cooldown(String key) {
        return cooldowns.getOrDefault(key, 0L);
    }

    public void setCooldown(String key, long when) {
        cooldowns.put(key, when);
    }

    public Map<String, Long> cooldowns() {
        return cooldowns;
    }
}
