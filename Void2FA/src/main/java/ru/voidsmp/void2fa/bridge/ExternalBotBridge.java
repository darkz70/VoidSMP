package ru.voidsmp.void2fa.bridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ru.voidsmp.void2fa.Void2FAPlugin;
import ru.voidsmp.void2fa.telegram.Json;
import ru.voidsmp.void2fa.telegram.TelegramCommandRouter;
import ru.voidsmp.void2fa.telegram.TelegramTransport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Authenticated loopback bridge. The bot pulls outbox events and submits Telegram facts.
 * This class never authorizes a Minecraft player: the router/AuthManager do that on the main thread.
 */
public final class ExternalBotBridge implements TelegramTransport {
    private static final int MAX_BODY = 65536;
    private static final int MAX_EVENTS = 1000;
    private static final long EVENT_TTL_MS = 10 * 60 * 1000L;
    private final Void2FAPlugin plugin;
    private final BridgeSecurity security;
    private final TelegramCommandRouter router;
    private final String instanceId = UUID.randomUUID().toString();
    private final List<Event> outbox = new ArrayList<>();
    private final Map<String, Long> receipts = new ConcurrentHashMap<>();
    private long sequence;
    private long acknowledged;
    private HttpServer server;
    private ExecutorService executor;
    private volatile boolean running;
    private volatile long lastPoll;
    private volatile long botId;
    private volatile String botUsername = "";
    private volatile boolean telegramReady;

    private record Event(long seq, String id, long createdAt, Map<String, Object> payload) {}

    public ExternalBotBridge(Void2FAPlugin plugin, byte[] secret) {
        this.plugin = plugin;
        this.security = new BridgeSecurity(secret);
        this.router = new TelegramCommandRouter(plugin);
    }

    public static byte[] loadSecret(Void2FAPlugin plugin, String filename) throws IOException {
        String env = System.getenv("VOID2FA_BRIDGE_SECRET");
        if (env != null && !env.isBlank()) return BridgeSecurity.decodeSecret(env);
        if (filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\"))
            throw new IOException("bridge.secret-file must be a filename inside the plugin data directory");
        Path path = plugin.getDataFolder().toPath().resolve(filename);
        if (!Files.isRegularFile(path)) throw new IOException("Bridge secret file is missing");
        if (Files.size(path) > 256) throw new IOException("Bridge secret file is too large");
        return BridgeSecurity.decodeSecret(Files.readString(path, StandardCharsets.UTF_8));
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(plugin.getConfigManager().getBridgeHost(),
                plugin.getConfigManager().getBridgePort()), 16);
        executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "Void2FA-Bridge"); t.setDaemon(true); return t;
        });
        server.setExecutor(executor);
        server.createContext("/v1/poll", ex -> handle(ex, true));
        server.createContext("/v1/command", ex -> handle(ex, false));
        running = true;
        server.start();
        plugin.getLogger().info("Void2FA bridge слушает " + plugin.getConfigManager().getBridgeHost() + ":"
                + plugin.getConfigManager().getBridgePort() + ". Ожидаю отдельный бот; Telegram API из плагина не вызывается.");
    }

    @Override public boolean isReady() {
        return running && telegramReady && System.currentTimeMillis() - lastPoll < 15000;
    }
    @Override public String getBotUsername() { return botUsername; }
    @Override public void sendMessage(long chatId, String text) {
        enqueue("message", Json.obj("chat_id", chatId, "text", text));
    }
    @Override public void sendAuthRequest(String authId, long chatId, String text, String approve, String deny) {
        enqueue("auth_request", Json.obj("auth_id", authId, "chat_id", chatId, "text", text,
                "approve_label", approve, "deny_label", deny));
    }
    @Override public void editAuthRequest(String authId, long chatId, String text) {
        enqueue("auth_edit", Json.obj("auth_id", authId, "chat_id", chatId, "text", text));
    }
    @Override public void answerCallbackQuery(String callbackId, String text) {
        enqueue("callback_answer", Json.obj("callback_id", callbackId, "text", text == null ? "" : text));
    }

    private synchronized void enqueue(String type, Map<String, Object> payload) {
        if (!running) return;
        prune(System.currentTimeMillis());
        if (outbox.size() >= MAX_EVENTS) {
            plugin.getLogger().warning("Очередь bridge переполнена. Проверь бот-сервис; вход не подтверждается автоматически.");
            return;
        }
        payload.put("type", type);
        payload.put("seq", sequence + 1);
        String id = UUID.randomUUID().toString();
        payload.put("event_id", id);
        outbox.add(new Event(++sequence, id, System.currentTimeMillis(), payload));
    }

    private void prune(long now) {
        outbox.removeIf(e -> e.seq <= acknowledged || now - e.createdAt > EVENT_TTL_MS);
        receipts.entrySet().removeIf(e -> now - e.getValue() > 10 * 60 * 1000L);
    }

    private void handle(HttpExchange ex, boolean poll) {
        try {
            if (!running) { error(ex, 503); return; }
            if (!"POST".equals(ex.getRequestMethod()) || !ex.getRequestURI().getRawPath().equals(poll ? "/v1/poll" : "/v1/command")) {
                error(ex, 404); return;
            }
            byte[] body = ex.getRequestBody().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY || ex.getRequestHeaders().getFirst("Content-Length") == null) { error(ex, 413); return; }
            long now = System.currentTimeMillis() / 1000;
            long ts;
            try { ts = Long.parseLong(ex.getRequestHeaders().getFirst("X-Void2FA-Timestamp")); }
            catch (Exception e) { error(ex, 401); return; }
            String nonce = ex.getRequestHeaders().getFirst("X-Void2FA-Nonce");
            String signature = ex.getRequestHeaders().getFirst("X-Void2FA-Signature");
            String path = ex.getRequestURI().getRawPath();
            if (nonce == null || !BridgeSecurity.fresh(ts, now)
                    || !security.matches(security.sign("request", path, ts, nonce, body), signature)
                    || !security.acceptNonce(nonce, now)) { error(ex, 401); return; }
            Object parsed;
            try { parsed = Json.parse(new String(body, StandardCharsets.UTF_8)); }
            catch (Exception e) { error(ex, 400); return; }
            Map<String, Object> req = Json.asObject(parsed);
            if (req == null || Json.asLong(req.get("version")) != 1) { error(ex, 400); return; }
            Map<String, Object> result = poll ? poll(req) : command(req);
            respond(ex, 200, result, ts, nonce, path);
        } catch (BridgeError e) {
            try { error(ex, e.status); } catch (IOException ignored) {}
        } catch (Exception e) {
            // Never log request bodies, Telegram codes, tokens, signatures or exception URLs.
            plugin.getLogger().warning("Bridge: внутренняя ошибка (" + e.getClass().getSimpleName() + ")");
            try { error(ex, 500); } catch (IOException ignored) {}
        } finally { ex.close(); }
    }

    private synchronized Map<String, Object> poll(Map<String, Object> req) {
        long id = Json.asLong(req.get("bot_id"));
        String username = Json.asString(req.get("bot_username"));
        if (id <= 0 || username == null || !username.matches("[A-Za-z0-9_]{5,32}")) throw new BridgeError(400);
        String expected = plugin.getConfigManager().getBotUsernameOverride();
        if (!expected.isEmpty() && !expected.equalsIgnoreCase(username)) throw new BridgeError(403);
        if (botId != 0 && botId != id) throw new BridgeError(409);
        botId = id;
        botUsername = username;
        telegramReady = Json.asBoolean(req.get("telegram_ready"));
        lastPoll = System.currentTimeMillis();
        long cursor = Json.asLong(req.get("cursor"));
        boolean reset = !instanceId.equals(Json.asString(req.get("instance_id"))) || cursor > sequence;
        if (reset) cursor = 0;
        // The request cursor acknowledges the previous durably processed batch.
        if (cursor >= acknowledged) acknowledged = cursor;
        prune(System.currentTimeMillis());
        if (!outbox.isEmpty() && cursor < outbox.get(0).seq - 1) {
            // Expired/overflowed events are missing: skip the gap, never replay an old login.
            cursor = outbox.get(0).seq - 1;
            acknowledged = cursor;
            reset = true;
        }
        List<Object> events = new ArrayList<>();
        for (Event e : outbox) if (e.seq > cursor && events.size() < 100) events.add(e.payload);
        return Json.obj("version", 1L, "instance_id", instanceId, "reset", reset,
                "events", events, "sequence", sequence);
    }

    private Map<String, Object> command(Map<String, Object> req) {
        if (!instanceId.equals(Json.asString(req.get("instance_id"))) || Json.asLong(req.get("bot_id")) != botId)
            throw new BridgeError(409);
        String eventId = Json.asString(req.get("event_id"));
        if (eventId == null || !eventId.matches("[A-Za-z0-9:_-]{8,100}")) throw new BridgeError(400);
        long issued = Json.asLong(req.get("issued_at"));
        long now = System.currentTimeMillis() / 1000;
        if (issued < now - 120 || issued > now + 30) throw new BridgeError(410);
        long userId = Json.asLong(req.get("user_id")), chatId = Json.asLong(req.get("chat_id"));
        if (userId <= 0 || userId != chatId || !"private".equals(Json.asString(req.get("chat_type")))) throw new BridgeError(400);
        synchronized (receipts) {
            if (receipts.containsKey(eventId)) return Json.obj("version", 1L, "accepted", true, "duplicate", true);
            if (receipts.size() >= 20000) throw new BridgeError(429);
            receipts.put(eventId, System.currentTimeMillis());
        }
        String type = Json.asString(req.get("type"));
        String text = Json.asString(req.get("text"));
        String callbackId = Json.asString(req.get("callback_id"));
        String data = Json.asString(req.get("data"));
        if ((text != null && text.length() > 4096) || (data != null && data.length() > 64)
                || (callbackId != null && callbackId.length() > 256)) throw new BridgeError(400);
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (!plugin.isEnabled() || !running) return;
                    if ("message".equals(type)) router.text(userId, chatId, text);
                    else if ("callback".equals(type)) router.callback(callbackId, userId, chatId, data);
                    else throw new IllegalArgumentException("Unknown command");
                    done.complete(null);
                } catch (Throwable t) { done.completeExceptionally(t); }
            });
            done.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            receipts.remove(eventId);
            throw new BridgeError(503);
        }
        return Json.obj("version", 1L, "accepted", true, "duplicate", false);
    }

    private void respond(HttpExchange ex, int status, Map<String, Object> value, long requestTs, String requestNonce, String path) throws IOException {
        byte[] body = Json.write(value).getBytes(StandardCharsets.UTF_8);
        long ts = System.currentTimeMillis() / 1000;
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Void2FA-Timestamp", Long.toString(ts));
        ex.getResponseHeaders().set("X-Void2FA-Nonce", requestNonce);
        ex.getResponseHeaders().set("X-Void2FA-Signature", security.sign("response", path, ts, requestNonce, body));
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
    }
    private void error(HttpExchange ex, int status) throws IOException {
        byte[] body = "{\"error\":\"request_rejected\"}".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body.length); ex.getResponseBody().write(body);
    }
    private static final class BridgeError extends RuntimeException {
        final int status; BridgeError(int status) { super(null, null, false, false); this.status = status; }
    }

    @Override public void stop() {
        running = false; telegramReady = false;
        if (server != null) server.stop(1);
        if (executor != null) executor.shutdownNow();
        synchronized (this) { outbox.clear(); }
        receipts.clear();
    }
}
