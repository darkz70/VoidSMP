package ru.voidsmp.void2fa;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import ru.voidsmp.void2fa.telegram.Json;
import ru.voidsmp.void2fa.telegram.TelegramTransport;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The 2FA state machine: freezing unauthenticated players, sending Telegram
 * codes / approval buttons, handling codes, links, timeouts and sessions.
 * Everything here runs on the main server thread.
 */
public final class AuthManager {

    private static final String ALPHANUM = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long LINK_TTL_MILLIS = 5 * 60 * 1000L;

    private final Void2FAPlugin plugin;
    private final ConfigManager config;
    private final PlayerDataManager data;
    private final SecureRandom random = new SecureRandom();

    private final Map<UUID, AuthState> pending = new ConcurrentHashMap<>();
    private final Map<String, AuthState> byAuthId = new ConcurrentHashMap<>();
    private final Map<String, LinkRequest> linkTokens = new HashMap<>();
    private final Map<UUID, String> linkByPlayer = new HashMap<>();

    public AuthManager(Void2FAPlugin plugin, ConfigManager config, PlayerDataManager data) {
        this.plugin = plugin;
        this.config = config;
        this.data = data;
    }

    // ------------------------------------------------------------------
    //  State holders
    // ------------------------------------------------------------------

    private static final class AuthState {
        UUID playerId;
        String playerName;
        String ip;
        boolean linked;
        String authId;
        String code;
        long codeExpiresAt;
        long chatId;
        int attempts;
        long lastSentAt;
        BukkitTask timeoutTask;
        BukkitTask reminderTask;
    }

    private static final class LinkRequest {
        final UUID playerId;
        final String playerName;
        final long expiresAt;

        LinkRequest(UUID playerId, String playerName, long expiresAt) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.expiresAt = expiresAt;
        }
    }

    // ------------------------------------------------------------------
    //  Join / quit
    // ------------------------------------------------------------------

    public void onJoin(Player player) {
        UUID id = player.getUniqueId();
        if (player.hasPermission("void2fa.bypass")) {
            return;
        }
        TelegramTransport bot = plugin.getBot();
        boolean botReady = bot != null && bot.isReady();
        if (!botReady) {
            if (config.isLockdownIfUnavailable()) {
                player.kickPlayer(config.kick("lockdown-kick"));
            } else if (data.isLinked(id) && data.isEnabled(id)) {
                player.sendMessage(config.msg("bot-not-configured"));
            }
            return;
        }

        boolean required = config.isRequiredMode() || (data.isLinked(id) && data.isEnabled(id));
        if (!required) {
            return;
        }

        String ip = ipOf(player);
        int session = config.getSessionMinutes();
        if (session > 0 && data.isLinked(id)) {
            long last = data.getLastAuth(id);
            String lastIp = data.getLastIp(id);
            if (last > 0 && ip != null && ip.equals(lastIp)
                    && System.currentTimeMillis() - last < session * 60_000L) {
                return; // recent session from the same IP — skip 2FA
            }
        }

        AuthState st = new AuthState();
        st.playerId = id;
        st.playerName = player.getName();
        st.ip = ip == null ? "?" : ip;
        st.linked = data.isLinked(id);
        st.authId = randomToken(16);
        pending.put(id, st);
        byAuthId.put(st.authId, st);

        applyBlindness(player);
        scheduleTimeout(st);
        scheduleReminder(st);

        if (st.linked) {
            st.chatId = data.getTelegramId(id);
            sendCode(st);
            player.sendMessage(config.msg("auth-required"));
        } else {
            player.sendMessage(config.msg("link-required"));
        }
    }

    public void onQuit(Player player) {
        UUID id = player.getUniqueId();
        AuthState st = pending.get(id);
        if (st != null) {
            editTelegram(st, config.tg("tg-quit", "player", st.playerName));
            cleanup(st);
        }
        String token = linkByPlayer.remove(id);
        if (token != null) {
            linkTokens.remove(token);
        }
    }

    public boolean isPending(UUID id) {
        return pending.containsKey(id);
    }

    // ------------------------------------------------------------------
    //  Code flow
    // ------------------------------------------------------------------

    private void sendCode(AuthState st) {
        st.code = randomCode(config.getCodeLength());
        st.codeExpiresAt = System.currentTimeMillis() + config.getCodeTtlSeconds() * 1000L;
        st.lastSentAt = System.currentTimeMillis();
        String text = config.tg("tg-auth-request", "player", st.playerName, "ip", st.ip, "code", st.code);
        TelegramTransport bot = plugin.getBot();
        if (bot == null) {
            return;
        }
        bot.sendAuthRequest(st.authId, st.chatId, text,
                config.tg("button-approve"), config.tg("button-deny"));
    }

    public void submitCode(Player player, String input) {
        AuthState st = pending.get(player.getUniqueId());
        if (st == null) {
            player.sendMessage(config.msg("no-pending"));
            return;
        }
        if (!st.linked || data.getTelegramId(st.playerId) != st.chatId) {
            player.sendMessage(config.msg("link-required"));
            return;
        }
        if (System.currentTimeMillis() >= st.codeExpiresAt) {
            player.sendMessage(config.msg("code-expired"));
            return;
        }
        if (java.security.MessageDigest.isEqual(st.code.getBytes(java.nio.charset.StandardCharsets.US_ASCII), input.trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            approve(st);
            return;
        }
        st.attempts++;
        int left = config.getMaxAttempts() - st.attempts;
        if (left <= 0) {
            editTelegram(st, config.tg("tg-denied", "player", st.playerName));
            kick(st.playerId, config.kick("attempts-kick"));
            cleanup(st);
        } else {
            player.sendMessage(config.msg("wrong-code", "attempts", String.valueOf(left)));
        }
    }

    public void resend(Player player) {
        AuthState st = pending.get(player.getUniqueId());
        if (st == null) {
            player.sendMessage(config.msg("no-pending"));
            return;
        }
        if (!st.linked) {
            player.sendMessage(config.msg("link-required"));
            return;
        }
        long now = System.currentTimeMillis();
        if (now - st.lastSentAt < 10000) {
            player.sendMessage(config.msg("reminder"));
            return;
        }
        editTelegram(st, config.tg("tg-expired", "player", st.playerName));
        byAuthId.remove(st.authId);
        st.authId = randomToken(16);
        byAuthId.put(st.authId, st);
        sendCode(st);
        player.sendMessage(config.msg("code-sent"));
    }

    /** Called when the player presses the approve button in Telegram. */
    public boolean approveByAuthId(String authId, long telegramUserId) {
        AuthState st = byAuthId.get(authId);
        if (!validButton(st, telegramUserId) || System.currentTimeMillis() >= st.codeExpiresAt) return false;
        approve(st);
        return true;
    }

    /** Called when the player presses the deny button in Telegram. */
    public boolean denyByAuthId(String authId, long telegramUserId) {
        AuthState st = byAuthId.get(authId);
        if (!validButton(st, telegramUserId)) return false;
        editTelegram(st, config.tg("tg-denied", "player", st.playerName));
        kick(st.playerId, config.kick("denied-kick"));
        cleanup(st);
        return true;
    }

    private boolean validButton(AuthState st, long telegramUserId) {
        return st != null && st.linked && st.chatId == telegramUserId
                && data.getTelegramId(st.playerId) == telegramUserId
                && pending.get(st.playerId) == st && Bukkit.getPlayer(st.playerId) != null;
    }

    private void approve(AuthState st) {
        editTelegram(st, config.tg("tg-approved", "player", st.playerName));
        cleanup(st);
        Player p = Bukkit.getPlayer(st.playerId);
        if (p != null) {
            removeBlindness(p);
            p.sendMessage(config.msg("approved"));
        }
        data.updateLastAuth(st.playerId, st.ip);
    }

    // ------------------------------------------------------------------
    //  Linking
    // ------------------------------------------------------------------

    public void startLink(Player player) {
        UUID id = player.getUniqueId();
        if (data.isLinked(id)) {
            player.sendMessage(config.msg("already-linked"));
            return;
        }
        TelegramTransport bot = plugin.getBot();
        if (bot == null || !bot.isReady()) {
            player.sendMessage(config.msg("bot-not-configured"));
            return;
        }
        long now = System.currentTimeMillis();
        String token = linkByPlayer.get(id);
        LinkRequest existing = token == null ? null : linkTokens.get(token);
        if (existing == null || existing.expiresAt < now) {
            if (token != null) {
                linkTokens.remove(token);
            }
            token = randomToken(12);
            linkTokens.put(token, new LinkRequest(id, player.getName(), now + LINK_TTL_MILLIS));
            linkByPlayer.put(id, token);
        }
        String botName = bot.getBotUsername();
        if (botName == null || botName.isEmpty()) {
            botName = config.getBotUsernameOverride();
        }
        String botDisplay = (botName == null || botName.isEmpty()) ? "(бот)" : "@" + botName;
        String url = (botName == null || botName.isEmpty())
                ? "(ссылка появится после подключения бота)"
                : "https://t.me/" + botName + "?start=LINK_" + token;
        player.sendMessage(config.msg("link-start",
                "token", token, "bot", botDisplay, "url", url, "minutes", "5"));
    }

    /** Called (on the main thread) when the bot receives a valid link code. */
    public void completeLink(String token, long telegramId) {
        TelegramTransport bot = plugin.getBot();
        if (bot == null) {
            return;
        }
        if (!claimLinkAttempt(telegramId)) {
            bot.sendMessage(telegramId, config.tg("tg-link-ratelimit"));
            return;
        }
        LinkRequest req = linkTokens.get(token);
        long now = System.currentTimeMillis();
        if (req == null || req.expiresAt < now) {
            if (req != null) {
                linkTokens.remove(token);
                linkByPlayer.remove(req.playerId);
            }
            bot.sendMessage(telegramId, config.tg("tg-link-invalid"));
            return;
        }
        UUID id = req.playerId;
        UUID existingOwner = data.findByTelegramId(telegramId);
        if (existingOwner != null && !existingOwner.equals(id)) {
            bot.sendMessage(telegramId, config.tg("tg-link-already", "player", data.getName(existingOwner)));
            return;
        }
        if (data.isLinked(id) && data.getTelegramId(id) != telegramId) {
            bot.sendMessage(telegramId, config.tg("tg-link-already", "player", data.getName(id)));
            return;
        }
        String name = req.playerName;
        data.link(id, name, telegramId);
        linkTokens.remove(token);
        linkByPlayer.remove(id);
        bot.sendMessage(telegramId, config.tg("tg-link-success", "player", name));

        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            p.sendMessage(config.msg("link-success"));
            AuthState st = pending.get(id);
            if (st != null && !st.linked) {
                st.linked = true;
                st.chatId = telegramId;
                sendCode(st);
                p.sendMessage(config.msg("auth-required"));
            }
        }
    }

    public void unlinkByPlayer(Player player) {
        UUID id = player.getUniqueId();
        if (!data.isLinked(id)) {
            player.sendMessage(config.msg("not-linked"));
            return;
        }
        if (isPending(id)) {
            player.sendMessage(config.msg("must-be-authenticated"));
            return;
        }
        data.unlink(id);
        player.sendMessage(config.msg("unlink-success"));
    }

    public void unlinkByTelegram(long telegramId) {
        TelegramTransport bot = plugin.getBot();
        if (bot == null) {
            return;
        }
        UUID id = data.findByTelegramId(telegramId);
        if (id == null) {
            bot.sendMessage(telegramId, config.tg("tg-unlink-none"));
            return;
        }
        if (isPending(id)) {
            bot.sendMessage(telegramId, config.tg("tg-unlink-pending"));
            return;
        }
        data.unlink(id);
        bot.sendMessage(telegramId, config.tg("tg-unlink-success"));
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            p.sendMessage(config.msg("unlink-success"));
        }
    }

    public void setEnabled(Player player, boolean enabled) {
        UUID id = player.getUniqueId();
        if (!data.isLinked(id)) {
            player.sendMessage(config.msg("not-linked"));
            return;
        }
        if (isPending(id)) {
            player.sendMessage(config.msg("must-be-authenticated"));
            return;
        }
        data.setEnabled(id, enabled);
        player.sendMessage(config.msg(enabled ? "enabled" : "disabled"));
    }

    public void sendTelegramStatus(long telegramId) {
        TelegramTransport bot = plugin.getBot();
        if (bot == null) {
            return;
        }
        UUID id = data.findByTelegramId(telegramId);
        if (id == null) {
            bot.sendMessage(telegramId, config.tg("tg-status-unlinked"));
        } else {
            String state = data.isEnabled(id) ? "включена" : "выключена";
            bot.sendMessage(telegramId, config.tg("tg-status-linked",
                    "player", data.getName(id), "state", state);
        }
    }

    // ------------------------------------------------------------------
    //  Admin helpers
    // ------------------------------------------------------------------

    public void adminReset(UUID id) {
        data.unlink(id);
        AuthState st = pending.get(id);
        if (st != null) {
            editTelegram(st, config.tg("tg-denied", "player", st.playerName));
            kick(id, config.kick("denied-kick"));
            cleanup(st);
        }
        String token = linkByPlayer.remove(id);
        if (token != null) {
            linkTokens.remove(token);
        }
    }

    // ------------------------------------------------------------------
    //  Tasks / freeze / cleanup
    // ------------------------------------------------------------------

    private void scheduleTimeout(AuthState st) {
        long ticks = config.getAuthTimeoutSeconds() * 20L;
        st.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.get(st.playerId) == st) {
                editTelegram(st, config.tg("tg-expired", "player", st.playerName));
                kick(st.playerId, config.kick("timeout-kick"));
                cleanup(st);
            }
        }, ticks);
    }

    private void scheduleReminder(AuthState st) {
        st.reminderTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (pending.get(st.playerId) != st) {
                return;
            }
            Player p = Bukkit.getPlayer(st.playerId);
            if (p != null) {
                p.sendMessage(config.msg("reminder"));
                applyBlindness(p);
            }
        }, 200L, 200L);
    }

    private void applyBlindness(Player p) {
        if (config.isBlindness()) {
            int duration = config.getAuthTimeoutSeconds() * 20 + 200;
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration, 0, false), true);
        }
    }

    private void removeBlindness(Player p) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    private void editTelegram(AuthState st, String text) {
        TelegramTransport bot = plugin.getBot();
        if (bot != null && st.chatId != 0) {
            bot.editAuthRequest(st.authId, st.chatId, text);
        }
    }

    private void kick(UUID id, String message) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            p.kickPlayer(message);
        }
    }

    private void cleanup(AuthState st) {
        pending.remove(st.playerId, st);
        byAuthId.remove(st.authId, st);
        if (st.timeoutTask != null) {
            st.timeoutTask.cancel();
        }
        if (st.reminderTask != null) {
            st.reminderTask.cancel();
        }
    }

    /** Clears all pending auth on plugin disable. */
    public void shutdown() {
        for (AuthState st : new HashMap<>(pending).values()) {
            Player p = Bukkit.getPlayer(st.playerId);
            if (p != null) {
                p.kickPlayer(config.kick("lockdown-kick"));
            }
            cleanup(st);
        }
        pending.clear();
        byAuthId.clear();
        linkTokens.clear();
        linkByPlayer.clear();
    }

    // ------------------------------------------------------------------
    //  Utilities
    // ------------------------------------------------------------------

    private final Map<Long, long[]> linkAttempts = new HashMap<>();
    private boolean claimLinkAttempt(long telegramId) {
        long now = System.currentTimeMillis();
        linkAttempts.entrySet().removeIf(e -> now - e.getValue()[0] >= 60000);
        long[] window = linkAttempts.computeIfAbsent(telegramId, k -> new long[]{now, 0});
        if (window[1] >= 10) return false;
        window[1]++;
        return true;
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('0' + random.nextInt(10)));
        }
        return sb.toString();
    }

    private String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM.charAt(random.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }

    private static String ipOf(Player player) {
        InetSocketAddress addr = player.getAddress();
        if (addr == null || addr.getAddress() == null) {
            return null;
        }
        return addr.getAddress().getHostAddress();
    }
}
