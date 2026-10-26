package ru.voidsmp.spawnrates.spawn;

import java.util.List;
import java.util.OptionalDouble;

import ru.voidsmp.spawnrates.zone.Zone;

/**
 * Чистая арифметика спавна: получает список зон, в которые попал моб, и решает —
 * пускать спавн, отменить, и сколько мобов доспаунить сверху.
 *
 * <p>Как читается множитель:</p>
 * <ul>
 *   <li>{@code 0.0} — спавна нет;</li>
 *   <li>{@code 0.0 < m < 1.0} — пропускаем m·100% попыток (остальное отменяем);</li>
 *   <li>{@code 1.0} — как ванилла;</li>
 *   <li>{@code m > 1.0} — пропускаем всё и добавляем floor(m − 1) + дробную часть
 *       мобов того же типа рядом. Отменой спавна «чаще» сделать нельзя, поэтому
 *       здесь именно добавка сущностей.</li>
 * </ul>
 *
 * <p>Класс без Bukkit-типов и без {@link java.util.Random}: случайность приходит
 * параметром, поэтому поведение детерминированно в тестах.</p>
 */
public final class SpawnRuleEngine {

    /** Источник случайного числа в диапазоне [0, 1). */
    public interface RandomSource {
        double next();
    }

    public static final RandomSource NEVER = () -> 0.0D;
    public static final RandomSource ALWAYS = () -> 0.999999D;

    public static final class Decision {
        private static final Decision PASS = new Decision(false, 0, false, null);

        private final boolean deny;
        private final int extraSpawns;
        private final boolean abortAttempts;
        private final Zone zone;

        Decision(boolean deny, int extraSpawns, boolean abortAttempts, Zone zone) {
            this.deny = deny;
            this.extraSpawns = extraSpawns;
            this.abortAttempts = abortAttempts;
            this.zone = zone;
        }

        public static Decision pass() {
            return PASS;
        }

        public boolean deny() {
            return deny;
        }

        public boolean allowed() {
            return !deny;
        }

        /** Нужно ли попросить сервер не пробовать другие точки в этом чанке. */
        public boolean abortAttempts() {
            return abortAttempts;
        }

        public int extraSpawns() {
            return extraSpawns;
        }

        /** Зона, которая приняла решение (для статистики и отладки). */
        public Zone zone() {
            return zone;
        }
    }

    public static final class Options {
        private OverlapPolicy overlap = OverlapPolicy.PRIORITY;
        private boolean denyAtCap = true;
        private boolean extraSpawns = true;
        private int maxExtraSpawns = 3;
        private boolean abortOnHardDeny = true;

        public Options overlap(OverlapPolicy value) {
            this.overlap = value;
            return this;
        }

        public Options denyAtCap(boolean value) {
            this.denyAtCap = value;
            return this;
        }

        public Options extraSpawns(boolean value) {
            this.extraSpawns = value;
            return this;
        }

        public Options maxExtraSpawns(int value) {
            this.maxExtraSpawns = Math.max(value, 0);
            return this;
        }

        public Options abortOnHardDeny(boolean value) {
            this.abortOnHardDeny = value;
            return this;
        }
    }

    private final Options options;

    public SpawnRuleEngine(Options options) {
        this.options = options;
    }

    public Options options() {
        return options;
    }

    /**
     * «Жёсткое» решение — только то, что не зависит от случайности: полный запрет
     * (множитель 0) или достигнутый лимит мобов. Используется на
     * {@code PreCreatureSpawnEvent}, чтобы вообще не строить сущность.
     *
     * <p>В политике {@code PRIORITY} решает самая приоритетная <em>действующая</em>
     * зона: если у неё множитель 1.0, спавн остаётся ванильным, и зоны с меньшим
     * приоритетом уже не учитываются.</p>
     */
    public Decision evaluateHard(List<Zone> zones, String entityTypeName, String categoryName) {
        if (zones.isEmpty()) {
            return Decision.pass();
        }
        Zone winner = null;
        double multiplier = 1.0D;
        boolean capReached = false;

        for (Zone zone : zones) {
            // Зона либо действует на моба (и тогда имеет голос, даже с множителем
            // 1.0), либо выключена / не та категория — тогда её просто нет.
            if (!zone.appliesTo(categoryName)) {
                continue;
            }
            OptionalDouble value = zone.effectiveMultiplier(entityTypeName, categoryName);
            double weight = value.isEmpty() ? 1.0D : value.getAsDouble();
            if (winner == null) {
                winner = zone;
                multiplier = weight;
                if (options.denyAtCap && zone.isCapReached()) {
                    capReached = true;
                }
                if (options.overlap == OverlapPolicy.PRIORITY) {
                    // Первая зона в списке уже самая приоритетная — остальные не считаем.
                    break;
                }
                continue;
            }
            if (options.denyAtCap && zone.isCapReached()) {
                capReached = true;
            }
            if (options.overlap == OverlapPolicy.MULTIPLY) {
                multiplier *= weight;
            } else {
                multiplier = Math.min(multiplier, weight);
            }
        }

        if (winner == null) {
            return Decision.pass();
        }
        if (capReached) {
            return new Decision(true, 0, options.abortOnHardDeny, winner);
        }
        if (multiplier <= 0.0D) {
            return new Decision(true, 0, options.abortOnHardDeny, winner);
        }
        return Decision.pass();
    }

    /**
     * Полное решение с броском случайного числа. Применяется на
     * {@code CreatureSpawnEvent} — здесь можно и отменить уже созданного моба,
     * и добавить лишних.
     */
    public Decision evaluate(List<Zone> zones, String entityTypeName, String categoryName,
                             RandomSource random) {
        if (zones.isEmpty()) {
            return Decision.pass();
        }
        Zone winner = null;
        double multiplier = 1.0D;
        boolean capReached = false;

        for (Zone zone : zones) {
            // Зона либо действует на моба (и тогда имеет голос, даже с множителем
            // 1.0), либо выключена / не та категория — тогда её просто нет.
            if (!zone.appliesTo(categoryName)) {
                continue;
            }
            OptionalDouble value = zone.effectiveMultiplier(entityTypeName, categoryName);
            double weight = value.isEmpty() ? 1.0D : value.getAsDouble();
            if (winner == null) {
                winner = zone;
                multiplier = weight;
                if (options.denyAtCap && zone.isCapReached()) {
                    capReached = true;
                }
                if (options.overlap == OverlapPolicy.PRIORITY) {
                    // Первая зона в списке уже самая приоритетная — остальные не считаем.
                    break;
                }
                continue;
            }
            if (options.denyAtCap && zone.isCapReached()) {
                capReached = true;
            }
            if (options.overlap == OverlapPolicy.MULTIPLY) {
                multiplier *= weight;
            } else {
                multiplier = Math.min(multiplier, weight);
            }
        }

        if (winner == null) {
            return Decision.pass();
        }
        if (capReached) {
            winner.runtime().recordDenied(entityTypeName);
            return new Decision(true, 0, false, winner);
        }
        if (multiplier <= 0.0D) {
            winner.runtime().recordDenied(entityTypeName);
            return new Decision(true, 0, false, winner);
        }
        // Статистику «пропущено» пишем, только если зона реально на спавн повлияла:
        // иначе в любой выключенной по множителю зоне счётчик молча растёт.
        if (multiplier != 1.0D || winner.record().hasCap()) {
            winner.runtime().recordAllowed();
        }
        if (multiplier >= 1.0D) {
            int extra = 0;
            if (options.extraSpawns && multiplier > 1.0D) {
                double expected = multiplier - 1.0D + random.next();
                extra = (int) Math.floor(expected);
                extra = Math.min(extra, options.maxExtraSpawns);
                if (extra > 0) {
                    winner.runtime().recordExtra(extra);
                }
            }
            return new Decision(false, extra, false, winner);
        }
        // 0 < multiplier < 1: пропускаем только долю попыток.
        if (random.next() >= multiplier) {
            winner.runtime().recordDenied(entityTypeName);
            return new Decision(true, 0, false, winner);
        }
        return new Decision(false, 0, false, winner);
    }

    /** Смоделировать поведение без сервера — используется командой /spawnrate test. */
    public double expectedRatio(double multiplier) {
        return Math.max(multiplier, 0.0D);
    }
}
