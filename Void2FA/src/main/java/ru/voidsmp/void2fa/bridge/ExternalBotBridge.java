package ru.voidsmp.void2fa.bridge;

import ru.voidsmp.void2fa.Void2FAPlugin;
import ru.voidsmp.void2fa.telegram.Json;
import ru.voidsmp.void2fa.telegram.TelegramCommandRouter;
import ru.voidsmp.void2fa.telegram.TelegramTransport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Client for the Python bot hosted on Bothost (or any HTTPS host).
 * The plugin initiates every request; no inbound Minecraft-server port is required.
 * This class never authorizes a Minecraft player: the router/AuthManager do that on the main thread.
 */
public final class ExternalBotBridge implements TelegramTransport {
    private static final int MAX_BODY = 262144;
    private static final int MAX_EVENTS = 1000;
    private static final int BATCH = 100;
    private static final long EVENT_TTL_MS = 10 * 60 * 1000L;
    private static final long WARN_INTERVAL_MS = 5 * 60 * 1000L;
    private static final String SYNC_PATH = "/v1/sync";
    private final Void2FAPlugin plugin;
    private final BridgeSecurity security;
    private final TelegramCommandRouter router;
    private final String instanceId = UUID.randomUUID().toString();
    private final List<Event> outbox = new ArrayList<>();
    private final Map<String, Long> receipts = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private HttpClient http;
    private URI syncUri;
    private Duration requestTimeout = Duration.ofSeconds(30);
    private long readyGraceMillis = 40_000L;
    private Thread worker;
    private volatile boolean running;
    private volatile long lastSync;
    private volatile long cursor;
    private volatile String botUsername = "";
    private volatile boolean telegramReady;
    // Written only by the single bridge worker thread.
    private long lastWarnAt;
    private String lastWarnKind = "";
    private int failureStreak;

    private record Event(String id, long createdAt, Map<String, Object> payload) {}

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
        String configured = plugin.getConfigManager().getBridgeUrl();
        URI base;
        try { base = URI.create(configured); } catch (IllegalArgumentException e) { throw new IOException("Invalid telegram.bridge.url"); }
        String scheme = base.getScheme();
        String host = base.getHost();
        if (host == null || base.getRawPath() != null && !base.getRawPath().isEmpty() && !base.getRawPath().equals("/")
                || base.getRawQuery() != null || base.getRawFragment() != null || base.getUserInfo() != null)
            throw new IOException("telegram.bridge.url must be an HTTPS origin without path, query or credentials");
        boolean loopback = host.equals("127.0.0.1") || host.equals("::1") || host.equals("localhost");
        if (!"https".equalsIgnoreCase(scheme) && !("http".equalsIgnoreCase(scheme) && loopback))
            throw new IOException("telegram.bridge.url must use HTTPS (HTTP is allowed only for loopback tests)");
        syncUri = URI.create((configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured) + SYNC_PATH);
        requestTimeout = Duration.ofSeconds(plugin.getConfigManager().getBridgeTimeoutSeconds());
        readyGraceMillis = plugin.getConfigManager().getBridgeReadyGraceSeconds() * 1000L;
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(plugin.getConfigManager().getBridgeConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        running = true;
        worker = new Thread(this::loop, "Void2FA-Bridge");
        worker.setDaemon(true);
        worker.start();
        plugin.getLogger().info("Void2FA bridge подключается к боту по адресу " + syncUri.getHost()
                + ". Входящий порт на Minecraft-сервере не нужен; Telegram API из плагина не вызывается.");
    }

    @Override public boolean isReady() {
        return running && telegramReady && System.currentTimeMillis() - lastSync < readyGraceMillis;
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
        String id = UUID.randomUUID().toString();
        payload.put("event_id", id);
        outbox.add(new Event(id, System.currentTimeMillis(), payload));
    }

    private synchronized List<Event> batch() {
        prune(System.currentTimeMillis());
        return new ArrayList<>(outbox.subList(0, Math.min(BATCH, outbox.size())));
    }

    private synchronized void accept(List<String> ids) {
        if (ids.isEmpty()) return;
        Set<String> accepted = new HashSet<>(ids);
        outbox.removeIf(e -> accepted.contains(e.id));
    }

    private void prune(long now) {
        outbox.removeIf(e -> now - e.createdAt > EVENT_TTL_MS);
        receipts.entrySet().removeIf(e -> now - e.getValue() > 10 * 60 * 1000L);
    }

    private void loop() {
        long delay = 1000;
        while (running) {
            try {
                syncOnce();
                delay = 1000;
                if (failureStreak > 0) {
                    plugin.getLogger().info("Bridge снова на связи с ботом (сбоев подряд: " + failureStreak + ").");
                    failureStreak = 0;
                    lastWarnAt = 0;
                    lastWarnKind = "";
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                telegramReady = false;
                delay = Math.min(delay * 2, 15000);
                warnUnavailable(e);
            }
            try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    /**
     * A dead bot endpoint fails every few seconds. Log the first failure and then at most
     * one line per WARN_INTERVAL_MS, so the server console stays usable.
     */
    private void warnUnavailable(Exception e) {
        String kind = e.getClass().getSimpleName();
        long now = System.currentTimeMillis();
        failureStreak++;
        if (!shouldWarn(kind, now, lastWarnAt, lastWarnKind)) return;
        String repeated = failureStreak > 1 ? " Сбоев подряд: " + failureStreak + "." : "";
        lastWarnAt = now;
        lastWarnKind = kind;
        plugin.getLogger().warning("Bridge недоступен (" + kind + ")." + repeated
                + " " + hint(kind) + " Пока связи нет, 2FA не подтверждает вход.");
    }

    /** First failure of a kind is always logged; repeats of the same kind are rate-limited. */
    static boolean shouldWarn(String kind, long now, long lastWarnAt, String lastWarnKind) {
        return !kind.equals(lastWarnKind) || now - lastWarnAt >= WARN_INTERVAL_MS;
    }

    static String hint(String kind) {
        return switch (kind) {
            case "HttpTimeoutException", "HttpConnectTimeoutException" ->
                    "Сервер не дождался ответа от бота. Проверь, что бот на Bothost запущен, "
                    + "и открой в браузере https://<домен-бота>/health: нет ответа — проблема домена/порта, "
                    + "telegram_ready=false — бот не достаёт Telegram.";
            case "ConnectException", "UnknownHostException" ->
                    "Не удаётся соединиться с доменом бота. Проверь telegram.bridge.url и домен в панели Bothost.";
            case "SSLHandshakeException", "SSLException" ->
                    "TLS-рукопожатие с доменом бота не прошло. Проверь сертификат и системное время на сервере.";
            default ->
                    "Проверь URL бота, домен Bothost и общий секрет. При неверном секрете был бы HTTP 401.";
        };
    }

    private void syncOnce() throws Exception {
        List<Event> events = batch();
        List<Object> payloads = new ArrayList<>();
        for (Event e : events) payloads.add(e.payload);
        byte[] body = Json.write(Json.obj("version", 1L, "instance_id", instanceId,
                "cursor", cursor, "events", payloads)).getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_BODY) throw new IOException("Bridge request too large");
        long ts = System.currentTimeMillis() / 1000;
        String nonce = nonce();
        HttpRequest request = HttpRequest.newBuilder(syncUri).timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Cache-Control", "no-store")
                .header("X-Void2FA-Timestamp", Long.toString(ts))
                .header("X-Void2FA-Nonce", nonce)
                .header("X-Void2FA-Signature", security.sign("request", SYNC_PATH, ts, nonce, body))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200 || response.body().length > MAX_BODY)
            throw new IOException("Bridge rejected request (HTTP " + response.statusCode() + ")");
        long responseTs;
        try { responseTs = Long.parseLong(response.headers().firstValue("X-Void2FA-Timestamp").orElse("")); }
        catch (NumberFormatException e) { throw new IOException("Unauthenticated bridge response"); }
        String responseNonce = response.headers().firstValue("X-Void2FA-Nonce").orElse("");
        String signature = response.headers().firstValue("X-Void2FA-Signature").orElse("");
        long now = System.currentTimeMillis() / 1000;
        if (!BridgeSecurity.fresh(responseTs, now) || !nonce.equals(responseNonce)
                || !security.matches(security.sign("response", SYNC_PATH, responseTs, responseNonce, response.body()), signature))
            throw new IOException("Unauthenticated bridge response");
        Map<String, Object> value = Json.asObject(Json.parse(new String(response.body(), StandardCharsets.UTF_8)));
        if (value == null || Json.asLong(value.get("version")) != 1) throw new IOException("Invalid bridge protocol response");
        String username = Json.asString(value.get("bot_username"));
        if (username == null || !username.matches("[A-Za-z0-9_]{5,32}")) throw new IOException("Invalid bot username");
        String expected = plugin.getConfigManager().getBotUsernameOverride();
        if (!expected.isEmpty() && !expected.equalsIgnoreCase(username)) throw new IOException("Bot username mismatch");
        List<Object> acceptedRaw = Json.asArray(value.get("accepted"));
        List<Object> commands = Json.asArray(value.get("commands"));
        if (acceptedRaw == null || commands == null) throw new IOException("Invalid bridge protocol response");
        List<String> accepted = new ArrayList<>();
        for (Object o : acceptedRaw) { String id = Json.asString(o); if (id != null) accepted.add(id); }
        accept(accepted);
        botUsername = username;
        telegramReady = Json.asBoolean(value.get("telegram_ready"));
        lastSync = System.currentTimeMillis();
        if (Json.asBoolean(value.get("reset"))) {
            cursor = Json.asLong(value.get("cursor"));
            receipts.clear();
        }
        for (Object o : commands) {
            Map<String, Object> command = Json.asObject(o);
            if (command == null) throw new IOException("Invalid bridge command");
            long seq = Json.asLong(command.get("seq"));
            if (seq <= cursor) continue;
            if (!dispatch(command)) return;
            cursor = seq;
        }
    }

    private boolean dispatch(Map<String, Object> command) throws InterruptedException {
        String eventId = Json.asString(command.get("event_id"));
        long issued = Json.asLong(command.get("issued_at"));
        long now = System.currentTimeMillis() / 1000;
        long userId = Json.asLong(command.get("user_id")), chatId = Json.asLong(command.get("chat_id"));
        String type = Json.asString(command.get("type"));
        String text = Json.asString(command.get("text"));
        String callbackId = Json.asString(command.get("callback_id"));
        String data = Json.asString(command.get("data"));
        boolean valid = eventId != null && eventId.matches("[A-Za-z0-9:_-]{8,100}")
                && issued >= now - 120 && issued <= now + 30
                && userId > 0 && userId == chatId && "private".equals(Json.asString(command.get("chat_type")))
                && ("message".equals(type) && text != null && text.length() <= 4096
                    || "callback".equals(type) && callbackId != null && callbackId.length() <= 256
                        && data != null && data.length() <= 64);
        if (!valid) return true; // drop expired/malformed facts and advance the cursor
        synchronized (receipts) {
            if (receipts.containsKey(eventId)) return true;
            if (receipts.size() >= 20000) return false;
            receipts.put(eventId, System.currentTimeMillis());
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (!plugin.isEnabled() || !running) return;
                    if ("message".equals(type)) router.text(userId, chatId, text);
                    else router.callback(callbackId, userId, chatId, data);
                    done.complete(null);
                } catch (Throwable t) { done.completeExceptionally(t); }
            });
            done.get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            receipts.remove(eventId);
            return false;
        }
    }

    private String nonce() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override public void stop() {
        running = false; telegramReady = false;
        if (worker != null) worker.interrupt();
        synchronized (this) { outbox.clear(); }
        receipts.clear();
    }
}
