package com.voidsmp.voidoferta;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Хранит игроков, уже подтвердивших оферту, в файле accepted-players.yml:
 * <pre>
 * players:
 *   &lt;uuid&gt;:
 *     name: "Steve"
 *     accepted-at: "2026-10-18T12:00:00Z"
 * </pre>
 * Подтверждение нужно ровно один раз — после этого книга при входе
 * больше не открывается, а /oferta просто показывает текст повторно.
 */
public final class AcceptedStorage {

    private final File file;
    private final Logger logger;
    private final Map<UUID, String> acceptedAt = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    public AcceptedStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    public synchronized void load() {
        acceptedAt.clear();
        names.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getConfigurationSection("players") == null) {
            return;
        }
        for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                acceptedAt.put(uuid, yaml.getString("players." + key + ".accepted-at", ""));
                names.put(uuid, yaml.getString("players." + key + ".name", ""));
            } catch (IllegalArgumentException ignored) {
                logger.warning("Пропущена битая запись в accepted-players.yml: " + key);
            }
        }
    }

    public synchronized boolean hasAccepted(UUID uuid) {
        return acceptedAt.containsKey(uuid);
    }

    /** Дата/время принятия в ISO-формате или null, если игрок ещё не принял оферту. */
    public synchronized String acceptedAt(UUID uuid) {
        return acceptedAt.get(uuid);
    }

    public synchronized void accept(UUID uuid, String playerName) {
        acceptedAt.put(uuid, Instant.now().toString());
        names.put(uuid, playerName);
        save();
    }

    /** Сброс подтверждения: при следующем входе игрок увидит книгу снова. */
    public synchronized boolean reset(UUID uuid) {
        boolean removed = acceptedAt.remove(uuid) != null;
        names.remove(uuid);
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized int size() {
        return acceptedAt.size();
    }

    public synchronized UUID findByName(String playerName) {
        for (Map.Entry<UUID, String> entry : names.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equalsIgnoreCase(playerName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : acceptedAt.entrySet()) {
            String path = "players." + entry.getKey();
            yaml.set(path + ".name", names.getOrDefault(entry.getKey(), ""));
            yaml.set(path + ".accepted-at", entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Не удалось сохранить accepted-players.yml", e);
        }
    }
}
