package me.darkz70.quirks.listener;

import java.util.List;
import java.util.Map;
import java.util.Random;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mechanic.SoulShards;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Осколки душ (приём причуды предметом) и освобождение душ из клеток лабиринта. */
public final class SoulShardListener implements Listener {

    private final VoidQuirksPlugin plugin;
    private final Random random = new Random();

    public SoulShardListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    /** Это помеченная клеточная душа из лабиринта? */
    private boolean isCageSoul(Entity entity) {
        if (!(entity instanceof Allay allay)) return false;
        if (!allay.getPersistentDataContainer().has(Keys.soulMark)) return false;
        String labWorld = plugin.getConfig().getString("labyrinth.world", "void_labyrinth");
        return allay.getWorld().getName().equalsIgnoreCase(labWorld);
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

    /** Клик по самой душе (любой рукой; тело освобождения — с антиспамом). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSoulInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Entity entity)) return;
        if (!isCageSoul(entity)) return;
        event.setCancelled(true); // не даём отдать аллаю предмет
        if (event.getHand() != EquipmentSlot.HAND) return;
        freeSoul(event.getPlayer(), entity);
    }

    /** Души неуязвимы: удары по ним не проходят. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSoulDamaged(EntityDamageEvent event) {
        if (isCageSoul(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Удар по душе = тоже освобождение (на случай, если ЛКМ удобнее в клетке). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSoulPunched(EntityDamageByEntityEvent event) {
        if (!isCageSoul(event.getEntity())) return;
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            freeSoul(player, event.getEntity());
        }
    }

    /** Клик по прутьям клетки рядом с душой — тоже освобождает (главный «человеческий» способ). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCageBars(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.IRON_BARS) return;

        String labWorld = plugin.getConfig().getString("labyrinth.world", "void_labyrinth");
        if (!clicked.getWorld().getName().equalsIgnoreCase(labWorld)) return;

        // ищем помеченную душу рядом с прутьем
        List<Entity> nearby = clicked.getWorld().getNearbyEntities(
                clicked.getLocation().add(0.5, 0.5, 0.5), 4, 4, 4, this::isCageSoul).stream().toList();
        if (nearby.isEmpty()) return;

        event.setCancelled(true);
        freeSoul(event.getPlayer(), nearby.get(0));
    }

    /** Освобождение души: партиклы, звук, случайный осколок 1 уровня. С антиспамом. */
    private void freeSoul(Player player, Entity soul) {
        if (!ScanUtil.tryUse(player.getUniqueId(), "soul-free", 1500)) return;
        if (soul.isDead() || !soul.isValid()) return;

        Location loc = soul.getLocation().add(0, 0.5, 0);
        soul.getWorld().spawnParticle(Particle.SOUL, loc, 40, 0.3, 0.5, 0.3, 0.02);
        soul.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.6f);
        soul.remove();

        Quirk quirk = Quirk.values()[random.nextInt(Quirk.values().length)];
        ItemStack shard = SoulShards.makeShard(quirk, 1);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(shard);
        leftovers.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));

        Bukkit.broadcast(Msg.comp("soul-freed", "%player%", player.getName()));
    }
}
