package me.darkz70.quirks.mechanic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Авто-раздача ресурспака игрокам при входе — вместо того чтобы полагаться на
 * ручную настройку {@code resource-pack=}/{@code resource-pack-sha1=} в
 * server.properties.
 * <p>
 * Почему это важно: ссылка на ci-build-релиз НЕ меняется между версиями (это
 * один и тот же URL, его содержимое просто подменяется при каждой сборке), но
 * клиент Minecraft решает, перекачивать ли пак заново, ИМЕННО по sha1-хэшу,
 * который ему присылает сервер. Если этот хэш в server.properties руками не
 * обновлять на каждую версию — клиент тихо продолжает использовать старый
 * закэшированный пак, и новые текстуры/модели никогда не доходят до игрока,
 * хотя сам файл на GitHub давно обновился. Это и было причиной повторяющихся
 * "текстур нет" багов.
 * <p>
 * Тут хэш считается заново ПРИ КАЖДОМ СТАРТЕ сервера — ровно тогда, когда
 * админ и так обязан положить новый jar и перезапустить сервер, так что
 * рассинхрона быть не может: какая версия пака реально лежит на GitHub, такой
 * хэш и посчитается.
 * <p>
 * Если первая попытка скачать пак не удалась (временная сеть/DNS при старте,
 * недоступность GitHub с хостинга и т.п.) — это НЕ приговор: ниже есть
 * автоматические повторные попытки с нарастающей паузой, которые продолжаются
 * в фоне, пока не получится (без необходимости перезапускать сервер), плюс
 * ручной форс-повтор командой {@code /quirk pack retry}.
 */
public final class ResourcePackServer {

    /**
     * Фиксированный ID именно НАШЕГО пака в мульти-паковом слоте клиента. ВАЖНО:
     * раньше тут использовался {@code Player#setResourcePack(...)}, который
     * ЗАМЕНЯЕТ активный ресурспак игрока целиком — если на сервере уже стоит свой
     * (основной) ресурспак, он слетал и подменялся на VoidQuirks. Теперь вместо
     * этого используется {@code Player#addResourcePack(id, ...)} — он добавляет
     * наш пак ДОПОЛНИТЕЛЬНО, остальные паки игрока (включая серверный основной
     * resource-pack= из server.properties) не трогает.
     */
    private static final UUID PACK_ID = UUID.fromString("c0ffee00-0000-4000-8000-000000000001");

    /** Пауза перед повторными попытками (сек): растёт, но не дальше 5 минут. */
    private static final long[] RETRY_DELAYS_SECONDS = {5, 15, 45, 90, 180, 300};

    private static volatile String url;
    private static volatile byte[] hash;
    private static volatile boolean ready = false;
    private static volatile boolean enabled = false;
    private static volatile int attempt = 0;
    private static volatile JavaPlugin plugin;

    private ResourcePackServer() {}

    /** Запускает фоновую загрузку текущего пака и подсчёт его sha1 (не блокирует старт сервера). */
    public static void init(JavaPlugin plugin) {
        ResourcePackServer.plugin = plugin;
        if (!plugin.getConfig().getBoolean("resourcepack.enabled", true)) {
            enabled = false;
            return;
        }
        enabled = true;
        ready = false;
        attempt = 0;
        url = plugin.getConfig().getString("resourcepack.url",
                "https://github.com/darkz70/VoidSMP/releases/download/ci-build/VoidQuirks-Pack.zip");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> refresh(plugin));
    }

    /**
     * Форсирует немедленную новую попытку скачивания (минуя паузу между
     * ретраями) — используется командой {@code /quirk pack retry}, чтобы
     * проверить, починилась ли сеть, не перезапуская весь сервер.
     *
     * @return true если запрос на попытку принят (функция включена в конфиге)
     */
    public static boolean retryNow() {
        if (plugin == null || !enabled) return false;
        attempt = 0;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> refresh(plugin));
        return true;
    }

    public static boolean isReady() {
        return ready;
    }

    public static String currentUrl() {
        return url;
    }

    private static void refresh(JavaPlugin plugin) {
        int thisAttempt = ++attempt;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    // Некоторые хостинги/файрволы на пути к GitHub режут запросы с "ботовскими" или
                    // пустыми User-Agent — явно представляемся, как обычный HTTP-клиент.
                    .header("User-Agent", "VoidQuirks-ResourcePackFetcher/1.0 (+https://github.com/darkz70/VoidSMP)")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                warnAndMaybeRetry(plugin, thisAttempt,
                        "сервер отдал код " + response.statusCode() + " при скачивании " + url);
                return;
            }
            byte[] body = response.body();
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            hash = sha1.digest(body);
            ready = true;
            plugin.getLogger().info("Авто-раздача ресурспака готова (попытка " + thisAttempt + "): " + body.length
                    + " байт, sha1 посчитан свежо. Игроки будут получать актуальный пак автоматически при входе.");
        } catch (Exception e) {
            String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
            Throwable cause = e.getCause();
            if (cause != null && cause != e) {
                detail += " (причина: " + cause.getClass().getSimpleName() + ": " + cause.getMessage() + ")";
            }
            warnAndMaybeRetry(plugin, thisAttempt, detail);
        }
    }

    private static void warnAndMaybeRetry(JavaPlugin plugin, int thisAttempt, String detail) {
        ready = false;
        long delay = RETRY_DELAYS_SECONDS[Math.min(thisAttempt - 1, RETRY_DELAYS_SECONDS.length - 1)];
        plugin.getLogger().warning("Авто-раздача ресурспака не удалась (попытка " + thisAttempt + "): " + detail
                + " — проверь, что серверу/хостингу доступен исходящий HTTPS на github.com"
                + " (некоторые хостинги режут исходящий трафик с игрового процесса)."
                + " Повторю через " + delay + " сек. Пока это не починится, игроки не получат пак автоматически;"
                + " можно раздать ссылку руками через /quirk pack.");
        if (!enabled) return;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> refresh(plugin), delay * 20L);
    }

    /** Отправляет игроку актуальный ресурспак ДОПОЛНИТЕЛЬНО (не заменяя его прочие паки). */
    public static void sendTo(Player player, JavaPlugin plugin) {
        if (!ready || url == null || hash == null) return;
        boolean force = plugin.getConfig().getBoolean("resourcepack.force", false);
        String promptRaw = plugin.getConfig().getString("resourcepack.prompt",
                "&bУстанови ресурспак VoidQuirks — иначе причуды будут выглядеть как обычные ванильные блоки!");
        String promptLegacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(Msg.color(promptRaw));
        player.addResourcePack(PACK_ID, url, hash, promptLegacy, force);
    }
}
