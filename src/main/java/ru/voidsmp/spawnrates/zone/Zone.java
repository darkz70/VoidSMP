package ru.voidsmp.spawnrates.zone;

import java.util.OptionalDouble;

import ru.voidsmp.spawnrates.storage.ZoneRecord;

/**
 * Зона спавна: неизменяемый снимок настроек + рантайм-счётчики.
 *
 * <p>Никаких {@code Location} и {@code World}: только имена миров и целые
 * координаты блоков. Поэтому логика проверки «попала ли точка в зону» и расчёт
 * множителя живут вне Bukkit и покрыты тестами.</p>
 *
 * <p>Все изменения — из главного потока сервера; читают зону тоже только
 * главному потоку (события спавна синхронные), а сохранение работает копией
 * {@link ZoneRecord}, поэтому гонки за async-запись нет.</p>
 */
public final class Zone {

    private final String name;
    private final ZoneRuntime runtime = new ZoneRuntime();
    private volatile ZoneRecord record;

    public Zone(ZoneRecord record) {
        this.record = record;
        this.name = record.name();
    }

    public ZoneRecord record() {
        return record;
    }

    public String name() {
        return name;
    }

    public ZoneRuntime runtime() {
        return runtime;
    }

    /** Применяет новые настройки (создаёт новый снимок целиком). */
    public void replace(ZoneRecord next) {
        this.record = next;
    }

    // ------------------------------------------------------------------ геометрия

    public boolean contains(int x, int y, int z) {
        ZoneRecord r = record;
        return x >= r.minX() && x <= r.maxX()
                && y >= r.minY() && y <= r.maxY()
                && z >= r.minZ() && z <= r.maxZ();
    }

    public boolean containsWorld(String worldName) {
        return record.world().equalsIgnoreCase(worldName);
    }

    public int minChunkX() {
        return ChunkPos.toChunk(record.minX());
    }

    public int maxChunkX() {
        return ChunkPos.toChunk(record.maxX());
    }

    public int minChunkZ() {
        return ChunkPos.toChunk(record.minZ());
    }

    public int maxChunkZ() {
        return ChunkPos.toChunk(record.maxZ());
    }

    public int centerX() {
        return record.minX() + record.sizeX() / 2;
    }

    public int centerZ() {
        return record.minZ() + record.sizeZ() / 2;
    }

    public int centerY() {
        return record.minY() + record.sizeY() / 2;
    }

    public double distanceTo(int x, int y, int z) {
        ZoneRecord r = record;
        double dx = outsideDelta(x, r.minX(), r.maxX());
        double dy = outsideDelta(y, r.minY(), r.maxY());
        double dz = outsideDelta(z, r.minZ(), r.maxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double outsideDelta(int value, int min, int max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0D;
    }

    // ------------------------------------------------------------------ настройки

    public boolean isEnabled() {
        return record.enabled();
    }

    public boolean affectsSpawners() {
        return record.affectSpawners();
    }

    public boolean removeExcess() {
        return record.removeExcess();
    }

    public int cap() {
        return record.cap();
    }

    /**
     * Эффективный множитель зоны для конкретного моба.
     *
     * @return пустое значение — «зона не имеет мнения», спавн идёт как в ванилле
     */
    public OptionalDouble effectiveMultiplier(String entityTypeName, String categoryName) {
        ZoneRecord r = record;
        if (!r.enabled() || !r.matchesCategory(categoryName)) {
            return OptionalDouble.empty();
        }
        double value = r.multiplierFor(entityTypeName);
        boolean hasCap = r.hasCap();
        if (!hasCap && value == 1.0D) {
            // Ничего не меняем — не тратим время на решение и статистику.
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(value);
    }

    /**
     * Действует ли зона на моба вообще: включена и пропускает фильтр категорий.
     * Не путать с {@link #effectiveMultiplier(String, String)}: зона может
     * действовать и при этом ничего не менять (множитель 1.0 без лимита). Такая
     * «нейтральная» зона всё равно решает судьбу спавна в политике PRIORITY —
     * именно поэтому движок спрашивает сначала её.
     */
    public boolean appliesTo(String categoryName) {
        ZoneRecord r = record;
        return r.enabled() && r.matchesCategory(categoryName);
    }

    /** Достигнут ли лимит мобов (по последнему полному пересчёту). */
    public boolean isCapReached() {
        ZoneRecord r = record;
        if (!r.enabled() || !r.hasCap()) {
            return false;
        }
        int counted = runtime.mobs();
        return counted >= 0 && counted >= r.cap();
    }
}
