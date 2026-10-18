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
        if (plugin.authHook().isPresent()) {
            // На сервере стоит SimpleLogin: ждём ввода пароля и только потом показываем оферту.
            // До авторизации игрока не трогаем вообще — иначе наш запрет команд заблокирует сам /login.
            plugin.beginAuthWait(player);
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
        plugin.clearAuthWait(event.getPlayer());
    }

    private boolean blocked(Player player) {
        return plugin.getConfig().getBoolean("freeze-until-accepted", true)
                && !plugin.isPendingAuth(player) // ещё не ввёл пароль — его и так держит SimpleLogin
                && !plugin.storage().hasAccepted(player.getUniqueId())
                && !player.hasPermission("voidoferta.admin");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player mover = event.getPlayer();
        if (plugin.isPendingAuth(mover)) {
            // SimpleLogin не даёт ходить до ввода пароля. Раз игрок пошёл —
            // пароль принят (если SimpleLogin сам не говорит обратного).
            Location from = event.getFrom();
            Location to = event.getTo();
            long graceMs = Math.max(0L, plugin.getConfig().getLong("auth.move-grace-ticks", 40L)) * 50L;
            if (to != null
                    && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ())
                    && System.currentTimeMillis() - plugin.joinedAt(mover.getUniqueId()) >= graceMs
                    && plugin.authHook().probe(mover) != AuthHook.Probe.NOT_LOGGED_IN) {
                plugin.completeAuth(mover);
            }
            return;
        }
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
        Player player = event.getPlayer();
        if (plugin.isPendingAuth(player)) {
            // Ждём команду ввода пароля (/login, /register, ...). Само событие
            // срабатывает ДО выполнения команды, поэтому проверяем чуть позже:
            // если SimpleLogin не подтверждает вход — продолжаем ждать.
            String root = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase();
            java.util.List<String> loginCommands = plugin.getConfig().getStringList("auth.login-commands");
            if (loginCommands.isEmpty()) { // старый config.yml без секции auth
                loginCommands = java.util.List.of("login", "register", "reg");
            }
            if (loginCommands.stream().anyMatch(cmd -> cmd.equalsIgnoreCase(root))) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && plugin.isPendingAuth(player)
                            && plugin.authHook().probe(player) != AuthHook.Probe.NOT_LOGGED_IN) {
                        plugin.completeAuth(player);
                    }
                }, 15L);
            }
            return;
        }
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
