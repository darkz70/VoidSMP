package ru.voidsmp.void2fa;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles {@code /2faadmin}: reload, status, reset, enable, disable.
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "status", "reset", "enable", "disable");

    private final Void2FAPlugin plugin;
    private final AuthManager auth;
    private final ConfigManager config;

    public AdminCommand(Void2FAPlugin plugin, AuthManager auth, ConfigManager config) {
        this.plugin = plugin;
        this.auth = auth;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("void2fa.admin")) {
            sender.sendMessage(config.msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(config.msg("help-admin"));
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) {
            plugin.reloadEverything();
            sender.sendMessage(config.msg("admin-reloaded"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(config.msg("help-admin"));
            return true;
        }
        UUID id = resolve(args[1]);
        if (id == null) {
            sender.sendMessage(config.msg("admin-player-not-found"));
            return true;
        }
        PlayerDataManager data = plugin.getDataManager();
        String name = data.getName(id);
        if (name == null || name.isEmpty()) {
            name = args[1];
        }

        switch (sub) {
            case "status": {
                boolean linked = data.isLinked(id);
                boolean enabled = data.isEnabled(id);
                String state = linked ? (enabled ? "вкл" : "выкл") : "не привязано";
                String link = linked ? "привязан" : "не привязан";
                sender.sendMessage(config.msg("admin-status", "player", name, "state", state, "link", link));
                break;
            }
            case "reset":
                auth.adminReset(id);
                sender.sendMessage(config.msg("admin-reset", "player", name));
                break;
            case "enable":
                if (!data.isLinked(id)) {
                    sender.sendMessage(config.msg("admin-not-linked", "player", name));
                } else {
                    data.setEnabled(id, true);
                    sender.sendMessage(config.msg("admin-enabled", "player", name));
                }
                break;
            case "disable":
                data.setEnabled(id, false);
                sender.sendMessage(config.msg("admin-disabled", "player", name));
                break;
            default:
                sender.sendMessage(config.msg("help-admin"));
                break;
        }
        return true;
    }

    /** Resolves a target UUID from an online player or stored data (no web lookups). */
    private UUID resolve(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) {
            return online.getUniqueId();
        }
        return plugin.getDataManager().findByName(name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!sender.hasPermission("void2fa.admin")) {
            return result;
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    result.add(sub);
                }
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            String prefix = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    result.add(p.getName());
                }
            }
        }
        return result;
    }
}
