package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.magic.Element;
import me.darkz70.quirks.magic.MagicListener;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * /magic            — меню выбора заклинания (также открывается F с фокус-предметом).
 * /magic book <стихия> [игрок] — выдать книгу стихии (админ).
 * /magic set <игрок> element <стихия|none> | level <N> | mana <N> (админ).
 * /magic brew <тег> [игрок] — выдать зелье дерева (админ).
 */
public final class MagicCommand implements TabExecutor {

    private final VoidQuirksPlugin plugin;
    private final MagicListener magicListener;

    public MagicCommand(VoidQuirksPlugin plugin, MagicListener magicListener) {
        this.plugin = plugin;
        this.magicListener = magicListener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                Msg.send(sender, "magic-usage");
                return true;
            }
            Element element = plugin.magic().elementOf(player);
            if (element == null) {
                Msg.send(player, "magic-no-mage");
                return true;
            }
            magicListener.openMenu(player, element);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "book" -> {
                if (!admin(sender)) return true;
                if (args.length < 2) {
                    Msg.send(sender, "magic-usage");
                    return true;
                }
                Element element = Element.byName(args[1]);
                if (element == null) {
                    Msg.send(sender, "magic-bad-element");
                    return true;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2])
                        : sender instanceof Player p ? p : null;
                if (target == null) {
                    Msg.send(sender, "target-offline");
                    return true;
                }
                give(target, me.darkz70.quirks.listener.CraftListener.makeElementBook(element));
                Msg.send(sender, "magic-book-given", "%element%", element.display(),
                        "%player%", target.getName());
            }
            case "upbook" -> {
                if (!admin(sender)) return true;
                if (args.length < 2) {
                    Msg.send(sender, "magic-usage");
                    return true;
                }
                int tier;
                try {
                    tier = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    Msg.send(sender, "magic-usage");
                    return true;
                }
                if (tier < 1 || tier > 5) {
                    Msg.send(sender, "magic-usage");
                    return true;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2])
                        : sender instanceof Player p ? p : null;
                if (target == null) {
                    Msg.send(sender, "target-offline");
                    return true;
                }
                give(target, me.darkz70.quirks.listener.CraftListener.makeUpgradeBook(tier));
                Msg.send(sender, "magic-book-given", "%element%", "ур." + tier,
                        "%player%", target.getName());
            }
            case "brew" -> {
                if (!admin(sender)) return true;
                if (args.length < 2) {
                    Msg.send(sender, "magic-usage");
                    return true;
                }
                ItemStack potion = me.darkz70.quirks.mechanic.BrewTree.byTag(args[1],
                        sender instanceof Player p ? p : null);
                if (potion == null) {
                    Msg.send(sender, "magic-bad-brew");
                    return true;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2])
                        : sender instanceof Player p ? p : null;
                if (target == null) {
                    Msg.send(sender, "target-offline");
                    return true;
                }
                give(target, potion);
                Msg.send(sender, "magic-brew-given", "%brew%", args[1], "%player%", target.getName());
            }
            case "set" -> {
                if (!admin(sender)) return true;
                handleSet(sender, args);
            }
            default -> Msg.send(sender, "magic-usage");
        }
        return true;
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            Msg.send(sender, "magic-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Msg.send(sender, "target-offline");
            return;
        }
        PlayerData data = plugin.magic().dataOrCreate(target);
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "element" -> {
                if (args[3].equalsIgnoreCase("none") || args[3].equalsIgnoreCase("нет")) {
                    data.magicElement(null);
                    data.selectedSpell(0);
                } else {
                    Element element = Element.byName(args[3]);
                    if (element == null) {
                        Msg.send(sender, "magic-bad-element");
                        return;
                    }
                    data.magicElement(element.id());
                }
            }
            case "level" -> {
                try {
                    int level = Integer.parseInt(args[3]);
                    data.magicLevel(Math.max(1, level));
                } catch (NumberFormatException ex) {
                    Msg.send(sender, "magic-usage");
                    return;
                }
            }
            case "mana" -> {
                try {
                    data.mana(Double.parseDouble(args[3]));
                } catch (NumberFormatException ex) {
                    Msg.send(sender, "magic-usage");
                    return;
                }
            }
            default -> {
                Msg.send(sender, "magic-usage");
                return;
            }
        }
        plugin.storage().save();
        Msg.send(sender, "magic-set-ok", "%player%", target.getName());
    }

    private static void give(Player target, ItemStack item) {
        target.getInventory().addItem(item).values()
                .forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("quirks.admin")) return true;
        Msg.send(sender, "no-permission");
        return false;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("quirks.admin")) return out;
        if (args.length == 1) {
            return filter(List.of("book", "upbook", "brew", "set"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("book")) {
            List<String> ids = new ArrayList<>();
            for (Element element : Element.values()) ids.add(element.id());
            return filter(ids, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("upbook")) {
            return filter(List.of("1", "2", "3", "4", "5"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("brew")) {
            return filter(List.of("neutral", "antidote", "infect", "samogon", "deny", "confirm", "quirkall",
                    "life", "paces", "noharm", "norestore", "flypot", "god", "berserk", "basis", "lucky",
                    "unlucky", "grandsam", "return", "infusion", "retrain", "satpot", "knowledge", "mind",
                    "miner", "dull", "demagic"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return filter(List.of("element", "level", "mana"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("set") && args[2].equalsIgnoreCase("element")) {
            List<String> ids = new ArrayList<>();
            ids.add("none");
            for (Element element : Element.values()) ids.add(element.id());
            return filter(ids, args[3]);
        }
        return out;
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(p)) out.add(candidate);
        }
        return out;
    }
}
