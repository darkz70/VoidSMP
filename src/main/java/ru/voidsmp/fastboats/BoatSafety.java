package ru.voidsmp.fastboats;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;

/** Conservative look-ahead check before adding thrust. Does not move any entities. */
final class BoatSafety {
    private BoatSafety() {}

    static boolean isPathClear(Boat boat, double dx, double dz) {
        Location start = boat.getLocation();
        World world = boat.getWorld();
        BoundingBox swept = boat.getBoundingBox().clone();
        for (Entity passenger : boat.getPassengers()) {
            swept.union(passenger.getBoundingBox());
        }
        swept.union(swept.clone().shift(dx, 0, dz));
        // Conservative swept box: may stop early on diagonal corners, but cannot skip a wall.
        int minX = (int) Math.floor(swept.getMinX()) - 1;
        int maxX = (int) Math.floor(swept.getMaxX()) + 1;
        int minZ = (int) Math.floor(swept.getMinZ()) - 1;
        int maxZ = (int) Math.floor(swept.getMaxZ()) + 1;
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                if (!world.isChunkLoaded(x, z)) return false;
            }
        }
        // Scan one block below as fences and walls can extend above their block.
        int minY = Math.max(world.getMinHeight(), (int) Math.floor(swept.getMinY()) - 1);
        int maxY = Math.min(world.getMaxHeight() - 1, (int) Math.floor(swept.getMaxY()));
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    for (BoundingBox shape : world.getBlockAt(x, y, z).getCollisionShape().getBoundingBoxes()) {
                        if (swept.overlaps(shape.clone().shift(x, y, z))) return false;
                    }
                }
            }
        }
        if (!world.getNearbyEntities(swept, entity -> entity != boat
                && !boat.getPassengers().contains(entity)
                && (entity instanceof org.bukkit.entity.Vehicle
                || entity instanceof org.bukkit.entity.LivingEntity living && living.isCollidable())).isEmpty()) {
            return false;
        }
        Location destination = start.clone().add(dx, 0, dz);
        if (!world.getWorldBorder().isInside(destination)) return false;
        return true;
    }
}
