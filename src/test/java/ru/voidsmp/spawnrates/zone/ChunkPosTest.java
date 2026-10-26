package ru.voidsmp.spawnrates.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChunkPosTest {

    @Test
    void packsRoundTrip() {
        int[][] samples = {{0, 0}, {-1, -1}, {3, -1000}, {-1000, 7}, {Integer.MAX_VALUE, Integer.MIN_VALUE}};
        for (int[] sample : samples) {
            long key = ChunkPos.pack(sample[0], sample[1]);
            assertEquals(sample[0], ChunkPos.x(key), "x для " + java.util.Arrays.toString(sample));
            assertEquals(sample[1], ChunkPos.z(key), "z для " + java.util.Arrays.toString(sample));
        }
    }

    @Test
    void differentChunksGetDifferentKeys() {
        assertEquals(ChunkPos.pack(1, 2), ChunkPos.pack(1, 2));
        org.junit.jupiter.api.Assertions.assertNotEquals(ChunkPos.pack(1, 2), ChunkPos.pack(2, 1));
        org.junit.jupiter.api.Assertions.assertNotEquals(ChunkPos.pack(0, 0), ChunkPos.pack(0, 1));
    }

    @Test
    void blockCoordinatesFloorDivideBySixteen() {
        assertEquals(0, ChunkPos.toChunk(0));
        assertEquals(1, ChunkPos.toChunk(16));
        assertEquals(0, ChunkPos.toChunk(15));
        // Отрицательные координаты: арифметический сдвиг = floor division, как в ванилле.
        assertEquals(-1, ChunkPos.toChunk(-1));
        assertEquals(-1, ChunkPos.toChunk(-16));
        assertEquals(-2, ChunkPos.toChunk(-17));
    }
}
