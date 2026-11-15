package me.darkz70.quirks.lore;

import me.darkz70.quirks.VoidQuirksPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Глобальный переключатель «Лор»: /gaid lore on|off.
 * Подменяет ШАПКУ И ПОДВАЛ таб-листа (НЕ ник игрока!) на РЕАЛЬНОЕ лого
 * VOID SMP (присланную картинку), вырезанное на две части — «VOID» (шапка)
 * и «SMP» (подвал) — и зашитое кастомным битмап-шрифтом ресурспака
 * (assets/voidquirks/font/logo.json, символы \uF001/\uF002). Игроку нужен
 * ресурспак сервера — он и так раздаётся автоматически при входе
 * (см. {@link me.darkz70.quirks.mechanic.ResourcePackServer}).
 */
public final class LoreTabManager {

    private static final Key LOGO_FONT = Key.key("voidquirks", "logo");
    private static final String VOID_GLYPH = "\uF001";
    private static final String SMP_GLYPH = "\uF002";

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
            player.sendPlayerListHeaderAndFooter(header(), footer());
        } else {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
    }

    private Component header() {
        return Component.text(VOID_GLYPH, NamedTextColor.WHITE)
                .font(LOGO_FONT)
                .append(Component.text("\n"))
                .append(Component.text("Лор активен", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
    }

    private Component footer() {
        return Component.text(SMP_GLYPH, NamedTextColor.WHITE).font(LOGO_FONT);
    }
}
