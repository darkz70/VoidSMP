package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.List;
import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /gaid — админ-(OP)-гайд: меню с книгами описаний и каталогом предметов + смена ника игрока. */
public final class GaidCommand implements CommandExecutor, TabCompleter {

    private final VoidQuirksPlugin plugin;

    public GaidCommand(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        if (!player.hasPermission("quirks.admin")) {
            me.darkz70.quirks.util.Msg.send(player, "no-permission");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("phrase")) {
            String text = args.length >= 2
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                    : "";
            plugin.gaidFx().skyPhraseFromCommand(player, text);
            player.sendMessage(me.darkz70.quirks.util.Msg.color(
                    text.isBlank()
                            ? "&5Небесное послание: повтор последней фразы."
                            : "&5Небесное послание: &f" + text));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("nick")) {
            handleNick(player, args);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("lore")) {
            handleLore(player, args);
            return true;
        }
        plugin.gaidMenus().openRoot(player);
        return true;
    }

    /** /gaid lore [on|off] — глобальный таб-баннер «VOID SMP» на время ролевого Лора. */
    private void handleLore(Player admin, String[] args) {
        boolean next;
        if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("on")) {
                next = true;
            } else if (args[1].equalsIgnoreCase("off")) {
                next = false;
            } else {
                admin.sendMessage(me.darkz70.quirks.util.Msg.color("&e/gaid lore &8| &e/gaid lore on &8| &e/gaid lore off"));
                return;
            }
        } else {
            next = !plugin.loreTab().enabled();
        }
        plugin.loreTab().setEnabled(next);
        admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                next ? "&5✦ Лор-таб &aВКЛ&5: у всех в таб-листе баннер «VOID SMP»."
                        : "&5✦ Лор-таб &cВЫКЛ&5: таб-лист вернулся к обычному виду."));
    }

    private void handleNick(Player admin, String[] args) {
        if (args.length < 2) {
            admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                    "&e/gaid nick <игрок> <новый ник> &8| &e/gaid nick <игрок> reset"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            me.darkz70.quirks.util.Msg.send(admin, "target-offline");
            return;
        }
        if (args.length < 3) {
            String current = plugin.nicknames().currentNickname(target);
            admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                    "&7Текущий ник &f" + target.getName() + "&7: "
                            + (current == null ? "&8— (настоящее имя)" : "&d" + current)));
            admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                    "&e/gaid nick " + target.getName() + " <новый ник> &8| &e/gaid nick " + target.getName() + " reset"));
            return;
        }
        if (args[2].equalsIgnoreCase("reset")) {
            plugin.nicknames().resetNickname(target);
            admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                    "&aНик игрока &f" + target.getName() + " &aсброшен — показывается настоящее имя."));
            return;
        }
        String newNick = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        boolean ok = plugin.nicknames().setNickname(target, newNick);
        if (!ok) {
            admin.sendMessage(me.darkz70.quirks.util.Msg.color("&cНик не может быть пустым."));
            return;
        }
        admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                "&aИгроку &f" + target.getName() + " &aназначен ник: " + newNick));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("quirks.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : new String[]{"phrase", "nick", "lore"}) {
                if (sub.startsWith(args[0].toLowerCase())) out.add(sub);
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("nick")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(online.getName());
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("nick")) {
            if ("reset".startsWith(args[2].toLowerCase())) out.add("reset");
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lore")) {
            for (String sub : new String[]{"on", "off"}) {
                if (sub.startsWith(args[1].toLowerCase())) out.add(sub);
            }
            return out;
        }
        return out;
    }
}
