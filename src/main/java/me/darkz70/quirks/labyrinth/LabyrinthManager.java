package me.darkz70.quirks.labyrinth;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.GameRule;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Внутренний мир (раньше «лабиринт душ»): пустота/готовый мир с телепортом туда-обратно.
 *
 * Команды: /quirk lab create — создать мир (если папки нет — чистый void),
 * /quirk lab tp — прыгнуть туда, /quirk lab back — вернуться туда, откуда пришёл.
 *
 * Лабиринт мы больше НЕ строим: мир отдаётся целиком вам — загрузите туда свои структуры
 * (схемы WorldEdit, датапаки) или соберите вручную.
 */
public final class LabyrinthManager {

    private final VoidQuirksPlugin plugin;
    private final File stateFile;
    private final YamlConfiguration state;
    /** Куда вернуть игрока (запоминается при телепорте в мир). */
    private final Map<UUID, Location> returnPos = new HashMap<>();

    public LabyrinthManager(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "state.yml");
        this.state = YamlConfiguration.loadConfiguration(stateFile);
    }

    // ---------- конфиг ----------

    private String worldName() {
        return plugin.getConfig().getString("labyrinth.world", "void_labyrinth");
    }

    private int floorY() {
        return plugin.getConfig().getInt("labyrinth.floor-y", 60);
    }

    public boolean isBuilt() {
        return state.getBoolean("labyrinth.built")
                || plugin.getConfig().getBoolean("labyrinth.prebuilt", false);
    }

    private void setBuilt(boolean built) {
        state.set("labyrinth.built", built);
        try {
            state.save(stateFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Не удалось сохранить state.yml", ex);
        }
    }

    @Nullable
    private World world() {
        return Bukkit.getWorld(worldName());
    }

    /** Точка входа: явная координата из конфига или центр начальной ячейки. */
    public Location entrance(World world) {
        org.bukkit.configuration.ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection("labyrinth.entrance");
        if (sec != null) {
            return new Location(world,
                    sec.getDouble("x", 2.5),
                    sec.getDouble("y", floorY() + 1),
                    sec.getDouble("z", 2.5), 180f, 0f);
        }
        return new Location(world, 2.5, floorY() + 1, 2.5, 180f, 0f);
    }

    // ---------- загрузка/создание мира ----------

    /** Подхватывает уже существующий мир при старте сервера (если папка есть). */
    public void loadIfExists() {
        File dir = new File(Bukkit.getWorldContainer(), worldName());
        if (!dir.isDirectory() || world() != null) return;
        World world = createWorldObject(true);
        if (world != null) {
            plugin.getLogger().info("Мир загружен как есть: " + worldName());
        }
    }

    @Nullable
    private World createWorldObject() {
        return createWorldObject(false);
    }

    private boolean vanillaGen() {
        return "vanilla".equalsIgnoreCase(
                plugin.getConfig().getString("labyrinth.generation", "vanilla"));
    }

    @Nullable
    private World createWorldObject(boolean existing) {
        WorldCreator creator = new WorldCreator(worldName());
        if (!existing) {
            creator.environment(World.Environment.NORMAL); // обычное небо
            if (!vanillaGen()) {
                creator.generator(new VoidWorldGenerator()); // generation: void — пустой мир
            } // generation: vanilla — обычная ванильная генерация
        } // чужая папка мира подхватывается КАК ЕСТЬ
        try {
            return creator.createWorld();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось создать мир " + worldName(), ex);
            return null;
        }
    }

    // ---------- команда create ----------

    /** /quirk lab create — принять/создать мир. НИКАКОГО строительства: мир отдаётся вам. */
    public void create(CommandSender sender) {
        if (!plugin.getConfig().getBoolean("labyrinth.create-allowed", true)) {
            Msg.send(sender, "lab-command-off");
            return;
        }
        if (world() != null) {
            setBuilt(true);
            Msg.send(sender, "lab-world-ready");
            return;
        }
        File dir = new File(Bukkit.getWorldContainer(), worldName());
        World world = createWorldObject(dir.isDirectory());
        if (world == null) {
            return;
        }
        if (dir.isDirectory()) {
            // чужая папка — лежит как была
            setBuilt(true);
            Msg.send(sender, "lab-prebuilt");
            return;
        }
        // свежий мир: спокойный день, погода выключена, бордюр из конфига (5к на 5к)
        world.setTime(6000L);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        int size = plugin.getConfig().getInt("labyrinth.world-size", 5000);
        world.getWorldBorder().setSize(size); // setSize — ШИРИНА: 5000 = 5000x5000
        setBuilt(true);
        Msg.send(sender, "lab-created", "%world%", worldName());
    }

    // ---------- телепорты ----------

    /** /quirk lab tp [игрок] — прыгнуть в мир. */
    public void teleport(CommandSender sender, @Nullable Player target) {
        if (target == null) {
            Msg.send(sender, "target-offline");
            return;
        }
        World world = world();
        if (world == null) {
            // робко попытаемся поднять папку готового мира
            File dir = new File(Bukkit.getWorldContainer(), worldName());
            if (dir.isDirectory()) {
                world = createWorldObject(true);
            }
        }
        if (world == null || !isBuilt()) {
            Msg.send(sender, "lab-no-world");
            return;
        }
        returnPos.put(target.getUniqueId(), target.getLocation());
        target.teleport(entrance(world));
        Msg.send(target, "lab-tp");
    }

    /** /quirk lab back [игрок] — обратно туда, откуда пришёл (или на спавн). */
    public void back(CommandSender sender, @Nullable Player target) {
        if (target == null) {
            Msg.send(sender, "target-offline");
            return;
        }
        Location back = returnPos.remove(target.getUniqueId());
        if (back == null || back.getWorld() == null
                || back.getWorld().getName().equals(worldName())) {
            back = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        target.teleport(back);
        Msg.send(target, "lab-back");
        if (sender != target) {
            Msg.send(sender, "lab-back", "%player%", target.getName());
        }
    }
}
