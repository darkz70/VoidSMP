package me.darkz70.quirks.listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Приватность сервер-листа: ваниль показывает список ников ВСЕХ игроков,
 * которые сейчас на сервере, во всплывающей подсказке при наведении на
 * счётчик онлайна в Multiplayer — а это пинг-запрос, для которого не нужно
 * даже заходить на сервер. Так любой человек в интернете может молча
 * проверить, онлайн ли конкретный ник (например, админ), просто добавив
 * сервер в избранное. Этот слушатель убирает именно список ников из этой
 * подсказки; сам счётчик игроков (например «1/66») не трогаем — выключатель
 * настраивается в config.yml: server-list-ping.hide-player-names.
 */
public final class ServerListPingListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public ServerListPingListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    // HIGHEST — чтобы наше решение было последним словом, даже если другой
    // плагин попытается добавить игроков в список позже обычного приоритета.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(PaperServerListPingEvent event) {
        if (!plugin.getConfig().getBoolean("server-list-ping.hide-player-names", true)) return;
        event.getListedPlayers().clear();
    }
}
