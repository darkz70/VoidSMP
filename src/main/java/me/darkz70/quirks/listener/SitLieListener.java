package me.darkz70.quirks.listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * /sit и /lie: игрок садится (на невидимую стойку) или ложится (поза SWIMMING).
 * Shift (красться) = встать на ноги.
 */
public final class SitLieListener implements Listener {

    private final VoidQuirksPlugin plugin;
    private final Set<UUID> sitting = new HashSet<>();
    private final Set<UUID> lying = new HashSet<>();

    public SitLieListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    /** /sit — присесть. */
    public boolean sit(Player player) {
        if (sitting.contains(player.getUniqueId())) {
            stand(player);
            return true;
        }
        Location at = player.getLocation();
        ArmorStand chair = at.getWorld().spawn(at.clone().add(0, -1.4, 0), ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setInvulnerable(true);
        });
        chair.addPassenger(player);
        sitting.add(player.getUniqueId());
        Msg.send(player, "sit-on");
        // стойка умирает, когда игрок встаёт/отходит
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!chair.isValid() || !sitting.contains(player.getUniqueId())
                        || !chair.getPassengers().contains(player)) {
                    sitting.remove(player.getUniqueId());
                    chair.remove();
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
        return true;
    }

    /** /lie — лечь (полежать = поза плавания). */
    public boolean lie(Player player) {
        if (lying.contains(player.getUniqueId())) {
            stand(player);
            return true;
        }
        lying.add(player.getUniqueId());
        player.setPose(Pose.SWIMMING, true);
        Msg.send(player, "lie-on");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!lying.contains(player.getUniqueId())) {
                    cancel();
                    return;
                }
                player.setPose(Pose.SWIMMING, true);
            }
        }.runTaskTimer(plugin, 10L, 10L);
        return true;
    }

    /** Встать (снимает и сидение, и покой). */
    public void stand(Player player) {
        if (sitting.remove(player.getUniqueId())) {
            player.eject();
            Msg.send(player, "stand");
        }
        if (lying.remove(player.getUniqueId())) {
            player.setPose(Pose.STANDING, true);
            Msg.send(player, "stand");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return; // красться = встать, отпустил шифт — реакции нет
        stand(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        stand(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stand(event.getPlayer());
    }

    public boolean isBusy(UUID id) {
        return sitting.contains(id) || lying.contains(id);
    }
}
