package ru.voidsmp.void2fa;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Loads {@code config.yml} and renders messages (in-game, kick and Telegram
 * variants) with placeholder + colour-code support.
 */
public final class ConfigManager {

    private final Void2FAPlugin plugin;

    private String botToken;
    private String botUsernameOverride;
    private String mode;
    private int codeLength;
    private int codeTtlSeconds;
    private int authTimeoutSeconds;
    private int maxAttempts;
    private int sessionMinutes;
    private boolean freeze;
    private boolean blindness;
    private boolean lockdownIfUnavailable;

    public ConfigManager(Void2FAPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        botToken = c.getString("telegram.bot-token", "").trim();
        botUsernameOverride = c.getString("telegram.bot-username", "").trim();
        mode = c.getString("settings.mode", "opt-in").trim().toLowerCase();
        if (!mode.equals("required")) {
            mode = "opt-in";
        }
        codeLength = clamp(c.getInt("settings.code-length", 6), 4, 10);
        codeTtlSeconds = clamp(c.getInt("settings.code-ttl-seconds", 120), 15, 600);
        authTimeoutSeconds = clamp(c.getInt("settings.auth-timeout-seconds", 90), 15, 600);
        maxAttempts = clamp(c.getInt("settings.max-attempts", 5), 1, 20);
        sessionMinutes = clamp(c.getInt("settings.session-minutes", 0), 0, 1440);
        freeze = c.getBoolean("settings.freeze", true);
        blindness = c.getBoolean("settings.blindness", true);
        lockdownIfUnavailable = c.getBoolean("settings.lockdown-if-unavailable", false);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------
    //  Getters
    // ------------------------------------------------------------------

    public String getBotToken() {
        return botToken;
    }

    public String getBotUsernameOverride() {
        return botUsernameOverride;
    }

    public boolean isRequiredMode() {
        return mode.equals("required");
    }

    public String getMode() {
        return mode;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public int getCodeTtlSeconds() {
        return codeTtlSeconds;
    }

    public int getAuthTimeoutSeconds() {
        return authTimeoutSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getSessionMinutes() {
        return sessionMinutes;
    }

    public boolean isFreeze() {
        return freeze;
    }

    public boolean isBlindness() {
        return blindness;
    }

    public boolean isLockdownIfUnavailable() {
        return lockdownIfUnavailable;
    }

    // ------------------------------------------------------------------
    //  Messages
    // ------------------------------------------------------------------

    /** In-game chat message: prefix + colour codes. */
    public String msg(String key, String... placeholders) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        return colorize(prefix + apply(key, placeholders));
    }

    /** Disconnect (kick) screen message: colour codes, no chat prefix. */
    public String kick(String key, String... placeholders) {
        return colorize(apply(key, placeholders));
    }

    /** Telegram message: plain text, colour codes stripped. */
    public String tg(String key, String... placeholders) {
        return stripColors(apply(key, placeholders));
    }

    private String apply(String key, String... placeholders) {
        String raw = plugin.getConfig().getString("messages." + key, "");
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return raw;
    }

    private static String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static String stripColors(String s) {
        return s.replaceAll("(?i)[&\u00a7][0-9a-fk-or]", "");
    }
}
