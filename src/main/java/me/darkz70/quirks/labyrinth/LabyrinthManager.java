package me.darkz70.quirks.labyrinth;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Allay;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

/**
 * Мир-преисподняя: пустота + лабиринт с клетками, где сидят заточённые души.
 * Создаётся командой /quirk lab create, телепорт — /quirk lab tp.
 */
public final class LabyrinthManager {

    private record BlockPlace(int x, int y, int z, Material material) {}

    private final VoidQuirksPlugin plugin;
    private final File stateFile;
    private YamlConfiguration state;

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

    private int cells() {
        return plugin.getConfig().getInt("labyrinth.cells", 19);
    }

    private int cellSize() {
        return plugin.getConfig().getInt("labyrinth.cell-size", 5);
    }

    private int wallHeight() {
        return plugin.getConfig().getInt("labyrinth.wall-height", 5);
    }

    private Material material(String path, Material fallback) {
        Material mat = Material.matchMaterial(plugin.getConfig().getString(path, fallback.name()));
        return mat != null ? mat : fallback;
    }

    public boolean isBuilt() {
        return state.getBoolean("labyrinth.built");
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

    /** Позиция массива лабиринта -> координата блока в мире. */
    private int offset(int index) {
        return (index / 2) * cellSize() + (index % 2);
    }

    /** Точка входа (центр начальной ячейки). */
    public Location entrance(World world) {
        return new Location(world, 2.5, floorY() + 1, 2.5, 180f, 0f);
    }

    // ---------- загрузка/создание мира ----------

    /** Подхватывает уже существующий мир при старте сервера. */
    public void loadIfExists() {
        File dir = new File(Bukkit.getWorldContainer(), worldName());
        if (!dir.isDirectory() || world() != null) return;
        World world = createWorldObject();
        if (world != null) {
            plugin.getLogger().info("Мир лабиринта загружен: " + worldName());
            purgeDragons(world);
        }
    }

    /** Никаких эндер-драконов в преисподней — убираем, если вдруг есть. */
    private void purgeDragons(World world) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
                dragon.remove();
            }
        }, 20L);
    }

    @Nullable
    private World createWorldObject() {
        WorldCreator creator = new WorldCreator(worldName());
        creator.environment(World.Environment.THE_END); // тьма преисподней
        creator.generator(new VoidWorldGenerator());
        try {
            return creator.createWorld();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось создать мир лабиринта", ex);
            return null;
        }
    }

    // ---------- команда create ----------

    public void create(CommandSender sender) {
        if (isBuilt() && world() != null) {
            Msg.send(sender, "lab-exists");
            return;
        }
        World world = world() != null ? world() : createWorldObject();
        if (world == null) {
            return;
        }
        Msg.send(sender, "lab-building", "%world%", worldName());

        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setTime(13000L);
        purgeDragons(world);

        buildMaze(sender, world);
    }

    public void teleport(CommandSender sender, @Nullable Player target) {
        World world = world();
        if (world == null || !isBuilt()) {
            Msg.send(sender, "lab-no-world");
            return;
        }
        if (target == null) {
            Msg.send(sender, "target-offline");
            return;
        }
        target.teleport(entrance(world));
        Msg.send(target, "lab-tp");
    }

    // ---------- постройка ----------

    private void buildMaze(CommandSender sender, World world) {
        int cells = cells();
        int cellSize = cellSize();
        int wallHeight = wallHeight();
        int floor = floorY();
        Material wallMat = material("labyrinth.wall-block", Material.OBSIDIAN);
        Material accentMat = material("labyrinth.accent-block", Material.CRYING_OBSIDIAN);
        Material floorMat = material("labyrinth.floor-block", Material.OBSIDIAN);
        int accentChance = plugin.getConfig().getInt("labyrinth.accent-chance", 12);

        Maze maze = new Maze(cells, new Random());
        Random random = new Random();
        Deque<BlockPlace> queue = new ArrayDeque<>();
        Material ceilingMat = material("labyrinth.ceiling-block", Material.OBSIDIAN);

        int span = cells * cellSize; // последний блок — стена с индексом span
        // пол и потолок
        for (int x = 0; x <= span; x++) {
            for (int z = 0; z <= span; z++) {
                queue.add(new BlockPlace(x, floor - 1, z, floorMat));
                queue.add(new BlockPlace(x, floor + wallHeight, z, ceilingMat));
            }
        }
        // стены
        int size = maze.arraySize();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (!maze.isWall(i, j)) continue;
                int xs = offset(i);
                int zs = offset(j);
                int lenX = (i % 2 == 0) ? 1 : cellSize - 1;
                int lenZ = (j % 2 == 0) ? 1 : cellSize - 1;
                for (int dx = 0; dx < lenX; dx++) {
                    for (int dz = 0; dz < lenZ; dz++) {
                        for (int dy = 0; dy < wallHeight; dy++) {
                            Material mat = random.nextInt(100) < accentChance ? accentMat : wallMat;
                            queue.add(new BlockPlace(xs + dx, floor + dy, zs + dz, mat));
                        }
                    }
                }
            }
        }

        final CommandSender requester = sender;
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int n = 0; n < 4096 && !queue.isEmpty(); n++) {
                    BlockPlace place = queue.poll();
                    world.getBlockAt(place.x(), place.y(), place.z())
                            .setType(place.material(), false);
                }
                if (queue.isEmpty()) {
                    cancel();
                    // чуть позже — декорации и сущности, когда блоки уже легли
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> decorations(requester, world, maze), 5L);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Клетки с душами в тупиках, алтарь демона в центре, площадка входа. */
    private void decorations(CommandSender sender, World world, Maze maze) {
        int floor = floorY();
        int cages = 0;
        int maxCages = plugin.getConfig().getInt("labyrinth.max-cages", 16);
        String soulName = plugin.getConfig().getString("labyrinth.soul-name", "&bЗаточённая душа");

        List<int[]> deadEnds = maze.deadEnds();
        for (int[] cell : deadEnds) {
            if (cages >= maxCages) break;
            int cx = offset(cell[0]) + 2;
            int cz = offset(cell[1]) + 2;
            if (!buildCage(world, cx, floor, cz, soulName)) continue;
            cages++;
        }

        buildAltar(world, maze);
        buildEntrance(world);
        world.setSpawnLocation(2, floor + 1, 2);

        setBuilt(true);
        int span = cells() * cellSize() + 1;
        Msg.send(sender, "lab-created",
                "%size%", span + "x" + span,
                "%cages%", String.valueOf(cages));
        if (sender instanceof Player player) {
            player.teleport(entrance(world));
        }
    }

    private boolean buildCage(World world, int cx, int floor, int cz, String soulName) {
        // кольцо прутьев 3x3x3 + крыша
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean ring = dx == -1 || dx == 1 || dz == -1 || dz == 1;
                for (int dy = 0; dy <= 2; dy++) {
                    if (ring) {
                        world.getBlockAt(cx + dx, floor + dy, cz + dz).setType(Material.IRON_BARS, false);
                    }
                }
                world.getBlockAt(cx + dx, floor + 3, cz + dz).setType(Material.IRON_BARS, false);
            }
        }
        world.getBlockAt(cx, floor + 4, cz).setType(Material.SOUL_LANTERN, false);

        // «душа» в клетке — светящийся скиталец
        Allay allay = world.spawn(new Location(world, cx + 0.5, floor + 0.2, cz + 0.5), Allay.class);
        allay.customName(Msg.color(soulName));
        allay.setCustomNameVisible(true);
        allay.setRemoveWhenFarAway(false);
        allay.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, true, false, false));
        return true;
    }

    /** «Трон демона азарта» в центре лабиринта. */
    private void buildAltar(World world, Maze maze) {
        int floor = floorY();
        int centerIndex = 2 * (maze.cells() / 2) + 1;
        int cx = offset(centerIndex) + 2;
        int cz = offset(centerIndex) + 2;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(cx + dx, floor, cz + dz).setType(Material.CRYING_OBSIDIAN, false);
            }
        }
        world.getBlockAt(cx, floor + 1, cz).setType(Material.CRYING_OBSIDIAN, false);
        world.getBlockAt(cx, floor + 2, cz).setType(Material.SOUL_SOIL, false);
        world.getBlockAt(cx, floor + 3, cz).setType(Material.SOUL_FIRE, false);
        world.getBlockAt(cx - 1, floor + 1, cz - 1).setType(Material.SOUL_LANTERN, false);
        world.getBlockAt(cx + 1, floor + 1, cz - 1).setType(Material.SOUL_LANTERN, false);
        world.getBlockAt(cx - 1, floor + 1, cz + 1).setType(Material.SOUL_LANTERN, false);
        world.getBlockAt(cx + 1, floor + 1, cz + 1).setType(Material.SOUL_LANTERN, false);
    }

    /** Площадка входа с табличкой. */
    private void buildEntrance(World world) {
        int floor = floorY();
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                world.getBlockAt(x, floor, z).setType(Material.CRYING_OBSIDIAN, false);
            }
        }
        world.getBlockAt(1, floor + 1, 1).setType(Material.SOUL_TORCH, false);
        world.getBlockAt(3, floor + 1, 3).setType(Material.SOUL_TORCH, false);

        Block signBlock = world.getBlockAt(2, floor + 1, 3);
        signBlock.setType(Material.OAK_SIGN, false);
        if (signBlock.getState() instanceof Sign sign) {
            sign.getSide(Side.FRONT).line(0, Msg.color("&5ЛАБИРИНТ ДУШ"));
            sign.getSide(Side.FRONT).line(1, Msg.color("&7Темница демона азарта"));
            sign.getSide(Side.FRONT).line(2, Msg.color("&8Освободи старичков…"));
            sign.update(true, false);
        }
    }
}
