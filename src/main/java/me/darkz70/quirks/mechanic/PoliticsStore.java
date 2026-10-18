package me.darkz70.quirks.mechanic;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Зайцекрольская политика (plugins/VoidQuirks/politics.yml):
 * текущий царь + генеалогия прошлых царей, партии, история выборов и составов парламента.
 * Редактируется из /gaid (вкладка «Политика») и командами.
 */
public final class PoliticsStore {

    private final VoidQuirksPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public PoliticsStore(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "politics.yml");
        load();
    }

    public void load() {
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            data.save(file);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "politics.yml не сохранён", ex);
        }
    }

    // ---------- партии ----------

    public Map<String, String> parties() {
        Map<String, String> map = new LinkedHashMap<>();
        ConfigurationSection sec = data.getConfigurationSection("parties");
        if (sec == null) return map;
        for (String key : sec.getKeys(false)) {
            map.put(key, sec.getString(key + ".description", "")
                    + "|" + (sec.getBoolean(key + ".active", true) ? "1" : "0"));
        }
        return map;
    }

    public void setParty(String name, String description, boolean active) {
        data.set("parties." + name + ".description", description);
        data.set("parties." + name + ".active", active);
        save();
    }

    public void removeParty(String name) {
        data.set("parties." + name, null);
        save();
    }

    // ---------- цари / генеалогия ----------

    public String currentTsar() {
        return data.getString("tsar.name", "—");
    }

    public String currentTsarBio() {
        return data.getString("tsar.bio", "");
    }

    public void setTsar(String name, String bio) {
        data.set("tsar.name", name);
        data.set("tsar.bio", bio);
        save();
    }

    /** Ушёл царь: переносится в прошлую родословную (список past-tsars). */
    public void retireTsar(String note) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("name", currentTsar());
        entry.put("bio", currentTsarBio());
        entry.put("note", note);
        List<Map<String, Object>> past = pastTsarsRaw();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", entry.get("name"));
        row.put("bio", entry.get("bio"));
        row.put("note", note);
        past.add(0, row);
        data.set("past-tsars", past);
        save();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pastTsarsRaw() {
        List<Map<String, Object>> out = new ArrayList<>();
        List<?> list = data.getList("past-tsars");
        if (list != null) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, v) -> row.put(String.valueOf(k), v));
                    out.add(row);
                }
            }
        }
        return out;
    }

    public List<Map<String, Object>> pastTsars() {
        return pastTsarsRaw();
    }

    // ---------- выборы / парламент ----------

    /** Записать итог выборов: {time, server, per-party {deputies, percent}}. */
    public void recordElection(List<Map<String, Object>> rowsHeader, List<Map<String, Object>> perParty) {
        List<Map<String, Object>> elections = electionsRaw();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", System.currentTimeMillis());
        entry.put("parties", perParty);
        elections.add(0, entry);
        data.set("elections", elections);
        save();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> electionsRaw() {
        List<Map<String, Object>> out = new ArrayList<>();
        List<?> list = data.getList("elections");
        if (list != null) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, v) -> row.put(String.valueOf(k), v));
                    out.add(row);
                }
            }
        }
        return out;
    }

    public List<Map<String, Object>> elections() {
        return electionsRaw();
    }
}
