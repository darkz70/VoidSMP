package ru.voidsmp.spawnrates.zone;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Счётчики и статистика зоны в рантайме. В хранилище не попадает — это только
 * наблюдение за тем, что происходит прямо сейчас.
 */
public final class ZoneRuntime {

    /** Сколько мобов нашли в зоне на последнем пересчёте; -1 — ещё не считали. */
    private final AtomicInteger mobs = new AtomicInteger(-1);
    /** Был ли пересчёт полным (все чанки зоны загружены). */
    private final AtomicBoolean countComplete = new AtomicBoolean(false);
    private final AtomicInteger lastCountTick = new AtomicInteger(Integer.MIN_VALUE);

    private final LongAdder denied = new LongAdder();
    private final LongAdder allowed = new LongAdder();
    private final LongAdder extra = new LongAdder();
    private final LongAdder removed = new LongAdder();
    private final Map<String, LongAdder> deniedByType = new ConcurrentHashMap<>();

    public int mobs() {
        return mobs.get();
    }

    public void setMobs(int value, boolean complete, int tick) {
        mobs.set(Math.max(value, -1));
        countComplete.set(complete);
        lastCountTick.set(tick);
    }

    /** Оптимистичная правка сразу после того, как разрешили/создали моба. */
    public void bumpMobs(int delta) {
        mobs.accumulateAndGet(delta, (current, change) -> {
            if (current < 0) {
                return current;
            }
            return Math.max(current + change, 0);
        });
    }

    public boolean isCountComplete() {
        return countComplete.get();
    }

    public int lastCountTick() {
        return lastCountTick.get();
    }

    public boolean isStale(int tick, int maxAgeTicks) {
        int last = lastCountTick.get();
        return last == Integer.MIN_VALUE || tick - last > maxAgeTicks;
    }

    public void recordDenied(String entityTypeName) {
        denied.increment();
        deniedByType.computeIfAbsent(entityTypeName, key -> new LongAdder()).increment();
    }

    public void recordAllowed() {
        allowed.increment();
    }

    public void recordExtra(int count) {
        extra.add(count);
    }

    public void recordRemoved(int count) {
        removed.add(count);
    }

    public long deniedTotal() {
        return denied.sum();
    }

    public long allowedTotal() {
        return allowed.sum();
    }

    public long extraTotal() {
        return extra.sum();
    }

    public long removedTotal() {
        return removed.sum();
    }

    public Map<String, Long> deniedByType() {
        Map<String, Long> out = new java.util.TreeMap<>();
        for (Map.Entry<String, LongAdder> entry : deniedByType.entrySet()) {
            out.put(entry.getKey(), entry.getValue().sum());
        }
        return out;
    }

    public void resetCounters() {
        denied.reset();
        allowed.reset();
        extra.reset();
        removed.reset();
        deniedByType.clear();
        mobs.set(-1);
        countComplete.set(false);
    }
}
