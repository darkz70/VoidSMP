package me.darkz70.quirks.arena;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Отдельный от лабиринта мир: обычный (ванильный рельеф, обычное небо/смена
 * дня-ночи — {@link World.Environment#NORMAL}, БЕЗ своего генератора), но
 * жёстко ограниченный {@link WorldBorder} размером 256×256 блоков по центру
 * (0; 0) — за пределами границы игрок физически пройти не может, и так как
 * чанки там никогда не понадобятся, дальше границы практически всегда будет
 * пустота (воздух/невыгруженные чанки).
 * <p>
 * Команды: /quirk arena create — создать мир, /quirk arena tp [игрок] —
 * прыгнуть внутрь (c синим эффектом открытия портала), /quirk arena back
 * [игрок] — вернуться туда, откуда пришёл.
 */
public final class ArenaManager {

    private final VoidQuirksPlugin plugin;
    /** Куда вернуть игрока (запоминается при телепорте в мир). */
    private final Map<UUID, Location> returnPos = new HashMap<>();

    public ArenaManager(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- конфиг ----------

    private String worldName() {
        return plugin.getConfig().getString("arena.world", "void_arena");
    }

    private double borderSize() {
        return plugin.getConfig().getDouble("arena.border-size", 256.0);
    }

    @Nullable
    private World world() {
        return Bukkit.getWorld(worldName());
    }

    // ---------- загрузка/создание мира ----------

    /** Подхватывает уже существующий мир при старте сервера (если папка есть). */
    public void loadIfExists() {
        File dir = new File(Bukkit.getWorldContainer(), worldName());
        if (!dir.isDirectory() || world() != null) return;
        World world = createWorldObject();
        if (world != null) {
            applyBorder(world);
            plugin.getLogger().info("Мир арены загружен: " + worldName());
        }
    }

    @Nullable
    private World createWorldObject() {
        // НИКАКОГО кастомного генератора — обычный ванильный рельеф/небо/погода,
        // как в стандартном оверворлде. Ограничение площади — только WorldBorder.
        WorldCreator creator = new WorldCreator(worldName());
        creator.environment(World.Environment.NORMAL);
        try {
            return creator.createWorld();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось создать мир " + worldName(), ex);
            return null;
        }
    }

    private void applyBorder(World world) {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(borderSize());
    }

    // ---------- команда create ----------

    /** /quirk arena create — создать обычный мир 256×256 с границей + синий эффект открытия. */
    public void create(CommandSender sender) {
        if (world() != null) {
            Msg.send(sender, "arena-exists");
            return;
        }
        World world = createWorldObject();
        if (world == null) return;
        applyBorder(world);
        int spawnY = world.getHighestBlockYAt(0, 0) + 1;
        world.setSpawnLocation(0, spawnY, 0);
        if (sender instanceof Player player) {
            openingEffect(player.getLocation());
        }
        Msg.send(sender, "arena-created",
                "%world%", worldName(),
                "%size%", String.valueOf((int) borderSize()));
    }

    // ---------- телепорты ----------

    /** /quirk arena tp [игрок] — прыгнуть в мир (со входным синим эффектом). */
    public void teleport(CommandSender sender, @Nullable Player target) {
        if (target == null) {
            Msg.send(sender, "target-offline");
            return;
        }
        World world = world();
        if (world == null) {
            File dir = new File(Bukkit.getWorldContainer(), worldName());
            if (dir.isDirectory()) {
                world = createWorldObject();
                if (world != null) applyBorder(world);
            }
        }
        if (world == null) {
            Msg.send(sender, "arena-no-world");
            return;
        }
        returnPos.put(target.getUniqueId(), target.getLocation());
        Location spawn = world.getSpawnLocation().clone().add(0.5, 0, 0.5);
        target.teleport(spawn);
        openingEffect(spawn);
        Msg.send(target, "arena-tp");
    }

    /** /quirk arena back [игрок] — обратно туда, откуда пришёл (или на спавн). */
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
        Msg.send(target, "arena-back");
        if (sender != target) {
            Msg.send(sender, "arena-back", "%player%", target.getName());
        }
    }

    // ---------- эффект открытия (синий) ----------

    /** Синяя вспышка-портал: звучит и искрит, будто открывается проход в новый мир. */
    private void openingEffect(Location at) {
        World world = at.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.FLASH, at.clone().add(0, 1.5, 0), 1, 0, 0, 0, 0);
        for (Player nearby : nearbyPlayers(at, 60)) {
            nearby.playSound(at, Sound.BLOCK_END_PORTAL_SPAWN, SoundCategory.MASTER, 1f, 1.3f);
            nearby.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1f, 0.6f);
        }
        for (int i = 0; i < 4; i++) {
            final int fi = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double ring = 0.6 + fi * 0.5;
                for (int p = 0; p < 16; p++) {
                    double ang = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
                    Location pt = at.clone().add(Math.cos(ang) * ring, 0.2 + fi * 0.5, Math.sin(ang) * ring);
                    world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(40, 120, 255), 2.4f));
                }
                world.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.5 + fi * 0.5, 0),
                        6, 0.3, 0.3, 0.3, 0.01);
            }, i * 4L);
        }
    }

    private java.util.List<Player> nearbyPlayers(Location center, double radius) {
        java.util.List<Player> out = new java.util.ArrayList<>();
        World world = center.getWorld();
        if (world == null) return out;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= radius * radius) out.add(player);
        }
        return out;
    }
}
