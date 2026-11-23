package me.darkz70.quirks;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Данные игрока: НАБОР причуд (несколько штук с уровнями),
 * кулдауны и личные настройки оповещений.
 */
public final class PlayerData {

    private final Map<Quirk, Integer> quirks = new EnumMap<>(Quirk.class);
    private final EnumSet<Quirk> paused = EnumSet.noneOf(Quirk.class); // самовольно выключенные (не пропадают, просто спят)
    private final Map<String, Long> cooldowns = new HashMap<>();
    private boolean notifications = true;

    // магия
    private String magicElement = null;   // fire|water|wind|earth|dark|light
    private int magicLevel = 1;
    private double mana = 0;
    private int selectedSpell = 0;
    /** бэкап набора причуд для зелья причуды/божества: "axe:2,cat:1" */
    private String tmpQuirks = null;
    private long tmpUntil = 0;

    /** Ник, назначенный админом через /gaid nick; null — ника нет (настоящее имя). */
    private String nickname = null;

    // новая магия Arcana (#99, причуда ADMIN_MAG): id стихии -> уровень 1-20
    private final Map<String, Integer> arcanaLevels = new HashMap<>();
    private final Map<String, Integer> arcanaSpellIndex = new HashMap<>();
    private final Map<String, Double> arcanaXp = new HashMap<>();
    private String arcanaActive = null;
    private double arcanaMana = 0;

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
        paused.remove(quirk);
    }

    public boolean isPaused(Quirk quirk) {
        return paused.contains(quirk);
    }

    public void pause(Quirk quirk, boolean on) {
        if (on) paused.add(quirk); else paused.remove(quirk);
    }

    public java.util.Set<Quirk> pausedAll() {
        return java.util.Collections.unmodifiableSet(paused);
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

    // ---------- магия ----------

    @org.jetbrains.annotations.Nullable
    public String magicElement() {
        return magicElement;
    }

    public void magicElement(@org.jetbrains.annotations.Nullable String element) {
        this.magicElement = element;
    }

    public int magicLevel() {
        return magicLevel;
    }

    public void magicLevel(int level) {
        this.magicLevel = Math.max(1, level);
    }

    public double mana() {
        return mana;
    }

    public void mana(double mana) {
        this.mana = Math.max(0, mana);
    }

    public int selectedSpell() {
        return selectedSpell;
    }

    public void selectedSpell(int index) {
        this.selectedSpell = index;
    }

    @org.jetbrains.annotations.Nullable
    public String tmpQuirks() {
        return tmpQuirks;
    }

    public void tmpQuirks(@org.jetbrains.annotations.Nullable String backup) {
        this.tmpQuirks = backup;
    }

    public long tmpUntil() {
        return tmpUntil;
    }

    public void tmpUntil(long until) {
        this.tmpUntil = until;
    }

    // ---------- новая магия Arcana (#99) ----------

    public Map<String, Integer> arcanaLevels() {
        return arcanaLevels;
    }

    public int arcanaLevelOf(String elementId) {
        return arcanaLevels.getOrDefault(elementId, 0);
    }

    public void arcanaSetLevel(String elementId, int level) {
        arcanaLevels.put(elementId, Math.max(0, Math.min(20, level)));
    }

    @org.jetbrains.annotations.Nullable
    public String arcanaActive() {
        return arcanaActive;
    }

    public void arcanaActive(@org.jetbrains.annotations.Nullable String elementId) {
        this.arcanaActive = elementId;
    }

    public int arcanaSpellIndex(String elementId) {
        return arcanaSpellIndex.getOrDefault(elementId, 0);
    }

    public void arcanaSpellIndex(String elementId, int index) {
        arcanaSpellIndex.put(elementId, index);
    }

    public Map<String, Integer> arcanaSpellIndexMap() {
        return arcanaSpellIndex;
    }

    public double arcanaXp(String elementId) {
        return arcanaXp.getOrDefault(elementId, 0.0);
    }

    public void arcanaXp(String elementId, double value) {
        arcanaXp.put(elementId, value);
    }

    public Map<String, Double> arcanaXpMap() {
        return arcanaXp;
    }

    public double arcanaMana() {
        return arcanaMana;
    }

    public void arcanaMana(double mana) {
        this.arcanaMana = Math.max(0, mana);
    }

    // ---------- ник ----------

    @org.jetbrains.annotations.Nullable
    public String nickname() {
        return nickname;
    }

    public void nickname(@org.jetbrains.annotations.Nullable String nickname) {
        this.nickname = nickname;
    }
}
