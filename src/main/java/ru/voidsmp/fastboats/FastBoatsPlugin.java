package ru.voidsmp.fastboats;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Input;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;


public final class FastBoatsPlugin extends JavaPlugin {
    private final Map<UUID, Location> lastPositions = new HashMap<>();
    private final Map<UUID, String> diagnostics = new HashMap<>();
    private final Map<UUID, String> lastAttempts = new HashMap<>();
    private boolean active;
    private double multiplier;
    private double maxSpeed;
    private List<String> worlds = List.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!loadSettings()) {
            getLogger().severe("Некорректный config.yml. Исправьте настройки и перезапустите плагин.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Objects.requireNonNull(getCommand("boatspeed")).setExecutor(this);
        getServer().getScheduler().runTaskTimer(this, this::tickBoats, 1L, 1L);
    }

    private boolean loadSettings() {
        if (!getConfig().isBoolean("enabled") || !getConfig().isList("worlds")) return false;
        double nextMultiplier = getConfig().getDouble("multiplier", Double.NaN);
        double nextMax = getConfig().getDouble("max-speed", Double.NaN);
        if (!SpeedMath.inRange(nextMultiplier, 1, SpeedMath.MAX_MULTIPLIER) || !SpeedMath.inRange(nextMax, 0.1, SpeedMath.MAX_SPEED)) {
            return false;
        }
        List<?> configuredWorlds = getConfig().getList("worlds");
        if (configuredWorlds == null || configuredWorlds.stream().anyMatch(w -> !(w instanceof String))) {
            return false;
        }
        active = getConfig().getBoolean("enabled");
        multiplier = nextMultiplier;
        maxSpeed = nextMax;
        worlds = List.copyOf(getConfig().getStringList("worlds"));
        return true;
    }

    private void tickBoats() {
        diagnostics.clear();
        Set<UUID> online = new HashSet<>();
        Set<UUID> tracked = new HashSet<>();
        for (Player player : getServer().getOnlinePlayers()) {
            online.add(player.getUniqueId());
            String reason = inactiveReason(player);
            if (reason != null) {
                diagnostics.put(player.getUniqueId(), reason);
                continue;
            }
            Boat boat = (Boat) player.getVehicle();
            UUID id = boat.getUniqueId();
            tracked.add(id);
            Location current = boat.getLocation();
            Location previous = lastPositions.put(id, current);
            if (previous == null || !previous.getWorld().equals(current.getWorld())) {
                diagnostics.put(player.getUniqueId(), "Сбор первого образца движения; продолжайте грести.");
                continue;
            }
            double dx = current.getX() - previous.getX();
            double dz = current.getZ() - previous.getZ();
            double distance = Math.hypot(dx, dz);
            double extra = SpeedMath.extraDistance(distance, multiplier, maxSpeed);
            if (extra == 0) {
                diagnostics.put(player.getUniqueId(), "Нет добавки: движение отсутствует, достигнут лимит или скачок позиции. Смещение: "
                        + String.format(Locale.ROOT, "%.3f", distance) + " блока/тик.");
                continue;
            }
            boolean moved = BoatMovement.move(boat, dx / distance * extra, dz / distance * extra);
            // Record the ACTUAL post-teleport position. Never multiply our own previous boost.
            lastPositions.put(id, boat.getLocation());
            diagnostics.put(player.getUniqueId(), moved
                    ? String.format(Locale.ROOT, "Добавка применена: %.3f блока/тик; исходное смещение %.3f.", extra, distance)
                    : "Добавка заблокирована: препятствие, сущность, незагруженный чанк, граница мира или отмена телепортации.");
            lastAttempts.put(player.getUniqueId(), diagnostics.get(player.getUniqueId()));
        }
        lastPositions.keySet().retainAll(tracked);
        lastAttempts.keySet().retainAll(online);
    }

    private String inactiveReason(Player player) {
        if (!active || multiplier == 1) return "Ускорение выключено или множитель равен 1.";
        if (!(player.getVehicle() instanceof Boat boat)) return "Вы не в лодке.";
        if (!boat.isValid()) return "Лодка недействительна.";
        if (boat.getPassengers().isEmpty() || !boat.getPassengers().getFirst().equals(player)) {
            return "Вы пассажир, а не водитель.";
        }
        if (!player.hasPermission("voidfastboats.use")) return "Нет права voidfastboats.use.";
        if (!worlds.isEmpty() && !worlds.contains(boat.getWorld().getName())) return "Мир исключён в конфиге.";
        if (boat.getStatus() != Boat.Status.IN_WATER) return "Лодка не на поверхности воды: " + boat.getStatus();
        Input input = player.getCurrentInput();
        if (input.isForward() == input.isBackward()) return "Нажмите вперёд или назад (не одновременно).";
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voidfastboats.admin")) {
            sender.sendMessage("Нет права voidfastboats.admin.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Лодки: " + (active ? "ускорение включено" : "ускорение выключено")
                    + ", множитель " + multiplier + ", лимит " + maxSpeed + " блоков/тик.");
            sender.sendMessage("/boatspeed <1-20|on|off|reload|debug>");
            return true;
        }
        if (args.length != 1) return false;
        String value = args[0].toLowerCase(Locale.ROOT);
        if (value.equals("debug")) {
            sender.sendMessage("VoidFastBoats " + getPluginMeta().getVersion() + ": "
                    + (active ? "ON" : "OFF") + ", множитель " + multiplier + ", лимит " + maxSpeed);
            if (sender instanceof Player player) {
                sender.sendMessage(diagnostics.getOrDefault(player.getUniqueId(), "Нет данных; подождите тик."));
                sender.sendMessage("Последняя попытка в этой сессии: " + lastAttempts.getOrDefault(player.getUniqueId(), "ещё не было"));
            } else {
                sender.sendMessage("Проверку движения выполняйте в игре, сидя за рулём лодки.");
            }
            return true;
        }
        if (value.equals("reload")) {
            lastPositions.clear();
            reloadConfig();
            boolean loaded = loadSettings();
            if (!loaded) {
                // Restore in-memory config too, so subsequent commands cannot save invalid values.
                getConfig().set("enabled", active);
                getConfig().set("multiplier", multiplier);
                getConfig().set("max-speed", maxSpeed);
                getConfig().set("worlds", worlds);
            }
            sender.sendMessage(loaded ? "Конфиг лодок перезагружен."
                    : "Ошибка конфига: проверьте enabled, multiplier (1–20), max-speed (0.1–8) и worlds. Старые настройки сохранены в памяти.");
            return true;
        }
        if (value.equals("on") || value.equals("off")) {
            active = value.equals("on");
            getConfig().set("enabled", active);
        } else {
            double requested;
            try {
                requested = Double.parseDouble(value);
            } catch (NumberFormatException exception) {
                sender.sendMessage("Укажите число от 1 до 20, on, off или reload.");
                return true;
            }
            if (!SpeedMath.inRange(requested, 1, SpeedMath.MAX_MULTIPLIER)) {
                sender.sendMessage("Множитель должен быть конечным числом от 1 до 20.");
                return true;
            }
            multiplier = requested;
            maxSpeed = SpeedMath.limitForMultiplier(requested);
            active = true;
            getConfig().set("multiplier", multiplier);
            getConfig().set("max-speed", maxSpeed);
            getConfig().set("enabled", active);
        }
        lastPositions.clear();
        saveConfig();
        sender.sendMessage("Настройки сохранены: ускорение " + (active ? "включено" : "выключено")
                + ", множитель " + multiplier + ", лимит " + maxSpeed + " блоков/тик. Настройка применяется ко всем лодкам в разрешённых мирах.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("voidfastboats.admin") || args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("1", "1.5", "2", "3", "5", "10", "15", "20", "on", "off", "reload", "debug").stream()
                .filter(value -> value.startsWith(prefix)).toList();
    }
}
