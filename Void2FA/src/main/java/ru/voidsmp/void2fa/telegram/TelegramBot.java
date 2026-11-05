package ru.voidsmp.void2fa.telegram;

import org.bukkit.Bukkit;
import ru.voidsmp.void2fa.Void2FAPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

/**
 * Talks to the Telegram Bot API. A single daemon thread long-polls
 * {@code getUpdates}; every API call is plain HTTPS + JSON, so the plugin has
 * no external dependencies. The bot account itself is created later by the
 * server owner via @BotFather — only its token is required here.
 */
public final class TelegramBot implements TelegramTransport {

    /** Receives raw updates on the polling thread. */
    public interface UpdateListener {
        void onUpdate(Map<String, Object> update);
    }

    private final Void2FAPlugin plugin;
    private final String token;
    private final HttpClient http;

    private volatile boolean running;
    private volatile boolean ready;
    private volatile String botUsername;
    private long offset;
    private Thread thread;
    private UpdateListener listener;
    private final Map<String, MessageRef> authMessages = new ConcurrentHashMap<>();
    private record MessageRef(long chatId, long messageId) {}

    public TelegramBot(Void2FAPlugin plugin, String token) {
        this.plugin = plugin;
        this.token = token;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public void setUpdateListener(UpdateListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::pollLoop, "Void2FA-Telegram");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        ready = false;
        authMessages.clear();
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    // ------------------------------------------------------------------
    //  Polling loop (runs on the dedicated thread)
    // ------------------------------------------------------------------

    private void pollLoop() {
        Map<String, Object> me = request("getMe", null, 20);
        if (me == null || !Json.asBoolean(me.get("ok"))) {
            plugin.getLogger().severe("Не удалось подключиться к Telegram. Проверь bot-token в config.yml. "
                    + describeError(me));
            running = false;
            return;
        }
        Map<String, Object> result = Json.asObject(me.get("result"));
        if (result != null) {
            botUsername = Json.asString(result.get("username"));
            String override = plugin.getConfigManager().getBotUsernameOverride();
            if (!override.isEmpty() && !override.equalsIgnoreCase(botUsername)) {
                plugin.getLogger().severe("telegram.bot-username не совпадает с токеном. Проверь локальный конфиг.");
                running = false;
                ready = false;
                return;
            }
        }
        ready = true;
        plugin.getLogger().info("Telegram-бот подключён" + (botUsername != null ? ": @" + botUsername : "") + ".");
        plugin.onBotReady();

        // Best-effort: show the commands in the Telegram menu so players can discover them.
        request("setMyCommands", Json.obj("commands", TelegramHelp.menuCommands()), 20);

        // Make sure long polling works even if a webhook was configured before.
        request("deleteWebhook", Json.obj("drop_pending_updates", false), 20);

        while (running) {
            Map<String, Object> body = Json.obj(
                    "offset", offset,
                    "timeout", 25,
                    "allowed_updates", List.of("message", "callback_query"));
            Map<String, Object> resp = request("getUpdates", body, 40);
            if (!running) {
                break;
            }
            if (resp == null) {
                sleep(3000);
                continue;
            }
            if (!Json.asBoolean(resp.get("ok"))) {
                long code = Json.asLong(resp.get("error_code"));
                if (code == 401) {
                    plugin.getLogger().severe("Telegram отклонил токен (401). Останавливаю бота.");
                    running = false;
                    ready = false;
                    break;
                }
                if (code == 409) {
                    plugin.getLogger().warning("Telegram: другой экземпляр уже опрашивает бота (409). Повтор через 15 с.");
                    sleep(15000);
                    continue;
                }
                plugin.getLogger().warning("Telegram getUpdates: " + describeError(resp));
                sleep(3000);
                continue;
            }
            List<Object> updates = Json.asArray(resp.get("result"));
            if (updates == null) {
                continue;
            }
            for (Object o : updates) {
                Map<String, Object> update = Json.asObject(o);
                if (update == null) {
                    continue;
                }
                long updateId = Json.asLong(update.get("update_id"));
                if (updateId >= offset) {
                    offset = updateId + 1;
                }
                UpdateListener l = listener;
                if (l != null) {
                    try {
                        l.onUpdate(update);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Ошибка обработки обновления Telegram: " + ex);
                    }
                }
            }
        }
        ready = false;
    }

    // ------------------------------------------------------------------
    //  Low-level request (synchronous; call from background threads)
    // ------------------------------------------------------------------

    public Map<String, Object> request(String method, Map<String, Object> body, int timeoutSeconds) {
        try {
            String url = "https://api.telegram.org/bot" + token + "/" + method;
            String json = body == null ? "{}" : Json.write(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                return Json.obj("ok", false, "error_code", (long) resp.statusCode());
            }
            Object parsed = Json.parse(resp.body());
            return Json.asObject(parsed);
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Telegram " + method + ": ошибка типа " + e.getClass().getSimpleName());
            return null;
        }
    }

    // ------------------------------------------------------------------
    //  High-level helpers (safe to call from the main thread; run async)
    // ------------------------------------------------------------------

    /** Sends a message; {@code onSent} (main thread) receives message_id or -1. */
    public void sendMessage(long chatId, String text, Object replyMarkup, LongConsumer onSent) {
        async(() -> {
            Map<String, Object> body = Json.obj(
                    "chat_id", chatId,
                    "text", text,
                    "disable_web_page_preview", true);
            if (replyMarkup != null) {
                body.put("reply_markup", replyMarkup);
            }
            Map<String, Object> resp = request("sendMessage", body, 20);
            long messageId = -1;
            if (resp != null && Json.asBoolean(resp.get("ok"))) {
                Map<String, Object> result = Json.asObject(resp.get("result"));
                if (result != null) {
                    messageId = Json.asLong(result.get("message_id"));
                }
            }
            if (onSent != null) {
                final long id = messageId;
                sync(() -> onSent.accept(id));
            }
        });
    }

    public void editMessageText(long chatId, long messageId, String text) {
        if (messageId < 0) {
            return;
        }
        async(() -> request("editMessageText", Json.obj(
                "chat_id", chatId,
                "message_id", messageId,
                "text", text), 20));
    }

    @Override
    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, null, null);
    }

    @Override
    public void sendAuthRequest(String authId, long chatId, String text, String approveLabel, String denyLabel) {
        Object keyboard = Json.obj("inline_keyboard", List.of(List.of(
                Json.obj("text", approveLabel, "callback_data", "v2fa:ok:" + authId),
                Json.obj("text", denyLabel, "callback_data", "v2fa:no:" + authId))));
        sendMessage(chatId, text, keyboard, messageId -> {
            if (running) authMessages.compute(authId, (key, existing) ->
                    existing != null ? existing : new MessageRef(chatId, messageId));
        });
    }

    @Override
    public void editAuthRequest(String authId, long chatId, String text) {
        MessageRef ref = authMessages.compute(authId, (key, existing) ->
                existing != null ? existing : new MessageRef(chatId, -1));
        if (ref.chatId == chatId && ref.messageId >= 0) editMessageText(ref.chatId, ref.messageId, text);
        // Keep a tombstone until transport restart: a late sendMessage callback must not
        // resurrect buttons after approval/denial/quit. Bound memory on busy servers.
        if (authMessages.size() > 4096) authMessages.entrySet().removeIf(e -> e.getValue().messageId < 0);
    }

    @Override
    public void answerCallbackQuery(String callbackId, String text) {
        async(() -> request("answerCallbackQuery", Json.obj(
                "callback_query_id", callbackId,
                "text", text == null ? "" : text), 20));
    }

    // ------------------------------------------------------------------
    //  Utilities
    // ------------------------------------------------------------------

    private void async(Runnable runnable) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    private void sync(Runnable runnable) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String describeError(Map<String, Object> resp) {
        if (resp == null) {
            return "(нет ответа / сеть недоступна)";
        }
        Object desc = resp.get("description");
        Object code = resp.get("error_code");
        return code == null ? "(неизвестная ошибка)" : "код " + code;
    }
}
