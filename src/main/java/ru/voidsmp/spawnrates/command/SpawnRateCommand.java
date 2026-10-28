package ru.voidsmp.spawnrates.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import ru.voidsmp.spawnrates.VoidSpawnRatesPlugin;
import ru.voidsmp.spawnrates.config.PluginSettings;
import ru.voidsmp.spawnrates.storage.ZoneRecord;
import ru.voidsmp.spawnrates.util.Args;
import ru.voidsmp.spawnrates.util.MobTypes;
import ru.voidsmp.spawnrates.util.Num;
import ru.voidsmp.spawnrates.zone.Zone;
import ru.voidsmp.spawnrates.zone.ZoneRegistry;
import ru.voidsmp.spawnrates.zone.ZoneSelection;

/**
 * /spawnrate — всё управление зонами. Команда маленькая, но важная: её
 * результаты сразу уезжают в хранилище, поэтому любая правка переживает
 * рестарт сервера.
 */
public final class SpawnRateCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 8;

    private final VoidSpawnRatesPlugin plugin;

    public SpawnRateCommand(VoidSpawnRatesPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class UsageException extends RuntimeException {
        private final String key;
        private final Map<String, ?> args;

        private UsageException(String key, Map<String, ?> args) {
            this.key = key;
            this.args = args;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        try {
            // Право проверяется до разбора аргументов: без него человек не должен
            // узнавать ни список зон, ни то, что такая-то зона существует.
            requirePermission(sender, permissionFor(sub));
            if (!plugin.settings().enabled) {
                send(sender, "general.plugin-disabled", Args.of());
            }
            switch (sub) {
                case "help" -> help(sender, label);
                case "wand" -> giveWand(sender);
                case "pos1" -> setPosition(sender, args, true);
                case "pos2" -> setPosition(sender, args, false);
                case "clear" -> clearSelection(sender);
                case "create" -> create(sender, args);
                case "resize" -> resize(sender, args);
                case "delete", "remove" -> delete(sender, args);
                case "toggle" -> toggle(sender, args);
                case "set" -> set(sender, args);
                case "mob" -> mobRule(sender, args);
                case "mobs" -> mobList(sender, args);
                case "list" -> list(sender, args);
                case "info" -> info(sender, args);
                case "near" -> near(sender);
                case "show" -> show(sender, args);
                case "hide" -> hide(sender);
                case "tp", "goto" -> teleport(sender, args);
                case "save" -> save(sender);
                case "reload" -> reload(sender);
                case "debug" -> debug(sender, args);
                case "stats" -> stats(sender);
                default -> send(sender, "general.unknown-subcommand", Args.of());
            }
        } catch (UsageException ex) {
            send(sender, ex.key, ex.args);
        } catch (RuntimeException ex) {
            sender.sendMessage(plugin.messages().parse("<red>Внутренняя ошибка: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage()));
            plugin.getLogger().warning("Команда /" + label + " упала:");
            plugin.getLogger().warning(ex.toString());
            for (StackTraceElement element : ex.getStackTrace()) {
                plugin.getLogger().warning("    at " + element);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ подкоманды

    private void help(CommandSender sender, String label) {
        List<String> lines = plugin.messages().lines("help", Map.of("label", label));
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).replace("/spawnrate", "/" + label);
            sender.sendMessage(plugin.messages().parse((index == 0 ? plugin.messages().prefix() + " " : "") + line));
        }
    }

    private void giveWand(CommandSender sender) {
        Player player = requirePlayer(sender, VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        Map<Integer, org.bukkit.inventory.ItemStack> leftover =
                player.getInventory().addItem(plugin.wand().createWand());
        for (org.bukkit.inventory.ItemStack stack : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
        send(sender, "selection.wand-given", Args.of());
    }

    private void setPosition(CommandSender sender, String[] args, boolean first) {
        Player player = requirePlayer(sender, VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        int x;
        int y;
        int z;
        String world = player.getWorld().getName();
        // /spawnrate pos1 <x> <y> <z> [мир] — без координат берём позицию игрока.
        if (args.length >= 4) {
            x = requireInt(args[2], Integer.MIN_VALUE, Integer.MAX_VALUE);
            y = requireInt(args[3], player.getWorld().getMinHeight(), player.getWorld().getMaxHeight() - 1);
            z = requireInt(args[4], Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (args.length >= 6) {
                World named = Bukkit.getWorld(args[5]);
                if (named == null) {
                    throw new UsageException("general.bad-number",
                            Args.of().put("min", "имя мира").put("max", "—").asMap());
                }
                world = named.getName();
            }
        } else {
            Location eye = player.getLocation();
            x = eye.getBlockX();
            y = eye.getBlockY();
            z = eye.getBlockZ();
        }
        ZoneSelection selection = plugin.selections().getOrCreate(player.getUniqueId());
        ZoneSelection.Point point = new ZoneSelection.Point(world, x, y, z);
        if (first) {
            selection.setFirst(point);
        } else {
            selection.setSecond(point);
        }
        send(sender, "selection.pos-set", Args.of()
                .put("index", first ? 1 : 2)
                .put("x", x).put("y", y).put("z", z)
                .put("world", world));
        if (selection.isComplete()) {
            send(sender, "selection.size", Args.of()
                    .put("x", selection.maxX() - selection.minX() + 1)
                    .put("y", selection.maxY() - selection.minY() + 1)
                    .put("z", selection.maxZ() - selection.minZ() + 1)
                    .put("chunks", selection.chunkCount()));
            plugin.preview().drawBoxOnce(player, selection);
        }
    }

    private void clearSelection(CommandSender sender) {
        Player player = requirePlayer(sender, VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        plugin.selections().clear(player.getUniqueId());
        send(sender, "selection.cleared", Args.of());
    }

    private void create(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        if (args.length < 2) {
            throw new UsageException("general.name-invalid", Args.of().asMap());
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (!ZoneRecord.isValidName(name)) {
            throw new UsageException("general.name-invalid", Args.of().asMap());
        }
        ZoneRegistry registry = plugin.registry();
        if (registry.exists(name)) {
            throw new UsageException("general.name-exists", Args.of().put("name", name).asMap());
        }
        PluginSettings settings = plugin.settings();
        if (registry.size() >= settings.maxZones) {
            throw new UsageException("general.too-many", Args.of().put("max", settings.maxZones).asMap());
        }
        ZoneSelection selection = plugin.selections().get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            throw new UsageException("selection.incomplete", Args.of().asMap());
        }
        checkSize(selection.maxSize(), settings.maxZoneSize);
        World world = Bukkit.getWorld(selection.world());
        if (world == null) {
            throw new UsageException("info.no-world", Args.of().asMap());
        }
        ZoneRecord record = ZoneRecord.normalize(name, world.getName(), world.getUID().toString(),
                selection.minX(), selection.minY(), selection.minZ(),
                selection.maxX(), selection.maxY(), selection.maxZ(),
                1.0D, ZoneRecord.CAP_OFF, 0, true, false, settings.removeExcessDefault,
                Set.of(), Map.of(), settings.maxMultiplier, now());

        Zone zone = new Zone(record);
        registry.add(zone);
        plugin.persistence().save(record);
        send(sender, "zone.created", Args.of()
                .put("name", name)
                .put("x1", record.minX()).put("y1", record.minY()).put("z1", record.minZ())
                .put("x2", record.maxX()).put("y2", record.maxY()).put("z2", record.maxZ())
                .put("multiplier", Num.multiplier(record.multiplier())));
        send(sender, "zone.created-note", Args.of().put("storage", plugin.persistence().describe()));
        plugin.preview().show(player, zone);
        send(sender, "preview.showing", Args.of().put("name", name));
    }

    private void resize(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        Zone zone = requireZone(args, 1);
        ZoneSelection selection = plugin.selections().get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            throw new UsageException("selection.incomplete", Args.of().asMap());
        }
        checkSize(selection.maxSize(), plugin.settings().maxZoneSize);
        World world = Bukkit.getWorld(selection.world());
        if (world == null) {
            throw new UsageException("info.no-world", Args.of().asMap());
        }
        ZoneRecord next = zone.record().withBox(world.getName(), world.getUID().toString(),
                selection.minX(), selection.minY(), selection.minZ(),
                selection.maxX(), selection.maxY(), selection.maxZ(), now());
        zone.replace(next);
        plugin.registry().refresh(zone);
        plugin.persistence().save(next);
        send(sender, "zone.resized", Args.of()
                .put("name", zone.name())
                .put("x1", next.minX()).put("y1", next.minY()).put("z1", next.minZ())
                .put("x2", next.maxX()).put("y2", next.maxY()).put("z2", next.maxZ()));
    }

    private void delete(CommandSender sender, String[] args) {
        requirePermission(sender, VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        Zone zone = requireZone(args, 1);
        plugin.registry().remove(zone.name());
        plugin.persistence().delete(zone.name());
        send(sender, "zone.deleted", Args.of().put("name", zone.name()));
    }

    private void toggle(CommandSender sender, String[] args) {
        requirePermission(sender, VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        Zone zone = requireZone(args, 1);
        boolean enabled = !zone.isEnabled();
        ZoneRecord next = zone.record().withFlag("enabled", enabled, now());
        zone.replace(next);
        plugin.persistence().save(next);
        send(sender, enabled ? "zone.toggled-on" : "zone.toggled-off", Args.of().put("name", zone.name()));
    }

    private void set(CommandSender sender, String[] args) {
        requirePermission(sender, VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        if (args.length < 4) {
            throw new UsageException("general.need-value",
                    Args.of().put("usage", "/spawnrate set <зона> <multiplier|cap|filter|priority|spawners|despawn|enabled> <значение>").asMap());
        }
        Zone zone = requireZone(args, 1);
        ZoneRecord record = zone.record();
        String property = args[2].toLowerCase(Locale.ROOT);
        String value = args[3].toLowerCase(Locale.ROOT);
        PluginSettings settings = plugin.settings();
        long now = now();
        ZoneRecord next;
        String key;
        String shown;

        switch (property) {
            case "multiplier", "rate", "множитель" -> {
                double parsed = requireDouble(value, 0.0D, settings.maxMultiplier);
                next = record.withMultiplier(parsed, settings.maxMultiplier, now);
                key = "zone.set-multiplier";
                shown = Num.multiplier(parsed);
            }
            case "cap", "limit", "лимит" -> {
                next = record.withCap("off".equals(value) || "-1".equals(value)
                        ? ZoneRecord.CAP_OFF : requireInt(value, 0, 1_000_000), now);
                key = next.hasCap() ? "zone.set-cap" : "zone.set-cap-off";
                shown = next.hasCap() ? String.valueOf(next.cap()) : "";
            }
            case "filter", "categories", "категории" -> {
                Set<String> categories;
                try {
                    categories = MobTypes.parseCategories(value);
                } catch (IllegalArgumentException ex) {
                    throw new UsageException("general.unknown-category",
                            Args.of().put("value", ex.getMessage())
                                    .put("options", MobTypes.categoryOptions()).asMap());
                }
                next = record.withCategories(categories, now);
                key = "zone.set-categories";
                shown = MobTypes.categoriesToString(categories);
            }
            case "priority", "приоритет" -> {
                int parsed = requireInt(value, -1000, 1000);
                next = record.withPriority(parsed, now);
                key = "zone.set-priority";
                shown = String.valueOf(parsed);
            }
            case "spawners", "affect-spawners", "спавнеры" -> {
                next = record.withFlag("spawners", requireBoolean(value), now);
                key = "zone.set-spawners";
                shown = yesNo(next.affectSpawners());
            }
            case "despawn", "remove-excess", "удаление" -> {
                next = record.withFlag("despawn", requireBoolean(value), now);
                key = "zone.set-remove-excess";
                shown = yesNo(next.removeExcess());
            }
            case "enabled", "on", "off" -> {
                boolean flag = "off".equals(value) ? !record.enabled() : requireBoolean(value);
                next = record.withFlag("enabled", flag, now);
                key = "zone.set-bool";
                shown = yesNo(next.enabled());
            }
            default -> {
                throw new UsageException("general.need-value",
                        Args.of().put("usage", "multiplier|cap|filter|priority|spawners|despawn|enabled").asMap());
            }
        }

        zone.replace(next);
        if ("priority".equals(property) || "filter".equals(property) || "categories".equals(property)) {
            // Приоритет меняет порядок в списке зон точки, категории — набор учтённых мобов.
            plugin.registry().refresh(zone);
        }
        plugin.persistence().save(next);
        send(sender, key, Args.of().put("name", zone.name()).put("key", property).put("value", shown));
    }

    private void mobRule(CommandSender sender, String[] args) {
        requirePermission(sender, VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        if (args.length < 4) {
            throw new UsageException("general.need-value",
                    Args.of().put("usage", "/spawnrate mob <зона> <моб|all> <множитель|remove>").asMap());
        }
        Zone zone = requireZone(args, 1);
        String target = args[2].toLowerCase(Locale.ROOT);
        String value = args[3].toLowerCase(Locale.ROOT);
        PluginSettings settings = plugin.settings();
        long now = now();
        ZoneRecord record = zone.record();

        if (target.equals("all") || target.equals("все") || target.equals("*")) {
            if ("remove".equals(value)) {
                ZoneRecord next = record.toBuilder()
                        .mobRules(Map.of())
                        .multiplier(1.0D)
                        .updatedAt(now)
                        .build();
                apply(zone, next);
                send(sender, "zone.mob-removed", Args.of().put("name", zone.name()).put("mob", "all"));
                return;
            }
            double parsed = requireDouble(value, 0.0D, settings.maxMultiplier);
            ZoneRecord next = record.withMultiplier(parsed, settings.maxMultiplier, now);
            apply(zone, next);
            send(sender, "zone.set-multiplier", Args.of()
                    .put("name", zone.name()).put("value", Num.multiplier(parsed)));
            return;
        }

        org.bukkit.entity.EntityType type = MobTypes.resolve(target);
        if (type == null) {
            throw new UsageException("zone.unknown-mob", Args.of().put("value", target).asMap());
        }
        if ("remove".equals(value) || "off".equals(value)) {
            ZoneRecord next = record.withoutMobRule(type.name(), now);
            apply(zone, next);
            send(sender, "zone.mob-removed", Args.of().put("name", zone.name()).put("mob", type.name()));
            return;
        }
        double parsed = requireDouble(value, 0.0D, settings.maxMultiplier);
        ZoneRecord next = record.withMobRule(type.name(), parsed, settings.maxMultiplier, now);
        apply(zone, next);
        send(sender, "zone.mob-set", Args.of()
                .put("name", zone.name())
                .put("mob", type.name().toLowerCase(Locale.ROOT))
                .put("value", Num.multiplier(parsed)));
    }

    private void apply(Zone zone, ZoneRecord next) {
        zone.replace(next);
        plugin.persistence().save(next);
    }

    private void mobList(CommandSender sender, String[] args) {
        requirePermission(sender, VoidSpawnRatesPlugin.PERMISSION_VIEW);
        Zone zone = requireZone(args, 1);
        Map<String, Double> rules = zone.record().mobRules();
        send(sender, "zone.mobs-header", Args.of().put("name", zone.name()));
        if (rules.isEmpty()) {
            send(sender, "zone.mobs-empty", Args.of());
            return;
        }
        List<String> lines = new ArrayList<>(rules.keySet());
        Collections.sort(lines);
        for (String entity : lines) {
            send(sender, "zone.mobs-entry", Args.of()
                    .put("mob", entity.toLowerCase(Locale.ROOT))
                    .put("value", Num.multiplier(rules.get(entity))));
        }
    }

    private void list(CommandSender sender, String[] args) {
        requirePermission(sender, VoidSpawnRatesPlugin.PERMISSION_VIEW);
        ZoneRegistry registry = plugin.registry();
        List<Zone> zones = registry.all();
        if (zones.isEmpty()) {
            send(sender, "list.empty", Args.of());
            return;
        }
        zones.sort((left, right) -> {
            int byWorld = left.record().world().compareToIgnoreCase(right.record().world());
            if (byWorld != 0) {
                return byWorld;
            }
            int byPriority = Integer.compare(right.record().priority(), left.record().priority());
            return byPriority != 0 ? byPriority : left.name().compareToIgnoreCase(right.name());
        });
        int page = 1;
        if (args.length >= 2) {
            page = requireInt(args[1], 1, 10_000);
        }
        int pages = (zones.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page > pages) {
            send(sender, "list.bad-page", Args.of());
            return;
        }
        send(sender, "list.header", Args.of()
                .put("page", page).put("pages", pages).put("total", zones.size()));
        int from = (page - 1) * PAGE_SIZE;
        for (int index = from; index < Math.min(from + PAGE_SIZE, zones.size()); index++) {
            Zone zone = zones.get(index);
            ZoneRecord record = zone.record();
            Args args2 = Args.of()
                    .put("name", zone.name())
                    .put("world", record.world())
                    .put("multiplier", Num.multiplier(record.multiplier()))
                    .put("priority", record.priority())
                    .put("cap", record.hasCap()
                            ? plugin.messages().text("list.entry-cap", Map.of("value", record.cap()))
                            : "")
                    .put("state", record.enabled()
                            ? "" : plugin.messages().text("list.entry-off", Map.of()));
            send(sender, "list.entry", args2);
        }
    }

    private void info(CommandSender sender, String[] args) {
        requirePermission(sender, VoidSpawnRatesPlugin.PERMISSION_VIEW);
        Zone zone = requireZone(args, 1);
        ZoneRecord record = zone.record();
        World world = Bukkit.getWorld(record.world());
        String mobNames = String.join(",", record.mobRules().keySet());
        Args info = Args.of()
                .put("name", zone.name())
                .put("world", record.world() + (world == null
                        ? plugin.messages().text("info.no-world", Map.of()) : ""))
                .put("x1", record.minX()).put("y1", record.minY()).put("z1", record.minZ())
                .put("x2", record.maxX()).put("y2", record.maxY()).put("z2", record.maxZ())
                .put("size", Num.size(record.sizeX(), record.sizeY(), record.sizeZ()))
                .put("chunks", record.chunkCount())
                .put("priority", record.priority())
                .put("multiplier", Num.multiplier(record.multiplier()))
                .put("categories", MobTypes.categoriesToString(record.categories()))
                .put("rules", record.mobRules().isEmpty() ? "нет" : mobNames)
                .put("cap", record.hasCap() ? String.valueOf(record.cap()) : "выключен")
                .put("mobs", zone.runtime().mobs() < 0 ? "—" : zone.runtime().mobs())
                .put("counted", zone.runtime().mobs() >= 0 && !zone.runtime().isCountComplete()
                        ? plugin.messages().text("info.counted-partial", Map.of()) : "")
                .put("spawners", yesNo(record.affectSpawners()))
                .put("remove-excess", yesNo(record.removeExcess()))
                .put("state", yesNo(record.enabled()))
                .put("allowed", zone.runtime().allowedTotal())
                .put("denied", zone.runtime().deniedTotal())
                .put("extra", zone.runtime().extraTotal())
                .put("created", Num.timestamp(record.createdAt()))
                .put("updated", Num.timestamp(record.updatedAt()))
                .put("storage", plugin.persistence().describe());

        send(sender, "info.header", Args.of().put("name", zone.name()).put("world", record.world()));
        for (String line : plugin.messages().lines("info.lines", info.asMap())) {
            sender.sendMessage(plugin.messages().parse(line));
        }
    }

    private void near(CommandSender sender) {
        Player player = requirePlayer(sender, VoidSpawnRatesPlugin.PERMISSION_VIEW);
        int radius = plugin.settings().playerDistance;
        List<Zone> zones = plugin.registry().zonesNear(player.getWorld().getName(),
                player.getLocation().getBlockX(), player.getLocation().getBlockY(),
                player.getLocation().getBlockZ(), radius);
        if (zones.isEmpty()) {
            send(sender, "near.none", Args.of().put("radius", radius));
            return;
        }
        send(sender, "near.header", Args.of());
        for (Zone zone : zones) {
            ZoneRecord record = zone.record();
            double distance = zone.distanceTo(player.getLocation().getBlockX(),
                    player.getLocation().getBlockY(), player.getLocation().getBlockZ());
            send(sender, "near.entry", Args.of()
                    .put("name", zone.name())
                    .put("distance", distance < 1.0D ? "внутри" : String.format(Locale.ROOT, "%.0f м", distance))
                    .put("multiplier", Num.multiplier(record.multiplier()))
                    .put("size", Num.size(record.sizeX(), record.sizeY(), record.sizeZ())));
        }
    }

    private void show(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, VoidSpawnRatesPlugin.PERMISSION_VIEW);
        Zone zone = requireZone(args, 1);
        plugin.preview().show(player, zone);
        send(sender, "preview.showing", Args.of().put("name", zone.name()));
    }

    private void hide(CommandSender sender) {
        Player player = requirePlayer(sender, VoidSpawnRatesPlugin.PERMISSION_VIEW);
        if (plugin.preview().hide(player)) {
            send(sender, "preview.hidden", Args.of());
        } else {
            send(sender, "preview.nothing-shown", Args.of());
        }
    }

    private void teleport(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, VoidSpawnRatesPlugin.PERMISSION_TELEPORT);
        Zone zone = requireZone(args, 1);
        ZoneRecord record = zone.record();
        World world = Bukkit.getWorld(record.world());
        if (world == null) {
            throw new UsageException("info.no-world", Args.of().asMap());
        }
        int x = zone.centerX();
        int z = zone.centerZ();
        int y = findSafeY(world, x, z, record);
        player.teleport(new Location(world, x + 0.5D, y, z + 0.5D));
        send(sender, "admin.tp-done", Args.of().put("name", zone.name()));
    }

    private static int findSafeY(World world, int x, int z, ZoneRecord record) {
        for (int y = record.maxY() - 1; y >= record.minY(); y--) {
            if (y < world.getMinHeight() + 1 || y + 1 >= world.getMaxHeight()) {
                continue;
            }
            boolean freeHere = !world.getBlockAt(x, y, z).getType().isSolid();
            boolean freeAbove = !world.getBlockAt(x, y + 1, z).getType().isSolid();
            boolean floor = !world.getBlockAt(x, y - 1, z).getType().isAir();
            if (freeHere && freeAbove && floor) {
                return y;
            }
        }
        return Math.max(record.minY(), world.getMinHeight() + 1);
    }

    private void save(CommandSender sender) {
        requirePermission(sender, VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        if (!plugin.persistence().isReady()) {
            send(sender, "admin.storage-failed",
                    Args.of().put("error", String.valueOf(plugin.persistence().failureReason())));
            return;
        }
        boolean ok = plugin.persistence().flushNow(5_000L);
        if (ok) {
            send(sender, "zone.saved", Args.of().put("storage", plugin.persistence().describe()));
        } else {
            String error = plugin.persistence().failureReason();
            send(sender, "zone.save-failed",
                    Args.of().put("error", error == null ? "таймаут" : error));
        }
        if (plugin.persistence().isDegraded()) {
            send(sender, "admin.storage-warning",
                    Args.of().put("error", String.valueOf(plugin.persistence().failureReason())));
        }
    }

    private void reload(CommandSender sender) {
        requirePermission(sender, VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        int count = plugin.reloadAll();
        send(sender, "zone.reloaded", Args.of().put("count", count));
        send(sender, "admin.storage-info", Args.of()
                .put("storage", plugin.persistence().describe())
                .put("detail", plugin.persistence().isDegraded() ? "аварийный режим" : "ок"));
    }

    private void debug(CommandSender sender, String[] args) {
        requirePermission(sender, VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        PluginSettings settings = plugin.settings();
        boolean value = args.length >= 2
                ? requireBoolean(args[1].toLowerCase(Locale.ROOT))
                : !settings.debug;
        settings.debug = value;
        send(sender, value ? "admin.debug-on" : "admin.debug-off", Args.of());
    }

    private void stats(CommandSender sender) {
        requirePermission(sender, VoidSpawnRatesPlugin.PERMISSION_VIEW);
        long denied = 0L;
        long extra = 0L;
        long allowed = 0L;
        long removed = 0L;
        for (Zone zone : plugin.registry().all()) {
            denied += zone.runtime().deniedTotal();
            allowed += zone.runtime().allowedTotal();
            extra += zone.runtime().extraTotal();
            removed += zone.runtime().removedTotal();
        }
        send(sender, "admin.stats", Args.of()
                .put("checks", allowed + denied)
                .put("denied", denied)
                .put("extra", extra)
                .put("removed", removed)
                .put("zones", plugin.registry().size()));
    }

    // ------------------------------------------------------------------ мелочи

    private void send(CommandSender sender, String key, Args args) {
        send(sender, key, args.asMap());
    }

    private void send(CommandSender sender, String key, Map<String, ?> args) {
        plugin.messages().send(sender, key, args);
    }

    private Zone requireZone(String[] args, int index) {
        if (args.length <= index) {
            throw new UsageException("general.name-invalid", Args.of().asMap());
        }
        Zone zone = plugin.registry().byName(args[index]);
        if (zone == null) {
            throw new UsageException("general.name-not-found",
                    Args.of().put("name", args[index]).asMap());
        }
        return zone;
    }

    private Player requirePlayer(CommandSender sender, String permission) {
        requirePermission(sender, permission);
        if (!(sender instanceof Player player)) {
            throw new UsageException("general.players-only", Args.of().asMap());
        }
        return player;
    }

    /** Подкоманды, которые ничего не меняют. */
    private static final Set<String> READ_ONLY =
            Set.of("help", "list", "info", "near", "mobs", "stats", "show", "hide");

    /**
     * Право подкоманды с учётом <code>permissions.admin-only</code>: по умолчанию
     * («только операторы») просмотр и телепорт тоже требуют voidspawnrates.admin.
     * С admin-only: false остаётся voidspawnrates.view / voidspawnrates.teleport.
     */
    private String permissionFor(String sub) {
        String base;
        if (sub.equals("tp") || sub.equals("goto")) {
            base = VoidSpawnRatesPlugin.PERMISSION_TELEPORT;
        } else if (READ_ONLY.contains(sub)) {
            base = VoidSpawnRatesPlugin.PERMISSION_VIEW;
        } else {
            base = VoidSpawnRatesPlugin.PERMISSION_ADMIN;
        }
        boolean soft = !base.equals(VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        return soft && plugin.settings().adminOnlyCommands
                ? VoidSpawnRatesPlugin.PERMISSION_ADMIN : base;
    }

    private void requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return;
        }
        // Если просмотр закрыт настройкой, честнее сказать про неё, а не про права.
        boolean hiddenByConfig = plugin.settings().adminOnlyCommands
                && !permission.equals(VoidSpawnRatesPlugin.PERMISSION_ADMIN);
        throw new UsageException(hiddenByConfig ? "general.admin-only" : "general.no-permission",
                Args.of().asMap());
    }

    private static void checkSize(int size, int max) {
        if (size > max) {
            throw new UsageException("general.too-big", Args.of().put("max", max).asMap());
        }
    }

    private static String yesNo(boolean value) {
        return value ? "да" : "нет";
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    private static double requireDouble(String raw, double min, double max) {
        try {
            double value = Double.parseDouble(raw.replace(',', '.'));
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new NumberFormatException();
            }
            return Num.clamp(value, min, max);
        } catch (NumberFormatException ex) {
            throw new UsageException("general.bad-number",
                    Args.of().put("min", Num.multiplier(min)).put("max", Num.multiplier(max)).asMap());
        }
    }

    private static int requireInt(String raw, int min, int max) {
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new UsageException("general.bad-number",
                    Args.of().put("min", min).put("max", max).asMap());
        }
    }

    private static boolean requireBoolean(String raw) {
        return switch (raw) {
            case "true", "on", "yes", "да", "1", "вкл" -> true;
            case "false", "off", "no", "нет", "0", "выкл" -> false;
            default -> throw new UsageException("general.bad-boolean", Args.of().asMap());
        };
    }

    // ------------------------------------------------------------------ автодополнение

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "wand", "pos1", "pos2", "clear", "create", "resize", "delete", "toggle",
            "set", "mob", "mobs", "list", "info", "near", "show", "hide", "tp", "save",
            "reload", "debug", "stats");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Подсказываем только то, на что есть право — иначе Tab обещает
            // /spawnrate delete, а сервер отвечает «недостаточно прав».
            List<String> allowed = new ArrayList<>();
            for (String name : SUBCOMMANDS) {
                if (sender.hasPermission(permissionFor(name))) {
                    allowed.add(name);
                }
            }
            return filter(allowed, args[0], 50);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> names = zoneNames();
        if (args.length == 2) {
            return switch (sub) {
                case "delete", "remove", "toggle", "set", "mob", "mobs", "info", "show", "tp", "goto", "resize" ->
                        filter(names, args[1], 40);
                case "list" -> filter(List.of("1", "2", "3"), args[1], 3);
                case "debug" -> filter(List.of("true", "false"), args[1], 2);
                default -> Collections.emptyList();
            };
        }
        if (args.length == 3 && sub.equals("set")) {
            return filter(List.of("multiplier", "cap", "filter", "priority", "spawners", "despawn", "enabled"),
                    args[2], 7);
        }
        if (args.length == 3 && sub.equals("mob")) {
            List<String> out = new ArrayList<>(filter(names, args[2], 5));
            out.addAll(filter(MobTypes.suggest(args[2], 30), args[2], 30));
            return out;
        }
        if (args.length == 4 && sub.equals("set")) {
            return switch (args[2].toLowerCase(Locale.ROOT)) {
                case "spawners", "despawn", "enabled" -> filter(List.of("true", "false"), args[3], 2);
                case "cap" -> filter(List.of("off", "10", "20", "30", "50"), args[3], 5);
                case "filter" -> filter(List.of("all", "monsters", "animals", "water", "ambient", "misc"),
                        args[3], 6);
                case "multiplier" -> filter(List.of("0", "0.25", "0.5", "1", "1.5", "2"), args[3], 6);
                default -> Collections.emptyList();
            };
        }
        if (args.length == 4 && sub.equals("mob")) {
            return filter(List.of("0", "0.5", "1", "2", "remove"), args[3], 5);
        }
        if (args.length == 6 && (sub.equals("pos1") || sub.equals("pos2"))) {
            List<String> worlds = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                worlds.add(world.getName());
            }
            return filter(worlds, args[5], 10);
        }
        return Collections.emptyList();
    }

    private List<String> zoneNames() {
        List<String> out = new ArrayList<>();
        for (Zone zone : plugin.registry().all()) {
            out.add(zone.name());
        }
        return out;
    }

    private static List<String> filter(List<String> options, String prefix, int limit) {
        String key = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(key)) {
                out.add(option);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }
}
