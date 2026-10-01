package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** /quirk — управление причудами. */
public final class QuirkCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("set", "remove", "info", "list", "reload", "lab");
    private static final List<String> QUIRKS = List.of(
            "инженер", "кот", "бедрок", "топор", "скалк",
            "engineer", "cat", "bedrock", "axe", "sculk");

    private final VoidQuirksPlugin plugin;

    public QuirkCommand(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            Msg.send(sender, "usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> handleSet(sender, args);
            case "remove", "clear" -> handleRemove(sender, args);
            case "info", "get" -> handleInfo(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            case "lab", "labyrinth" -> handleLab(sender, args);
            default -> Msg.send(sender, "usage");
        }
        return true;
    }

    /** /quirk lab <create|tp> — мир лабиринта душ. */
    private void handleLab(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> plugin.labyrinth().create(sender);
            case "tp" -> {
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    target = null;
                }
                plugin.labyrinth().teleport(sender, target);
            }
            default -> Msg.send(sender, "usage");
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 3) {
            Msg.send(sender, "usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Msg.send(sender, "target-offline");
            return;
        }
        Quirk quirk = Quirk.byName(args[2]);
        if (quirk == null) {
            Msg.send(sender, "invalid-quirk");
            return;
        }
        int level = 1;
        if (args.length >= 4) {
            try {
                level = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                Msg.send(sender, "invalid-level");
                return;
            }
            if (level < 1 || level > 3) {
                Msg.send(sender, "invalid-level");
                return;
            }
        }

        plugin.quirks().assign(target, quirk, level);
        Msg.send(sender, "set-ok",
                "%player%", target.getName(),
                "%quirk%", quirk.display(),
                "%level%", String.valueOf(level));
        Msg.send(target, "you-got-quirk",
                "%quirk%", quirk.display(),
                "%level%", String.valueOf(level));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Msg.send(sender, "target-offline");
            return;
        }
        if (plugin.quirks().remove(target)) {
            Msg.send(sender, "removed", "%player%", target.getName());
            Msg.send(target, "you-lost-quirk");
        } else {
            Msg.send(sender, "info-none");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!sender.equals(Bukkit.getPlayerExact(args[1])) && !admin(sender)) return;
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Msg.send(sender, "target-offline");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            Msg.send(sender, "usage");
            return;
        }

        PlayerData data = plugin.storage().get(target.getUniqueId());
        if (data == null) {
            Msg.send(sender, "info-none");
            return;
        }
        sender.sendMessage(Msg.comp("info-header", "%player%", target.getName()));
        sender.sendMessage(Msg.comp("info-line",
                "%quirk%", data.quirk().display(),
                "%level%", String.valueOf(data.level())));
    }

    private void handleList(CommandSender sender) {
        if (!admin(sender)) return;
        sender.sendMessage(Msg.comp("list-header"));
        boolean empty = true;
        for (Map.Entry<UUID, PlayerData> entry : plugin.storage().all().entrySet()) {
            empty = false;
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            sender.sendMessage(Msg.comp("list-line",
                    "%player%", name != null ? name : entry.getKey().toString().substring(0, 8),
                    "%quirk%", entry.getValue().quirk().display(),
                    "%level%", String.valueOf(entry.getValue().level())));
        }
        if (empty) {
            Msg.send(sender, "list-empty");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.reloadPluginConfig();
        Msg.send(sender, "reload-ok");
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("quirks.admin")) return true;
        Msg.send(sender, "no-permission");
        return false;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(sub);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("lab")) {
            if ("create".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("create");
            if ("tp".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("tp");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("lab") && args[1].equalsIgnoreCase("tp")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(player.getName());
            }
        } else if (args.length == 2 && List.of("set", "remove", "info", "get").contains(args[0].toLowerCase(Locale.ROOT))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(player.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            for (String quirk : QUIRKS) {
                if (quirk.startsWith(prefix)) out.add(quirk);
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            if ("1".startsWith(args[3])) out.add("1");
            if ("2".startsWith(args[3])) out.add("2");
            if ("3".startsWith(args[3])) out.add("3");
        }
        return out;
    }
}
