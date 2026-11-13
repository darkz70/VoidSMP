package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** /quirk — управление причудами. Причуд можно несколько на одного игрока. */
public final class QuirkCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("set", "remove", "remove_admin", "remove_tmp", "info", "list", "reload", "notify", "item", "pack", "lab", "arena", "zayce", "dome", "block");
    private static final List<String> QUIRKS = List.of(
            "инженер", "кот", "бедрок", "топор", "скалк", "фермер", "земноводный", "паук",
            "engineer", "cat", "bedrock", "axe", "sculk", "farmer", "amphibian", "spider",
            "глава скалка", "глава скалков", "sculk_leader",
            "admin", "admin_pro", "админ", "админпро");

    private final VoidQuirksPlugin plugin;

    public QuirkCommand(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // директива: обычным игрокам доступна ТОЛЬКО /quirk info (и только про себя),
        // всё остальное — с OP/правом quirks.admin
        if (!sender.hasPermission("quirks.admin")) {
            if (args.length >= 1 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("get"))) {
                handleInfo(sender, args);
            } else {
                Msg.send(sender, "no-permission");
            }
            return true;
        }
        if (args.length == 0) {
            Msg.send(sender, "usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> handleSet(sender, args);
            case "remove", "clear" -> handleRemove(sender, args);
            case "remove_admin" -> handleRemoveAdmin(sender, args);
            case "remove_tmp" -> handleRemoveTmp(sender, args);
            case "info", "get" -> handleInfo(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            case "notify", "notifications" -> handleNotify(sender);
            case "item", "shard" -> handleItem(sender, args);
            case "pack", "resourcepack" -> handlePack(sender, args);
            case "lab", "labyrinth" -> handleLab(sender, args);
            case "arena" -> handleArena(sender, args);
            case "zayce" -> handleZayce(sender, args);
            case "dome" -> handleDome(sender, args);
            case "block" -> handleBlock(sender, args);
            case "acceptsculk" -> {
                if (sender instanceof Player p) {
                    plugin.sculkLeader().acceptInvite(p);
                }
            }
            default -> Msg.send(sender, "usage");
        }
        return true;
    }

    /** /quirk zayce — админ-спавн зайцекролов (только командой) и их очистка. */
    private void handleZayce(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        String sub = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "help";
        if (sub.equals("purge")) {
            int killed = plugin.zayceTask().purgeAll();
            Msg.send(sender, "zayce-purged", "%count%", String.valueOf(killed));
            return;
        }
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "target-offline");
            return;
        }
        org.bukkit.Location at = player.getLocation();
        switch (sub) {
            case "colony" -> {
                plugin.zayceTask().spawnColony(at);
                Msg.send(sender, "zayce-colony");
            }
            case "farmer" -> spawnZayce(sender, at, me.darkz70.quirks.mob.ZayceKrol.Role.FARMER);
            case "guard" -> spawnZayce(sender, at, me.darkz70.quirks.mob.ZayceKrol.Role.GUARD);
            case "elite" -> spawnZayce(sender, at, me.darkz70.quirks.mob.ZayceKrol.Role.ELITE);
            case "resident" -> spawnZayce(sender, at, me.darkz70.quirks.mob.ZayceKrol.Role.RESIDENT);
            case "tsar" -> spawnZayce(sender, at, me.darkz70.quirks.mob.ZayceKrol.Role.TSAR);
            case "deputy" -> spawnZayce(sender, at, me.darkz70.quirks.mob.ZayceKrol.Role.DEPUTY);
            case "servant" -> spawnZayce(sender, at, me.darkz70.quirks.mob.ZayceKrol.Role.SERVANT);
            case "revolutioner" -> spawnZayce(sender, at, me.darkz70.quirks.mob.ZayceKrol.Role.REVOLUTIONER);
            case "party" -> {
                if (args.length >= 3) {
                    String party = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                    plugin.getConfig().set("zaycekrol.deputy-party", party);
                    plugin.saveConfig();
                    Msg.send(sender, "zayce-party-set", "%party%", party);
                } else {
                    Msg.send(sender, "usage");
                }
            }
            default -> Msg.send(sender, "usage");
        }
    }

    private void spawnZayce(CommandSender sender, org.bukkit.Location at, me.darkz70.quirks.mob.ZayceKrol.Role role) {
        plugin.zayceTask().spawnOne(at, role);
        Msg.send(sender, "zayce-spawned", "%role%", role.display);
    }

    /**
     * /quirk pack — показать ссылку на ресурспак с текстурой осколка.
     * /quirk pack retry — (админ) форсировать немедленную повторную попытку авто-раздачи
     * ресурспака, не перезапуская сервер — полезно после починки сети/файрвола хостинга.
     */
    private void handlePack(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("retry")) {
            if (!admin(sender)) return;
            if (me.darkz70.quirks.mechanic.ResourcePackServer.retryNow()) {
                Msg.send(sender, "pack-retry-started");
            } else {
                Msg.send(sender, "pack-retry-disabled");
            }
            return;
        }
        Msg.send(sender, "pack-link",
                "%link%", "https://github.com/darkz70/VoidSMP/releases/download/ci-build/VoidQuirks-Pack.zip");
        if (sender.hasPermission("quirks.admin")) {
            boolean ready = me.darkz70.quirks.mechanic.ResourcePackServer.isReady();
            Msg.send(sender, ready ? "pack-status-ready" : "pack-status-not-ready");
        }
    }

    /** /quirk item <причуда> [уровень] [игрок] — выдать осколок души предметом. */
    private void handleItem(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "usage");
            return;
        }
        Quirk quirk = Quirk.byName(args[1]);
        if (quirk == null) {
            Msg.send(sender, "invalid-quirk");
            return;
        }
        int level = 1;
        if (args.length >= 3) {
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                Msg.send(sender, "invalid-level");
                return;
            }
            if (level < 1 || level > 3) {
                Msg.send(sender, "invalid-level");
                return;
            }
        }
        if (quirk == Quirk.ADMIN || quirk == Quirk.ADMIN_PRO) level = 1; // техпричуда всегда 1-го
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            Msg.send(sender, "usage");
            return;
        }
        if (target == null) {
            Msg.send(sender, "target-offline");
            return;
        }

        ItemStack shard = me.darkz70.quirks.mechanic.SoulShards.makeShard(quirk, level);
        var leftovers = target.getInventory().addItem(shard);
        leftovers.values().forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        Msg.send(sender, "shard-given",
                "%player%", target.getName(),
                "%quirk%", quirk.display(),
                "%level%", String.valueOf(level));
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 3) {
            Msg.send(sender, "usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Msg.send(sender, "target-offline");
            return;
        }
        Quirk quirk = Quirk.byName(args[2]);
        if (quirk == null) {
            Msg.send(sender, "invalid-quirk");
            return;
        }
        int level = 1;
        if (args.length >= 4) {
            try {
                level = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                Msg.send(sender, "invalid-level");
                return;
            }
            if (level < 1 || level > 3) {
                Msg.send(sender, "invalid-level");
                return;
            }
        }
        if (quirk == Quirk.ADMIN || quirk == Quirk.ADMIN_PRO) level = 1; // техпричуда всегда 1-го

        plugin.quirks().assign(target, quirk, level);
        Msg.send(sender, "set-ok",
                "%player%", target.getName(),
                "%quirk%", quirk.display(),
                "%level%", String.valueOf(level));
        // скрытность админ-выдачи: получение причуды не анонсируем (спека 1.0)
    }

    /** /quirk remove_admin <игрок> — снять причуду Админ / АдминПро. */
    private void handleRemoveAdmin(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Msg.send(sender, "target-offline");
            return;
        }
        boolean removed = plugin.quirks().remove(target, Quirk.ADMIN);
        removed |= plugin.quirks().remove(target, Quirk.ADMIN_PRO);
        if (removed) {
            Msg.send(sender, "removed", "%player%", target.getName());
        } else {
            Msg.send(sender, "info-none");
        }
    }

    /** /quirk remove_tmp <игрок> — снять только временные причуды. */
    private void handleRemoveTmp(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Msg.send(sender, "target-offline");
            return;
        }
        // «временные причуды» — активный чит набора от зелья причуд: откатываем к бэкапу
        PlayerData data = plugin.storage().get(target.getUniqueId());
        boolean removed = data != null && data.tmpQuirks() != null;
        if (removed) {
            plugin.magic().restoreTmpQuirks(target);
        }
        if (removed) {
            Msg.send(sender, "removed", "%player%", target.getName());
        } else {
            Msg.send(sender, "info-none");
        }
    }

    /** /quirk remove <игрок> [причуда] — снимает одну причуду, а без её имени — весь набор. */
    private void handleRemove(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Msg.send(sender, "target-offline");
            return;
        }

        boolean removed;
        if (args.length >= 3) {
            Quirk quirk = Quirk.byName(args[2]);
            if (quirk == null) {
                Msg.send(sender, "invalid-quirk");
                return;
            }
            removed = plugin.quirks().remove(target, quirk);
        } else {
            removed = plugin.quirks().removeAll(target);
        }

        if (removed) {
            Msg.send(sender, "removed", "%player%", target.getName());
            // скрытность: снятие админом не анонсируется (спека 1.0)
        } else {
            Msg.send(sender, "info-none");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("quirks.admin")
                    && !sender.getName().equalsIgnoreCase(args[1])) {
                Msg.send(sender, "info-self-only"); // обычные игроки — только про себя
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Msg.send(sender, "target-offline");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            Msg.send(sender, "usage");
            return;
        }

        PlayerData data = plugin.storage().get(target.getUniqueId());
        if (data == null || data.isEmpty()) {
            Msg.send(sender, "info-none");
            return;
        }
        sender.sendMessage(Msg.comp("info-header", "%player%", target.getName()));
        for (Map.Entry<Quirk, Integer> entry : data.entries()) {
            sender.sendMessage(Msg.comp("info-line",
                    "%quirk%", entry.getKey().display(),
                    "%level%", String.valueOf(entry.getValue())));
        }
        sender.sendMessage(Msg.comp("info-notify",
                "%state%", data.notifications() ? "включены" : "выключены"));
    }

    private void handleList(CommandSender sender) {
        if (!admin(sender)) return;
        sender.sendMessage(Msg.comp("list-header"));
        boolean empty = true;
        for (Map.Entry<UUID, PlayerData> entry : plugin.storage().all().entrySet()) {
            PlayerData data = entry.getValue();
            if (data.isEmpty()) continue;
            empty = false;
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            List<String> parts = new ArrayList<>();
            for (Map.Entry<Quirk, Integer> quirkEntry : data.entries()) {
                if (quirkEntry.getKey() == Quirk.ADMIN || quirkEntry.getKey() == Quirk.ADMIN_PRO) {
                    continue; // техпричуды — вне списков
                }
                parts.add(quirkEntry.getKey().display() + " ур. " + quirkEntry.getValue());
            }
            sender.sendMessage(Msg.comp("list-line",
                    "%player%", name != null ? name : entry.getKey().toString().substring(0, 8),
                    "%quirks%", String.join(", ", parts)));
        }
        if (empty) {
            Msg.send(sender, "list-empty");
        }
    }

    /** /quirk notify — личный тумблер оповещений причуд. */
    private void handleNotify(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "usage");
            return;
        }
        PlayerData data = plugin.storage().get(player.getUniqueId());
        if (data == null) {
            data = new PlayerData();
            plugin.storage().put(player.getUniqueId(), data);
        }
        boolean enabled = !data.notifications();
        data.notifications(enabled);
        plugin.storage().save();
        Msg.send(player, enabled ? "notify-on" : "notify-off");
    }

    /** /quirk lab <create|tp> — мир лабиринта душ. */
    private void handleLab(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> plugin.labyrinth().create(sender);
            case "tp" -> {
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    target = null;
                }
                plugin.labyrinth().teleport(sender, target);
            }
            case "back" -> {
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    target = null;
                }
                plugin.labyrinth().back(sender, target);
            }
            default -> Msg.send(sender, "usage");
        }
    }

    /** /quirk arena <create|tp|back> — отдельный обычный мир 256×256 с границей и синим порталом. */
    private void handleArena(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> plugin.arena().create(sender);
            case "tp" -> {
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    target = null;
                }
                plugin.arena().teleport(sender, target);
            }
            case "back" -> {
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    target = null;
                }
                plugin.arena().back(sender, target);
            }
            default -> Msg.send(sender, "usage");
        }
    }

    /**
     * /quirk dome create <имя> <x1> <z1> <x2> <z2> <высота> — скалк-купол: стена по периметру
     *   прямоугольника (X/Z), высотой от текущего Y отправителя. Проходим насквозь, не ломается,
     *   внутри подавлена вся магия кроме Тьмы и Света.
     * /quirk dome remove <имя>
     * /quirk dome list
     */
    private void handleDome(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 8) {
                    sender.sendMessage(Msg.color("&cИспользование: /quirk dome create <имя> <x1> <z1> <x2> <z2> <высота>"));
                    return;
                }
                try {
                    String name = args[2];
                    int x1 = Integer.parseInt(args[3]);
                    int z1 = Integer.parseInt(args[4]);
                    int x2 = Integer.parseInt(args[5]);
                    int z2 = Integer.parseInt(args[6]);
                    int height = Integer.parseInt(args[7]);
                    plugin.domes().create(sender, name, x1, z1, x2, z2, height);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Msg.color("&cКоординаты и высота должны быть целыми числами."));
                }
            }
            case "remove", "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage(Msg.color("&cИспользование: /quirk dome remove <имя>"));
                    return;
                }
                plugin.domes().remove(sender, args[2]);
            }
            case "list" -> plugin.domes().list(sender);
            default -> Msg.send(sender, "usage");
        }
    }

    /**
     * /quirk block list — какие типы кастомных блоков вообще есть в VoidQuirks
     *   (общий реестр, не только для купола — любая будущая механика может взять готовый тип).
     * /quirk block place <тип> — поставить один такой блок там, где стоит игрок (для теста/декора).
     * /quirk block remove — убрать ближайший кастомный блок VoidQuirks в радиусе 3 блоков.
     */
    private void handleBlock(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                sender.sendMessage(Msg.color("&3&lКастомные блоки VoidQuirks:"));
                for (var type : me.darkz70.quirks.mechanic.customblock.CustomBlockRegistry.all()) {
                    sender.sendMessage(Msg.color("&7— &f" + type.id() + " &7(" + type.label()
                            + "&7, база &f" + type.base() + "&7)"));
                }
            }
            case "place" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Msg.color("&cТолько для игрока."));
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(Msg.color("&cИспользование: /quirk block place <тип>"));
                    return;
                }
                var type = me.darkz70.quirks.mechanic.customblock.CustomBlockRegistry.get(args[2]);
                if (type == null) {
                    sender.sendMessage(Msg.color("&cНет такого типа. /quirk block list"));
                    return;
                }
                if (type.id().startsWith("sculk_log") || type.id().startsWith("sculk_leaves")
                        || type.id().startsWith("sculk_planks")) {
                    sender.sendMessage(Msg.color("&cЭто настоящий блок скалк-древесины — его ставят "
                            + "обычным кликом из предмета (крафт/дроп), а не через эту тестовую команду."));
                    return;
                }
                plugin.customBlocks().place(p.getWorld(), p.getLocation().getBlock().getLocation(), type, 1f, 1f, 1f);
                sender.sendMessage(Msg.color("&a✦ Поставлено: &f" + type.id()));
            }
            default -> Msg.send(sender, "usage");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.reloadPluginConfig();
        Msg.send(sender, "reload-ok");
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
        if (!sender.hasPermission("quirks.admin")) {
            return out; // обычным игрокам — никаких подсказок: меню само всё покажет
        }
        if (args.length == 1) {
            for (String sub : SUBS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(sub);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("zayce")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String opt : List.of("colony", "farmer", "guard", "elite", "resident", "tsar", "deputy", "servant", "revolutioner", "party", "purge")) {
                if (opt.startsWith(prefix)) out.add(opt);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("lab")) {
            if ("create".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("create");
            if ("tp".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("tp");
            if ("back".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("back");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("lab")
                && (args[1].equalsIgnoreCase("tp") || args[1].equalsIgnoreCase("back"))) {
            addPlayers(out, args[2]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("arena")) {
            if ("create".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("create");
            if ("tp".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("tp");
            if ("back".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("back");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("arena")
                && (args[1].equalsIgnoreCase("tp") || args[1].equalsIgnoreCase("back"))) {
            addPlayers(out, args[2]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pack")) {
            if ("retry".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("retry");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("dome")) {
            if ("create".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("create");
            if ("remove".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("remove");
            if ("list".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("list");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("block")) {
            if ("list".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("list");
            if ("place".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("place");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("block") && args[1].equalsIgnoreCase("place")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            for (var type : me.darkz70.quirks.mechanic.customblock.CustomBlockRegistry.all()) {
                if (type.id().startsWith(prefix)) out.add(type.id());
            }
        } else if (args.length == 2
                && List.of("set", "remove", "info", "get", "remove_admin", "remove_tmp")
                    .contains(args[0].toLowerCase(Locale.ROOT))) {
            addPlayers(out, args[1]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("item")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String quirk : QUIRKS) {
                if (quirk.startsWith(prefix)) out.add(quirk);
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove"))) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            for (String quirk : QUIRKS) {
                if (quirk.startsWith(prefix)) out.add(quirk);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("item")) {
            if ("1".startsWith(args[2])) out.add("1");
            if ("2".startsWith(args[2])) out.add("2");
            if ("3".startsWith(args[2])) out.add("3");
        } else if (args.length == 4 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("item"))) {
            if (args[0].equalsIgnoreCase("set")) {
                if ("1".startsWith(args[3])) out.add("1");
                if ("2".startsWith(args[3])) out.add("2");
                if ("3".startsWith(args[3])) out.add("3");
            } else {
                addPlayers(out, args[3]);
            }
        }
        return out;
    }

    private void addPlayers(List<String> out, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) out.add(player.getName());
        }
    }
}
