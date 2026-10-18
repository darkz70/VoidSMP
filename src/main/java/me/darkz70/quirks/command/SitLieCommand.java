package me.darkz70.quirks.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import me.darkz70.quirks.VoidQuirksPlugin;

/** /sit и /lie: сесть/лечь, Shift — встать. */
public final class SitLieCommand implements CommandExecutor {

    private final VoidQuirksPlugin plugin;
    private final boolean sit;

    public SitLieCommand(VoidQuirksPlugin plugin, boolean sit) {
        this.plugin = plugin;
        this.sit = sit;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            me.darkz70.quirks.util.Msg.send(sender, "gaid-player-only");
            return true;
        }
        if (sit) plugin.sitLie().sit(player);
        else plugin.sitLie().lie(player);
        return true;
    }
}
