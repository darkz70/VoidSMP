package ru.voidsmp.fastboats;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import java.time.Instant;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Input;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;


public final class FastBoatsPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, Sample> lastPositions = new HashMap<>();
    private final Map<UUID, String> diagnostics = new HashMap<>();
    private final Map<UUID, Attempt> lastAttempts = new HashMap<>();
    private boolean active;
    private double multiplier;
    private double targetSpeed;
    private VelocityTransport transport;
    private String transportError = "";
    private final Map<UUID, Long> sentPackets = new HashMap<>();
    private record Sample(Location location, UUID driver, long nanoTime) {}
    private record Attempt(Instant when, String message) {}
    private String configError = "";
    private List<String> worlds = List.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        boolean valid = loadSettings();
        if (!valid) {
            active = false;
            getLogger().severe("Разгон выключен: " + configError + ". Исправьте config.yml и выполните /boatspeed reload.");
        }
        Objects.requireNonNull(getCommand("boatspeed")).setExecutor(this);
        Objects.requireNonNull(getCommand("boatdebug")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        initializeTransport();
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

    private void initializeTransport() {
        transport = null;
        transportError = "";
        try {
            if (!getServer().getPluginManager().isPluginEnabled("packetevents")) {
                throw new IllegalStateException("Установите и включите PacketEvents 2.13.0+ и перезапустите сервер");
            }
            transport = new PacketVelocityTransport();
            getLogger().info("Транспорт: PacketEvents, прямые пакеты движения лодки водителю.");
        } catch (RuntimeException | LinkageError error) {
            transportError = error.getClass().getSimpleName() + ": " + error.getMessage();
            getLogger().severe("Разгон недоступен: " + transportError + ". Команда boatdebug доступна.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBoatTeleport(EntityTeleportEvent event) {
        lastPositions.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        lastPositions.values().removeIf(sample -> sample.driver().equals(event.getPlayer().getUniqueId()));
    }

    @Override
    public void onDisable() {
        lastPositions.clear();
        diagnostics.clear();
        lastAttempts.clear();
        sentPackets.clear();
        transport = null;
        // No zero-velocity packet: releasing the throttle leaves vanilla deceleration intact.
    }

    private void attempt(Player player, String message) {
        diagnostics.put(player.getUniqueId(), message);
        lastAttempts.put(player.getUniqueId(), new Attempt(Instant.now(), message));
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
            long now = System.nanoTime();
            Sample previousSample = lastPositions.put(id, new Sample(current, player.getUniqueId(), now));
            if (previousSample == null || !previousSample.driver().equals(player.getUniqueId())
                    || !previousSample.location().getWorld().equals(current.getWorld())) {
                diagnostics.put(player.getUniqueId(), "Сбор первого образца движения; продолжайте грести.");
                continue;
            }
            Location previous = previousSample.location();
            double seconds = (now - previousSample.nanoTime()) / 1_000_000_000.0;
            if (seconds > 0.5 || seconds <= 0 || Math.abs(current.getY() - previous.getY()) > 0.5) {
                attempt(player, "Нестабильный образец: задержка тика или скачок высоты; пропускаем импульс.");
                continue;
            }
            double dx = current.getX() - previous.getX();
            double dz = current.getZ() - previous.getZ();
            // Two-tick position samples reflect client movement, unlike server deltaMovement.
            double distance = Math.hypot(dx, dz);
            double measuredSpeed = distance / 2.0;
            Input input = player.getCurrentInput();
            double requestedSpeed = SpeedMath.impulseSpeed(measuredSpeed, multiplier, input.isForward());
            if (requestedSpeed <= 0 || distance < 0.002) {
                diagnostics.put(player.getUniqueId(), "Импульс не нужен: нет движения или достигнута выбранная скорость.");
                continue;
            }
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
                attempt(player, diagnostics.get(player.getUniqueId()));
                continue;
            }
            try {
                double vy = (current.getY() - previous.getY()) / 2.0;
                transport.send(player, boat, vx, vy, vz);
                sentPackets.merge(player.getUniqueId(), 1L, Long::sum);
                attempt(player, String.format(Locale.ROOT,
                        "Замер: %.2f блоков/с (реальное время); %.3f блока/тик. Пакет лодке #%d: %.3f блока/тик, в очереди отправки.",
                        distance / seconds, measuredSpeed, boat.getEntityId(), requestedSpeed));
            } catch (IllegalStateException error) {
                // A disconnected/unsupported client must not stop other boats.
                attempt(player, "Импульс пропущен: " + error.getMessage());
            } catch (RuntimeException | LinkageError error) {
                // Stop once, retain diagnostics, and don't flood console every two ticks.
                transportError = error.getClass().getSimpleName() + ": " + error.getMessage();
                transport = null;
                attempt(player, "Ошибка отправки: " + transportError);
                getLogger().log(java.util.logging.Level.SEVERE,
                        "Остановлена отправка импульсов. Проверьте PacketEvents; затем /boatspeed reload.", error);
            }
        }
        lastPositions.keySet().retainAll(tracked);
        lastAttempts.keySet().retainAll(online);
        sentPackets.keySet().retainAll(online);
    }

    private String inactiveReason(Player player) {
        if (transport == null) return "Нет транспорта: " + transportError;
        if (!getServer().getPluginManager().isPluginEnabled("packetevents")) return "PacketEvents выключен.";
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

    private void debug(CommandSender sender, Player player) {
        sender.sendMessage("Игрок " + player.getName() + ": "
                + Objects.requireNonNullElse(inactiveReason(player), "Условия разгона выполнены."));
        sender.sendMessage("Последний цикл: " + diagnostics.getOrDefault(player.getUniqueId(), "ещё нет данных")
                + " Пакетов за подключение: " + sentPackets.getOrDefault(player.getUniqueId(), 0L));
        Attempt last = lastAttempts.get(player.getUniqueId());
        if (last != null) sender.sendMessage("Последняя попытка [" + last.when() + "]: " + last.message());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voidfastboats.admin")) {
            sender.sendMessage("Нет права voidfastboats.admin.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("boatdebug")) {
            String[] debugArgs = new String[args.length + 1];
            debugArgs[0] = "debug";
            System.arraycopy(args, 0, debugArgs, 1, args.length);
            args = debugArgs;
        }
        if (args.length == 0) {
            if (!configError.isEmpty()) sender.sendMessage("Ошибка конфига: " + configError);
            sender.sendMessage("Лодки: " + (active ? "ускорение включено" : "ускорение выключено")
                    + ", множитель " + multiplier + ", целевая скорость " + targetSpeed + " блоков/тик.");
            sender.sendMessage("/boatspeed <число|on|off|reload>; /boatdebug [ник]");
            return true;
        }
        String value = args[0].toLowerCase(Locale.ROOT);
        if (value.equals("debug")) {
            sender.sendMessage("VoidFastBoats " + getPluginMeta().getVersion() + ": "
                    + (active ? "ON" : "OFF") + ", множитель " + multiplier + ", целевая скорость " + targetSpeed);
            if (!configError.isEmpty()) sender.sendMessage("Ошибка конфига: " + configError);
            sender.sendMessage("Транспорт: " + (transport == null ? "недоступен: " + transportError : "PacketEvents / ENTITY_VELOCITY"));
            if (args.length > 2) return false;
            if (args.length == 2) {
                Player player = getServer().getPlayerExact(args[1]);
                if (player == null) sender.sendMessage("Игрок не в сети: " + args[1]);
                else debug(sender, player);
            } else if (sender instanceof Player player) {
                debug(sender, player);
            } else {
                sender.sendMessage("Для одного игрока: boatdebug <ник>. Сейчас онлайн: " + getServer().getOnlinePlayers().size());
                for (Player player : getServer().getOnlinePlayers()) debug(sender, player);
            }
            return true;
        }
        if (args.length != 1) return false;
        if (value.equals("reload")) {
            lastPositions.clear();
            reloadConfig();
            boolean loaded = loadSettings();
            initializeTransport();
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
        if (transport == null) sender.sendMessage("Разгон недоступен: " + transportError);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("voidfastboats.admin")) return List.of();
        if ((command.getName().equalsIgnoreCase("boatdebug") && args.length == 1)
                || (args.length == 2 && args[0].equalsIgnoreCase("debug"))) {
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            return getServer().getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (command.getName().equalsIgnoreCase("boatdebug") || args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("1", "1.5", "2", "3", "5", "10", "15", "20", "50", "80", "100", "on", "off", "reload", "debug").stream()
                .filter(value -> value.startsWith(prefix)).toList();
    }
}
