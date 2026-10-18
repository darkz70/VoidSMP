package com.voidsmp.voidoferta;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * При входе показывает книгу игрокам, которые ещё не приняли оферту.
 * Пока оферта не принята (и freeze-until-accepted: true) игрок не может
 * двигаться, взаимодействовать с миром и писать команды, кроме /oferta.
 * Подтверждение требуется ровно один раз.
 */
public final class OfertaListener implements Listener {

    private final VoidOfertaPlugin plugin;

    public OfertaListener(VoidOfertaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.storage().hasAccepted(player.getUniqueId())) {
            return;
        }
        long delay = Math.max(1L, plugin.getConfig().getLong("open-delay-ticks", 20L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || plugin.storage().hasAccepted(player.getUniqueId())) {
                return;
            }
            plugin.openOferta(player);
            plugin.sendNotAcceptedMessage(player);
        }, delay);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Ничего чистить не нужно: состояние хранится только на диске (accepted-players.yml)
    }

    private boolean blocked(Player player) {
        return plugin.getConfig().getBoolean("freeze-until-accepted", true)
                && !plugin.storage().hasAccepted(player.getUniqueId())
                && !player.hasPermission("voidoferta.admin");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!blocked(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ())) {
            // Поворачивать голову можно, ходить — нет, пока не принята оферта
            Location frozen = from.clone();
            frozen.setYaw(to.getYaw());
            frozen.setPitch(to.getPitch());
            event.setTo(frozen);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && blocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!blocked(event.getPlayer())) {
            return;
        }
        String cmd = event.getMessage().toLowerCase();
        // Разрешаем только саму оферту (и её алиас), чтобы было чем подтвердить
        if (cmd.equals("/oferta") || cmd.startsWith("/oferta ")
                || cmd.equals("/offer") || cmd.startsWith("/offer ")) {
            return;
        }
        event.setCancelled(true);
        plugin.sendNotAcceptedMessage(event.getPlayer());
    }
}
