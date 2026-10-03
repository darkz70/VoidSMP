package me.darkz70.quirks.listener;

import java.util.Map;
import java.util.Random;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mechanic.SoulShards;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Осколки душ (приём причуды предметом) и освобождение душ из клеток лабиринта. */
public final class SoulShardListener implements Listener {

    private final VoidQuirksPlugin plugin;
    private final Random random = new Random();

    public SoulShardListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    /** ПКМ осколком души: причуда добавляется в набор игрока (или повышает уровень). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!SoulShards.isShard(item)) return;

        Player player = event.getPlayer();
        Quirk quirk = SoulShards.quirkOf(item);
        int level = SoulShards.levelOf(item);
        if (quirk == null) return;

        event.setUseItemInHand(Event.Result.DENY);

        int current = plugin.quirks().levelOf(player, quirk);
        if (current >= level) {
            // у игрока эта причуда уже есть и не слабее — осколок не тратим
            Msg.send(player, "shard-weaker");
            return;
        }

        item.setAmount(item.getAmount() - 1);
        player.swingMainHand();
        plugin.quirks().assign(player, quirk, level);
        Msg.send(player, "shard-used",
                "%quirk%", quirk.display(),
                "%level%", String.valueOf(level));
        Location above = player.getLocation().add(0, 1.0, 0);
        player.getWorld().spawnParticle(Particle.SOUL, above, 25, 0.4, 0.6, 0.4, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.8f, 0.9f);
    }

    /** Клик по душе-аллаю в лабиринте: освобождаем её — в награду случайный осколок. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSoulInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Allay allay)) return;
        if (!allay.getPersistentDataContainer().has(Keys.soulMark)) return;
        String labWorld = plugin.getConfig().getString("labyrinth.world", "void_labyrinth");
        if (!allay.getWorld().getName().equalsIgnoreCase(labWorld)) return;

        event.setCancelled(true); // не даём отдать аллаю предмет

        Player player = event.getPlayer();
        Location loc = allay.getLocation().add(0, 0.5, 0);
        allay.getWorld().spawnParticle(Particle.SOUL, loc, 40, 0.3, 0.5, 0.3, 0.02);
        allay.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.6f);
        allay.remove();

        Quirk quirk = Quirk.values()[random.nextInt(Quirk.values().length)];
        ItemStack shard = SoulShards.makeShard(quirk, 1);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(shard);
        leftovers.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));

        Bukkit.broadcast(Msg.comp("soul-freed", "%player%", player.getName()));
    }
}
