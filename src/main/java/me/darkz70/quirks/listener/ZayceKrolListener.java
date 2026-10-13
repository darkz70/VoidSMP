package me.darkz70.quirks.listener;

import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mob.ZayceKrol;
import me.darkz70.quirks.mob.ZayceKrol.Role;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Зайцекролы — САМОСТОЯТЕЛЬНАЯ ворода мобов: ванильные кролики не трогаются,
 * колонии спавнит двигатель (ZayceKrolTask). Здесь — только удар по царю.
 */
public final class ZayceKrolListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public ZayceKrolListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTsarHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Rabbit rabbit)) return;
        if (!ZayceKrol.isZayce(rabbit) || ZayceKrol.roleOf(rabbit) != Role.TSAR) return;
        if (event.getDamager() instanceof Player player) {
            plugin.zayceTask().onTsarHit(rabbit, player);
        }
    }
}
