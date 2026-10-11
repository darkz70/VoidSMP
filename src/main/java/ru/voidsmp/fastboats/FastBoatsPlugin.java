package ru.voidsmp.fastboats;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.util.Vector;
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
    private double targetSpeed;
    private boolean settingsValid;
    private String configError = "";
    private List<String> worlds = List.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settingsValid = loadSettings();
        if (!settingsValid) {
            active = false;
            getLogger().severe("Разгон выключен: " + configError + ". Исправьте config.yml и выполните /boatspeed reload.");
        }
        Objects.requireNonNull(getCommand("boatspeed")).setExecutor(this);
        getServer().getScheduler().runTaskTimer(this, this::tickBoats, 2L, 2L);
    }

    private boolean loadSettings() {
        if (!getConfig().isBoolean("enabled")) return invalidConfig("enabled", "true или false");
        double nextMultiplier = getConfig().getDouble("multiplier", Double.NaN);
        if (!SpeedMath.validMultiplier(nextMultiplier)) return invalidConfig("multiplier", "конечное число >= 1");
        List<?> configuredWorlds = getConfig().getList("worlds");
        if (configuredWorlds == null || configuredWorlds.stream().anyMatch(w -> !(w instanceof String))) {
            return invalidConfig("worlds", "список имён миров, например []");
        }
        active = getConfig().getBoolean("enabled");
        multiplier = nextMultiplier;
        targetSpeed = SpeedMath.targetForMultiplier(nextMultiplier);
        worlds = List.copyOf(getConfig().getStringList("worlds"));
        configError = "";
        return true;
    }

    private boolean invalidConfig(String key, String expected) {
        configError = "Поле " + key + ": ожидалось " + expected + ", получено " + getConfig().get(key);
        return false;
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
            // Two-tick position samples reflect client movement, unlike server deltaMovement.
            double distance = Math.hypot(dx, dz);
            double measuredSpeed = distance / 2.0;
            double requestedSpeed = SpeedMath.acceleratedSpeed(measuredSpeed, multiplier);
            if (requestedSpeed <= measuredSpeed || distance < 0.002) {
                diagnostics.put(player.getUniqueId(), "Импульс не нужен: нет движения или достигнута выбранная скорость.");
                continue;
            }
            Input input = player.getCurrentInput();
            double yaw = Math.toRadians(current.getYaw());
            int direction = input.isForward() ? 1 : -1;
            double alongInput = (dx * -Math.sin(yaw) + dz * Math.cos(yaw)) * direction;
            if (alongInput <= 0) {
                diagnostics.put(player.getUniqueId(), "Смена направления: ожидаем движения в сторону гребли.");
                continue;
            }
            double vx = dx / distance * requestedSpeed;
            double vz = dz / distance * requestedSpeed;
            // Do not add thrust toward nearby obstacles or unloaded chunks.
            if (!BoatSafety.isPathClear(boat, dx / distance * SpeedMath.lookAheadDistance(requestedSpeed),
                    dz / distance * SpeedMath.lookAheadDistance(requestedSpeed))) {
                diagnostics.put(player.getUniqueId(), "Разгон приостановлен: препятствие, сущность, граница мира или незагруженный чанк.");
                lastAttempts.put(player.getUniqueId(), diagnostics.get(player.getUniqueId()));
                continue;
            }
            // No teleportation: allow vanilla movement and collision handling to move the boat.
            // Preserve vertical physics; never derive horizontal speed from stale server velocity.
            boat.setVelocity(new Vector(vx, boat.getVelocity().getY(), vz));
            diagnostics.put(player.getUniqueId(), String.format(Locale.ROOT,
                    "Замер за 2 тика: %.2f блоков/с при 20 TPS; отправлено %.3f блока/тик.",
                    measuredSpeed * 20, requestedSpeed));
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
            if (!configError.isEmpty()) sender.sendMessage("Ошибка конфига: " + configError);
            sender.sendMessage("Лодки: " + (active ? "ускорение включено" : "ускорение выключено")
                    + ", множитель " + multiplier + ", целевая скорость " + targetSpeed + " блоков/тик.");
            sender.sendMessage("/boatspeed <число>=1|on|off|reload|debug>");
            return true;
        }
        if (args.length != 1) return false;
        String value = args[0].toLowerCase(Locale.ROOT);
        if (value.equals("debug")) {
            sender.sendMessage("VoidFastBoats " + getPluginMeta().getVersion() + ": "
                    + (active ? "ON" : "OFF") + ", множитель " + multiplier + ", целевая скорость " + targetSpeed);
            if (!configError.isEmpty()) sender.sendMessage("Ошибка конфига: " + configError);
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
            if (loaded) settingsValid = true;
            // Validation is atomic: a failed reload keeps the previous working settings.
            sender.sendMessage(loaded ? "Конфиг лодок перезагружен."
                    : "Ошибка конфига: " + configError + ". Предыдущие настройки остались в памяти.");
            return true;
        }
        if (!configError.isEmpty()) {
            if (value.equals("off")) {
                active = false;
                lastPositions.clear();
                sender.sendMessage("Разгон выключен в памяти. Конфиг с ошибкой не перезаписан.");
                return true;
            }
            sender.sendMessage("Сначала исправьте config.yml: " + configError + ". Затем /boatspeed reload.");
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
                sender.sendMessage("Укажите конечное число >= 1, on, off, reload или debug.");
                return true;
            }
            if (!SpeedMath.validMultiplier(requested)) {
                sender.sendMessage("Множитель должен быть конечным числом >= 1, без верхнего предела.");
                return true;
            }
            multiplier = requested;
            targetSpeed = SpeedMath.targetForMultiplier(requested);
            active = true;
            getConfig().set("multiplier", multiplier);

            getConfig().set("enabled", active);
        }
        lastPositions.clear();
        getConfig().set("max-speed", null); // Obsolete setting, no longer used.
        saveConfig();
        sender.sendMessage("Настройки сохранены: ускорение " + (active ? "включено" : "выключено")
                + ", множитель " + multiplier + ", целевая скорость " + targetSpeed + " блоков/тик. Настройка применяется ко всем лодкам в разрешённых мирах.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("voidfastboats.admin") || args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("1", "1.5", "2", "3", "5", "10", "15", "20", "50", "80", "100", "on", "off", "reload", "debug").stream()
                .filter(value -> value.startsWith(prefix)).toList();
    }
}
