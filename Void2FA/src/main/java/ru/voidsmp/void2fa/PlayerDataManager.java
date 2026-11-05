package ru.voidsmp.void2fa;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Persists the Telegram link for each player in {@code data.yml}.
 * All access happens on the main server thread (Telegram callbacks are
 * dispatched there by {@link ru.voidsmp.void2fa.telegram.TelegramUpdateHandler}).
 */
public final class PlayerDataManager {

    private final Void2FAPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public PlayerDataManager(Void2FAPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    public void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку плагина.");
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить data.yml: " + e.getMessage());
        }
    }

    private static String base(UUID id) {
        return "players." + id;
    }

    public boolean isLinked(UUID id) {
        return getTelegramId(id) != 0L;
    }

    public long getTelegramId(UUID id) {
        return data.getLong(base(id) + ".telegram-id", 0L);
    }

    public boolean isEnabled(UUID id) {
        return data.getBoolean(base(id) + ".enabled", false);
    }

    public void setEnabled(UUID id, boolean enabled) {
        data.set(base(id) + ".enabled", enabled);
        save();
    }

    public void link(UUID id, String name, long telegramId) {
        String b = base(id);
        data.set(b + ".name", name);
        data.set(b + ".telegram-id", telegramId);
        data.set(b + ".enabled", true);
        data.set(b + ".linked-at", System.currentTimeMillis());
        save();
    }

    public void unlink(UUID id) {
        data.set(base(id), null);
        save();
    }

    public String getName(UUID id) {
        return data.getString(base(id) + ".name", "");
    }

    public void updateLastAuth(UUID id, String ip) {
        String b = base(id);
        data.set(b + ".last-auth", System.currentTimeMillis());
        data.set(b + ".last-ip", ip);
        save();
    }

    public long getLastAuth(UUID id) {
        return data.getLong(base(id) + ".last-auth", 0L);
    }

    public String getLastIp(UUID id) {
        return data.getString(base(id) + ".last-ip", "");
    }

    /** Invalidates the short same-IP session cache; trusted devices are kept separately. */
    public void invalidateSession(UUID id) {
        data.set(base(id) + ".last-auth", null);
        data.set(base(id) + ".last-ip", null);
        save();
    }

    // ------------------------------------------------------------------
    //  Account lock and trusted devices
    // ------------------------------------------------------------------

    /** A locked account cannot join until the owner (or an admin) unlocks it. */
    public boolean isLocked(UUID id) {
        return data.getBoolean(base(id) + ".locked", false);
    }

    public void setLocked(UUID id, boolean locked) {
        data.set(base(id) + ".locked", locked);
        save();
    }

    /** One remembered device. The server can only see the client IP, not hardware IDs. */
    public static final class TrustedDevice {
        public final String id;
        public final String ip;
        public final long createdAt;
        public final long lastUsedAt;

        TrustedDevice(String id, String ip, long createdAt, long lastUsedAt) {
            this.id = id;
            this.ip = ip;
            this.createdAt = createdAt;
            this.lastUsedAt = lastUsedAt;
        }
    }

    public List<TrustedDevice> getTrustedDevices(UUID id) {
        List<TrustedDevice> result = new ArrayList<>();
        ConfigurationSection devices = data.getConfigurationSection(base(id) + ".devices");
        if (devices == null) {
            return result;
        }
        for (String key : devices.getKeys(false)) {
            String ip = devices.getString(key + ".ip", "");
            if (ip.isEmpty()) {
                continue;
            }
            result.add(new TrustedDevice(key, ip,
                    devices.getLong(key + ".created-at", 0L),
                    devices.getLong(key + ".last-used-at", 0L)));
        }
        result.sort(Comparator.comparingLong((TrustedDevice d) -> d.lastUsedAt).reversed());
        return result;
    }

    /**
     * Remembers an IP as a trusted device. If the same IP is already trusted,
     * only refreshes it. When the limit is reached, the least recently used
     * device is evicted.
     */
    public TrustedDevice addTrustedDevice(UUID id, String ip, int maxDevices) {
        if (ip == null || ip.isEmpty() || "?".equals(ip)) {
            return null;
        }
        String b = base(id) + ".devices";
        ConfigurationSection devices = data.getConfigurationSection(b);
        long now = System.currentTimeMillis();
        if (devices != null) {
            for (String key : devices.getKeys(false)) {
                if (ip.equals(devices.getString(key + ".ip", ""))) {
                    data.set(b + "." + key + ".last-used-at", now);
                    save();
                    return new TrustedDevice(key, ip, devices.getLong(key + ".created-at", now), now);
                }
            }
        }
        int max = Math.max(1, maxDevices);
        List<TrustedDevice> existing = getTrustedDevices(id);
        while (existing.size() >= max) {
            TrustedDevice oldest = existing.get(existing.size() - 1);
            data.set(b + "." + oldest.id, null);
            existing.remove(existing.size() - 1);
        }
        String deviceId;
        do {
            deviceId = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        } while (devices != null && devices.contains(deviceId));
        data.set(b + "." + deviceId + ".ip", ip);
        data.set(b + "." + deviceId + ".created-at", now);
        data.set(b + "." + deviceId + ".last-used-at", now);
        save();
        return new TrustedDevice(deviceId, ip, now, now);
    }

    /** Removes by device id, exact IP, or {@code all}. Returns number removed. */
    public int removeTrustedDevice(UUID id, String key) {
        if (key == null || key.isEmpty()) {
            return 0;
        }
        ConfigurationSection devices = data.getConfigurationSection(base(id) + ".devices");
        if (devices == null) {
            return 0;
        }
        int removed = 0;
        boolean all = "all".equalsIgnoreCase(key) || "*".equals(key);
        for (String deviceKey : new ArrayList<>(devices.getKeys(false))) {
            String ip = devices.getString(deviceKey + ".ip", "");
            if (all || deviceKey.equalsIgnoreCase(key) || ip.equals(key)) {
                data.set(base(id) + ".devices." + deviceKey, null);
                removed++;
            }
        }
        if (removed > 0) {
            save();
        }
        return removed;
    }

    /** True when this IP is trusted and not older than {@code ttlMillis} (0 = forever). */
    public boolean isTrustedDevice(UUID id, String ip, long ttlMillis) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (TrustedDevice device : getTrustedDevices(id)) {
            if (!device.ip.equals(ip)) {
                continue;
            }
            long used = device.lastUsedAt > 0 ? device.lastUsedAt : device.createdAt;
            if (ttlMillis <= 0 || now - used <= ttlMillis) {
                return true;
            }
        }
        return false;
    }

    public void touchTrustedDevice(UUID id, String ip) {
        ConfigurationSection devices = data.getConfigurationSection(base(id) + ".devices");
        if (devices == null || ip == null) {
            return;
        }
        for (String key : devices.getKeys(false)) {
            if (ip.equals(devices.getString(key + ".ip", ""))) {
                data.set(base(id) + ".devices." + key + ".last-used-at", System.currentTimeMillis());
                save();
                return;
            }
        }
    }

    /** Finds a stored player UUID by (case-insensitive) name, or {@code null}. */
    public UUID findByName(String name) {
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return null;
        }
        for (String key : players.getKeys(false)) {
            String stored = players.getString(key + ".name", "");
            if (stored.equalsIgnoreCase(name)) {
                try {
                    return UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    /** Finds the player UUID linked to a given Telegram id, or {@code null}. */
    public UUID findByTelegramId(long telegramId) {
        if (telegramId == 0L) {
            return null;
        }
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return null;
        }
        for (String key : players.getKeys(false)) {
            if (players.getLong(key + ".telegram-id", 0L) == telegramId) {
                try {
                    return UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }
}
