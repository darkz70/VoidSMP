package ru.voidsmp.spawnrates.zone;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Текущие выделения игроков. Только память: выделение — черновик, а
 * настоящая «постоянность» начинается с зоны, которая уходит в SQLite.
 */
public final class SelectionManager {

    private final Map<UUID, ZoneSelection> selections = new HashMap<>();

    public ZoneSelection get(UUID player) {
        return selections.get(player);
    }

    public ZoneSelection getOrCreate(UUID player) {
        return selections.computeIfAbsent(player, key -> new ZoneSelection());
    }

    public void clear(UUID player) {
        selections.remove(player);
    }

    public void dropAll() {
        selections.clear();
    }
}
