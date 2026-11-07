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
    private String transport;
    private String bridgeUrl;
    private String bridgeSecretFile;
    private int bridgeTimeoutSeconds;
    private int bridgeConnectTimeoutSeconds;
    private int bridgeReadyGraceSeconds;
    private String mode;
    private int codeLength;
    private int codeTtlSeconds;
    private int authTimeoutSeconds;
    private int maxAttempts;
    private int sessionMinutes;
    private boolean freeze;
    private boolean blindness;
    private boolean lockdownIfUnavailable;
    private boolean trustedDevicesEnabled;
    private boolean rememberDeviceOnApprove;
    private int trustedDeviceMax;
    private int trustedDeviceTtlDays;
    private boolean accountLockEnabled;

    public ConfigManager(Void2FAPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        botToken = c.getString("telegram.bot-token", "").trim();
        botUsernameOverride = c.getString("telegram.bot-username", "").trim();
        transport = c.getString("telegram.transport", "embedded").trim().toLowerCase(java.util.Locale.ROOT);
        if (!transport.equals("external")) transport = "embedded";
        bridgeUrl = c.getString("telegram.bridge.url", "").trim();
        bridgeSecretFile = c.getString("telegram.bridge.secret-file", "bridge-secret.txt").trim();
        bridgeTimeoutSeconds = clamp(c.getInt("telegram.bridge.timeout-seconds", 30), 5, 120);
        bridgeConnectTimeoutSeconds = clamp(c.getInt("telegram.bridge.connect-timeout-seconds", 10), 3, 60);
        // 0 = derive from the request timeout, so a slow-but-working bot never reads as "offline".
        bridgeReadyGraceSeconds = clamp(c.getInt("telegram.bridge.ready-grace-seconds", 0), 0, 600);
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
        trustedDevicesEnabled = c.getBoolean("settings.trusted-devices.enabled", true);
        rememberDeviceOnApprove = c.getBoolean("settings.trusted-devices.remember-on-approve", true);
        trustedDeviceMax = clamp(c.getInt("settings.trusted-devices.max-devices", 5), 1, 50);
        trustedDeviceTtlDays = clamp(c.getInt("settings.trusted-devices.ttl-days", 0), 0, 3650);
        accountLockEnabled = c.getBoolean("settings.account-lock.enabled", true);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------
    //  Getters
    // ------------------------------------------------------------------

    public String getTransport() { return transport; }
    public String getBridgeUrl() { return bridgeUrl; }
    public String getBridgeSecretFile() { return bridgeSecretFile; }
    public int getBridgeTimeoutSeconds() { return bridgeTimeoutSeconds; }
    public int getBridgeConnectTimeoutSeconds() { return bridgeConnectTimeoutSeconds; }

    /**
     * How long a successful sync keeps the transport "ready".
     * Must exceed one request timeout plus the loop delay, otherwise a slow bot
     * looks permanently offline even while every sync succeeds.
     */
    public int getBridgeReadyGraceSeconds() {
        return bridgeReadyGraceSeconds > 0
                ? bridgeReadyGraceSeconds
                : Math.max(15, bridgeTimeoutSeconds + 10);
    }

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

    public boolean isTrustedDevicesEnabled() {
        return trustedDevicesEnabled;
    }

    public boolean isRememberDeviceOnApprove() {
        return rememberDeviceOnApprove;
    }

    public int getTrustedDeviceMax() {
        return trustedDeviceMax;
    }

    /** 0 = trusted devices never expire. */
    public int getTrustedDeviceTtlDays() {
        return trustedDeviceTtlDays;
    }

    public long getTrustedDeviceTtlMillis() {
        return trustedDeviceTtlDays <= 0 ? 0L : trustedDeviceTtlDays * 86_400_000L;
    }

    public boolean isAccountLockEnabled() {
        return accountLockEnabled;
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
        String path = "messages." + key;
        String raw = plugin.getConfig().getString(path, null);
        if (raw == null && plugin.getConfig().getDefaults() != null) {
            // Let an older local config.yml keep working after a plugin update:
            // newly added messages fall back to the defaults bundled in the JAR.
            raw = plugin.getConfig().getDefaults().getString(path, "");
        }
        if (raw == null) {
            raw = "";
        }
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
