package ru.voidsmp.spawnrates.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ru.voidsmp.spawnrates.storage.ZoneRecord;

class ZoneTest {

    private static final long NOW = 1_700_000_000L;

    private static Zone zone(double multiplier, int cap, Set<String> categories, Map<String, Double> rules) {
        return new Zone(ZoneRecord.normalize("test", "world", null,
                16, 64, 16, 32, 80, 32,
                multiplier, cap, 0, true, false, false,
                categories, rules, 10.0D, NOW));
    }

    @Test
    void containsIsInclusiveOnBothEnds() {
        Zone zone = zone(1.0D, ZoneRecord.CAP_OFF, Set.of(), Map.of());
        assertTrue(zone.contains(16, 64, 16));
        assertTrue(zone.contains(32, 80, 32));
        assertFalse(zone.contains(15, 64, 16));
        assertFalse(zone.contains(33, 64, 16));
        assertFalse(zone.contains(20, 63, 20));
    }

    @Test
    void chunkBoundsCoverTheWholeBox() {
        Zone zone = zone(1.0D, ZoneRecord.CAP_OFF, Set.of(), Map.of());
        assertEquals(1, zone.minChunkX());
        assertEquals(2, zone.maxChunkX());
        assertEquals(1, zone.minChunkZ());
        assertEquals(2, zone.maxChunkZ());
    }

    @Test
    void worldIsComparedIgnoringCase() {
        Zone zone = zone(1.0D, ZoneRecord.CAP_OFF, Set.of(), Map.of());
        assertTrue(zone.containsWorld("WORLD"));
        assertFalse(zone.containsWorld("world_nether"));
    }

    @Test
    void distanceToIsZeroInsideAndShortestOutside() {
        Zone zone = zone(1.0D, ZoneRecord.CAP_OFF, Set.of(), Map.of());
        assertEquals(0.0D, zone.distanceTo(20, 70, 20));
        assertEquals(3.0D, zone.distanceTo(13, 64, 16));
        assertEquals(5.0D, Math.round(zone.distanceTo(16, 64, 37) * 10) / 10.0D);
    }

    @Test
    void neutralZoneWithoutCapDoesNotInterfere() {
        Zone zone = zone(1.0D, ZoneRecord.CAP_OFF, Set.of(), Map.of());
        assertTrue(zone.effectiveMultiplier("ZOMBIE", "MONSTER").isEmpty());
    }

    @Test
    void ruleOverridesZoneMultiplier() {
        Zone zone = zone(0.0D, ZoneRecord.CAP_OFF, Set.of(), Map.of("BAT", 1.0D));
        assertEquals(OptionalDouble.of(0.0D), zone.effectiveMultiplier("ZOMBIE", "MONSTER"));
        // Правило 1.0 = «для летучих мышей ничего не меняем», то есть пустой ответ.
        // При этом сама зона на них действует — только бы не перекрывали соседи.
        assertTrue(zone.effectiveMultiplier("BAT", "AMBIENT").isEmpty());
        assertTrue(zone.appliesTo("AMBIENT"));
        assertFalse(zone.appliesTo("ANIMAL"));
    }

    @Test
    void categoryFilterRejectsOtherCreatures() {
        Zone zone = zone(0.0D, ZoneRecord.CAP_OFF, Set.of("MONSTER"), Map.of());
        assertTrue(zone.effectiveMultiplier("ZOMBIE", "MONSTER").isPresent());
        assertTrue(zone.effectiveMultiplier("COW", "ANIMAL").isEmpty());
    }

    @Test
    void capStateComesFromRuntimeCounter() {
        Zone zone = zone(1.0D, 20, Set.of(), Map.of());
        // Пока мобов не считали (−1), лимит ничего не запрещает.
        assertFalse(zone.isCapReached());
        zone.runtime().setMobs(19, true, 1);
        assertFalse(zone.isCapReached());
        zone.runtime().setMobs(20, true, 2);
        assertTrue(zone.isCapReached());
        zone.runtime().bumpMobs(-5);
        assertFalse(zone.isCapReached());
    }

    @Test
    void disabledZoneIgnoresEverything() {
        Zone zone = zone(0.0D, 1, Set.of(), Map.of());
        zone.replace(zone.record().withFlag("enabled", false, NOW));
        assertFalse(zone.isEnabled());
        assertTrue(zone.effectiveMultiplier("ZOMBIE", "MONSTER").isEmpty());
        assertFalse(zone.isCapReached());
    }

    @Test
    void statisticsAreCountedPerZone() {
        Zone zone = zone(0.5D, ZoneRecord.CAP_OFF, Set.of(), Map.of());
        zone.runtime().recordDenied("ZOMBIE");
        zone.runtime().recordDenied("ZOMBIE");
        zone.runtime().recordAllowed();
        zone.runtime().recordExtra(2);
        assertEquals(2L, zone.runtime().deniedTotal());
        assertEquals(1L, zone.runtime().allowedTotal());
        assertEquals(2L, zone.runtime().extraTotal());
        assertEquals(Map.of("ZOMBIE", 2L), zone.runtime().deniedByType());
    }
}
