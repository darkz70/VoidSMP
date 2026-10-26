package ru.voidsmp.spawnrates.storage;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.sql.Driver;

/**
 * SQLite — основное хранилище. Именно оно отвечает за «зона осталась на месте
 * после рестарта»: файл {@code plugins/VoidSpawnRates/data.db} переживает
 * перезапуск сервера, /reload и выгрузку чанков.
 *
 * <p>Драйвер не прилинковывается на compile-time и не перекладывается в jar:
 * Paper кладёт sqlite-jdbc на classloader сервера, поэтому хватает
 * {@code java.sql.*} и {@code Class.forName("org.sqlite.JDBC")}.</p>
 */
public final class SqliteZoneStorage implements ZoneStorage {

    private static final int SCHEMA_VERSION = 1;

    private static final String CREATE_ZONES = """
            CREATE TABLE IF NOT EXISTS vsr_zones (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                name            TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                world           TEXT    NOT NULL,
                world_uuid      TEXT,
                min_x           INTEGER NOT NULL,
                min_y           INTEGER NOT NULL,
                min_z           INTEGER NOT NULL,
                max_x           INTEGER NOT NULL,
                max_y           INTEGER NOT NULL,
                max_z           INTEGER NOT NULL,
                multiplier      REAL    NOT NULL DEFAULT 1,
                cap             INTEGER NOT NULL DEFAULT -1,
                priority        INTEGER NOT NULL DEFAULT 0,
                enabled         INTEGER NOT NULL DEFAULT 1,
                affect_spawners INTEGER NOT NULL DEFAULT 0,
                remove_excess   INTEGER NOT NULL DEFAULT 0,
                categories      TEXT    NOT NULL DEFAULT '',
                created_at      INTEGER NOT NULL DEFAULT 0,
                updated_at      INTEGER NOT NULL DEFAULT 0
            )""";

    private static final String CREATE_RULES = """
            CREATE TABLE IF NOT EXISTS vsr_zone_rules (
                zone_id    INTEGER NOT NULL,
                entity     TEXT    NOT NULL,
                multiplier REAL    NOT NULL,
                PRIMARY KEY (zone_id, entity)
            )""";

    private static final String CREATE_INDEX =
            "CREATE INDEX IF NOT EXISTS vsr_zones_world ON vsr_zones (world)";

    private static final String UPSERT_ZONE = """
            INSERT INTO vsr_zones (name, world, world_uuid, min_x, min_y, min_z, max_x, max_y, max_z,
                                   multiplier, cap, priority, enabled, affect_spawners, remove_excess,
                                   categories, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (name) DO UPDATE SET
                world = excluded.world,
                world_uuid = excluded.world_uuid,
                min_x = excluded.min_x,
                min_y = excluded.min_y,
                min_z = excluded.min_z,
                max_x = excluded.max_x,
                max_y = excluded.max_y,
                max_z = excluded.max_z,
                multiplier = excluded.multiplier,
                cap = excluded.cap,
                priority = excluded.priority,
                enabled = excluded.enabled,
                affect_spawners = excluded.affect_spawners,
                remove_excess = excluded.remove_excess,
                categories = excluded.categories,
                updated_at = excluded.updated_at""";

    private static final String SELECT_ID = "SELECT id FROM vsr_zones WHERE name = ?";

    private final Object lock = new Object();
    private final File file;
    private Connection connection;

    private SqliteZoneStorage(File file) {
        this.file = file;
    }

    /**
     * Открывает базу и поднимает схему. Бросает {@link StorageException}, если
     * SQLite недоступен (драйвера нет, файл повреждён, нативная либа не грузится) —
     * вызывающий код в этом случае переходит на YAML.
     */
    public static SqliteZoneStorage open(File file) {
        SqliteZoneStorage storage = new SqliteZoneStorage(file);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new StorageException("Не удалось создать каталог " + parent.getAbsolutePath());
            }
            storage.connection = connect(file);
            storage.setup();
            return storage;
        } catch (StorageException ex) {
            storage.closeQuietly();
            throw ex;
        } catch (Throwable throwable) {
            storage.closeQuietly();
            throw new StorageException(throwable.getClass().getSimpleName() + ": " + throwable.getMessage(), throwable);
        }
    }

    private static Connection connect(File file) throws Exception {
        String url = "jdbc:sqlite:" + file.getAbsolutePath();
        try {
            Class<?> driverClass = Class.forName("org.sqlite.JDBC");
            Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
            Connection created = driver.connect(url, new Properties());
            if (created != null) {
                return created;
            }
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                 | IllegalAccessException | InvocationTargetException ex) {
            // Драйвера нет под каноничным именем — пробуем то, что зарегистрировано в DriverManager.
        } catch (NoClassDefFoundError | UnsatisfiedLinkError ex) {
            throw new StorageException("SQLite-драйвер не загружается: " + ex.getMessage(), ex);
        }
        try {
            return DriverManager.getConnection(url);
        } catch (SQLException ex) {
            throw new StorageException("Нет драйвера JDBC для SQLite: " + ex.getMessage(), ex);
        }
    }

    private void setup() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute(CREATE_ZONES);
            statement.execute(CREATE_RULES);
            statement.execute(CREATE_INDEX);
            try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
                int version = result.next() ? result.getInt(1) : 0;
                if (version < SCHEMA_VERSION) {
                    statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
                }
            }
        }
    }

    @Override
    public List<ZoneRecord> loadAll() {
        synchronized (lock) {
            requireConnection();
            Map<Long, ZoneRecord> zones = new LinkedHashMap<>();
            Map<Long, Map<String, Double>> rules = new LinkedHashMap<>();
            try {
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT * FROM vsr_zones ORDER BY priority DESC, id ASC");
                     ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        long id = result.getLong("id");
                        zones.put(id, read(result, id, result.getString("name")));
                        rules.put(id, new LinkedHashMap<>());
                    }
                }
                if (!zones.isEmpty()) {
                    try (PreparedStatement query = connection.prepareStatement(
                            "SELECT zone_id, entity, multiplier FROM vsr_zone_rules");
                         ResultSet result = query.executeQuery()) {
                        while (result.next()) {
                            Map<String, Double> map = rules.get(result.getLong("zone_id"));
                            if (map != null) {
                                map.put(result.getString("entity"), result.getDouble("multiplier"));
                            }
                        }
                    }
                }
            } catch (SQLException ex) {
                throw new StorageException("Не удалось прочитать зоны: " + ex.getMessage(), ex);
            }
            List<ZoneRecord> out = new ArrayList<>(zones.size());
            for (Map.Entry<Long, ZoneRecord> entry : zones.entrySet()) {
                Map<String, Double> mobRules = rules.get(entry.getKey());
                ZoneRecord base = entry.getValue();
                out.add(base.toBuilder()
                        .mobRules(Map.copyOf(mobRules == null ? Map.of() : mobRules))
                        .build());
            }
            return out;
        }
    }

    /**
     * Имя зоны нужно знать и для нормализации, и для ключа — читаем его отдельно,
     * потому что {@code read(ResultSet, long)} возвращает запись без правил по мобам.
     */
    private ZoneRecord read(ResultSet result, long id, String name) throws SQLException {
        String categories = result.getString("categories");
        Set<String> cats = new LinkedHashSet<>();
        if (categories != null && !categories.isBlank()) {
            for (String part : categories.split(",")) {
                String trimmed = part.trim().toUpperCase(Locale.ROOT);
                if (!trimmed.isEmpty()) {
                    cats.add(trimmed);
                }
            }
        }
        return new ZoneRecord(
                name,
                result.getString("world"),
                result.getString("world_uuid"),
                result.getInt("min_x"), result.getInt("min_y"), result.getInt("min_z"),
                result.getInt("max_x"), result.getInt("max_y"), result.getInt("max_z"),
                result.getDouble("multiplier"),
                result.getInt("cap"),
                result.getInt("priority"),
                result.getInt("enabled") != 0,
                result.getInt("affect_spawners") != 0,
                result.getInt("remove_excess") != 0,
                Set.copyOf(cats),
                Map.of(),
                result.getLong("created_at"),
                result.getLong("updated_at"));
    }

    @Override
    public void save(ZoneRecord record) {
        synchronized (lock) {
            requireConnection();
            try {
                connection.setAutoCommit(false);
                try {
                    upsert(record);
                    connection.commit();
                } catch (SQLException ex) {
                    try {
                        connection.rollback();
                    } catch (SQLException ignored) {
                        // нечего добавить к исходной ошибке
                    }
                    throw ex;
                } finally {
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException ignored) {
                        // соединение всё равно живое
                    }
                }
            } catch (SQLException ex) {
                throw new StorageException("Запись зоны '" + record.name() + "' не удалась: " + ex.getMessage(), ex);
            }
        }
    }

    private void upsert(ZoneRecord record) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(UPSERT_ZONE)) {
            int index = 1;
            insert.setString(index++, record.name());
            insert.setString(index++, record.world());
            insert.setString(index++, record.worldUuid());
            insert.setInt(index++, record.minX());
            insert.setInt(index++, record.minY());
            insert.setInt(index++, record.minZ());
            insert.setInt(index++, record.maxX());
            insert.setInt(index++, record.maxY());
            insert.setInt(index++, record.maxZ());
            insert.setDouble(index++, record.multiplier());
            insert.setInt(index++, record.cap());
            insert.setInt(index++, record.priority());
            insert.setInt(index++, record.enabled() ? 1 : 0);
            insert.setInt(index++, record.affectSpawners() ? 1 : 0);
            insert.setInt(index++, record.removeExcess() ? 1 : 0);
            insert.setString(index++, joinCategories(record.categories()));
            insert.setLong(index++, record.createdAt());
            insert.setLong(index, record.updatedAt());
            insert.executeUpdate();
        }

        long id;
        try (PreparedStatement find = connection.prepareStatement(SELECT_ID)) {
            find.setString(1, record.name());
            try (ResultSet result = find.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Зона '" + record.name() + "' не найдена сразу после записи");
                }
                id = result.getLong(1);
            }
        }

        try (PreparedStatement clear = connection.prepareStatement(
                "DELETE FROM vsr_zone_rules WHERE zone_id = ?")) {
            clear.setLong(1, id);
            clear.executeUpdate();
        }
        if (record.mobRules().isEmpty()) {
            return;
        }
        try (PreparedStatement addRule = connection.prepareStatement(
                "INSERT INTO vsr_zone_rules (zone_id, entity, multiplier) VALUES (?, ?, ?)")) {
            for (Map.Entry<String, Double> entry : record.mobRules().entrySet()) {
                addRule.setLong(1, id);
                addRule.setString(2, entry.getKey().toUpperCase(Locale.ROOT));
                addRule.setDouble(3, entry.getValue());
                addRule.addBatch();
            }
            addRule.executeBatch();
        }
    }

    @Override
    public void saveAll(Collection<ZoneRecord> records) {
        for (ZoneRecord record : records) {
            save(record);
        }
    }

    @Override
    public void delete(String name) {
        synchronized (lock) {
            requireConnection();
            try {
                connection.setAutoCommit(false);
                try {
                    long id;
                    try (PreparedStatement find = connection.prepareStatement(SELECT_ID)) {
                        find.setString(1, name.toLowerCase(Locale.ROOT));
                        try (ResultSet result = find.executeQuery()) {
                            if (!result.next()) {
                                connection.rollback();
                                return;
                            }
                            id = result.getLong(1);
                        }
                    }
                    try (PreparedStatement clear = connection.prepareStatement(
                            "DELETE FROM vsr_zone_rules WHERE zone_id = ?")) {
                        clear.setLong(1, id);
                        clear.executeUpdate();
                    }
                    try (PreparedStatement remove = connection.prepareStatement(
                            "DELETE FROM vsr_zones WHERE id = ?")) {
                        remove.setLong(1, id);
                        remove.executeUpdate();
                    }
                    connection.commit();
                } catch (SQLException ex) {
                    try {
                        connection.rollback();
                    } catch (SQLException ignored) {
                        // исходная ошибка важнее
                    }
                    throw ex;
                } finally {
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException ignored) {
                        // соединение живое
                    }
                }
            } catch (SQLException ex) {
                throw new StorageException("Удаление зоны '" + name + "' не удалось: " + ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void checkpoint() {
        synchronized (lock) {
            if (connection == null) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            } catch (SQLException ex) {
                throw new StorageException("WAL checkpoint не пройден: " + ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (connection == null) {
                return;
            }
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
                connection.close();
            } catch (SQLException ex) {
                throw new StorageException("Не удалось закрыть базу: " + ex.getMessage(), ex);
            } finally {
                connection = null;
            }
        }
    }

    @Override
    public String describe() {
        return "SQLite";
    }

    public File file() {
        return file;
    }

    private void requireConnection() {
        if (connection == null) {
            throw new StorageException("База закрыта");
        }
    }

    private void closeQuietly() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // уже закрываемся после ошибки
        } finally {
            connection = null;
        }
    }

    private static String joinCategories(Set<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(",");
        for (String category : categories) {
            joiner.add(category.toUpperCase(Locale.ROOT));
        }
        return joiner.toString();
    }
}
