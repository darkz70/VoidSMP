package ru.voidsmp.spawnrates.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.voidsmp.spawnrates.storage.ZoneRecord;

/**
 * Индекс «мир → чанк → зоны» — то, за счёт чего решение о спавне принимается
 * без перебора всех зон. Если индекс врет, зона «пропадает» на границе чанка,
 * и это именно то, что тут проверяется.
 */
class ZoneRegistryTest {

    private static final long NOW = 1_700_000_000L;

    private ZoneRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ZoneRegistry();
    }

    private Zone add(String name, String world,
                     int x1, int y1, int z1, int x2, int y2, int z2) {
        return add(name, world, x1, y1, z1, x2, y2, z2, 1.0D, 0);
    }

    private Zone add(String name, String world,
                     int x1, int y1, int z1, int x2, int y2, int z2,
                     double multiplier, int priority) {
        Zone zone = new Zone(ZoneRecord.normalize(name, world, null,
                x1, y1, z1, x2, y2, z2,
                multiplier, ZoneRecord.CAP_OFF, priority, true, false, false,
                Set.of(), Map.of(), 10.0D, NOW));
        registry.add(zone);
        return zone;
    }

    @Test
    void findsZoneByPointInsideIt() {
        add("farm", "world", 0, 60, 0, 31, 80, 31);
        assertEquals(1, registry.size());
        assertEquals(List.of("farm"), names(registry.zonesAt("world", 5, 70, 5)));
        assertEquals(List.of("farm"), names(registry.zonesAt("world", 31, 60, 31)));
        assertTrue(registry.zonesAt("world", 32, 60, 5).isEmpty());
        assertTrue(registry.zonesAt("world", 5, 59, 5).isEmpty());
    }

    @Test
    void otherWorldNeverMatches() {
        add("farm", "world", 0, 0, 0, 100, 100, 100);
        assertTrue(registry.zonesAt("world_nether", 5, 5, 5).isEmpty());
    }

    @Test
    void worldNameIsCaseInsensitive() {
        add("farm", "World", 0, 0, 0, 100, 100, 100);
        assertFalse(registry.zonesAt("world", 5, 5, 5).isEmpty());
    }

    @Test
    void overlappingZonesAreSortedByPriority() {
        add("weak", "world", 0, 0, 0, 100, 100, 100, 0.5D, 0);
        add("strong", "world", 0, 0, 0, 100, 100, 100, 0.1D, 10);
        add("middle", "world", 0, 0, 0, 100, 100, 100, 0.2D, 5);

        List<Zone> zones = registry.zonesAt("world", 10, 10, 10);
        assertEquals(3, zones.size());
        assertEquals("strong", zones.get(0).name());
        assertEquals("middle", zones.get(1).name());
        assertEquals("weak", zones.get(2).name());
    }

    @Test
    void resizeMovesIndexWithZone() {
        Zone zone = add("farm", "world", 0, 0, 0, 15, 15, 15);
        assertFalse(registry.zonesAt("world", 5, 5, 5).isEmpty());

        zone.replace(zone.record().withBox("world", null, 100, 0, 100, 115, 15, 115, NOW));
        registry.refresh(zone);

        assertTrue(registry.zonesAt("world", 5, 5, 5).isEmpty());
        assertFalse(registry.zonesAt("world", 105, 5, 105).isEmpty());
    }

    @Test
    void removeDropsAllChunksOfZone() {
        add("farm", "world", 0, 0, 0, 63, 63, 63);
        assertFalse(registry.zonesAt("world", 60, 1, 60).isEmpty());

        registry.remove("farm");
        assertEquals(0, registry.size());
        assertTrue(registry.zonesAt("world", 60, 1, 60).isEmpty());
        assertNull(registry.byName("farm"));
        assertFalse(registry.exists("farm"));
    }

    @Test
    void rebuildIndexRestoresLookup() {
        add("farm", "world", 0, 0, 0, 47, 47, 47);
        registry.rebuildIndex();
        assertFalse(registry.zonesAt("world", 40, 5, 40).isEmpty());
    }

    @Test
    void replaceAllSwapsEverything() {
        add("old", "world", 0, 0, 0, 15, 15, 15);
        Zone fresh = new Zone(ZoneRecord.normalize("new", "world", null,
                16, 0, 16, 31, 15, 31, 0.0D, ZoneRecord.CAP_OFF, 0, true, false, false,
                Set.of(), Map.of(), 10.0D, NOW));
        registry.replaceAll(List.of(fresh));

        assertEquals(1, registry.size());
        assertNull(registry.byName("old"));
        assertFalse(registry.zonesAt("world", 20, 5, 20).isEmpty());
    }

    @Test
    void zonesNearSortsByDistance() {
        add("close", "world", 0, 0, 0, 10, 10, 10);
        add("far", "world", 200, 0, 200, 210, 10, 210);
        add("other-world", "world_nether", 0, 0, 0, 10, 10, 10);

        List<Zone> near = registry.zonesNear("world", 30, 0, 30, 100);
        assertEquals(1, near.size());
        assertEquals("close", near.get(0).name());

        assertEquals(2, registry.zonesOfWorld("world").size());
        assertEquals(1, registry.zonesOfWorld("world_nether").size());
    }

    @Test
    void clearRemovesIndices() {
        add("farm", "world", 0, 0, 0, 15, 15, 15);
        registry.clear();
        assertEquals(0, registry.size());
        assertTrue(registry.zonesAt("world", 5, 5, 5).isEmpty());
    }

    private static List<String> names(List<Zone> zones) {
        return zones.stream().map(Zone::name).toList();
    }
}
