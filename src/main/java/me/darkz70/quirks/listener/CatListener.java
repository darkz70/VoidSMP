package me.darkz70.quirks.listener;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.MaterialLists;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

/** Причуда «Кот»: боязнь воды, мягкое падение, рыба, чутьё воды (3 ур.). */
public final class CatListener implements Listener {

    private final VoidQuirksPlugin plugin;
    /** последнее известное состояние «в воде» */
    private final Map<UUID, Boolean> inWater = new HashMap<>();

    public CatListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        // ловим случай "вода натекла на стоящего кота" — проверка раз в 2 секунды
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (catData(player) != null) waterCheck(player);
            }
        }, 40L, 40L);
    }

    @Nullable
    private PlayerData catData(Player player) {
        PlayerData data = plugin.quirks().data(player);
        return data != null && data.quirk() == Quirk.CAT ? data : null;
    }

    /** Дебафф воды: срабатывает только при ВХОДЕ в воду (переход false -> true). */
    public void waterCheck(Player player) {
        boolean now = player.isInWater();
        Boolean before = inWater.put(player.getUniqueId(), now);
        if (before == null || before || !now) return;

        PlayerData data = catData(player);
        if (data == null || data.level() >= 3) return;

        double damage = plugin.getConfig().getDouble(
                "cat.water-damage-" + Math.min(2, data.level()), data.level() == 1 ? 3.0 : 1.0);
        player.damage(damage);
        int levitation = plugin.getConfig().getInt("cat.levitation-seconds", 1) * 20;
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
        if (catData(player) == null) return;
        waterCheck(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (catData(player) == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) waterCheck(player);
        }, 2L);
    }

    /** Мягкое приземление: урона от падения нет (без эффекта плавного падения). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && catData(player) != null) {
            event.setCancelled(true);
        }
    }

    /** Рыба даёт насыщение. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEatFish(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        PlayerData data = catData(player);
        if (data == null) return;
        Material eaten = event.getItem().getType();
        if (!MaterialLists.isFish(eaten)) return;

        int seconds = plugin.getConfig().getInt(
                "cat.fish-saturation-seconds-" + Math.min(2, data.level()), data.level() == 1 ? 5 : 10);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, seconds * 20, 0, true, false, true));
    }

    /** Чутьё воды (3 ур.): шифт -> сабтайтл с количеством воды рядом. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        PlayerData data = catData(player);
        if (data == null || data.level() < 3) return;

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
