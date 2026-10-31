package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /ivent — управление ивентом «Недрочабрь» (световые мечи). Только для OP-админов.
 *   /ivent open <игрок>          — открыть игроку меню выбора стороны
 *   /ivent give <игрок> light|dark — выдать сторону и клинок
 *   /ivent reset <игрок>         — забрать клинок и обнулить сторону
 *   /ivent status [игрок]        — показать сторону и дату конца ивента
 */
public final class IventCommand implements CommandExecutor, TabCompleter {

    private final me.darkz70.quirks.VoidQuirksPlugin plugin;

    public IventCommand(me.darkz70.quirks.VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("quirks.admin")) {
            Msg.send(sender, "no-permission");
            return true;
        }
        var sabers = plugin.sabers();
        if (args.length == 0) {
            sender.sendMessage(Msg.color("&d/ivent open <игрок> &7— открыть выбор стороны"));
            sender.sendMessage(Msg.color("&d/ivent give <игрок> <light|dark> &7— выдать сторону и клинок"));
            sender.sendMessage(Msg.color("&d/ivent reset <игрок> &7— забрать клинок, обнулить сторону"));
            sender.sendMessage(Msg.color("&d/ivent status [игрок] &7— сторона и дата конца ивента"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "open" -> {
                Player target = require(sender, args);
                if (target == null) return true;
                sabers.purgeNow(target);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (target.isOnline()) sabers.openChoice(target);
                });
                sender.sendMessage(Msg.color("&dИгроку &f" + target.getName() + " &dоткрыт выбор стороны."));
            }
            case "give" -> {
                Player target = require(sender, args);
                if (target == null) return true;
                if (args.length < 3 || !(args[2].equalsIgnoreCase("light") || args[2].equalsIgnoreCase("dark"))) {
                    sender.sendMessage(Msg.color("&cУкажи сторону: light или dark"));
                    return true;
                }
                sabers.forceSide(target, args[2].equalsIgnoreCase("dark"));
                sender.sendMessage(Msg.color("&dИгроку &f" + target.getName() + " &dвыдан клинок &f"
                        + (args[2].equalsIgnoreCase("dark") ? "ТЁМНОЙ" : "СВЕТЛОЙ") + " &dстороны."));
            }
            case "reset" -> {
                Player target = require(sender, args);
                if (target == null) return true;
                sabers.reset(target);
                sender.sendMessage(Msg.color("&dИгрок &f" + target.getName() + " &dобнулён: клинок забран, сторона снята."));
            }
            case "status" -> {
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayerExact(args[1]);
                } else if (sender instanceof Player self) {
                    target = self;
                } else {
                    target = null;
                }
                if (target == null) {
                    sender.sendMessage(Msg.color("&cИгрок не в сети."));
                    return true;
                }
                String side = sabers.side(target);
                sender.sendMessage(Msg.color("&d" + target.getName() + "&7: сторона = &f"
                        + (side.isEmpty() ? "не выбрана" : side.equals("dark") ? "тёмная" : "светлая")
                        + "&7, конец ивента: &f" + sabers.untilDateText()));
            }
            default -> sender.sendMessage(Msg.color("&7Неизвестно. Просто /ivent для помощи."));
        }
        return true;
    }

    private Player require(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.color("&cУкажи игрока."));
            return null;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) sender.sendMessage(Msg.color("&cИгрок не в сети."));
        return target;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("quirks.admin")) return out;
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : List.of("open", "give", "reset", "status")) {
                if (sub.startsWith(prefix)) out.add(sub);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            for (String side : List.of("light", "dark")) {
                if (side.startsWith(prefix)) out.add(side);
            }
        }
        return out;
    }

}
