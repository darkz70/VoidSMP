package me.darkz70.quirks.lore;

import me.darkz70.quirks.VoidQuirksPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Глобальный переключатель «Лор»: /gaid lore on|off.
 * Подменяет ШАПКУ И ПОДВАЛ таб-листа (НЕ ник игрока!) на стилизованный
 * баннер «VOID SMP» — часть текста магическая (&k), поэтому клиент сам
 * хаотично «глючит» символами — без ресурспака и без повторной отправки
 * пакетов с сервера.
 */
public final class LoreTabManager {

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

    private Component glitch(String text, NamedTextColor color) {
        return Component.text(text, color).decorate(TextDecoration.OBFUSCATED);
    }

    private Component header() {
        return Component.empty()
                .append(Component.text("▓▓▓ ", NamedTextColor.DARK_RED))
                .append(glitch("#", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" V", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
                .append(Component.text("O", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD))
                .append(Component.text("I", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
                .append(Component.text("D", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD))
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(glitch("#", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" ▓▓▓", NamedTextColor.DARK_RED))
                .append(Component.text("\n"))
                .append(Component.text("Лор активен", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
    }

    private Component footer() {
        return Component.empty()
                .append(Component.text("[", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD))
                .append(Component.text("S", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
                .append(Component.text("M", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD))
                .append(Component.text("P", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
                .append(Component.text("]", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
    }
}
