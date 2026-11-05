package ru.voidsmp.void2fa.telegram;

import ru.voidsmp.void2fa.Void2FAPlugin;

/** Shared by embedded polling and the authenticated external bridge. Call on Bukkit's main thread. */
public final class TelegramCommandRouter {
    private final Void2FAPlugin plugin;
    public TelegramCommandRouter(Void2FAPlugin plugin) { this.plugin = plugin; }

    public void text(long userId, long chatId, String text) {
        TelegramTransport bot = plugin.getBot();
        if (bot == null || userId <= 0 || userId != chatId || text == null || text.length() > 4096) return;
        text = text.trim();
        if (!text.startsWith("/")) { bot.sendMessage(chatId, plugin.getConfigManager().tg("tg-unknown")); return; }
        String[] split = text.split("\\s+", 2);
        String cmd = split[0].toLowerCase(java.util.Locale.ROOT);
        int at = cmd.indexOf('@');
        if (at >= 0) {
            String address = cmd.substring(at + 1);
            if (!address.equalsIgnoreCase(bot.getBotUsername())) return;
            cmd = cmd.substring(0, at);
        }
        String arg = split.length > 1 ? split[1].trim() : "";
        switch (cmd) {
            case "/start" -> {
                if (arg.startsWith("LINK_")) link(userId, arg.substring(5));
                else bot.sendMessage(chatId, plugin.getConfigManager().tg("tg-start"));
            }
            case "/link" -> link(userId, arg);
            case "/status" -> plugin.getAuthManager().sendTelegramStatus(userId);
            case "/unlink" -> plugin.getAuthManager().unlinkByTelegram(userId);
            case "/lock" -> plugin.getAuthManager().lockByTelegram(userId);
            case "/unlock" -> plugin.getAuthManager().unlockByTelegram(userId);
            case "/devices" -> plugin.getAuthManager().listDevicesByTelegram(userId);
            case "/untrust" -> {
                if (arg.isEmpty()) {
                    bot.sendMessage(chatId, plugin.getConfigManager().tg("tg-usage-untrust"));
                } else {
                    plugin.getAuthManager().untrustDeviceByTelegram(userId, arg);
                }
            }
            case "/help" -> bot.sendMessage(chatId, plugin.getConfigManager().tg("tg-help"));
            default -> bot.sendMessage(chatId, plugin.getConfigManager().tg("tg-unknown"));
        }
    }

    private void link(long userId, String token) {
        token = token.toUpperCase(java.util.Locale.ROOT);
        if (!token.matches("[A-Z2-9]{6,32}")) {
            plugin.getBot().sendMessage(userId, plugin.getConfigManager().tg("tg-link-invalid"));
            return;
        }
        plugin.getAuthManager().completeLink(token, userId);
    }

    public void callback(String callbackId, long userId, long chatId, String data) {
        TelegramTransport bot = plugin.getBot();
        if (bot == null || userId <= 0 || userId != chatId || callbackId == null || callbackId.length() > 256 || data == null) return;
        String[] parts = data.split(":", -1);
        if (parts.length != 3 || !parts[0].equals("v2fa") || !parts[2].matches("[A-Z2-9]{16}")) return;
        boolean ok = switch (parts[1]) {
            case "ok" -> plugin.getAuthManager().approveByAuthId(parts[2], userId);
            case "no" -> plugin.getAuthManager().denyByAuthId(parts[2], userId);
            default -> false;
        };
        bot.answerCallbackQuery(callbackId, ok ? "" : "Запрос недействителен или устарел.");
    }
}
