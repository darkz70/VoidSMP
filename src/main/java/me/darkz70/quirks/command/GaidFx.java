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
        inv.setItem(15, btn(Material.DEEPSLATE_TILES, "&4Большой разлом",
                "&7В раз 5 масштабнее Адских разломов: длина трещин,",
                "&7огонь, пар и тряска (300 блоков)."));
        inv.setItem(16, btn(Material.NETHERRACK, "&4&lОткрытие ада",
                "&7Всем трясётся экран 12 сек, молнии вокруг игроков,",
                "&7день и ночь скачут 7 сек, потом перед админом встаёт",
                "&7ВЕРТИКАЛЬНЫЙ круг магмы (маленький), по краям — короткие",
                "&7вертикальные разломы, окантовка: магма/незерак/светокамень/",
                "&7чернокамень/плачущий и обычный обсидиан — и портал в ад."));
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
            case 15 -> hellRift(player, 5.0, 4);
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
        hellRift(admin, 1.0, 4);
    }

    /** Адские разломы со шкалой: scale=5 — «Большой разлом». */
    private void hellRift(Player admin, double scale, int lines) {
        Location at = admin.getLocation();
        World world = at.getWorld();
        if (world == null) return;
        int bx = at.getBlockX();
        int by = at.getBlockY() - 1; // грунт под ногами
        int bz = at.getBlockZ();

        // магма-подложка под админом
        int pad = Math.max(1, (int) Math.round(scale * 1.4));
        for (int dx = -pad; dx <= pad; dx++) {
            for (int dz = -pad; dz <= pad; dz++) {
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
                    world.spawnParticle(Particle.FLAME, bx + 0.5 + ox * Math.min(3, scale),
                            by + 1.1, bz + 0.5 + oz * Math.min(3, scale),
                            (int) (4 * scale), 0.15, 0.25, 0.15, 0.01);
                }
            }, ticks);
        }
        // 4 трещины-линии по 9 блоков в случайные стороны
        for (int line = 0; line < lines; line++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            int ticks = line * 6;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                int len = (int) Math.round(9 * scale);
                for (int step = 1; step <= len; step++) {
                    int px = bx + (int) Math.round(Math.cos(angle) * step);
                    int pz = bz + (int) Math.round(Math.sin(angle) * step);
                    Block block = world.getBlockAt(px, by, pz);
                    if (!canTurn(block)) continue;
                    block.setType(Material.MAGMA_BLOCK);
                    world.spawnParticle(Particle.LAVA, px + 0.5, by + 1.2, pz + 0.5, 2, 0.2, 0.1, 0.2, 0);
                    world.playSound(new Location(world, px, by, pz),
                            Sound.BLOCK_LAVA_POP, SoundCategory.BLOCKS, 1.2f, 0.6f);
                }
                if (scale >= 2) { // большой разлом: кратер на конце трещины
                    int ex = bx + (int) Math.round(Math.cos(angle) * len);
                    int ez = bz + (int) Math.round(Math.sin(angle) * len);
                    crater(world, new Location(world, ex, by, ez), (int) Math.round(2 + scale * 0.6));
                }
            }, ticks);
        }
        if (scale >= 2) { // и воронка под эпицентром
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    crater(world, new Location(world, bx, by - 1, bz), (int) Math.round(2 + scale * 0.8)), 30L);
        }
        // пар из-под блока админа во все стороны
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            double spread = Math.min(15, 4.0 * scale);
            world.spawnParticle(Particle.WHITE_SMOKE, bx + 0.5, by + 1.0, bz + 0.5,
                    (int) (160 * scale), spread, 0.4, spread, 0.05);
            world.playSound(at, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 2f, 0.7f);
        }, 12L);
        shakeNearby(admin.getLocation(), 60 * scale, 3);
    }


    /** Кратер: выгрызает полусферу радиуса r вниз от center, кромка — обожжённый камень. */
    private void crater(World world, Location center, int r) {
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= 0; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
                    Block b = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (!canTurn(b) || b.getType() == Material.MAGMA_BLOCK) continue;
                    if (d <= r - 1.2) {
                        b.setType(Material.AIR);                 // выемка
                    } else if (d <= r + 0.4) {
                        b.setType(HELL_MIX[ThreadLocalRandom.current()
                                .nextInt(HELL_MIX.length)]);     // обожжённая кромка
                    }
                }
            }
        }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 3, 0.4, 0.4, 0.4, 0);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.4f, 0.7f);
        // языки пламени на кромке — разлом ещё дышит
        for (int i = 0; i < r * 2; i++) {
            double a = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            Block edge = world.getBlockAt(cx + (int) Math.round(Math.cos(a) * r),
                    cy, cz + (int) Math.round(Math.sin(a) * r));
            Block above = edge.getRelative(0, 1, 0);
            if (above.getType().isAir() && edge.getType().isSolid()) {
                above.setType(Material.FIRE);
            }
        }
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
            // «скрипка» звучит ОТ САМОГО ИГРОКА (а не из блока-невидимки) и гаснет через 20 сек
            victim.playSound(victim, "music_disc.far", SoundCategory.RECORDS, 0.8f, 0.9f);
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,
                    () -> victim.stopSound("music_disc.far", SoundCategory.RECORDS), 400L);
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
        Material.CRYING_OBSIDIAN, Material.OBSIDIAN, Material.BLACKSTONE};

    /** Сценическое открытие ада (~20 секунд): вертикальный круг 4 радиуса. */
    private void openHell(Player admin) {
        World world = admin.getWorld();
        final int by = admin.getLocation().getBlockY() - 1;
        final int bx = admin.getLocation().getBlockX();
        final int bz = admin.getLocation().getBlockZ();
        // стена перпендикулярна взгляду админа: смотрим вдоль X -> стена через Z и наоборот
        double yawRad = Math.toRadians(admin.getLocation().getYaw());
        boolean wallAlongX = Math.abs(Math.cos(yawRad)) > Math.abs(Math.sin(yawRad));
        final long savedTime = world.getTime();
        final Map<Block, Material> original = new IdentityHashMap<>();

        // разрыв мира рвёт поверхность: кратер под кругом ада
        Bukkit.getScheduler().runTaskLater(plugin, () -> crater(world,
                new Location(world, bx, by, bz), 3), 40L);
        new BukkitRunnable2().start(this, world, bx, by, bz, savedTime, original, wallAlongX);
    }

    /** Запись очередной клетки круга (в владении анимации). */
    static Block claim(Map<Block, Material> original, World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        original.computeIfAbsent(block, Block::getType);
        return block;
    }

    /** Точки вертикального кольца на стене (без дублей). alongX: u -> x, иначе u -> z. */
    static List<int[]> circleWall(int cu, int cy, int radius) {
        List<int[]> out = new ArrayList<>();
        int steps = Math.max(12, radius * 8);
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < steps; i++) {
            double a = (Math.PI * 2 * i) / steps;
            int u = cu + (int) Math.round(Math.cos(a) * radius);
            int v = cy + (int) Math.round(Math.sin(a) * radius);
            long key = (((long) u) << 32) | (v & 0xFFFFFFFFL);
            if (seen.add(key)) out.add(new int[]{u, v});
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



    /** Задача «Открытие ада»: тряска 12 сек, день/ночь и молнии 7 сек, ВЕРТИКАЛЬНЫЙ круг 4, рамка, портал. */
    private final class BukkitRunnable2 extends org.bukkit.scheduler.BukkitRunnable {

        private GaidFx fx;
        private World world;
        private Map<Block, Material> original;
        private boolean wallAlongX;
        private int bx, by, bz, maxR = 4, cy, cu, t = 0, r = 0;
        private long savedTime;
        private boolean dayNightRestored = false, portalDone = false;

        void start(GaidFx fx, World world, int bx, int by, int bz, long savedTime,
                   Map<Block, Material> original, boolean wallAlongX) {
            this.fx = fx; this.world = world;
            this.bx = bx; this.by = by; this.bz = bz;
            this.savedTime = savedTime; this.original = original;
            this.wallAlongX = wallAlongX;
            this.cy = by + maxR + 1;           // низ круга — на уровне земли
            this.cu = wallAlongX ? bx : bz;    // центр стены по горизонтали
            runTaskTimer(fx.plugin, 5L, 2L);
        }

        private Block at(int u, int y) {
            return world.getBlockAt(wallAlongX ? u : bx, y, wallAlongX ? bz : u);
        }

        private org.bukkit.Location locAt(int u, int y) {
            return new org.bukkit.Location(world,
                    (wallAlongX ? u : bx) + 0.5, y + 0.2, (wallAlongX ? bz : u) + 0.5);
        }

        @Override
        public void run() {
            t += 2;
            // 1) тряска всех экранов — 12 секунд
            if (t <= 240) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.playHurtAnimation((t / 2) % 2 == 0 ? 70 : -70);
                }
            }
            // 2) день/ночь скачут — первые 7 секунд
            if (t <= 140) {
                world.setTime((t / 10) % 2 == 0 ? 13000L : 1000L);
            } else if (!dayNightRestored) {
                world.setTime(savedTime);
                dayNightRestored = true;
            }
            // 3) молнии рядом с игроками — первые 7 секунд
            if (t <= 140 && t % 22 == 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    var p = player.getLocation().clone().add(
                            ThreadLocalRandom.current().nextDouble(-6, 6), 0,
                            ThreadLocalRandom.current().nextDouble(-6, 6));
                    world.strikeLightningEffect(p);
                    world.playSound(p, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 2f, 0.9f);
                }
            }
            // 4) вертикальный круг магмы: старый слой убирается
            if (t >= 140 && !portalDone) {
                if (r == 0) { r = 1; return; }
                if (r <= maxR) {
                    if (r > 1) {
                        for (int[] pt : circleWall(cu, cy, r - 1)) {
                            Block prev = at(pt[0], pt[1]);
                            Material back = original.get(prev);
                            if (back != null) prev.setType(back);
                        }
                    }
                    java.util.HashSet<String> seen = new java.util.HashSet<>();
                    for (int[] pt : circleWall(cu, cy, r)) {
                        Block block = at(pt[0], pt[1]);
                        if (!seen.add(block.getX() + ":" + block.getY() + ":" + block.getZ())) continue;
                        claim(original, world, block.getX(), block.getY(), block.getZ());
                        // лавовая вспышка + смена на магму
                        world.spawnParticle(Particle.LAVA, locAt(pt[0], pt[1]), 2, 0.2, 0.2, 0.2, 0);
                        if (canTurn(block)) block.setType(Material.MAGMA_BLOCK);
                    }
                    r++;
                } else {
                    // 5) окантовка: адская смесь (+ чернокамень)
                    for (int[] pt : circleWall(cu, cy, maxR)) {
                        Block block = at(pt[0], pt[1]);
                        claim(original, world, block.getX(), block.getY(), block.getZ());
                        block.setType(HELL_MIX[ThreadLocalRandom.current().nextInt(HELL_MIX.length)]);
                        world.spawnParticle(Particle.FLAME, locAt(pt[0], pt[1]), 6, 0.3, 0.3, 0.3, 0.02);
                    }
                    // 6) маленькие вертикальные разломы по краям стены
                    for (int off : new int[]{-(maxR + 2), -(maxR + 3), maxR + 2, maxR + 3}) {
                        int u = cu + off;
                        int gx = wallAlongX ? u : bx;
                        int gz = wallAlongX ? bz : u;
                        int gy = fx.floorY(world, by, gx, gz);
                        for (int h = 1; h <= 3; h++) {
                            Block rift = world.getBlockAt(gx, gy + h, gz);
                            if (canTurn(rift)) rift.setType(Material.MAGMA_BLOCK);
                            world.spawnParticle(Particle.LAVA, new org.bukkit.Location(world, gx + 0.5, gy + h + 0.6, gz + 0.5),
                                    2, 0.15, 0.15, 0.15, 0);
                        }
                    }
                    // 7) портал внутри круга (вертикальный, как стена)
                    for (int w = -1; w <= 0; w++) {
                        for (int h = -1; h <= 1; h++) {
                            at(cu + w, cy + h).setType(Material.NETHER_PORTAL);
                        }
                    }
                    var pLoc = locAt(cu, cy);
                    world.playSound(pLoc, Sound.BLOCK_PORTAL_AMBIENT, SoundCategory.BLOCKS, 2f, 0.7f);
                    world.spawnParticle(Particle.PORTAL, pLoc, 120, 1.5, 1.5, 1.5, 0.3);
                    portalDone = true;
                    cancel();
                }
            }
            if (t > 600) cancel(); // страховка
        }
    }
}