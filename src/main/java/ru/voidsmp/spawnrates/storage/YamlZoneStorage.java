package ru.voidsmp.spawnrates.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Запасное хранилище — обычный YAML рядом с SQLite. Нужен не для удобства,
 * а чтобы зоны не пропали, если на сервере нет рабочего SQLite-драйвера или
 * файл базы повреждён: тогда админ всё равно получает zones.yml и данные
 * переживают рестарт.
 */
public final class YamlZoneStorage implements ZoneStorage {

    private static final int FILE_VERSION = 1;

    private final Object lock = new Object();
    private final File file;
    private final Map<String, ZoneRecord> zones = new LinkedHashMap<>();
    private boolean loaded;

    public YamlZoneStorage(File file) {
        this.file = file;
    }

    @Override
    public List<ZoneRecord> loadAll() {
        synchronized (lock) {
            ensureLoaded();
            return new ArrayList<>(zones.values());
        }
    }

    @Override
    public void save(ZoneRecord record) {
        synchronized (lock) {
            ensureLoaded();
            zones.put(record.name(), record);
            write();
        }
    }

    @Override
    public void saveAll(Collection<ZoneRecord> records) {
        synchronized (lock) {
            ensureLoaded();
            for (ZoneRecord record : records) {
                zones.put(record.name(), record);
            }
            write();
        }
    }

    @Override
    public void delete(String name) {
        synchronized (lock) {
            ensureLoaded();
            if (zones.remove(name.toLowerCase(Locale.ROOT)) != null) {
                write();
            }
        }
    }

    @Override
    public void checkpoint() {
        synchronized (lock) {
            ensureLoaded();
            write();
        }
    }

    @Override
    public void close() {
        // Файл пишется целиком на каждое изменение — закрывать нечего.
    }

    @Override
    public String describe() {
        return "YAML (" + file.getName() + ")";
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("zones");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            ZoneRecord record = read(key, section);
            if (record != null) {
                zones.put(record.name(), record);
            }
        }
    }

    private ZoneRecord read(String key, ConfigurationSection section) {
        ConfigurationSection min = section.getConfigurationSection("min");
        ConfigurationSection max = section.getConfigurationSection("max");
        if (min == null || max == null) {
            return null;
        }
        String world = section.getString("world");
        if (world == null || world.isBlank()) {
            return null;
        }
        Set<String> categories = new LinkedHashSet<>();
        for (String category : section.getStringList("categories")) {
            if (category != null && !category.isBlank()) {
                categories.add(category.trim().toUpperCase(Locale.ROOT));
            }
        }
        Map<String, Double> mobRules = new LinkedHashMap<>();
        ConfigurationSection mobs = section.getConfigurationSection("mobs");
        if (mobs != null) {
            for (String entity : mobs.getKeys(false)) {
                mobRules.put(entity.toUpperCase(Locale.ROOT), mobs.getDouble(entity));
            }
        }
        return ZoneRecord.normalize(
                key,
                world,
                section.getString("world-uuid"),
                min.getInt("x"), min.getInt("y"), min.getInt("z"),
                max.getInt("x"), max.getInt("y"), max.getInt("z"),
                section.getDouble("multiplier", 1.0D),
                section.getInt("cap", ZoneRecord.CAP_OFF),
                section.getInt("priority", 0),
                section.getBoolean("enabled", true),
                section.getBoolean("affect-spawners", false),
                section.getBoolean("remove-excess", false),
                categories,
                mobRules,
                Double.MAX_VALUE,
                section.getLong("updated-at", System.currentTimeMillis() / 1000L));
    }

    private void write() {
        YamlConfiguration config = new YamlConfiguration();
        config.options().setHeader(List.of(
                "VoidSpawnRates — запасное хранилище зон.",
                "Штатный режим — SQLite (storage.type: AUTO); этот файл используется,",
                "когда SQLite недоступен, и как точка для ручного переноса зон.",
                "Не удаляйте файл при work-ключе storage.type: YAML — это единственная копия."));
        config.set("version", FILE_VERSION);
        for (ZoneRecord record : zones.values()) {
            String path = "zones." + record.name();
            config.set(path + ".world", record.world());
            config.set(path + ".world-uuid", record.worldUuid());
            config.set(path + ".min.x", record.minX());
            config.set(path + ".min.y", record.minY());
            config.set(path + ".min.z", record.minZ());
            config.set(path + ".max.x", record.maxX());
            config.set(path + ".max.y", record.maxY());
            config.set(path + ".max.z", record.maxZ());
            config.set(path + ".multiplier", record.multiplier());
            config.set(path + ".cap", record.cap());
            config.set(path + ".priority", record.priority());
            config.set(path + ".enabled", record.enabled());
            config.set(path + ".affect-spawners", record.affectSpawners());
            config.set(path + ".remove-excess", record.removeExcess());
            config.set(path + ".categories", new ArrayList<>(record.categories()));
            if (!record.mobRules().isEmpty()) {
                for (Map.Entry<String, Double> entry : record.mobRules().entrySet()) {
                    config.set(path + ".mobs." + entry.getKey(), entry.getValue());
                }
            }
            config.set(path + ".created-at", record.createdAt());
            config.set(path + ".updated-at", record.updatedAt());
        }

        Path target = file.toPath();
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("не удалось создать каталог " + parent);
            }
            Files.writeString(temp, config.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // важнее исходная ошибка
            }
            throw new StorageException("Не удалось записать " + file.getName() + ": " + ex.getMessage(), ex);
        }
    }
}
