package ru.voidsmp.spawnrates.zone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Зоны в памяти + индекс «мир → чанк → зоны». Индекс нужен, чтобы решение при
 * попытке спавна было O(1): событие спавна случается очень часто, а перебирать
 * все зоны подряд нельзя.
 *
 * <p>Мутации — только из главного потока сервера.</p>
 */
public final class ZoneRegistry {

    private static final Comparator<Zone> BY_PRIORITY =
            Comparator.comparingInt((Zone zone) -> zone.record().priority()).reversed()
                    .thenComparing(Zone::name);

    private final Map<String, Zone> byName = new LinkedHashMap<>();
    private final Map<String, Map<Long, List<Zone>>> chunkIndex = new HashMap<>();

    public void clear() {
        byName.clear();
        chunkIndex.clear();
    }

    public void replaceAll(Collection<Zone> zones) {
        clear();
        for (Zone zone : zones) {
            add(zone);
        }
    }

    public boolean exists(String name) {
        return byName.containsKey(lower(name));
    }

    public Zone byName(String name) {
        return byName.get(lower(name));
    }

    public int size() {
        return byName.size();
    }

    public List<Zone> all() {
        return new ArrayList<>(byName.values());
    }

    /** Добавляет зону (или заменяет одноимённую) и перестраивает её часть индекса. */
    public void add(Zone zone) {
        Zone previous = byName.put(zone.name(), zone);
        if (previous != null) {
            unindex(previous);
        }
        index(zone);
    }

    /**
     * Перестроение индекса после изменения границ зоны (resize/перенос в другой
     * мир). Пересобираем мир целиком: у зоны уже новый снимок настроек, и
     * старыми координатами её из индекса иначе не вычистить. Операция редкая
     * (только по команде админа), так что полное перестроение дешевле отладки.
     */
    public void refresh(Zone zone) {
        String world = lower(zone.record().world());
        chunkIndex.remove(world);
        Map<Long, List<Zone>> perWorld =
                chunkIndex.computeIfAbsent(world, key -> new HashMap<>());
        for (Zone other : byName.values()) {
            if (lower(other.record().world()).equals(world)) {
                index(other);
            }
        }
        if (perWorld.isEmpty()) {
            chunkIndex.remove(world);
        }
    }

    public Zone remove(String name) {
        Zone zone = byName.remove(lower(name));
        if (zone != null) {
            unindex(zone);
        }
        return zone;
    }

    /** Полная пересборка индекса — после /reload и импорта из хранилища. */
    public void rebuildIndex() {
        chunkIndex.clear();
        for (Zone zone : byName.values()) {
            index(zone);
        }
    }

    /** Зоны, которые покрывают точку (пересечения отсортированы по приоритету). */
    public List<Zone> zonesAt(String world, int x, int y, int z) {
        Map<Long, List<Zone>> perWorld = chunkIndex.get(lower(world));
        if (perWorld == null) {
            return Collections.emptyList();
        }
        List<Zone> candidates = perWorld.get(ChunkPos.pack(x >> 4, z >> 4));
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<Zone> hits = null;
        for (Zone zone : candidates) {
            if (zone.contains(x, y, z)) {
                if (hits == null) {
                    hits = new ArrayList<>(2);
                }
                hits.add(zone);
            }
        }
        if (hits == null) {
            return Collections.emptyList();
        }
        if (hits.size() > 1) {
            hits.sort(BY_PRIORITY);
        }
        return hits;
    }

    /** Зоны мира, чьи границы пересекают шар радиуса вокруг точки (для /spawnrate near). */
    public List<Zone> zonesNear(String world, int x, int y, int z, double radius) {
        List<Zone> out = new ArrayList<>();
        for (Zone zone : byName.values()) {
            if (!zone.containsWorld(world)) {
                continue;
            }
            if (zone.distanceTo(x, y, z) <= radius) {
                out.add(zone);
            }
        }
        out.sort(Comparator.comparingDouble(zone -> zone.distanceTo(x, y, z)));
        return out;
    }

    public List<Zone> zonesOfWorld(String world) {
        List<Zone> out = new ArrayList<>();
        for (Zone zone : byName.values()) {
            if (zone.containsWorld(world)) {
                out.add(zone);
            }
        }
        return out;
    }

    private void index(Zone zone) {
        Map<Long, List<Zone>> perWorld =
                chunkIndex.computeIfAbsent(lower(zone.record().world()), key -> new HashMap<>());
        for (int cx = zone.minChunkX(); cx <= zone.maxChunkX(); cx++) {
            for (int cz = zone.minChunkZ(); cz <= zone.maxChunkZ(); cz++) {
                perWorld.computeIfAbsent(ChunkPos.pack(cx, cz), key -> new ArrayList<>(1)).add(zone);
            }
        }
    }

    private void unindex(Zone zone) {
        Map<Long, List<Zone>> perWorld = chunkIndex.get(lower(zone.record().world()));
        if (perWorld == null) {
            return;
        }
        for (int cx = zone.minChunkX(); cx <= zone.maxChunkX(); cx++) {
            for (int cz = zone.minChunkZ(); cz <= zone.maxChunkZ(); cz++) {
                long key = ChunkPos.pack(cx, cz);
                List<Zone> list = perWorld.get(key);
                if (list == null) {
                    continue;
                }
                list.remove(zone);
                if (list.isEmpty()) {
                    perWorld.remove(key);
                }
            }
        }
        if (perWorld.isEmpty()) {
            chunkIndex.remove(lower(zone.record().world()));
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
