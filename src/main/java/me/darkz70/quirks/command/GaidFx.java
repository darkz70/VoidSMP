package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;


/**
 * /gaid → «Эффекты»: сценические явления на месте админа —
 * адские разломы, гром и молнии, страх и глобальный страх.
 */
public final class GaidFx implements Listener {

    private final VoidQuirksPlugin plugin;

    private record Holder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 27, Component.empty());
        }
    }

    public GaidFx(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(), 27, Msg.color("&5Эффекты (на месте админа)"));
        inv.setItem(10, btn(Material.MAGMA_BLOCK, "&c&lАдские разломы",
                "&7Магма под ногой, 4 трещины по 9 блоков,",
                "&7огни из-под земли, пар во все стороны,",
                "&7тряска экрана у всех в 60 блоках."));
        inv.setItem(12, btn(Material.LIGHTNING_ROD, "&e&lГром и молния",
                "&7Трижды удар в твою точку, затем молнии",
                "&7по случайным блокам в 20 блоках, магма,",
                "&7белые молнии-частицы, грохот и тряска в 90 блоках."));
        inv.setItem(13, btn(Material.SOUL_LANTERN, "&b&lСтрах",
                "&7Тьма на глазах у игроков в 60 блоках,",
                "&7шквал звуков вардена и страшная скрипка."));
        inv.setItem(14, btn(Material.DRAGON_HEAD, "&5&lГлобальный страх",
                "&7То же, что страх, но на 300 блоков."));
        inv.setItem(16, btn(Material.NETHER_PORTAL, "&4&lОткрытие ада",
                "&7Всем трясётся экран 12 сек, молнии вокруг игроков,",
                "&7день и ночь скачут 7 сек, потом из-под админа растёт",
                "&7магма-круг, окантовка из магмы/незерака/светокамня/",
                "&7плачущего и обычного обсидиана, и открывается портал."));
        inv.setItem(22, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        switch (event.getRawSlot()) {
            case 10 -> hellRift(player);
            case 12 -> thunder(player);
            case 13 -> fear(player, 60);
            case 14 -> fear(player, 300);
            case 16 -> openHell(player);
            case 22 -> {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("gaid"));
                return;
            }
            default -> { return; }
        }
        player.closeInventory();
    }

    // ---------- адские разломы ----------

    private void hellRift(Player admin) {
        Location at = admin.getLocation();
        World world = at.getWorld();
        if (world == null) return;
        int bx = at.getBlockX();
        int by = at.getBlockY() - 1; // грунт под ногами
        int bz = at.getBlockZ();

        // магма 3x3 под админом
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = world.getBlockAt(bx + dx, by, bz + dz);
                if (canTurn(block)) block.setType(Material.MAGMA_BLOCK);
            }
        }
        // огни из-под земли (три вспышки)
        for (int burst = 0; burst < 3; burst++) {
            int ticks = burst * 8;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < 24; i++) {
                    double ox = ThreadLocalRandom.current().nextDouble(-4, 4);
                    double oz = ThreadLocalRandom.current().nextDouble(-4, 4);
                    world.spawnParticle(Particle.FLAME, bx + 0.5 + ox, by + 1.1, bz + 0.5 + oz,
                            4, 0.15, 0.25, 0.15, 0.01);
                }
            }, ticks);
        }
        // 4 трещины-линии по 9 блоков в случайные стороны
        for (int line = 0; line < 4; line++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            int ticks = line * 6;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int step = 1; step <= 9; step++) {
                    int px = bx + (int) Math.round(Math.cos(angle) * step);
                    int pz = bz + (int) Math.round(Math.sin(angle) * step);
                    Block block = world.getBlockAt(px, by, pz);
                    if (!canTurn(block)) continue;
                    block.setType(Material.MAGMA_BLOCK);
                    world.spawnParticle(Particle.LAVA, px + 0.5, by + 1.2, pz + 0.5, 2, 0.2, 0.1, 0.2, 0);
                    world.playSound(new Location(world, px, by, pz),
                            Sound.BLOCK_LAVA_POP, SoundCategory.BLOCKS, 1.2f, 0.6f);
                }
            }, ticks);
        }
        // пар из-под блока админа во все стороны
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.spawnParticle(Particle.WHITE_SMOKE, bx + 0.5, by + 1.0, bz + 0.5,
                    160, 4.0, 0.4, 4.0, 0.05);
            world.playSound(at, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 2f, 0.7f);
        }, 12L);
        shakeNearby(admin.getLocation(), 60, 3);
    }

    private static boolean canTurn(Block block) {
        return switch (block.getType()) {
            case AIR, BEDROCK, BARRIER, COMMAND_BLOCK, STRUCTURE_BLOCK,
                 WATER, LAVA, OBSIDIAN, MAGMA_BLOCK -> false;
            default -> block.getType().isSolid();
        };
    }

    // ---------- гром и молния ----------

    private void thunder(Player admin) {
        Location at = admin.getLocation();
        World world = at.getWorld();
        if (world == null) return;
        int bx = at.getBlockX();
        int by = at.getBlockY() - 1;
        int bz = at.getBlockZ();

        // три удара прямо в админа
        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                world.strikeLightningEffect(at);
                thunderSounds(at, 90);
            }, i * 10L);
        }
        Block base = world.getBlockAt(bx, by, bz);
        if (canTurn(base)) base.setType(Material.MAGMA_BLOCK);

        // 14 ударов по случайным точкам в 20 блоках, вразнобой
        for (int i = 0; i < 14; i++) {
            int ticks = 40 + i * 4 + ThreadLocalRandom.current().nextInt(0, 3);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double a = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
                double r = ThreadLocalRandom.current().nextDouble(3, 20);
                int px = bx + (int) Math.round(Math.cos(a) * r);
                int pz = bz + (int) Math.round(Math.sin(a) * r);
                int py = world.getHighestBlockYAt(px, pz) + 1;
                Location strike = new Location(world, px + 0.5, py, pz + 0.5);
                world.strikeLightningEffect(strike);
                Block floor = world.getBlockAt(px, py - 1, pz);
                if (canTurn(floor)) floor.setType(Material.MAGMA_BLOCK);
                lightningParticles(world, strike);
                thunderSounds(strike, 90);
            }, ticks);
        }
        shakeNearby(at, 90, 4);
    }

    /** Белые частицы, идущие зигзагом вверх — «силуэт молнии». */
    private void lightningParticles(World world, Location strike) {
        double x = strike.getX();
        double z = strike.getZ();
        for (int h = 0; h < 6; h++) {
            x += ThreadLocalRandom.current().nextDouble(-0.4, 0.4);
            z += ThreadLocalRandom.current().nextDouble(-0.4, 0.4);
            world.spawnParticle(Particle.END_ROD, x, strike.getY() + h, z, 1, 0, 0, 0, 0);
        }
        world.spawnParticle(Particle.FLASH, strike, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.WHITE_SMOKE, strike, 12, 0.5, 0.5, 0.5, 0.0);
    }

    private void thunderSounds(Location at, double radius) {
        for (Player player : playersIn(at, radius)) {
            player.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 3f, 0.7f);
        }
    }

    // ---------- страх / глобальный страх ----------

    private void fear(Player admin, double radius) {
        List<Player> victims = playersIn(admin.getLocation(), radius);
        for (Player victim : victims) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 300, 0, true, false, true));
        }
        // шквал вардена + скрипка
        Location at = admin.getLocation();
        for (Player victim : victims) {
            victim.playSound(at, Sound.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 1.0f, 0.8f);
            victim.playSound(victim.getLocation(), "music_disc.far", SoundCategory.RECORDS, 0.8f, 0.9f);
        }
        for (int i = 0; i < 4; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player victim : victims) {
                    if (!victim.isOnline()) continue;
                    Sound growing = switch ((int) Math.floor(Math.random() * 4)) {
                        case 0 -> Sound.ENTITY_WARDEN_HEARTBEAT;
                        case 1 -> Sound.ENTITY_WARDEN_LISTENING_ANGRY;
                        case 2 -> Sound.ENTITY_WARDEN_SNIFF;
                        default -> Sound.ENTITY_WARDEN_ANGRY;
                    };
                    victim.playSound(victim.getLocation(), growing, SoundCategory.HOSTILE, 1.0f, 0.8f);
                }
            }, 20L * i + 10L);
        }
        admin.sendMessage(Msg.color("&5Страх (" + (int) radius + " блоков) запущен: "
                + victims.size() + " игроков."));
    }

    // ---------- открытие ада ----------

    private static final Material[] HELL_MIX = {
        Material.MAGMA_BLOCK, Material.NETHERRACK, Material.GLOWSTONE,
        Material.CRYING_OBSIDIAN, Material.OBSIDIAN};

    /** Сценическое открытие ада (~20 секунд). */
    private void openHell(Player admin) {
        World world = admin.getWorld();
        final int by = admin.getLocation().getBlockY() - 1;
        final int bx = admin.getLocation().getBlockX();
        final int bz = admin.getLocation().getBlockZ();
        final long savedTime = world.getTime();
        final Map<Block, Material> original = new IdentityHashMap<>();
        final Map<Block, Boolean> border = new IdentityHashMap<>();

        new BukkitRunnable2().start(this, world, bx, by, bz, savedTime, original, border);
    }

    /** Запись очередной клетки круга (в владении анимации). */
    static Block claim(Map<Block, Material> original, World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        original.computeIfAbsent(block, Block::getType);
        return block;
    }

    /** Кольцо точек круга (целочисленные координаты, без дублей). */
    static List<int[]> circlePoints(int cx, int cz, int radius) {
        List<int[]> out = new ArrayList<>();
        int steps = Math.max(8, radius * 6);
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < steps; i++) {
            double a = (Math.PI * 2 * i) / steps;
            int x = cx + (int) Math.round(Math.cos(a) * radius);
            int z = cz + (int) Math.round(Math.sin(a) * radius);
            long key = (((long) x) << 32) | (z & 0xFFFFFFFFL);
            if (seen.add(key)) out.add(new int[]{x, z});
        }
        return out;
    }

    private int floorY(World world, int baseY, int x, int z) {
        int hi = world.getHighestBlockYAt(x, z) - 1;
        return Math.max(baseY - 2, Math.min(baseY + 3, hi));
    }

    // ---------- сервис ----------

    private List<Player> playersIn(Location center, double radius) {
        List<Player> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(center.getWorld())
                    && player.getLocation().distanceSquared(center) <= radius * radius) {
                out.add(player);
            }
        }
        return out;
    }

    /** «Тряска экрана»: тройной наклон камеры хurt-анимацией. */
    private void shakeNearby(Location center, double radius, int times) {
        for (int i = 0; i < times; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player player : playersIn(center, radius)) {
                    player.playHurtAnimation(90);
                }
            }, i * 10L);
        }
    }

    private ItemStack btn(Material mat, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Msg.color(l));
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> {
            meta.displayName(Msg.color(name));
            meta.lore(lines);
        });
        return stack;
    }


}