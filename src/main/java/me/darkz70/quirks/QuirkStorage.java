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
                PlayerData data = new PlayerData();

                ConfigurationSection quirksSection = section.getConfigurationSection("quirks");
                if (quirksSection != null) {
                    // новый формат: несколько причуд
                    for (String quirkId : quirksSection.getKeys(false)) {
                        Quirk quirk = Quirk.byName(quirkId);
                        if (quirk != null) {
                            data.put(quirk, quirksSection.getInt(quirkId, 1));
                        }
                    }
                } else {
                    // миграция со старого формата (одна причуда)
                    Quirk quirk = Quirk.byName(section.getString("quirk", ""));
                    if (quirk != null) {
                        data.put(quirk, section.getInt("level", 1));
                    }
                }

                data.notifications(section.getBoolean("notifications", true));

                data.magicElement(section.getString("magic.element", null));
                data.magicLevel(section.getInt("magic.level", 1));
                data.mana(section.getDouble("magic.mana", 0));
                data.selectedSpell(section.getInt("magic.spell", 0));
                data.tmpQuirks(section.getString("tmpquirks", null));
                data.tmpUntil(section.getLong("tmpuntil", 0));

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
            PlayerData data = entry.getValue();
            yaml.set(path + ".notifications", data.notifications());
            for (Map.Entry<Quirk, Integer> quirkEntry : data.entries()) {
                yaml.set(path + ".quirks." + quirkEntry.getKey().id(), quirkEntry.getValue());
            }
            if (data.magicElement() != null) yaml.set(path + ".magic.element", data.magicElement());
            yaml.set(path + ".magic.level", data.magicLevel());
            yaml.set(path + ".magic.mana", data.mana());
            yaml.set(path + ".magic.spell", data.selectedSpell());
            if (data.tmpQuirks() != null) yaml.set(path + ".tmpquirks", data.tmpQuirks());
            yaml.set(path + ".tmpuntil", data.tmpUntil());
            for (Map.Entry<String, Long> cd : data.cooldowns().entrySet()) {
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
