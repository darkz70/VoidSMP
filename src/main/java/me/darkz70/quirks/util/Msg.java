package me.darkz70.quirks.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Сообщения из config.yml с &-цветами и простыми %-плейсхолдерами. */
public final class Msg {

    private static FileConfiguration cfg;

    private Msg() {}

    public static void init(JavaPlugin plugin) {
        cfg = plugin.getConfig();
    }

    /** Компонент из messages.<path>. repl — пары "ключ", "значение". */
    public static Component comp(String path, String... repl) {
        String raw = cfg.getString("messages." + path, path);
        raw = raw.replace("%prefix%", cfg.getString("prefix", ""));
        for (int i = 0; i + 1 < repl.length; i += 2) {
            raw = raw.replace(repl[i], repl[i + 1]);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    /** Окраска произвольной строки &-кодами. */
    public static Component color(String input) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(input);
    }

    public static void send(CommandSender to, String path, String... repl) {
        to.sendMessage(comp(path, repl));
    }
}
