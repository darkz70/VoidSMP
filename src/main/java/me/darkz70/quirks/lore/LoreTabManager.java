package me.darkz70.quirks.lore;

import me.darkz70.quirks.VoidQuirksPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Глобальный переключатель «Лор»: /gaid lore on|off.
 * Подменяет ШАПКУ таб-листа (НЕ ник игрока!) на РЕАЛЬНОЕ присланное лого
 * VOID SMP — одним целым изображением (не разрезанным), зашитым кастомным
 * битмап-шрифтом ресурспака (assets/voidquirks/font/logo.json, символ
 * \uF001, текстура textures/font/logo.png). Игроку нужен ресурспак
 * сервера — он и так раздаётся автоматически при входе
 * (см. {@link me.darkz70.quirks.mechanic.ResourcePackServer}).
 */
public final class LoreTabManager {

    private static final Key LOGO_FONT = Key.key("voidquirks", "logo");
    private static final String LOGO_GLYPH = "\uF001";

    private final VoidQuirksPlugin plugin;

    public LoreTabManager(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("lore.tab-enabled", false);
    }

    /** Включить/выключить «Лор»-таб сразу для ВСЕХ игроков онлайн (и сохранить для новых входов). */
    public void setEnabled(boolean enabled) {
        plugin.getConfig().set("lore.tab-enabled", enabled);
        plugin.saveConfig();
        for (Player online : Bukkit.getOnlinePlayers()) {
            apply(online);
        }
    }

    /** Применить (или снять) баннер конкретному игроку — используется и при входе на сервер. */
    public void apply(Player player) {
        if (enabled()) {
            player.sendPlayerListHeaderAndFooter(header(), Component.empty());
        } else {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
    }

    private Component header() {
        return Component.text(LOGO_GLYPH, NamedTextColor.WHITE).font(LOGO_FONT);
    }
}
