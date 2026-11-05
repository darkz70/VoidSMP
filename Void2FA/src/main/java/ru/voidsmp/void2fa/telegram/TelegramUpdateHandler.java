package ru.voidsmp.void2fa.telegram;

import org.bukkit.Bukkit;
import ru.voidsmp.void2fa.AuthManager;
import ru.voidsmp.void2fa.ConfigManager;
import ru.voidsmp.void2fa.Void2FAPlugin;

import java.util.Map;

/**
 * Routes incoming Telegram updates. Runs on the polling thread; anything that
 * touches Bukkit state is dispatched to the main thread via the scheduler.
 */
public final class TelegramUpdateHandler implements TelegramBot.UpdateListener {

    private final Void2FAPlugin plugin;

    public TelegramUpdateHandler(Void2FAPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onUpdate(Map<String, Object> update) {
        Map<String, Object> callback = Json.asObject(update.get("callback_query"));
        if (callback != null) {
            handleCallback(callback);
            return;
        }
        Map<String, Object> message = Json.asObject(update.get("message"));
        if (message != null) {
            handleMessage(message);
        }
    }

    // ------------------------------------------------------------------
    //  Inline buttons
    // ------------------------------------------------------------------

    private void handleCallback(Map<String, Object> callback) {
        TelegramBot bot = plugin.getBot();
        if (bot == null) {
            return;
        }
        String callbackId = Json.asString(callback.get("id"));
        Map<String, Object> from = Json.asObject(callback.get("from"));
        long userId = from == null ? 0L : Json.asLong(from.get("id"));
        String data = Json.asString(callback.get("data"));
        if (data == null) {
            return;
        }
        String[] parts = data.split(":");
        if (parts.length != 3 || !parts[0].equals("v2fa")) {
            return;
        }
        String action = parts[1];
        String authId = parts[2];

        sync(() -> {
            AuthManager auth = plugin.getAuthManager();
            if (auth == null) {
                return;
            }
            boolean ok;
            if (action.equals("ok")) {
                ok = auth.approveByAuthId(authId, userId);
            } else if (action.equals("no")) {
                ok = auth.denyByAuthId(authId, userId);
            } else {
                return;
            }
            bot.answerCallbackQuery(callbackId, ok ? "" : "Запрос недействителен или устарел.");
        });
    }

    // ------------------------------------------------------------------
    //  Text messages / bot commands
    // ------------------------------------------------------------------

    private void handleMessage(Map<String, Object> message) {
        TelegramBot bot = plugin.getBot();
        ConfigManager config = plugin.getConfigManager();
        if (bot == null || config == null) {
            return;
        }
        Map<String, Object> chat = Json.asObject(message.get("chat"));
        if (chat == null) {
            return;
        }
        // Only private chats are trusted for account linking / approval.
        if (!"private".equals(Json.asString(chat.get("type")))) {
            return;
        }
        long chatId = Json.asLong(chat.get("id"));
        String text = Json.asString(message.get("text"));
        if (text == null) {
            return;
        }
        text = text.trim();
        if (!text.startsWith("/")) {
            bot.sendMessage(chatId, config.tg("tg-unknown"), null, null);
            return;
        }

        String[] split = text.split("\\s+", 2);
        String cmd = split[0].toLowerCase();
        int at = cmd.indexOf('@');
        if (at >= 0) {
            cmd = cmd.substring(0, at);
        }
        String arg = split.length > 1 ? split[1].trim() : "";

        switch (cmd) {
            case "/start":
                if (arg.startsWith("LINK_")) {
                    String token = arg.substring(5).trim().toUpperCase();
                    sync(() -> plugin.getAuthManager().completeLink(token, chatId));
                } else {
                    bot.sendMessage(chatId, config.tg("tg-start"), null, null);
                }
                break;
            case "/link":
                if (arg.isEmpty()) {
                    bot.sendMessage(chatId, config.tg("tg-link-invalid"), null, null);
                } else {
                    String token = arg.toUpperCase();
                    sync(() -> plugin.getAuthManager().completeLink(token, chatId));
                }
                break;
            case "/status":
                sync(() -> plugin.getAuthManager().sendTelegramStatus(chatId));
                break;
            case "/unlink":
                sync(() -> plugin.getAuthManager().unlinkByTelegram(chatId));
                break;
            case "/help":
                bot.sendMessage(chatId, config.tg("tg-help"), null, null);
                break;
            default:
                bot.sendMessage(chatId, config.tg("tg-unknown"), null, null);
                break;
        }
    }

    private void sync(Runnable runnable) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }
}
