package ru.voidsmp.spawnrates.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ru.voidsmp.spawnrates.storage.ZoneRecord;
import ru.voidsmp.spawnrates.zone.Zone;

/**
 * Проверка арифметики спавна — самой чувствительной части плагина: её
 * результат игрок замечает сразу, а сервер тут не нужен.
 */
class SpawnRuleEngineTest {

    private static final long NOW = 1_700_000_000L;

    private static Zone zone(String name, double multiplier, int cap, int priority) {
        ZoneRecord record = ZoneRecord.normalize(name, "world", null,
                0, 0, 0, 100, 100, 100,
                multiplier, cap, priority, true, false, false,
                Set.of(), Map.of(), 10.0D, NOW);
        return new Zone(record);
    }

    private static SpawnRuleEngine engine(SpawnRuleEngine.Options options) {
        return new SpawnRuleEngine(options);
    }

    private static SpawnRuleEngine.Options options() {
        return new SpawnRuleEngine.Options().maxExtraSpawns(3);
    }

    @Test
    void emptyZoneListChangesNothing() {
        SpawnRuleEngine engine = engine(options());
        assertTrue(engine.evaluateHard(List.of(), "ZOMBIE", "MONSTER").allowed());
        assertTrue(engine.evaluate(List.of(), "ZOMBIE", "MONSTER", SpawnRuleEngine.NEVER).allowed());
    }

    @Test
    void zeroMultiplierIsHardDenyAndAbortsAttempts() {
        Zone banned = zone("no_mobs", 0.0D, ZoneRecord.CAP_OFF, 0);
        SpawnRuleEngine engine = engine(options());

        SpawnRuleEngine.Decision hard = engine.evaluateHard(List.of(banned), "ZOMBIE", "MONSTER");
        assertTrue(hard.deny());
        assertTrue(hard.abortAttempts());
        assertSame(banned, hard.zone());

        SpawnRuleEngine.Decision soft = engine.evaluate(List.of(banned), "ZOMBIE", "MONSTER",
                SpawnRuleEngine.ALWAYS);
        assertTrue(soft.deny());
        assertEquals(1L, banned.runtime().deniedTotal());
    }

    @Test
    void oneMeansVanilla() {
        Zone plain = zone("plain", 1.0D, ZoneRecord.CAP_OFF, 0);
        // Зона без множителя и без лимита вообще не должна вмешиваться.
        assertSame(SpawnRuleEngine.Decision.pass(),
                engine(options()).evaluateHard(List.of(plain), "ZOMBIE", "MONSTER"));

        // ...зато зона с лимитом уже участвует в решении и должна пропустить спавн.
        plain.replace(plain.record().withCap(50, NOW));
        SpawnRuleEngine.Decision decision = engine(options())
                .evaluate(List.of(plain), "ZOMBIE", "MONSTER", SpawnRuleEngine.ALWAYS);
        assertTrue(decision.allowed());
        assertEquals(0, decision.extraSpawns());
    }

    @Test
    void fractionIsRandomPassThrough() {
        Zone half = zone("half", 0.5D, ZoneRecord.CAP_OFF, 0);
        SpawnRuleEngine engine = engine(options());

        // Случайность меньше доли — пропускаем.
        assertTrue(engine.evaluate(List.of(half), "ZOMBIE", "MONSTER", () -> 0.4D).allowed());
        // Больше или равно — отменяем.
        assertTrue(engine.evaluate(List.of(half), "ZOMBIE", "MONSTER", () -> 0.5D).deny());
        assertTrue(engine.evaluate(List.of(half), "ZOMBIE", "MONSTER", () -> 0.9D).deny());
        // Дробная часть не решает всё сама: hard-путь её игнорирует.
        assertTrue(engine.evaluateHard(List.of(half), "ZOMBIE", "MONSTER").allowed());
    }

    @Test
    void extraSpawnsForMultiplierAboveOne() {
        Zone doubled = zone("double", 2.0D, ZoneRecord.CAP_OFF, 0);
        SpawnRuleEngine engine = engine(options());

        assertEquals(1, engine.evaluate(List.of(doubled), "ZOMBIE", "MONSTER", () -> 0.0D).extraSpawns());
        assertEquals(1, engine.evaluate(List.of(doubled), "ZOMBIE", "MONSTER", () -> 0.9D).extraSpawns());

        Zone oneAndHalf = zone("and-a-half", 1.5D, ZoneRecord.CAP_OFF, 0);
        assertEquals(0, engine.evaluate(List.of(oneAndHalf), "ZOMBIE", "MONSTER", () -> 0.4D).extraSpawns());
        assertEquals(1, engine.evaluate(List.of(oneAndHalf), "ZOMBIE", "MONSTER", () -> 0.6D).extraSpawns());
    }

    @Test
    void extraSpawnsCanBeSwitchedOff() {
        Zone doubled = zone("double", 2.0D, ZoneRecord.CAP_OFF, 0);
        SpawnRuleEngine engine = engine(options().extraSpawns(false));
        SpawnRuleEngine.Decision decision =
                engine.evaluate(List.of(doubled), "ZOMBIE", "MONSTER", SpawnRuleEngine.ALWAYS);
        assertTrue(decision.allowed());
        assertEquals(0, decision.extraSpawns());
    }

    @Test
    void extraSpawnsRespectConfiguredLimit() {
        Zone many = zone("many", 9.0D, ZoneRecord.CAP_OFF, 0);
        SpawnRuleEngine engine = engine(options().maxExtraSpawns(2));
        assertEquals(2, engine.evaluate(List.of(many), "ZOMBIE", "MONSTER", SpawnRuleEngine.ALWAYS)
                .extraSpawns());
    }

    @Test
    void capReachedDeniesSpawn() {
        Zone capped = zone("capped", 1.0D, 10, 0);
        SpawnRuleEngine engine = engine(options());
        assertFalse(engine.evaluateHard(List.of(capped), "ZOMBIE", "MONSTER").deny());

        capped.runtime().setMobs(10, true, 1);
        SpawnRuleEngine.Decision decision = engine.evaluateHard(List.of(capped), "ZOMBIE", "MONSTER");
        assertTrue(decision.deny());
        assertTrue(decision.abortAttempts());

        // С недосчитанными чанками (countComplete=false) число всё равно наше:
        // лимит — approximate по умолчанию, но не выдумывает данные.
        capped.runtime().setMobs(3, false, 2);
        assertFalse(engine.evaluateHard(List.of(capped), "ZOMBIE", "MONSTER").deny());
    }

    @Test
    void capDenialCanBeDisabledInConfig() {
        Zone capped = zone("capped", 1.0D, 10, 0);
        capped.runtime().setMobs(99, true, 1);
        SpawnRuleEngine engine = engine(options().denyAtCap(false));
        assertFalse(engine.evaluateHard(List.of(capped), "ZOMBIE", "MONSTER").deny());
    }

    @Test
    void priorityPolicyIgnoresLowerPriorityZone() {
        Zone weak = zone("weak", 0.0D, ZoneRecord.CAP_OFF, 0);
        Zone strong = zone("strong", 1.0D, ZoneRecord.CAP_OFF, 10);
        // Список отсортирован по убыванию приоритета: strong первый.
        SpawnRuleEngine engine = engine(options().overlap(OverlapPolicy.PRIORITY));
        SpawnRuleEngine.Decision decision =
                engine.evaluateHard(List.of(strong, weak), "ZOMBIE", "MONSTER");
        assertFalse(decision.deny());
    }

    @Test
    void multiplyPolicyCombinesBothZones() {
        Zone quarter = zone("quarter", 0.5D, ZoneRecord.CAP_OFF, 0);
        Zone half = zone("half", 0.5D, ZoneRecord.CAP_OFF, 1);
        SpawnRuleEngine engine = engine(options().overlap(OverlapPolicy.MULTIPLY));

        // 0.5 * 0.5 = 0.25: при roll 0.3 спавн отменяется, при 0.1 — проходит.
        assertTrue(engine.evaluate(List.of(half, quarter), "ZOMBIE", "MONSTER", () -> 0.3D).deny());
        assertTrue(engine.evaluate(List.of(half, quarter), "ZOMBIE", "MONSTER", () -> 0.1D).allowed());
    }

    @Test
    void strictestPolicyPicksTheSmallestMultiplier() {
        Zone ban = zone("ban", 0.0D, ZoneRecord.CAP_OFF, 5);
        Zone boost = zone("boost", 4.0D, ZoneRecord.CAP_OFF, 1);
        SpawnRuleEngine engine = engine(options().overlap(OverlapPolicy.STRICTEST));
        assertTrue(engine.evaluateHard(List.of(boost, ban), "ZOMBIE", "MONSTER").deny());
    }

    @Test
    void perMobRuleOverridesZoneMultiplier() {
        ZoneRecord record = ZoneRecord.normalize("mixed", "world", null,
                0, 0, 0, 100, 100, 100,
                0.0D, ZoneRecord.CAP_OFF, 0, true, false, false,
                Set.of(), Map.of("ARMOR_STAND", 1.0D), 10.0D, NOW)
                .withMobRule("BAT", 1.0D, 10.0D, NOW);
        Zone mixed = new Zone(record);
        SpawnRuleEngine engine = engine(options());

        // Общий запрет зоны действует на всех, кроме перечисленных в правилах.
        assertTrue(engine.evaluateHard(List.of(mixed), "ZOMBIE", "MONSTER").deny());
        assertFalse(engine.evaluateHard(List.of(mixed), "BAT", "AMBIENT").deny());
    }

    @Test
    void categoryFilterSkipsOtherMobs() {
        ZoneRecord record = ZoneRecord.normalize("only_monsters", "world", null,
                0, 0, 0, 100, 100, 100,
                0.0D, ZoneRecord.CAP_OFF, 0, true, false, false,
                Set.of("MONSTER"), Map.of(), 10.0D, NOW);
        Zone onlyMonsters = new Zone(record);
        SpawnRuleEngine engine = engine(options());

        assertTrue(engine.evaluateHard(List.of(onlyMonsters), "ZOMBIE", "MONSTER").deny());
        assertFalse(engine.evaluateHard(List.of(onlyMonsters), "COW", "ANIMAL").deny());
    }

    @Test
    void disabledZoneIsInert() {
        Zone banned = zone("off", 0.0D, ZoneRecord.CAP_OFF, 0);
        banned.replace(banned.record().withFlag("enabled", false, NOW));
        assertFalse(engine(options()).evaluateHard(List.of(banned), "ZOMBIE", "MONSTER").deny());
    }
}
