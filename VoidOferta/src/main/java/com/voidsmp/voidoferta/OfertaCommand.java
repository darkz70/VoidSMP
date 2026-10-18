package com.voidsmp.voidoferta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /oferta            — открыть книгу оферты (повторное чтение доступно всегда)
 * /oferta accept     — подтвердить оферту (эта же команда вызывается кнопкой в книге)
 * /oferta reload     — [admin] перечитать config.yml и oferta.txt
 * /oferta reset <ник>— [admin] сбросить подтверждение игрока
 * /oferta status [ник] — [admin] проверить, принял ли игрок оферту
 */
public final class OfertaCommand implements CommandExecutor, TabCompleter {

    private final VoidOfertaPlugin plugin;

    public OfertaCommand(VoidOfertaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Книгу оферты может открыть только игрок. Из консоли: /oferta status <ник>", NamedTextColor.RED));
                return true;
            }
            plugin.openOferta(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "accept" -> handleAccept(sender);
            case "reload" -> handleReload(sender);
            case "reset" -> handleReset(sender, args);
            case "status" -> handleStatus(sender, args);
            default -> sender.sendMessage(Component.text(
                    "Использование: /oferta [accept|reload|reset <ник>|status [ник]]", NamedTextColor.RED));
        }
        return true;
    }

    private void handleAccept(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Принять оферту может только игрок.", NamedTextColor.RED));
            return;
        }
        if (plugin.isPendingAuth(player)) {
            player.sendMessage(Component.text("Сначала войдите на сервер (пароль), затем принимайте оферту.", NamedTextColor.RED));
            return;
        }
        if (plugin.storage().hasAccepted(player.getUniqueId())) {
            player.sendMessage(Component.text("✔ Оферта № VDS-VOID-01 уже принята вами ранее. Повторное подтверждение не требуется.", NamedTextColor.GREEN));
            return;
        }
        plugin.storage().accept(player.getUniqueId(), player.getName());
        player.closeInventory();
        player.playSound(player.getLocation(), "minecraft:entity.player.levelup", 1.0f, 1.0f);
        player.sendMessage(Component.empty()
                .append(Component.text("✔ Оферта № VDS-VOID-01 принята! ", NamedTextColor.GREEN))
                .append(Component.text("Душа успешно передана OZO™ через VDS Corp и помещена в сундук на спавне. Сундук охраняет курица.", NamedTextColor.GRAY)));
        plugin.getLogger().info("Игрок " + player.getName() + " (" + player.getUniqueId() + ") принял оферту VDS-VOID-01");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("voidoferta.admin")) {
            sender.sendMessage(Component.text("Нет прав: voidoferta.admin", NamedTextColor.RED));
            return;
        }
        plugin.reloadOferta();
        sender.sendMessage(Component.text("✔ VoidOferta: config.yml и oferta.txt перечитаны. Приняло оферту игроков: "
                + plugin.storage().size(), NamedTextColor.GREEN));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("voidoferta.admin")) {
            sender.sendMessage(Component.text("Нет прав: voidoferta.admin", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /oferta reset <ник>", NamedTextColor.RED));
            return;
        }
        UUID uuid = resolveUuid(args[1]);
        if (uuid == null) {
            sender.sendMessage(Component.text("Игрок «" + args[1] + "» не найден среди принимавших оферту и не заходил на сервер.", NamedTextColor.RED));
            return;
        }
        boolean removed = plugin.storage().reset(uuid);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            plugin.openOferta(online);
            plugin.sendNotAcceptedMessage(online);
        }
        sender.sendMessage(Component.text(removed
                ? "✔ Подтверждение игрока " + args[1] + " сброшено. При следующем входе (или прямо сейчас, если он онлайн) он снова увидит книгу."
                : "Игрок " + args[1] + " и так ещё не принимал оферту.", NamedTextColor.YELLOW));
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (sender instanceof Player player) {
                report(sender, player.getName(), player.getUniqueId());
            } else if (!sender.hasPermission("voidoferta.admin")) {
                sender.sendMessage(Component.text("Использование: /oferta status <ник>", NamedTextColor.RED));
            } else {
                sender.sendMessage(Component.text("Использование: /oferta status <ник>. Всего приняло оферту: "
                        + plugin.storage().size(), NamedTextColor.YELLOW));
            }
            return;
        }
        if (!sender.hasPermission("voidoferta.admin") && !(sender instanceof Player p && p.getName().equalsIgnoreCase(args[1]))) {
            sender.sendMessage(Component.text("Проверять других игроков может только администратор (voidoferta.admin).", NamedTextColor.RED));
            return;
        }
        UUID uuid = resolveUuid(args[1]);
        if (uuid == null) {
            sender.sendMessage(Component.text("Игрок «" + args[1] + "» не найден.", NamedTextColor.RED));
            return;
        }
        report(sender, args[1], uuid);
    }

    private void report(CommandSender sender, String name, UUID uuid) {
        if (plugin.storage().hasAccepted(uuid)) {
            String at = plugin.storage().acceptedAt(uuid);
            sender.sendMessage(Component.text("✔ " + name + " принял(а) оферту VDS-VOID-01"
                    + (at == null || at.isBlank() ? "" : " — " + at), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("✘ " + name + " ещё НЕ принял(а) оферту VDS-VOID-01.", NamedTextColor.RED));
        }
    }

    @Nullable
    private UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        UUID stored = plugin.storage().findByName(name);
        if (stored != null) {
            return stored;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        return offline == null ? null : offline.getUniqueId();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = sender.hasPermission("voidoferta.admin")
                    ? List.of("accept", "reload", "reset", "status")
                    : List.of("accept", "status");
            for (String sub : subs) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    result.add(sub);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("status"))
                && sender.hasPermission("voidoferta.admin")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    result.add(player.getName());
                }
            }
        }
        return result;
    }
}
