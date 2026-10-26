package ru.voidsmp.spawnrates.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZoneSelectionTest {

    private static ZoneSelection.Point point(String world, int x, int y, int z) {
        return new ZoneSelection.Point(world, x, y, z);
    }

    @Test
    void needsBothPoints() {
        ZoneSelection selection = new ZoneSelection();
        assertTrue(selection.isEmpty());
        assertFalse(selection.isComplete());

        selection.setFirst(point("world", 0, 64, 0));
        assertFalse(selection.isComplete());

        selection.setSecond(point("world", 10, 70, 10));
        assertTrue(selection.isComplete());
    }

    @Test
    void boxIsNormalized() {
        ZoneSelection selection = new ZoneSelection();
        selection.setFirst(point("world", 10, 70, 20));
        selection.setSecond(point("world", 2, 60, 4));

        assertEquals(2, selection.minX());
        assertEquals(10, selection.maxX());
        assertEquals(60, selection.minY());
        assertEquals(70, selection.maxY());
        assertEquals(4, selection.minZ());
        assertEquals(20, selection.maxZ());
        assertEquals(20, selection.maxSize());
        assertEquals(2L, selection.chunkCount());
    }

    @Test
    void otherWorldPointIsRejected() {
        ZoneSelection selection = new ZoneSelection();
        selection.setFirst(point("world", 0, 0, 0));
        selection.setSecond(point("world_nether", 0, 0, 0));

        // Сбрасывать нужно первую точку, а не молча хранить несогласованное выделение.
        assertFalse(selection.isComplete());
        assertEquals("world_nether", selection.world());
    }

    @Test
    void clearRemovesBothPoints() {
        ZoneSelection selection = new ZoneSelection();
        selection.setFirst(point("world", 1, 2, 3));
        selection.setSecond(point("world", 4, 5, 6));
        selection.clear();

        assertTrue(selection.isEmpty());
        assertNull(selection.world());
    }

    @Test
    void managerKeepsSelectionsPerPlayer() {
        SelectionManager manager = new SelectionManager();
        java.util.UUID player = java.util.UUID.nameUUIDFromBytes("player".getBytes());

        assertNull(manager.get(player));
        ZoneSelection selection = manager.getOrCreate(player);
        selection.setFirst(point("world", 0, 0, 0));
        assertEquals(selection, manager.get(player));

        manager.clear(player);
        assertNull(manager.get(player));
    }
}
