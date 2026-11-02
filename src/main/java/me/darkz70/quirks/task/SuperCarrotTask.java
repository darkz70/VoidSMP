package me.darkz70.quirks.task;

import me.darkz70.quirks.Keys;
import me.darkz70.quirks.mob.ZayceKrol;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Раз в секунду: у кого в руке супер-морковь — все обычные (не зайцекрольские)
 * зайцы в радиусе 100 блоков получают путь к игроку.
 */
public final class SuperCarrotTask extends BukkitRunnable {

    private static final double RADIUS = 100.0;

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!holdsCarrot(player)) continue;
            Location at = player.getLocation();
            for (Entity e : player.getNearbyEntities(RADIUS, RADIUS, RADIUS)) {
                if (!(e instanceof Rabbit rabbit)) continue;
                if (ZayceKrol.isZayce(rabbit)) continue; // у них своя жизнь в королевстве
                if (rabbit.getLocation().distanceSquared(at) < 4) continue;
                ((Mob) rabbit).getPathfinder().moveTo(at, 1.0);
            }
        }
    }

    private static boolean holdsCarrot(Player player) {
        return isCarrot(player.getInventory().getItemInMainHand())
                || isCarrot(player.getInventory().getItemInOffHand());
    }

    private static boolean isCarrot(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(Keys.superCarrot, PersistentDataType.BYTE);
    }
}
