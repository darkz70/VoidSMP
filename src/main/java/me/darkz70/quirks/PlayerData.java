package me.darkz70.quirks;

import java.util.HashMap;
import java.util.Map;

/** Данные игрока: причуда, уровень и кулдауны (времена в epoch-миллисекундах). */
public final class PlayerData {

    private Quirk quirk;
    private int level;
    private final Map<String, Long> cooldowns = new HashMap<>();

    public PlayerData(Quirk quirk, int level) {
        this.quirk = quirk;
        this.level = level;
    }

    public Quirk quirk() {
        return quirk;
    }

    public void quirk(Quirk quirk) {
        this.quirk = quirk;
    }

    public int level() {
        return level;
    }

    public void level(int level) {
        this.level = Math.max(1, Math.min(3, level));
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
