package ru.voidsmp.spawnrates.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ZoneRecordTest {

    private static final long NOW = 1_700_000_000L;

    private static ZoneRecord sample(double multiplier) {
        return ZoneRecord.normalize("Farm", "world", "uuid-1",
                10, 60, 10, 0, 70, 0,
                multiplier, ZoneRecord.CAP_OFF, 0, true, false, false,
                Set.of(), Map.of(), 10.0D, NOW);
    }

    @Test
    void normalizesNameAndBox() {
        ZoneRecord record = sample(1.0D);
        assertEquals("farm", record.name());
        // Порядок точек не важен: min/max считаем сами.
        assertEquals(0, record.minX());
        assertEquals(10, record.maxX());
        assertEquals(60, record.minY());
        assertEquals(70, record.maxY());
        assertEquals(11, record.sizeX());
        assertEquals(11, record.sizeZ());
    }

    @Test
    void clampsMultiplierToConfiguredMaximum() {
        assertEquals(10.0D, sample(999.0D).multiplier());
        assertEquals(0.0D, sample(-3.0D).multiplier());
        assertEquals(0.5D, sample(0.5D).multiplier());
    }

    @Test
    void treatsInvalidNumbersAsZero() {
        ZoneRecord record = ZoneRecord.normalize("farm", "world", null,
                0, 0, 0, 1, 1, 1,
                Double.POSITIVE_INFINITY, 0, 0, true, false, false,
                Set.of(), Map.of(), 10.0D, NOW);
        assertEquals(0.0D, record.multiplier());
    }

    @Test
    void ignoresBlankWorldUuid() {
        ZoneRecord record = ZoneRecord.normalize("farm", "world", "   ",
                0, 0, 0, 5, 5, 5, 1.0D, ZoneRecord.CAP_OFF, 0, true, false, false,
                Set.of(), Map.of(), 10.0D, NOW);
        assertNull(record.worldUuid());
        assertEquals("uuid-1", sample(1.0D).worldUuid());
    }

    @Test
    void capMinusOneMeansNoCap() {
        assertFalse(sample(1.0D).hasCap());
        assertTrue(sample(1.0D).withCap(5, NOW).hasCap());
        assertEquals(ZoneRecord.CAP_OFF, sample(1.0D).withCap(-42, NOW).cap());
    }

    @Test
    void mobRulesAreUppercasedAndClamped() {
        Map<String, Double> rules = new LinkedHashMap<>();
        rules.put("zombie", 999.0D);
        rules.put("sulfur_cube", 0.0D);
        ZoneRecord record = ZoneRecord.normalize("farm", "world", null,
                0, 0, 0, 5, 5, 5,
                1.0D, ZoneRecord.CAP_OFF, 0, true, false, false,
                Set.of("monster"), rules, 10.0D, NOW);

        assertEquals(10.0D, record.multiplierFor("ZOMBIE"));
        assertEquals(0.0D, record.multiplierFor("SULFUR_CUBE"));
        // Моб без правила получает общий множитель зоны.
        assertEquals(1.0D, record.multiplierFor("CREEPER"));
        assertTrue(record.categories().contains("MONSTER"));
    }

    @Test
    void categoriesEmptyMeansAll() {
        assertTrue(sample(1.0D).isAllCategories());
        assertTrue(sample(1.0D).matchesCategory("MONSTER"));

        ZoneRecord monsters = sample(1.0D).withCategories(Set.of("MONSTER"), NOW);
        assertTrue(monsters.matchesCategory("MONSTER"));
        assertFalse(monsters.matchesCategory("ANIMAL"));
    }

    @Test
    void flagsUpdateIndependently() {
        ZoneRecord record = sample(1.0D)
                .withFlag("spawners", true, NOW)
                .withFlag("despawn", true, NOW)
                .withFlag("enabled", false, NOW);
        assertTrue(record.affectSpawners());
        assertTrue(record.removeExcess());
        assertFalse(record.enabled());
        // Неизвестный флаг ничего не ломает и не меняет.
        assertEquals(record, record.withFlag("nope", true, NOW));
    }

    @Test
    void mobRuleAddAndRemove() {
        ZoneRecord record = sample(1.0D).withMobRule("zombie", 0.0D, 10.0D, NOW);
        assertEquals(0.0D, record.multiplierFor("ZOMBIE"));

        ZoneRecord cleared = record.withoutMobRule("ZOMBIE", NOW);
        assertEquals(1.0D, cleared.multiplierFor("ZOMBIE"));
        assertTrue(cleared.mobRules().isEmpty());
    }

    @Test
    void boxMembershipIgnoresWorld() {
        ZoneRecord record = sample(1.0D);
        assertTrue(record.containsBlock(0, 60, 0));
        assertTrue(record.containsBlock(10, 70, 10));
        assertFalse(record.containsBlock(11, 65, 5));
        assertFalse(record.containsBlock(5, 59, 5));
    }

    @Test
    void chunkCountMatchesBox() {
        ZoneRecord record = ZoneRecord.normalize("wide", "world", null,
                0, 0, 0, 15, 15, 15,
                1.0D, ZoneRecord.CAP_OFF, 0, true, false, false,
                Set.of(), Map.of(), 10.0D, NOW);
        // 0..15 — ровно один чанк по каждой оси.
        assertEquals(1L, record.chunkCount());

        ZoneRecord four = record.withBox("world", null, 0, 0, 0, 31, 15, 31, NOW);
        assertEquals(4L, four.chunkCount());

        ZoneRecord single = ZoneRecord.normalize("tiny", "world", null,
                3, 3, 3, 4, 4, 4,
                1.0D, ZoneRecord.CAP_OFF, 0, true, false, false,
                Set.of(), Map.of(), 10.0D, NOW);
        assertEquals(1L, single.chunkCount());
    }

    @Test
    void nameValidation() {
        assertTrue(ZoneRecord.isValidName("farm"));
        assertTrue(ZoneRecord.isValidName("nether-1_spawning"));
        assertFalse(ZoneRecord.isValidName("a"));
        assertFalse(ZoneRecord.isValidName("суп"));
        assertFalse(ZoneRecord.isValidName("bad name"));
        assertFalse(ZoneRecord.isValidName("bad;drop"));
        assertFalse(ZoneRecord.isValidName(null));
    }

    @Test
    void withBoxKeepsSettingsAndMovesGeometry() {
        ZoneRecord record = sample(0.5D).withCap(12, NOW);
        ZoneRecord moved = record.withBox("world_nether", "uuid-2", 100, 10, 100, 110, 20, 110, NOW + 5);
        assertEquals("world_nether", moved.world());
        assertEquals("uuid-2", moved.worldUuid());
        assertEquals(100, moved.minX());
        assertEquals(110, moved.maxZ());
        assertEquals(0.5D, moved.multiplier());
        assertEquals(12, moved.cap());
        assertEquals(NOW + 5, moved.updatedAt());
        assertEquals(NOW, moved.createdAt());
    }
}
