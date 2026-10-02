package me.darkz70.quirks.listener;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.MaterialLists;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Причуда «Кот»: страх криперов, боязнь воды, мягкое падение, рыба, чутьё воды (3 ур.). */
public final class CatListener implements Listener {

    private final VoidQuirksPlugin plugin;
    /** последнее известное состояние «в воде» */
    private final Map<UUID, Boolean> inWater = new HashMap<>();

    public CatListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        // вода, натекающая на стоящего кота + пасс криперов — раз в секунду
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (level(player) < 1) continue;
                waterCheck(player);
                scareCreepers(player);
            }
        }, 20L, 20L);
    }

    /** 0 = причуды нет. */
    private int level(Player player) {
        return plugin.quirks().levelOf(player, Quirk.CAT);
    }

    /** Криперы боятся кота с 1 уровня: разлетаются прочь рядом с ним. */
    private void scareCreepers(Player player) {
        double radius = plugin.getConfig().getDouble("cat.creeper-fear-radius", 7.0);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Creeper creeper)) continue;
            Vector away = creeper.getLocation().toVector()
                    .subtract(player.getLocation().toVector());
            away.setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
            }
            away.normalize().multiply(0.45);
            away.setY(0.25);
            creeper.setVelocity(away);
        }
    }

    /** Криперы даже не берут кота в цель. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Creeper)) return;
        if (!(event.getTarget() instanceof Player target)) return;
        if (level(target) < 1) return;
        event.setCancelled(true);
    }

    /** Дебафф воды: срабатывает только при ВХОДЕ в воду (переход false -> true). */
    public void waterCheck(Player player) {
        boolean now = player.isInWater();
        Boolean before = inWater.put(player.getUniqueId(), now);
        if (before == null || before || !now) return;

        int lvl = level(player);
        if (lvl < 1 || lvl >= 3) return;

        double damage = plugin.getConfig().getDouble(
                "cat.water-damage-" + Math.min(2, lvl), lvl == 1 ? 3.0 : 1.0);
        player.damage(damage);
        int levitation = plugin.getConfig().getInt("cat.levitation-seconds", 2) * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, levitation, 0, true, false, true));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // только поворот головы
        }
        Player player = event.getPlayer();
        if (level(player) < 1) return;
        waterCheck(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (level(player) < 1) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) waterCheck(player);
        }, 2L);
    }

    /** Мягкое приземление: урона от падения нет. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && level(player) >= 1) {
            event.setCancelled(true);
        }
    }

    /** Рыба даёт насыщение. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEatFish(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        int lvl = level(player);
        if (lvl < 1) return;
        Material eaten = event.getItem().getType();
        if (!MaterialLists.isFish(eaten)) return;

        int seconds = plugin.getConfig().getInt(
                "cat.fish-saturation-seconds-" + Math.min(2, lvl), lvl == 1 ? 5 : 10);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, seconds * 20, 0, true, false, true));
    }

    /** Чутьё воды (3 ур.): шифт -> сабтайтл с количеством воды рядом. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (level(player) < 3) return;

        long cooldown = plugin.getConfig().getLong("scan.cooldown-ms", 2000);
        if (!ScanUtil.tryUse(player.getUniqueId(), "scan", cooldown)) return;

        int radius = plugin.getConfig().getInt("scan.cat.water-radius", 5);
        int water = ScanUtil.countBlocks(player.getLocation(), radius,
                material -> material == Material.WATER);
        if (water <= 0) return;

        Component sub = Msg.comp("title-cat-water", "%count%", String.valueOf(water));
        Title.Times times = Title.Times.times(
                Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500));
        player.showTitle(Title.title(Component.empty(), sub, times));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inWater.remove(event.getPlayer().getUniqueId());
    }
}
