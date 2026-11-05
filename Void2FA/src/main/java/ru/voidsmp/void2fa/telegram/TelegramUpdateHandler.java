package ru.voidsmp.void2fa.telegram;

import org.bukkit.Bukkit;
import ru.voidsmp.void2fa.Void2FAPlugin;
import java.util.Map;

/** Parses private Telegram updates; all account decisions are routed to the main thread. */
public final class TelegramUpdateHandler implements TelegramBot.UpdateListener {
    private final Void2FAPlugin plugin;
    private final TelegramCommandRouter router;

    public TelegramUpdateHandler(Void2FAPlugin plugin) {
        this.plugin = plugin;
        this.router = new TelegramCommandRouter(plugin);
    }

    @Override
    public void onUpdate(Map<String, Object> update) {
        Map<String, Object> callback = Json.asObject(update.get("callback_query"));
        Map<String, Object> item = callback != null ? callback : Json.asObject(update.get("message"));
        if (item == null) return;
        Map<String, Object> from = Json.asObject(item.get("from"));
        Map<String, Object> chat = Json.asObject(callback == null ? item.get("chat")
                : Json.asObject(item.get("message")) == null ? null : Json.asObject(item.get("message")).get("chat"));
        if (from == null || chat == null || !"private".equals(Json.asString(chat.get("type")))
                || Json.asBoolean(from.get("is_bot"))) return;
        long userId = Json.asLong(from.get("id"));
        long chatId = Json.asLong(chat.get("id"));
        if (userId <= 0 || userId != chatId) return;
        if (callback != null) {
            String id = Json.asString(callback.get("id"));
            String data = Json.asString(callback.get("data"));
            sync(() -> router.callback(id, userId, chatId, data));
        } else {
            String text = Json.asString(item.get("text"));
            if (text != null) sync(() -> router.text(userId, chatId, text));
        }
    }

    private void sync(Runnable runnable) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, runnable);
    }
}
