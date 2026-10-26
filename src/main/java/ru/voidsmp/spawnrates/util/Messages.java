package ru.voidsmp.spawnrates.util;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Сообщения из {@code messages.yml} в формате MiniMessage.
 *
 * <p>Файл пользователя накладывается поверх дефолтов из jar, поэтому новая
 * версия плагина может добавлять ключи — они подтянутся сами, а свои правки
 * админ не потеряет. Для /spawnrate reload объект просто пересоздаётся.</p>
 */
public final class Messages {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final YamlConfiguration user;
    private final YamlConfiguration defaults;
    private final String prefix;

    private Messages(YamlConfiguration user, YamlConfiguration defaults, String prefix) {
        this.user = user;
        this.defaults = defaults;
        this.prefix = prefix == null ? "" : prefix;
    }

    public static Messages load(JavaPlugin plugin) {
        File folder = plugin.getDataFolder();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать каталог " + folder.getAbsolutePath());
        }
        File file = new File(folder, "messages.yml");

        YamlConfiguration defaults = new YamlConfiguration();
        try (Reader reader = plugin.getResource("messages.yml") == null ? null
                : new InputStreamReader(plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            if (reader != null) {
                defaults = YamlConfiguration.loadConfiguration(reader);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Не удалось прочитать встроенные сообщения: " + ex.getMessage());
        }

        if (!file.isFile() && plugin.getResource("messages.yml") != null) {
            try {
                plugin.saveResource("messages.yml", false);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Не удалось создать messages.yml: " + ex.getMessage());
            }
        }

        YamlConfiguration user = new YamlConfiguration();
        if (file.isFile()) {
            YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
            for (String key : loaded.getKeys(true)) {
                if (!loaded.isConfigurationSection(key)) {
                    user.set(key, loaded.get(key));
                }
            }
        }

        return new Messages(user, defaults, userString(user, defaults, "prefix"));
    }

    private static String userString(YamlConfiguration user, YamlConfiguration defaults, String key) {
        String value = user.getString(key);
        return value != null ? value : defaults.getString(key);
    }

    private String raw(String key) {
        String value = user.getString(key);
        return value != null ? value : defaults.getString(key);
    }

    private List<String> rawList(String key) {
        List<String> value = user.getStringList(key);
        if (value.isEmpty()) {
            value = defaults.getStringList(key);
        }
        return value;
    }

    public boolean has(String key) {
        return raw(key) != null || !rawList(key).isEmpty();
    }

    public String prefix() {
        return prefix;
    }

    /** Текст сообщения с подставленными плейсхолдерами, без префикса. */
    public String text(String key, Map<String, ?> args) {
        String value = raw(key);
        if (value == null) {
            return "<red>" + key + "</red>";
        }
        return apply(value, args);
    }

    public Component format(String key, Map<String, ?> args) {
        return parse(text(key, args));
    }

    /** Строка с префиксом — используется командой, когда нужен один Component. */
    public Component prefixed(String key, Map<String, ?> args) {
        String line = text(key, args);
        if (!prefix.isBlank()) {
            line = prefix + " " + line;
        }
        return parse(line);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, ?> args) {
        sender.sendMessage(prefixed(key, args));
    }

    /** Многострочное сообщение (справка, info): префикс только на первой строке. */
    public void sendLines(CommandSender sender, String key, Map<String, ?> args) {
        List<String> lines = rawList(key);
        for (int index = 0; index < lines.size(); index++) {
            String line = apply(lines.get(index), args);
            if (index == 0 && !prefix.isBlank()) {
                line = prefix + " " + line;
            }
            sender.sendMessage(parse(line));
        }
    }

    public List<String> lines(String key, Map<String, ?> args) {
        List<String> out = new ArrayList<>();
        for (String line : rawList(key)) {
            out.add(apply(line, args));
        }
        return out;
    }

    public Component parse(String text) {
        try {
            return miniMessage.deserialize(text);
        } catch (Exception ex) {
            // Кривой тег в пользовательском сообщении не должен ломать команду.
            return Component.text(text);
        }
    }

    private static String apply(String text, Map<String, ?> args) {
        if (args == null || args.isEmpty() || text.indexOf('{') < 0) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, ?> entry : args.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
