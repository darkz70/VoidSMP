package ru.voidsmp.void2fa;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles {@code /2fa} (and its aliases) plus the quick {@code /login} command.
 */
public final class AuthCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("link", "unlink", "on", "off", "code", "resend", "status", "help");

    private final Void2FAPlugin plugin;
    private final AuthManager auth;
    private final ConfigManager config;

    public AuthCommand(Void2FAPlugin plugin, AuthManager auth, ConfigManager config) {
        this.plugin = plugin;
        this.auth = auth;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.msg("player-only"));
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("void2fa.use")) {
            player.sendMessage(config.msg("no-permission"));
            return true;
        }

        if (command.getName().equalsIgnoreCase("login")) {
            if (args.length < 1) {
                player.sendMessage(config.msg("usage-login"));
            } else {
                auth.submitCode(player, args[0]);
            }
            return true;
        }

        if (args.length == 0) {
            sendStatus(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "link":
                auth.startLink(player);
                break;
            case "unlink":
                auth.unlinkByPlayer(player);
                break;
            case "on":
                auth.setEnabled(player, true);
                break;
            case "off":
                auth.setEnabled(player, false);
                break;
            case "code":
                if (args.length < 2) {
                    player.sendMessage(config.msg("usage-login"));
                } else {
                    auth.submitCode(player, args[1]);
                }
                break;
            case "resend":
                auth.resend(player);
                break;
            case "status":
                sendStatus(player);
                break;
            case "help":
                player.sendMessage(config.msg("help-player"));
                break;
            default:
                player.sendMessage(config.msg("unknown-subcommand"));
                break;
        }
        return true;
    }

    private void sendStatus(Player player) {
        UUID id = player.getUniqueId();
        PlayerDataManager data = plugin.getDataManager();
        boolean linked = data.isLinked(id);
        boolean enabled = data.isEnabled(id);
        String state = !linked ? "не настроена" : (enabled ? "включена" : "выключена");
        String link = linked ? "привязан" : "не привязан";
        player.sendMessage(config.msg("status-self", "state", state, "link", link));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("2fa") && args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    result.add(sub);
                }
            }
        }
        return result;
    }
}
