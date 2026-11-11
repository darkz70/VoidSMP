package me.darkz70.quirks.command;

import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /mage (/маг) — для причуды «Маг»: открывает меню прямого выбора стихии. */
public final class MageCommand implements CommandExecutor {

    private final VoidQuirksPlugin plugin;

    public MageCommand(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        if (!plugin.mageMenu().hasAccess(player)) {
            Msg.send(player, "mage-need-quirk");
            return true;
        }
        plugin.mageMenu().open(player);
        return true;
    }
}
