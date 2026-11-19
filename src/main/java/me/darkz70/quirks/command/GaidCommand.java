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
        if (!me.darkz70.quirks.util.Perms.isAdmin(player)) {
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
        if (args.length >= 1 && args[0].equalsIgnoreCase("relic")) {
            handleRelic(player, args);
            return true;
        }
        plugin.gaidMenus().openRoot(player);
        return true;
    }

    private static final List<String> RELIC_IDS = List.of("cover", "voidblock", "lavacore", "prism");

    /** /gaid relic <cover|voidblock|lavacore|prism> [игрок] — выдать реликвию #98. */
    private void handleRelic(Player admin, String[] args) {
        if (args.length < 2) {
            admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                    "&e/gaid relic <cover|voidblock|lavacore|prism> [игрок]"));
            return;
        }
        Player target = admin;
        if (args.length >= 3) {
            Player found = Bukkit.getPlayerExact(args[2]);
            if (found == null) {
                me.darkz70.quirks.util.Msg.send(admin, "target-offline");
                return;
            }
            target = found;
        }
        org.bukkit.inventory.ItemStack relic = switch (args[1].toLowerCase()) {
            case "cover" -> me.darkz70.quirks.mechanic.RelicItems.makeWorldbuilderCover();
            case "voidblock" -> me.darkz70.quirks.mechanic.RelicItems.makeVoidBlock();
            case "lavacore" -> me.darkz70.quirks.mechanic.RelicItems.makeLavaCore();
            case "prism" -> me.darkz70.quirks.mechanic.RelicItems.makePrism();
            default -> null;
        };
        if (relic == null) {
            admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                    "&cНеизвестная реликвия. Варианты: cover, voidblock, lavacore, prism."));
            return;
        }
        var leftover = target.getInventory().addItem(relic);
        leftover.values().forEach(it -> target.getWorld().dropItemNaturally(target.getLocation(), it));
        String plainName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(relic.getItemMeta().displayName());
        admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                "&aИгроку &f" + target.getName() + " &aвыдана реликвия: &d" + plainName));
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
        if (next && isTabPluginBlockingHeaderFooter()) {
            admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                    "&c⚠ На сервере обнаружен плагин TAB (NEZNAMY) с включённым header-footer."));
            admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                    "&cОн &nблокирует&c header/footer от ВСЕХ других плагинов (так задумано самим TAB,"));
            admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                    "&cсм. его wiki), поэтому баннер этой командой скорее всего &nне появится&c."));
            admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                    "&7Пропиши баннер прямо в plugins/TAB/config.yml → header-footer → header, строкой:"));
            admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                    "&f  - \"<font:voidquirks:logo>\\uF001</font>\"  &7(именно в двойных кавычках!)"));
            admin.sendMessage(me.darkz70.quirks.util.Msg.color(
                    "&7после этого — &f/tab reload &7(перезапуск сервера не нужен)."));
        }
    }

    /** TAB (NEZNAMY) с включённым header-footer молча блокирует sendPlayerListHeaderAndFooter() от других плагинов. */
    private boolean isTabPluginBlockingHeaderFooter() {
        org.bukkit.plugin.Plugin tab = Bukkit.getPluginManager().getPlugin("TAB");
        return tab != null && tab.isEnabled();
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
        if (!me.darkz70.quirks.util.Perms.isAdmin(sender)) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : new String[]{"phrase", "nick", "lore", "relic"}) {
                if (sub.startsWith(args[0].toLowerCase())) out.add(sub);
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("relic")) {
            for (String id : RELIC_IDS) {
                if (id.startsWith(args[1].toLowerCase())) out.add(id);
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("relic")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(args[2].toLowerCase())) out.add(online.getName());
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
