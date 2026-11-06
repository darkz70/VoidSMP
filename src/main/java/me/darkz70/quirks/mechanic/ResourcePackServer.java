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

    private static volatile String url;
    private static volatile byte[] hash;
    private static volatile boolean ready = false;

    private ResourcePackServer() {}

    /** Запускает фоновую загрузку текущего пака и подсчёт его sha1 (не блокирует старт сервера). */
    public static void init(JavaPlugin plugin) {
        if (!plugin.getConfig().getBoolean("resourcepack.enabled", true)) return;
        url = plugin.getConfig().getString("resourcepack.url",
                "https://github.com/darkz70/VoidSMP/releases/download/ci-build/VoidQuirks-Pack.zip");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> refresh(plugin));
    }

    private static void refresh(JavaPlugin plugin) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                plugin.getLogger().warning("Авто-раздача ресурспака: сервер отдал код " + response.statusCode()
                        + " при скачивании " + url + " — игроки НЕ получат пак автоматически, пока это не"
                        + " исправится (возможна старая версия текстур у игроков).");
                return;
            }
            byte[] body = response.body();
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            hash = sha1.digest(body);
            ready = true;
            plugin.getLogger().info("Авто-раздача ресурспака готова: " + body.length
                    + " байт, sha1 посчитан свежо. Игроки будут получать актуальный пак автоматически при входе.");
        } catch (Exception e) {
            ready = false;
            plugin.getLogger().warning("Авто-раздача ресурспака не удалась (" + e.getMessage()
                    + ") — проверь, что серверу доступен интернет (github.com). Игроки могут видеть устаревшие"
                    + " текстуры, пока это не починится; можно по-прежнему раздать пак руками через /quirk pack.");
        }
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
