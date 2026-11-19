package me.darkz70.quirks.listener;

import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mechanic.RelicBlocks;
import me.darkz70.quirks.mechanic.RelicItems;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.Perms;
import me.darkz70.quirks.util.ScanUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Реликвии (#98): размещение/разрушение/смерть/заморозка.
 * Периодические эффекты (купола, пассивные баффы, яйцо дракона) — см. RelicTask.
 */
public final class RelicListener implements Listener {

    private static final long COVER_DEATH_COOLDOWN_MS = 10 * 60_000L;
    private static final long COVER_DEATH_DELAY_TICKS = 200L; // 10 сек

    private final VoidQuirksPlugin plugin;

    public RelicListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Обложку и Блок пустоты нельзя поставить вообще; Ядро и Призму — можно (и тогда они метятся). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Player player = event.getPlayer();

        if (RelicItems.isWorldbuilderCover(item) || RelicItems.isVoidBlock(item)) {
            event.setCancelled(true);
            player.sendMessage(Msg.color("&cЭту реликвию нельзя поставить — только носить с собой."));
            return;
        }
        if (RelicItems.isLavaCore(item)) {
            plugin.relicBlocks().register(event.getBlock().getLocation(), RelicBlocks.Type.LAVA_CORE);
            player.sendMessage(Msg.color("&6✦ Лавовое ядро поставлено — вокруг разгорается купол."));
            return;
        }
        if (RelicItems.isPrism(item)) {
            plugin.relicBlocks().register(event.getBlock().getLocation(), RelicBlocks.Type.PRISM);
            player.sendMessage(Msg.color("&b✦ Призма поставлена — вокруг рассеивается её свет."));
        }
    }

    /**
     * Реликвии-блоки нельзя сломать обычным игрокам. Админ (OP/quirks.admin) может
     * убрать её, присев (sneak) — иначе реликвии было бы некуда деться при ошибке
     * постройки. Обычное разрушение (без sneak) у админа тоже блокируется — чтобы
     * случайный клик не снёс реликвию.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        RelicBlocks.Type type = plugin.relicBlocks().typeAt(loc);
        if (type == null) return;

        Player player = event.getPlayer();
        if (Perms.isAdmin(player) && player.isSneaking()) {
            plugin.relicBlocks().unregister(loc);
            event.setDropItems(false);
            ItemStack relic = type == RelicBlocks.Type.LAVA_CORE
                    ? RelicItems.makeLavaCore() : RelicItems.makePrism();
            var leftover = player.getInventory().addItem(relic);
            leftover.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
            player.sendMessage(Msg.color("&7Реликвия снята и возвращена в инвентарь."));
            return;
        }
        event.setCancelled(true);
        player.sendMessage(Msg.color("&cЭту реликвию нельзя сломать."));
    }

    /** Блок пустоты в инвентаре = полная защита от урона заморозки (порошковый снег и т.п.). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFreezeDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FREEZE) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (RelicItems.hasVoidBlock(player.getInventory())) {
            event.setCancelled(true);
        }
    }

    /** Обложка конструктора миров: телепорт на место смерти через 10 сек (КД 10 мин). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!RelicItems.hasCover(victim.getInventory())) return;
        if (!ScanUtil.tryUse(victim.getUniqueId(), "relic-cover-death-tp", COVER_DEATH_COOLDOWN_MS)) return;

        Location deathLoc = victim.getLocation().clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(victim.getUniqueId());
            if (online == null || !online.isOnline()) return;
            online.teleport(deathLoc);
            online.sendMessage(Msg.color(
                    "&5✦ Обложка конструктора миров возвращает тебя к месту гибели."));
        }, COVER_DEATH_DELAY_TICKS);
    }
}
