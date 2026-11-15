package me.darkz70.quirks.listener;

import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;

/**
 * Глобальный переключатель «Можно спать» (/gaid → Эффекты, рядом с «9 глаз»/
 * «красное небо»/демоном). Пока выключено — НИКТО на сервере не может лечь
 * спать: попытка отклоняется с сообщением «Что-то могущественное не даёт вам
 * уснуть».
 */
public final class SleepLockListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public SleepLockListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean sleepDisabled() {
        return plugin.getConfig().getBoolean("sleep.disabled", false);
    }

    public void setSleepDisabled(boolean disabled) {
        plugin.getConfig().set("sleep.disabled", disabled);
        plugin.saveConfig();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (!sleepDisabled()) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Msg.send(player, "sleep-locked");
    }
}
