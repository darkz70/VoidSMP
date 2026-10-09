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
        plugin.gaidMenus().openRoot(player);
        return true;
    }
}
