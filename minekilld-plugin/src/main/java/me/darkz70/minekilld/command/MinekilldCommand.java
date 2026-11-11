package me.darkz70.minekilld.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.darkz70.minekilld.MinekilldPlugin;
import me.darkz70.minekilld.monolith.MonolithManager;
import me.darkz70.minekilld.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class MinekilldCommand implements CommandExecutor, TabCompleter {

    private final MinekilldPlugin plugin;

    public MinekilldCommand(MinekilldPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("minekilld.admin")) {
            sender.sendMessage(Msg.color("&cНет прав (требуется minekilld.admin)."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> sendStatus(sender);
            case "eggtrigger" -> {
                int count = plugin.eggs().triggerEffectsPulse();
                sender.sendMessage(Msg.color("&d✦ Эффект Яйца дракона применён вручную. Игроков затронуто: &f" + count));
            }
            case "eggparticles" -> {
                int count = plugin.eggs().spawnParticlesPulse();
                sender.sendMessage(Msg.color("&d✦ Частицы Яйца дракона вызваны вручную. Точек: &f" + count));
            }
            case "eggrescan" -> {
                plugin.eggs().forceRescanLoadedAroundPlayers();
                sender.sendMessage(Msg.color("&d✦ Пересканированы чанки вокруг игроков. Яиц в базе: &f"
                        + plugin.eggs().trackedEggCount()));
            }
            case "monolith" -> handleMonolith(sender, args);
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(Msg.color("&a✔ Конфигурация MinekilldEvents перезагружена."));
            }
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void handleMonolith(CommandSender sender, String[] args) {
        var mm = plugin.monoliths();
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            List<MonolithManager.MonolithRecord> list = mm.monoliths();
            if (list.isEmpty()) {
                sender.sendMessage(Msg.color("&7Монолитов пока не появлялось."));
                return;
            }
            sender.sendMessage(Msg.color("&5✦ Монолиты (" + list.size() + "):"));
            int limit = Math.min(20, list.size());
            for (int i = list.size() - limit; i < list.size(); i++) {
                MonolithManager.MonolithRecord rec = list.get(i);
                sender.sendMessage(Msg.color("&7 - &f" + rec.world()
                        + " &d[" + rec.x() + ", " + rec.y() + ", " + rec.z() + "] &7(h=" + rec.height() + ")"));
            }
            return;
        }

        Player target = null;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Msg.color("&cИгрок &f" + args[1] + " &cне в сети."));
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        }

        if (target == null && Bukkit.getOnlinePlayers().isEmpty()) {
            sender.sendMessage(Msg.color("&cНа сервере нет игроков для выбора точки рядом."));
            return;
        }

        sender.sendMessage(Msg.color("&7Ищу подходящее место для монолита..."));
        mm.spawnNearRandomPlayer(target, loc -> {
            if (loc == null) {
                sender.sendMessage(Msg.color("&cНе удалось найти безопасную поверхность поблизости."));
            } else {
                sender.sendMessage(Msg.color("&5✦ Монолит воздвигнут в &f" + loc.getWorld().getName()
                        + " &d[" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]"));
            }
        });
    }

    private void sendStatus(CommandSender sender) {
        var eggs = plugin.eggs();
        var mon = plugin.monoliths();

        sender.sendMessage(Msg.color("&5&lMinekilldEvents — статус"));
        sender.sendMessage(Msg.color("&7Яйцо дракона: " + (eggs.isEnabled() ? "&aвкл" : "&cвыкл")
                + " &7| блоков в базе: &f" + eggs.trackedEggCount()
                + " &7| эффектов: &f" + eggs.eligibleEffectCount()
                + " &7| частиц: &f" + eggs.eligibleParticleCount()));
        if (eggs.isEnabled()) {
            sender.sendMessage(Msg.color("&7  До следующего эффекта: &f" + eggs.secondsUntilNextEffect()
                    + "с &7| до частиц: &f" + eggs.secondsUntilNextParticles() + "с"));
        }

        sender.sendMessage(Msg.color("&7Монолиты: " + (mon.isEnabled() ? "&aвкл" : "&cвыкл")
                + " &7| всего в мире: &f" + mon.monoliths().size()));
        if (mon.isEnabled()) {
            sender.sendMessage(Msg.color("&7  До следующего монолита: &f"
                    + mon.minutesUntilNextSpawn() + "м (" + mon.secondsUntilNextSpawn() + "с)"));
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Msg.color("&5&lMinekilldEvents &7— команды:"));
        sender.sendMessage(Msg.color("&d/" + label + " status &7— статус таймеров и объектов"));
        sender.sendMessage(Msg.color("&d/" + label + " eggtrigger &7— выдать эффект от яйца сейчас"));
        sender.sendMessage(Msg.color("&d/" + label + " eggparticles &7— заспавнить частицы яйца сейчас"));
        sender.sendMessage(Msg.color("&d/" + label + " eggrescan &7— пересканировать чанки на яйца"));
        sender.sendMessage(Msg.color("&d/" + label + " monolith [игрок|list] &7— заспавнить монолит / список"));
        sender.sendMessage(Msg.color("&d/" + label + " reload &7— перезагрузить config.yml"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("minekilld.admin")) return out;
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : List.of("status", "eggtrigger", "eggparticles", "eggrescan", "monolith", "reload")) {
                if (sub.startsWith(prefix)) out.add(sub);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("monolith")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if ("list".startsWith(prefix)) out.add("list");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
