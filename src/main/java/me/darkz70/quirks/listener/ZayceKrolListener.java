package me.darkz70.quirks.listener;

import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mob.ZayceKrol;
import me.darkz70.quirks.mob.ZayceKrol.Role;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Превращает часть естественных кроликов в зайцекролов и ловит удар по царю.
 */
public final class ZayceKrolListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public ZayceKrolListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRabbitSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Rabbit rabbit)) return;
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL
                && reason != CreatureSpawnEvent.SpawnReason.CHUNK_GEN) {
            return; // только дикие кролики
        }
        if (ZayceKrol.isZayce(rabbit)) return;
        double chance = plugin.getConfig().getDouble("zaycekrol.spawn-chance", 8);
        if (Math.random() * 100 >= chance) return;

        double colonyRadius = plugin.getConfig().getDouble("zaycekrol.colony-radius", 60);
        boolean tsarNear = false;
        for (Entity entity : rabbit.getNearbyEntities(colonyRadius, 24, colonyRadius)) {
            if (entity instanceof Rabbit other && ZayceKrol.isZayce(other)
                    && ZayceKrol.roleOf(other) == Role.TSAR) {
                tsarNear = true;
                break;
            }
        }
        if (!tsarNear) {
            // новая колония: царь + компания
            ZayceKrol.convert(rabbit, Role.TSAR);
            spawnAround(rabbit, Role.RESIDENT, 2);
            spawnAround(rabbit, Role.GUARD, 1);
            return;
        }
        double roll = Math.random();
        Role role = roll < 0.40 ? Role.RESIDENT
                : roll < 0.65 ? Role.GUARD
                : roll < 0.80 ? Role.FARMER
                : Role.ELITE;
        ZayceKrol.convert(rabbit, role);
    }

    private void spawnAround(Rabbit center, Role role, int amount) {
        for (int i = 0; i < amount; i++) {
            Rabbit buddy = center.getWorld().spawn(
                    center.getLocation().add((Math.random() - 0.5) * 5, 0.5, (Math.random() - 0.5) * 5),
                    Rabbit.class);
            ZayceKrol.convert(buddy, role);
        }
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
