package ru.voidsmp.spawnrates.storage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import ru.voidsmp.spawnrates.config.PluginSettings;

/**
 * Единственная точка доступа к хранилищу.
 *
 * <p>Игровой поток никогда не трогает диск: изменения копятся в очереди и
 * пишутся в отдельном потоке одной транзакцией. При выключении плагина
 * (restar/reload) очередь принудительно сбрасывается на диск и только потом
 * закрывается соединение — поэтому зона, созданная за секунду до рестарта,
 * никуда не девается.</p>
 */
public final class PersistenceService {

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "VoidSpawnRates-Storage");
        thread.setDaemon(true);
        return thread;
    });

    /** Последняя известная версия зоны по имени — из неё же берём «удалить». */
    private final Map<String, ZoneRecord> pending = new LinkedHashMap<>();
    private final List<String> pendingDeletes = new ArrayList<>();
    private final Object queueLock = new Object();

    private ZoneStorage storage;
    private boolean degraded;
    private volatile boolean stopped;
    private volatile String failureReason = null;

    public PersistenceService(JavaPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /**
     * Поднимает хранилище и читает зоны. Вызывается из onEnable, то есть из
     * главного потока — до регистрации слушателей, чтобы ни одна попытка спавна
     * не осталась без данных.
     */
    public List<ZoneRecord> start() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать каталог " + dataFolder.getAbsolutePath());
        }
        File dbFile = new File(dataFolder, settings.databaseFile);
        File yamlFile = new File(dataFolder, settings.yamlFile);

        switch (settings.storageMode) {
            case YAML -> {
                storage = new YamlZoneStorage(yamlFile);
                degraded = true;
            }
            case SQLITE -> storage = SqliteZoneStorage.open(dbFile);
            default -> {
                try {
                    storage = SqliteZoneStorage.open(dbFile);
                    importFromYaml(yamlFile, dbFile);
                } catch (StorageException ex) {
                    failureReason = ex.getMessage();
                    plugin.getLogger().warning("SQLite недоступен (" + ex.getMessage()
                            + "), перехожу на YAML: " + yamlFile.getName());
                    storage = new YamlZoneStorage(yamlFile);
                    degraded = true;
                }
            }
        }
        return load();
    }

    /** Перечитывает зоны из уже открытого хранилища (/spawnrate reload). */
    public List<ZoneRecord> load() {
        if (storage == null) {
            throw new StorageException("Хранилище не открыто");
        }
        flush();
        List<ZoneRecord> loaded = storage.loadAll();
        List<ZoneRecord> sanitized = new ArrayList<>(loaded.size());
        for (ZoneRecord record : loaded) {
            if (!ZoneRecord.isValidName(record.name())) {
                plugin.getLogger().warning("Пропускаю зону с незаконным именем: " + record.name());
                continue;
            }
            sanitized.add(record);
        }
        plugin.getLogger().info("Загружено зон: " + sanitized.size() + " из " + storage.describe());
        return sanitized;
    }

    /**
     * Одноразовый перенос: если в plugins/VoidSpawnRates остался zones.yml
     * (ручной перенос с другого сервера или прошлый YAML-режим), а база пуста —
     * забираем зоны в SQLite и помечаем YAML как импортированный.
     */
    private void importFromYaml(File yamlFile, File dbFile) {
        if (!yamlFile.isFile() || dbFile.length() > 8192L) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(yamlFile);
        if (config.getConfigurationSection("zones") == null) {
            return;
        }
        YamlZoneStorage legacy = new YamlZoneStorage(yamlFile);
        List<ZoneRecord> records = legacy.loadAll();
        if (records.isEmpty()) {
            return;
        }
        storage.saveAll(records);
        File moved = yamlFile.toPath()
                .resolveSibling(yamlFile.getName() + ".imported").toFile();
        if (yamlFile.renameTo(moved)) {
            plugin.getLogger().info("Импортировано зон из " + yamlFile.getName() + ": " + records.size()
                    + " (файл переименован в " + moved.getName() + ")");
        } else {
            plugin.getLogger().info("Импортировано зон из " + yamlFile.getName() + ": " + records.size());
        }
    }

    public void save(ZoneRecord record) {
        synchronized (queueLock) {
            if (stopped) {
                return;
            }
            pendingDeletes.remove(record.name());
            pending.put(record.name(), record);
        }
        flush();
    }

    public void saveAll(Collection<ZoneRecord> records) {
        synchronized (queueLock) {
            if (stopped) {
                return;
            }
            for (ZoneRecord record : records) {
                pending.put(record.name(), record);
            }
        }
        flush();
    }

    public void delete(String name) {
        synchronized (queueLock) {
            if (stopped) {
                return;
            }
            pending.remove(name);
            pendingDeletes.add(name);
        }
        flush();
    }

    /** Ставит задачу в очередь и тут же её исполняет — зоны пишутся сразу после изменения. */
    public void flush() {
        Map<String, ZoneRecord> saves;
        List<String> deletes;
        synchronized (queueLock) {
            if (stopped || storage == null || (pending.isEmpty() && pendingDeletes.isEmpty())) {
                return;
            }
            saves = new LinkedHashMap<>(pending);
            pending.clear();
            deletes = new ArrayList<>(pendingDeletes);
            pendingDeletes.clear();
        }
        writer.execute(() -> {
            try {
                for (ZoneRecord record : saves.values()) {
                    storage.save(record);
                }
                for (String name : deletes) {
                    storage.delete(name);
                }
            } catch (StorageException ex) {
                failureReason = ex.getMessage();
                Bukkit.getLogger().severe("[VoidSpawnRates] Не удалось сохранить зоны: " + ex.getMessage());
                Bukkit.getLogger().severe("[VoidSpawnRates] Изменения остались только в памяти —"
                        + " проверьте диск/файл базы перед рестартом сервера.");
            }
        });
    }

    /**
     * Ждёт, пока поток сохранения разберётся с очередью. Используется
     * /spawnrate save, чтобы админ видел честное «записано».
     */
    public boolean flushNow(long timeoutMillis) {
        flush();
        if (stopped || storage == null) {
            return storage != null;
        }
        CountDownLatch latch = new CountDownLatch(1);
        writer.execute(latch::countDown);
        try {
            return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Периодическая синхронизация с диском (WAL checkpoint). */
    public void checkpoint() {
        if (storage == null) {
            return;
        }
        writer.execute(() -> {
            try {
                storage.checkpoint();
            } catch (StorageException ex) {
                failureReason = ex.getMessage();
                Bukkit.getLogger().warning("[VoidSpawnRates] checkpoint: " + ex.getMessage());
            }
        });
    }

    /** Синхронная допись на диск: используется при выключении плагина. */
    public void shutdown() {
        flush();
        stopped = true;
        CountDownLatch latch = new CountDownLatch(1);
        writer.execute(() -> {
            try {
                if (storage != null) {
                    storage.checkpoint();
                    storage.close();
                }
            } catch (StorageException ex) {
                Bukkit.getLogger().severe("[VoidSpawnRates] Ошибка при закрытии хранилища: " + ex.getMessage());
            } finally {
                latch.countDown();
            }
        });
        writer.shutdown();
        try {
            if (!latch.await(10L, TimeUnit.SECONDS)) {
                Bukkit.getLogger().severe("[VoidSpawnRates] Поток сохранения не успел записать зоны за 10 секунд.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public String describe() {
        if (storage == null) {
            return "нет";
        }
        return degraded ? storage.describe() + " (аварийный режим)" : storage.describe();
    }

    public boolean isDegraded() {
        return degraded;
    }

    public String failureReason() {
        return failureReason;
    }

    /** Есть ли смысл вообще обрабатывать спавн: без хранилища зоны всё равно не переживут рестарт. */
    public boolean isReady() {
        return storage != null;
    }
}
