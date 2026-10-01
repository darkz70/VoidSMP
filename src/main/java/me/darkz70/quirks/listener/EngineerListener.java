package me.darkz70.quirks.listener;

import java.time.Duration;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.MaterialLists;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

/** Причуда «Инженер»: слабость (1 ур.), скан редстоуна, ТНТ, эльфийский желудок (3 ур.). */
public final class EngineerListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public EngineerListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @Nullable
    private PlayerData engineerData(Player player) {
        PlayerData data = plugin.quirks().data(player);
        return data != null && data.quirk() == Quirk.ENGINEER ? data : null;
    }

    /** Скан редстоуна/ТНТ по шифту. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        PlayerData data = engineerData(player);
        if (data == null) return;

        long cooldown = plugin.getConfig().getLong("scan.cooldown-ms", 2000);
        if (!ScanUtil.tryUse(player.getUniqueId(), "scan", cooldown)) return;

        int level = data.level();
        int radius = plugin.getConfig().getInt("scan.engineer.radius-" + level, 1);
        boolean withSigns = level >= 2 && MaterialLists.engineerSigns;

        int redstone = ScanUtil.countBlocks(player.getLocation(), radius, material -> {
            if (MaterialLists.engineerBase.contains(material)) return true;
            if (level >= 2 && MaterialLists.engineerExtra.contains(material)) return true;
            return withSigns && Tag.SIGNS.isTagged(material);
        });

        int tnt = 0;
        if (level >= 3) {
            int tntRadius = plugin.getConfig().getInt("scan.engineer.tnt-radius", 5);
            tnt = ScanUtil.countBlocks(player.getLocation(), tntRadius,
                    material -> MaterialLists.engineerTnt.contains(material));
            for (Entity entity : player.getNearbyEntities(tntRadius, tntRadius, tntRadius)) {
                if (entity instanceof ExplosiveMinecart) tnt++;
            }
        }

        if (redstone <= 0 && tnt <= 0) return;

        Component main = tnt > 0
                ? Msg.comp("title-engineer-tnt", "%count%", String.valueOf(tnt))
                : Component.empty();
        Component sub = redstone > 0
                ? Msg.comp("title-engineer-redstone", "%count%", String.valueOf(redstone))
                : Component.empty();
        Title.Times times = Title.Times.times(
                Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500));
        player.showTitle(Title.title(main, sub, times));
    }

    /** Эльфийский желудок (3 ур.): мясо отменяется + эффект голода. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        PlayerData data = engineerData(player);
        if (data == null || data.level() < 3) return;
        Material eaten = event.getItem().getType();
        if (!MaterialLists.isMeat(eaten)) return;

        event.setCancelled(true);
        Msg.send(player, "engineer-meat-denied");
        int seconds = plugin.getConfig().getInt("engineer.hunger-effect-seconds", 10);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, seconds * 20, 0, true, false, true));
    }
}
