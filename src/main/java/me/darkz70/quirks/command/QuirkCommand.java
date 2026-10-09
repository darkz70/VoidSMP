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
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** /quirk — управление причудами. Причуд можно несколько на одного игрока. */
public final class QuirkCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("set", "remove", "remove_admin", "remove_tmp", "info", "list", "reload", "notify", "item", "pack", "lab");
    private static final List<String> QUIRKS = List.of(
            "инженер", "кот", "бедрок", "топор", "скалк", "фермер", "земноводный", "паук",
            "engineer", "cat", "bedrock", "axe", "sculk", "farmer", "amphibian", "spider",
            "admin", "admin_pro", "админ", "админпро");

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
            case "remove_admin" -> handleRemoveAdmin(sender, args);
            case "remove_tmp" -> handleRemoveTmp(sender, args);
            case "info", "get" -> handleInfo(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            case "notify", "notifications" -> handleNotify(sender);
            case "item", "shard" -> handleItem(sender, args);
            case "pack", "resourcepack" -> handlePack(sender);
            case "lab", "labyrinth" -> handleLab(sender, args);
            default -> Msg.send(sender, "usage");
        }
        return true;
    }

    /** /quirk pack — показать ссылку на ресурспак с текстурой осколка. */
    private void handlePack(CommandSender sender) {
        Msg.send(sender, "pack-link",
                "%link%", "https://github.com/darkz70/VoidSMP/releases/download/ci-build/VoidQuirks-Pack.zip");
    }

    /** /quirk item <причуда> [уровень] [игрок] — выдать осколок души предметом. */
    private void handleItem(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "usage");
            return;
        }
        Quirk quirk = Quirk.byName(args[1]);
        if (quirk == null) {
            Msg.send(sender, "invalid-quirk");
            return;
        }
        int level = 1;
        if (args.length >= 3) {
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                Msg.send(sender, "invalid-level");
                return;
            }
            if (level < 1 || level > 3) {
                Msg.send(sender, "invalid-level");
                return;
            }
        }
        if (quirk == Quirk.ADMIN || quirk == Quirk.ADMIN_PRO) level = 1; // техпричуда всегда 1-го
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            Msg.send(sender, "usage");
            return;
        }
        if (target == null) {
            Msg.send(sender, "target-offline");
            return;
        }

        ItemStack shard = me.darkz70.quirks.mechanic.SoulShards.makeShard(quirk, level);
        var leftovers = target.getInventory().addItem(shard);
        leftovers.values().forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        Msg.send(sender, "shard-given",
                "%player%", target.getName(),
                "%quirk%", quirk.display(),
                "%level%", String.valueOf(level));
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
        if (quirk == Quirk.ADMIN || quirk == Quirk.ADMIN_PRO) level = 1; // техпричуда всегда 1-го

        plugin.quirks().assign(target, quirk, level);
        Msg.send(sender, "set-ok",
                "%player%", target.getName(),
                "%quirk%", quirk.display(),
                "%level%", String.valueOf(level));
        // скрытность админ-выдачи: получение причуды не анонсируем (спека 1.0)
    }

    /** /quirk remove_admin <игрок> — снять причуду Админ / АдминПро. */
    private void handleRemoveAdmin(CommandSender sender, String[] args) {
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
        boolean removed = plugin.quirks().remove(target, Quirk.ADMIN);
        removed |= plugin.quirks().remove(target, Quirk.ADMIN_PRO);
        if (removed) {
            Msg.send(sender, "removed", "%player%", target.getName());
        } else {
            Msg.send(sender, "info-none");
        }
    }

    /** /quirk remove_tmp <игрок> — снять только временные причуды. */
    private void handleRemoveTmp(CommandSender sender, String[] args) {
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
        // «временные причуды» — активный чит набора от зелья причуд: откатываем к бэкапу
        PlayerData data = plugin.storage().get(target.getUniqueId());
        boolean removed = data != null && data.tmpQuirks() != null;
        if (removed) {
            plugin.magic().restoreTmpQuirks(target);
        }
        if (removed) {
            Msg.send(sender, "removed", "%player%", target.getName());
        } else {
            Msg.send(sender, "info-none");
        }
    }

    /** /quirk remove <игрок> [причуда] — снимает одну причуду, а без её имени — весь набор. */
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

        boolean removed;
        if (args.length >= 3) {
            Quirk quirk = Quirk.byName(args[2]);
            if (quirk == null) {
                Msg.send(sender, "invalid-quirk");
                return;
            }
            removed = plugin.quirks().remove(target, quirk);
        } else {
            removed = plugin.quirks().removeAll(target);
        }

        if (removed) {
            Msg.send(sender, "removed", "%player%", target.getName());
            // скрытность: снятие админом не анонсируется (спека 1.0)
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
        if (data == null || data.isEmpty()) {
            Msg.send(sender, "info-none");
            return;
        }
        sender.sendMessage(Msg.comp("info-header", "%player%", target.getName()));
        for (Map.Entry<Quirk, Integer> entry : data.entries()) {
            sender.sendMessage(Msg.comp("info-line",
                    "%quirk%", entry.getKey().display(),
                    "%level%", String.valueOf(entry.getValue())));
        }
        sender.sendMessage(Msg.comp("info-notify",
                "%state%", data.notifications() ? "включены" : "выключены"));
    }

    private void handleList(CommandSender sender) {
        if (!admin(sender)) return;
        sender.sendMessage(Msg.comp("list-header"));
        boolean empty = true;
        for (Map.Entry<UUID, PlayerData> entry : plugin.storage().all().entrySet()) {
            PlayerData data = entry.getValue();
            if (data.isEmpty()) continue;
            empty = false;
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            List<String> parts = new ArrayList<>();
            for (Map.Entry<Quirk, Integer> quirkEntry : data.entries()) {
                if (quirkEntry.getKey() == Quirk.ADMIN || quirkEntry.getKey() == Quirk.ADMIN_PRO) {
                    continue; // техпричуды — вне списков
                }
                parts.add(quirkEntry.getKey().display() + " ур. " + quirkEntry.getValue());
            }
            sender.sendMessage(Msg.comp("list-line",
                    "%player%", name != null ? name : entry.getKey().toString().substring(0, 8),
                    "%quirks%", String.join(", ", parts)));
        }
        if (empty) {
            Msg.send(sender, "list-empty");
        }
    }

    /** /quirk notify — личный тумблер оповещений причуд. */
    private void handleNotify(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "usage");
            return;
        }
        PlayerData data = plugin.storage().get(player.getUniqueId());
        if (data == null) {
            data = new PlayerData();
            plugin.storage().put(player.getUniqueId(), data);
        }
        boolean enabled = !data.notifications();
        data.notifications(enabled);
        plugin.storage().save();
        Msg.send(player, enabled ? "notify-on" : "notify-off");
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
            addPlayers(out, args[2]);
        } else if (args.length == 2
                && List.of("set", "remove", "info", "get", "remove_admin", "remove_tmp")
                    .contains(args[0].toLowerCase(Locale.ROOT))) {
            addPlayers(out, args[1]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("item")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String quirk : QUIRKS) {
                if (quirk.startsWith(prefix)) out.add(quirk);
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove"))) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            for (String quirk : QUIRKS) {
                if (quirk.startsWith(prefix)) out.add(quirk);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("item")) {
            if ("1".startsWith(args[2])) out.add("1");
            if ("2".startsWith(args[2])) out.add("2");
            if ("3".startsWith(args[2])) out.add("3");
        } else if (args.length == 4 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("item"))) {
            if (args[0].equalsIgnoreCase("set")) {
                if ("1".startsWith(args[3])) out.add("1");
                if ("2".startsWith(args[3])) out.add("2");
                if ("3".startsWith(args[3])) out.add("3");
            } else {
                addPlayers(out, args[3]);
            }
        }
        return out;
    }

    private void addPlayers(List<String> out, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) out.add(player.getName());
        }
    }
}
