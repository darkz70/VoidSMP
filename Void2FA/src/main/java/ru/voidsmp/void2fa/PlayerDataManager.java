package ru.voidsmp.void2fa;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
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
