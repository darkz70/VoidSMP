package me.darkz70.quirks.listener;

import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Если игрока убивает ОП (напрямую, вручную), причина смерти в чате подменяется
 * на «магию» — чтобы не светить реальное оружие/способность администратора.
 */
public final class MagicDeathListener implements Listener {

    public MagicDeathListener(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        if (!killer.isOp() && !killer.hasPermission("quirks.admin")) return;
        event.deathMessage(Msg.color("&5✦ Магия настигла &f" + victim.getName() + "&5."));
    }
}
