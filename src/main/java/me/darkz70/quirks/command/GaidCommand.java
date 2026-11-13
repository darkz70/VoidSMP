package me.darkz70.quirks.command;

import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /gaid — админ-(OP)-гайд: меню с книгами описаний и каталогом предметов. */
public final class GaidCommand implements CommandExecutor {

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
        plugin.gaidMenus().openRoot(player);
        return true;
    }
}
