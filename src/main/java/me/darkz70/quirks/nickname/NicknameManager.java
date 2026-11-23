package me.darkz70.quirks.nickname;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Смена ника игрока админом через /gaid nick &lt;игрок&gt; &lt;текст&gt;.
 * Меняет «везде»: ник над головой (через подмену {@link com.destroystokyo.paper.profile.PlayerProfile}),
 * имя в табе ({@link Player#playerListName(Component)}) и имя в чате
 * ({@link Player#displayName(Component)}). Настоящее имя сохраняется в
 * памяти на время сессии (на вход фиксируется до подмены) — им же ник
 * возвращается обратно через /gaid nick &lt;игрок&gt; reset.
 * <p>
 * Ник сохраняется в players.yml ({@link PlayerData#nickname()}) и
 * переприменяется автоматически при повторном входе игрока.
 */
public final class NicknameManager implements Listener {

    private static final int MAX_LEN = 16;

    private final VoidQuirksPlugin plugin;
    /** Настоящее имя аккаунта, зафиксированное на входе до любых подмен профиля. */
    private final Map<UUID, String> originalNames = new HashMap<>();

    public NicknameManager(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        originalNames.put(player.getUniqueId(), player.getName());

        PlayerData data = plugin.storage().get(player.getUniqueId());
        String nick = data == null ? null : data.nickname();
        if (nick == null || nick.isBlank()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) apply(player, nick);
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        originalNames.remove(event.getPlayer().getUniqueId());
    }

    /** Настоящее имя аккаунта (то, под которым игрок вошёл на сервер). */
    public String originalName(Player player) {
        return originalNames.getOrDefault(player.getUniqueId(), player.getName());
    }

    /**
     * Текущий ник игрока — работает и для офлайн/никогда не заходивших (берётся
     * из players.yml по UUID, без требования, чтобы игрок был сейчас на сервере).
     */
    @Nullable
    public String currentNickname(OfflinePlayer target) {
        PlayerData data = plugin.storage().get(target.getUniqueId());
        return data == null ? null : data.nickname();
    }

    /**
     * Назначает ник. rawText может содержать &amp;-цвета — они применятся в чате/табе.
     * Работает и для офлайн/никогда не заходивших игроков: ник просто сохраняется
     * в players.yml по UUID и подхватится автоматически при следующем входе
     * (см. {@link #onJoin}); если игрок сейчас онлайн — применяется сразу же.
     */
    public boolean setNickname(OfflinePlayer target, String rawText) {
        String trimmed = rawText == null ? "" : rawText.trim();
        Component colored = Msg.color(trimmed);
        String plainTag = PlainTextComponentSerializer.plainText().serialize(colored).trim();
        if (plainTag.isEmpty()) return false;

        // храним исходный текст с &-цветами — так ник корректно перекрасится при повторном входе
        PlayerData data = dataOrCreate(target.getUniqueId());
        data.nickname(trimmed);
        plugin.storage().save();

        Player online = target.getPlayer();
        if (online != null) apply(online, trimmed);
        return true;
    }

    /** Снимает ник, возвращает настоящее имя. Работает и для офлайн/никогда не заходивших. */
    public void resetNickname(OfflinePlayer target) {
        PlayerData data = plugin.storage().get(target.getUniqueId());
        if (data != null) data.nickname(null);
        plugin.storage().save();

        Player online = target.getPlayer();
        if (online == null) return;
        String real = originalName(online);
        applyProfileName(online, real);
        Component plain = Component.text(real);
        online.displayName(plain);
        online.playerListName(plain);
        refreshForViewers(online);
    }

    private PlayerData dataOrCreate(UUID uuid) {
        PlayerData data = plugin.storage().get(uuid);
        if (data == null) {
            data = new PlayerData();
            plugin.storage().put(uuid, data);
        }
        return data;
    }

    private void apply(Player target, String rawText) {
        Component colored = Msg.color(rawText);
        String plainTag = PlainTextComponentSerializer.plainText().serialize(colored).trim();
        if (plainTag.isEmpty()) plainTag = originalName(target);
        if (plainTag.length() > MAX_LEN) plainTag = plainTag.substring(0, MAX_LEN);

        applyProfileName(target, plainTag);
        target.displayName(colored);
        target.playerListName(colored);
        refreshForViewers(target);
    }

    @SuppressWarnings("deprecation")
    private void applyProfileName(Player target, String name) {
        try {
            var profile = target.getPlayerProfile();
            profile.setName(name);
            target.setPlayerProfile(profile);
        } catch (Exception ex) {
            plugin.getLogger().warning("Не удалось сменить профиль ника для " + target.getName() + ": " + ex.getMessage());
        }
    }

    /** Отдельным клиентам нужно пересоздать сущность игрока, чтобы увидеть новый ник над головой/в табе. */
    private void refreshForViewers(Player target) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isOnline()) return;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) continue;
                viewer.hidePlayer(plugin, target);
            }
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isOnline()) return;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) continue;
                viewer.showPlayer(plugin, target);
            }
        }, 3L);
    }
}
