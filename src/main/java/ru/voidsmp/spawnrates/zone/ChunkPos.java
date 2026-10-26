package ru.voidsmp.spawnrates.zone;

/**
 * Упаковка координат чанка в одно число. Тот же приём, что и в
 * {@code org.bukkit.Chunk#getChunkKey()}, но без зависимости от Bukkit —
 * благодаря этому индекс зон можно тестировать вне сервера.
 */
public final class ChunkPos {

    private ChunkPos() {
    }

    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int x(long key) {
        return (int) (key >> 32);
    }

    public static int z(long key) {
        return (int) key;
    }

    public static int toChunk(int block) {
        return block >> 4;
    }
}
