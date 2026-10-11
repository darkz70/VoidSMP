package ru.voidsmp.fastboats;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Input;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class FastBoatsPlugin extends JavaPlugin {
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
        if (!SpeedMath.inRange(nextMultiplier, 1, 5) || !SpeedMath.inRange(nextMax, 0.1, 2)) {
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
        if (!active || multiplier == 1.0) return;
        // Only online drivers, no full-world entity scans or persistent entity references.
        for (Player player : getServer().getOnlinePlayers()) {
            if (!(player.getVehicle() instanceof Boat boat) || !boat.isValid()) continue;
            if (boat.getPassengers().isEmpty() || !boat.getPassengers().getFirst().equals(player)) continue;
            if (!player.hasPermission("voidfastboats.use")) continue;
            if (!worlds.isEmpty() && !worlds.contains(boat.getWorld().getName())) continue;
            if (boat.getStatus() != Boat.Status.IN_WATER) continue;
            Input input = player.getCurrentInput();
            int direction = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
            if (direction == 0) continue;
            Vector velocity = boat.getVelocity();
            double[] boosted = SpeedMath.boost(velocity.getX(), velocity.getZ(),
                    boat.getLocation().getYaw(), direction, multiplier, maxSpeed);
            // Preserve vertical physics. No teleportation and no forced chunk loading.
            boat.setVelocity(velocity.setX(boosted[0]).setZ(boosted[1]));
        }
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
            sender.sendMessage("/boatspeed <1-5|on|off|reload>");
            return true;
        }
        if (args.length != 1) return false;
        String value = args[0].toLowerCase(Locale.ROOT);
        if (value.equals("reload")) {
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
                    : "Ошибка конфига: проверьте enabled, multiplier (1–5), max-speed (0.1–2) и worlds. Старые настройки сохранены в памяти.");
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
                sender.sendMessage("Укажите число от 1 до 5, on, off или reload.");
                return true;
            }
            if (!SpeedMath.inRange(requested, 1, 5)) {
                sender.sendMessage("Множитель должен быть конечным числом от 1 до 5.");
                return true;
            }
            multiplier = requested;
            maxSpeed = SpeedMath.limitForMultiplier(requested);
            active = true;
            getConfig().set("multiplier", multiplier);
            getConfig().set("max-speed", maxSpeed);
            getConfig().set("enabled", active);
        }
        saveConfig();
        sender.sendMessage("Настройки сохранены: ускорение " + (active ? "включено" : "выключено")
                + ", множитель " + multiplier + ", лимит " + maxSpeed + " блоков/тик. Настройка применяется ко всем лодкам в разрешённых мирах.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("voidfastboats.admin") || args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("1", "1.5", "2", "3", "5", "on", "off", "reload").stream()
                .filter(value -> value.startsWith(prefix)).toList();
    }
}
