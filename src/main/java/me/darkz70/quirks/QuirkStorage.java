package me.darkz70.quirks;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Хранение причуд в plugins/VoidQuirks/players.yml. */
public final class QuirkStorage {

    private final VoidQuirksPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerData> players = new HashMap<>();

    public QuirkStorage(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public PlayerData get(UUID uuid) {
        return players.get(uuid);
    }

    public void put(UUID uuid, PlayerData data) {
        players.put(uuid, data);
    }

    public void remove(UUID uuid) {
        players.remove(uuid);
    }

    public Map<UUID, PlayerData> all() {
        return players;
    }

    public void load() {
        players.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) continue;
                Quirk quirk = Quirk.byName(section.getString("quirk", ""));
                if (quirk == null) continue;
                PlayerData data = new PlayerData(quirk, section.getInt("level", 1));
                ConfigurationSection cds = section.getConfigurationSection("cooldowns");
                if (cds != null) {
                    for (String cd : cds.getKeys(false)) {
                        data.setCooldown(cd, cds.getLong(cd));
                    }
                }
                players.put(uuid, data);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Пропуск битой записи игрока: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> entry : players.entrySet()) {
            String path = "players." + entry.getKey();
            yaml.set(path + ".quirk", entry.getValue().quirk().id());
            yaml.set(path + ".level", entry.getValue().level());
            for (Map.Entry<String, Long> cd : entry.getValue().cooldowns().entrySet()) {
                yaml.set(path + ".cooldowns." + cd.getKey(), cd.getValue());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить players.yml", ex);
        }
    }
}
