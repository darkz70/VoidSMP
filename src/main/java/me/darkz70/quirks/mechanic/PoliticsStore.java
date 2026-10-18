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

    /** Удалить текущего царя (без переноса в родословную). */
    public void clearTsar() {
        data.set("tsar", null);
        save();
    }

    /** Заменить запись прошлого царя (name/bio/note). */
    public void updatePastTsar(int idx, String name, String bio, String note) {
        List<Map<String, Object>> past = pastTsarsRaw();
        if (idx < 0 || idx >= past.size()) return;
        Map<String, Object> row = past.get(idx);
        row.put("name", name);
        row.put("bio", bio);
        row.put("note", note);
        data.set("past-tsars", past);
        save();
    }

    /** Удалить прошлого царя по индексу. */
    public void removePastTsar(int idx) {
        List<Map<String, Object>> past = pastTsarsRaw();
        if (idx < 0 || idx >= past.size()) return;
        past.remove(idx);
        data.set("past-tsars", past);
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

    /** Создать пустую запись выборов (временная метка — сейчас); возвращает индекс (0). */
    public int createElection(long voters) {
        List<Map<String, Object>> elections = electionsRaw();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", System.currentTimeMillis());
        entry.put("voters", voters);
        entry.put("parties", new ArrayList<Map<String, Object>>());
        elections.add(0, entry);
        data.set("elections", elections);
        save();
        return 0;
    }

    /** Заменить целиком запись выборов (time/voters/parties) по индексу. */
    public void saveElection(int idx, Map<String, Object> entry) {
        List<Map<String, Object>> elections = electionsRaw();
        if (idx < 0 || idx >= elections.size()) return;
        elections.set(idx, entry);
        data.set("elections", elections);
        save();
    }

    /** Удалить выборы по индексу. */
    public void removeElection(int idx) {
        List<Map<String, Object>> elections = electionsRaw();
        if (idx < 0 || idx >= elections.size()) return;
        elections.remove(idx);
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
