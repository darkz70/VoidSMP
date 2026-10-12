package dev.voidsmp;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Keeps Nether portal travel at a configurable horizontal coordinate ratio. */
public final class VoidSMPPlugin extends JavaPlugin implements Listener {
    private static final double DEFAULT_NETHER_COORDINATE_SCALE = 1.0D;

    /** Ratio used for Overworld -> Nether travel (vanilla uses 8.0). */
    private double netherCoordinateScale = DEFAULT_NETHER_COORDINATE_SCALE;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCoordinateScale();
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("Configured Overworld-to-Nether coordinate divisor: " + netherCoordinateScale);
    }

    private void loadCoordinateScale() {
        double configuredScale = getConfig().getDouble(
                "nether-coordinate-scale",
                DEFAULT_NETHER_COORDINATE_SCALE
        );

        if (!Double.isFinite(configuredScale) || configuredScale <= 0.0D) {
            getLogger().warning(
                    "Invalid nether-coordinate-scale; using " + DEFAULT_NETHER_COORDINATE_SCALE + "."
            );
            netherCoordinateScale = DEFAULT_NETHER_COORDINATE_SCALE;
            return;
        }

        netherCoordinateScale = configuredScale;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Location destination = mapPortalDestination(event.getFrom(), event.getTo());
        if (destination != null) {
            event.setTo(destination);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Location destination = mapPortalDestination(event.getFrom(), event.getTo());
        if (destination != null) {
            event.setTo(destination);
        }
    }

    /**
     * Maps only Overworld <-> Nether travel. The portal search/creation and destination Y
     * coordinate remain Paper's responsibility; only the horizontal X/Z ratio is changed.
     */
    private Location mapPortalDestination(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) {
            return null;
        }

        World.Environment sourceEnvironment = from.getWorld().getEnvironment();
        World.Environment destinationEnvironment = to.getWorld().getEnvironment();
        double coordinateMultiplier;

        if (sourceEnvironment == World.Environment.NORMAL
                && destinationEnvironment == World.Environment.NETHER) {
            coordinateMultiplier = 1.0D / netherCoordinateScale;
        } else if (sourceEnvironment == World.Environment.NETHER
                && destinationEnvironment == World.Environment.NORMAL) {
            coordinateMultiplier = netherCoordinateScale;
        } else {
            // Leave End portals and all other dimension transitions untouched.
            return null;
        }

        double x = from.getX() * coordinateMultiplier;
        double z = from.getZ() * coordinateMultiplier;
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            getLogger().warning("Portal destination coordinates were out of range; leaving the default destination unchanged.");
            return null;
        }

        Location mappedDestination = to.clone();
        mappedDestination.setX(x);
        mappedDestination.setZ(z);
        return mappedDestination;
    }
}
