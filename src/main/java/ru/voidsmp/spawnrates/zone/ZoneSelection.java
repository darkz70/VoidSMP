package ru.voidsmp.spawnrates.zone;

/**
 * Текущее выделение игрока («палка» как в WorldEdit): две точки, заданные
 * кликами по блокам. Хранится только в памяти — выделение одноразовое,
 * а вот уже созданная зона уходит в хранилище навсегда.
 */
public final class ZoneSelection {

    public static final class Point {
        public final String world;
        public final int x;
        public final int y;
        public final int z;

        public Point(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private Point first;
    private Point second;

    public Point first() {
        return first;
    }

    public Point second() {
        return second;
    }

    public void setFirst(Point point) {
        this.first = point;
        if (second != null && !sameWorld(point)) {
            second = null;
        }
    }

    public void setSecond(Point point) {
        this.second = point;
        if (first != null && !sameWorld(point)) {
            first = null;
        }
    }

    public void clear() {
        first = null;
        second = null;
    }

    public boolean isComplete() {
        return first != null && second != null;
    }

    public boolean isEmpty() {
        return first == null && second == null;
    }

    public String world() {
        Point point = first != null ? first : second;
        return point == null ? null : point.world;
    }

    public int minX() {
        return Math.min(first.x, second.x);
    }

    public int minY() {
        return Math.min(first.y, second.y);
    }

    public int minZ() {
        return Math.min(first.z, second.z);
    }

    public int maxX() {
        return Math.max(first.x, second.x);
    }

    public int maxY() {
        return Math.max(first.y, second.y);
    }

    public int maxZ() {
        return Math.max(first.z, second.z);
    }

    /** Максимальный размах по одной из осей — для проверки лимита размера. */
    public int maxSize() {
        return Math.max(maxX() - minX() + 1, Math.max(maxY() - minY() + 1, maxZ() - minZ() + 1));
    }

    public long chunkCount() {
        long cx = (maxX() >> 4) - (minX() >> 4) + 1L;
        long cz = (maxZ() >> 4) - (minZ() >> 4) + 1L;
        return cx * cz;
    }

    private boolean sameWorld(Point point) {
        Point other = first == null ? second : first;
        return other == null || (point.world != null && point.world.equalsIgnoreCase(other.world));
    }
}
