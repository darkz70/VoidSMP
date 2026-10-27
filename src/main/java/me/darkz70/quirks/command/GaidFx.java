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
        Inventory inv = Bukkit.createInventory(new Holder(), 54,
                Msg.color("&5Эффекты (на месте админа) — 25 штук"));
        // — ряд 1: классика —
        fx(inv, 11, Material.MAGMA_BLOCK, "&c&lАдские разломы",
                "&7Магма-подложка, 4 трещины растут по 9 блоков с искрами,",
                "&7дымом и потрескиванием; бульканье магмы, тряска 60 блоков.");
        fx(inv, 12, Material.DEEPSLATE_TILES, "&4&lБольшой разлом",
                "&7То же, но ×5: длинные трещины, кратеры на концах,",
                "&7кратер под эпицентром, тряска 300 блоков.");
        fx(inv, 13, Material.END_PORTAL_FRAME, "&4&lОткрытие ада",
                "&7Тряска 12 сек, молнии, скачки дня/ночи, затем вертикальный",
                "&7круг магмы с окантовкой ад-микса и порталом + кратер.");
        fx(inv, 14, Material.ENDER_PEARL, "&5&lПортальный гул",
                "&7Гул стыков миров во все 120 блоков,",
                "&7столб портальных частиц 15 блоков вверх.");
        fx(inv, 15, Material.LAVA_BUCKET, "&6&lЛавовый гейзер",
                "&7Столб раскалённых брызг на 25 блоков вверх,",
                "&7остаётся обожжённое кольцо магмы вокруг.");
        // — ряд 2: разрывы миров —
        fx(inv, 20, Material.MAGMA_CREAM, "&c&lДвойной разлом",
                "&7Две анимированные трещины врозь под ±30°, по 14 блоков,",
                "&7каждый шов трещит и искрит.");
        fx(inv, 21, Material.ECHO_SHARD, "&3&lКолодец пустоты",
                "&7Вертикальный провал-крест глубиной 6 блоков с окантовкой",
                "&7плачущим обсидианом и лучом END-частиц вверх.");
        fx(inv, 22, Material.PRISMARINE_SHARD, "&9&lСтолб разлома",
                "&7Вертикальный шов магмы/чернокамня вверх на 8 блоков —",
                "&7мир трещит в небо, слышно по шагу.");
        fx(inv, 23, Material.SOUL_TORCH, "&5&lВарп-искажение",
                "&7Все в 30 блоках плывут (тошнота 5 с), кольца портальной",
                "&7карусели частиц вокруг эпицентра.");
        fx(inv, 24, Material.WITHER_ROSE, "&8&lЦунами тьмы",
                "&8Тьма накрывает 50 блоков на 8 секунд,",
                "&7тройной вал душ скалка по земле.");
        // — ряд 3: стихии неба и глубины —
        fx(inv, 29, Material.LIGHTNING_ROD, "&e&lГром и молния",
                "&7Вспышка и рёв перед грозой; 3 удара в твою точку,",
                "&7молнии по кругу с магмой/чернокамнем/огнём, 3 мини-кратера.");
        fx(inv, 30, Material.BLAZE_ROD, "&6&lМолния в небо",
                "&7Обратный разряд: белый столб END-частиц уходит вверх,",
                "&7вспышка и сухой щелчок грома.");
        fx(inv, 31, Material.SOUL_LANTERN, "&b&lСтрах",
                "&7Тьма в 60 блоках, шквал вардена, скрипка от игрока.",
                "&74 волны душ скалка на каждого, шаги под ногами.");
        fx(inv, 32, Material.DRAGON_HEAD, "&5&lГлобальный страх",
                "&7То же, но на 300 блоков.");
        fx(inv, 33, Material.SCULK_SHRIEKER, "&5&lПульс пустоты",
                "&7Кольцо SCULK-заряда расходится по земле,",
                "&7соник-бум вардена слышен в 60 блоках.");
        // — ряд 4: атмосфера и бури —
        fx(inv, 38, Material.GUNPOWDER, "&7&lПепельная буря",
                "&710 секунд идёт пепел в 25 блоках — как после пожара миров.");
        fx(inv, 39, Material.GHAST_TEAR, "&f&lПорыв гаста",
                "&7Крик гаста у всех в 60 блоках, огненный след по кругу,",
                "&7один хлопок взрыва сверху.");
        fx(inv, 40, Material.BLAZE_POWDER, "&6&lОгненный купол",
                "&7Полусфера пламени-душ радиусом 4 вокруг точки,",
                "&58 секунд дышит (только частицы, без блоков).");
        fx(inv, 41, Material.WHITE_DYE, "&f&lДымовая завеса",
                "&7Столб дыма высотой 12 на 10 секунд — тонкий,",
                "&7плотный, виден издалека.");
        fx(inv, 42, Material.FIREWORK_STAR, "&e&lИскровой ливень",
                "&78 секунд с неба сыплются искры пламени в 15 блоках.");
        // — ряд 5: финалы —
        fx(inv, 47, Material.FLINT_AND_STEEL, "&6&lОгненное кольцо",
                "&7Три расширяющихся кольца пламени по земле,",
                "&7свист огня на каждой волне.");
        fx(inv, 48, Material.REDSTONE, "&c&lКровавый дождь",
                "&78 секунд тёплых капель лавы с неба в 15 блоках.");
        fx(inv, 49, Material.GRAVEL, "&8&lЗемлетрясение",
                "&7Шесть ударов тряски экрана подряд, грохот гравия",
                "&7ходит волнами кругом 40 блоков.");
        fx(inv, 50, Material.ENDER_EYE, "&a&lХор эндерменов",
                "&7Шесть телепортов-хора по кругу вокруг точки,",
                "&7фиолетовые шлейфы между ними.");
        fx(inv, 51, Material.FIRE_CHARGE, "&4&lВспышка-разряд",
                "&7Мгновенная вспышка неба, глухой удар грома,",
                "&7магма-пыль расходится от твоих ног.");
        inv.setItem(4, btn(Material.ARROW, "&7&lНазад"));
        player.openInventory(inv);
    }

    private void fx(Inventory inv, int slot, Material icon, String name, String... lore) {
        inv.setItem(slot, btn(icon, name, lore));
    }

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        switch (event.getRawSlot()) {
            case 4 -> {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("gaid"));
                return;
            }
            case 11 -> hellRift(player);
            case 12 -> hellRift(player, 5.0, 4);
            case 13 -> openHell(player);
            case 14 -> portalHum(player);
            case 15 -> lavaGeyser(player);
            case 20 -> doubleRift(player);
            case 21 -> voidWell(player);
            case 22 -> phaseScar(player);
            case 23 -> warpDistortion(player);
            case 24 -> darknessTsunami(player);
            case 29 -> thunder(player);
            case 30 -> skyBolt(player);
            case 31 -> fear(player, 60);
            case 32 -> fear(player, 300);
            case 33 -> voidPulse(player);
            case 38 -> ashStorm(player);
            case 39 -> ghastBurst(player);
            case 40 -> fireDome(player);
            case 41 -> smokeVeil(player);
            case 42 -> sparkRain(player);
            case 47 -> fireRing(player);
            case 48 -> lavaRain(player);
            case 49 -> quake(player);
            case 50 -> enderChoir(player);
            case 51 -> flashDischarge(player);
            default -> { return; }
        }
        // эффект запущен — меню остаётся открытым (переоткрываем, чтобы сбросить курсор/слот)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) open(player);
        });
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
        // трещины-линии РАСТУТ от эпицентра шаг за шагом (слышно и видно продвижение)
        for (int line = 0; line < lines; line++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            int len = (int) Math.round(9 * scale);
            for (int step = 1; step <= len; step++) {
                final int fs = step;
                int px = bx + (int) Math.round(Math.cos(angle) * step);
                int pz = bz + (int) Math.round(Math.sin(angle) * step);
                // каждые 2 шага трещины — свой тик (растёт в глазах)
                int ticks = line * 4 + step * 2;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Block block = world.getBlockAt(px, by, pz);
                    if (!canTurn(block)) return;
                    block.setType(Material.MAGMA_BLOCK);
                    Location tip = new Location(world, px + 0.5, by, pz + 0.5);
                    // раскалённый шов: лава-искры + серный дым + потрескивание
                    world.spawnParticle(Particle.LAVA, px + 0.5, by + 1.2, pz + 0.5, 3, 0.2, 0.1, 0.2, 0);
                    world.spawnParticle(Particle.WHITE_SMOKE, px + 0.5, by + 1.0, pz + 0.5, 4, 0.15, 0.3, 0.15, 0.01);
                    world.spawnParticle(Particle.FLAME, px + 0.5, by + 1.3, pz + 0.5, 2, 0.15, 0.2, 0.15, 0.01);
                    world.playSound(tip, Sound.BLOCK_LAVA_POP, SoundCategory.BLOCKS, 0.9f, 0.55f + fs * 0.02f);
                    world.playSound(tip, Sound.BLOCK_BASALT_BREAK, SoundCategory.BLOCKS, 0.7f, 0.8f);
                }, ticks);
            }
            // финал линии: всплеск жара (+ кратер при большом разломе)
            int endTicks = line * 4 + len * 2 + 4;
            double ex = bx + Math.cos(angle) * len;
            double ez = bz + Math.sin(angle) * len;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                world.spawnParticle(Particle.LAVA, ex, by + 1.4, ez, 20, 0.6, 0.4, 0.6, 0.02);
                world.spawnParticle(Particle.EXPLOSION, ex, by + 1.2, ez, 2, 0.1, 0.1, 0.1, 0);
                world.playSound(new Location(world, ex, by, ez),
                        Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.8f, 0.8f);
                if (scale >= 2) { // большой разлом: кратер на конце трещины
                    crater(world, new Location(world, (int) Math.round(ex), by, (int) Math.round(ez)),
                            (int) Math.round(2 + scale * 0.6));
                }
            }, endTicks);
        }
        // бульканье магмы над швами — три волны после завершения роста
        for (int wave = 0; wave < 3; wave++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < lines * 4; i++) {
                    double a = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
                    double d = ThreadLocalRandom.current().nextDouble(2, 9 * scale);
                    world.playSound(new Location(world, bx + Math.cos(a) * d, by, bz + Math.sin(a) * d),
                            Sound.BLOCK_LAVA_AMBIENT, SoundCategory.BLOCKS, 0.8f, 0.7f);
                }
            }, lines * 4 + (int) (20 * wave));
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

        // удар 0-й: вспышка неба и низкий гул перед грозой
        world.spawnParticle(Particle.FLASH, at.clone().add(0, 2, 0), 1, 0, 0, 0, 0);
        for (Player nearby : playersIn(at, 90)) {
            nearby.playSound(at, Sound.ENTITY_WITHER_SPAWN, SoundCategory.WEATHER, 0.5f, 0.5f);
        }

        // три удара прямо в админа — с растущей силой звука
        for (int i = 0; i < 3; i++) {
            final float pitch = 0.7f + i * 0.1f;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                world.strikeLightningEffect(at);
                lightningParticles(world, at);
                for (Player nearby : playersIn(at, 90)) {
                    nearby.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                            SoundCategory.WEATHER, 3f, pitch);
                }
            }, 10L + i * 10L);
        }
        Block base = world.getBlockAt(bx, by, bz);
        if (canTurn(base)) {
            base.setType(Material.MAGMA_BLOCK);
            Block above = base.getRelative(0, 1, 0);
            if (above.getType().isAir()) above.setType(Material.FIRE);
        }

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
                if (canTurn(floor)) {
                    floor.setType(ThreadLocalRandom.current().nextInt(3) == 0
                            ? Material.BLACKSTONE : Material.MAGMA_BLOCK);
                    Block flame = floor.getRelative(0, 1, 0);
                    if (flame.getType().isAir()
                            && ThreadLocalRandom.current().nextBoolean()) flame.setType(Material.FIRE);
                }
                lightningParticles(world, strike);
                thunderSounds(strike, 90);
            }, ticks);
        }
        // три «входящих» удара выбивают маленькие кратеры
        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double a = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
                double r = ThreadLocalRandom.current().nextDouble(6, 16);
                int px = bx + (int) Math.round(Math.cos(a) * r);
                int pz = bz + (int) Math.round(Math.sin(a) * r);
                int py = world.getHighestBlockYAt(px, pz);
                world.strikeLightningEffect(new Location(world, px + 0.5, py + 1, pz + 0.5));
                crater(world, new Location(world, px, py, pz), 2);
            }, 60L + i * 10L);
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
        // тень накрывает каждую жертву: скалковые души стягиваются к ногам 4 волнами
        for (int wave = 0; wave < 4; wave++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player victim : victims) {
                    if (!victim.isOnline()) continue;
                    Location vl = victim.getLocation();
                    victim.getWorld().spawnParticle(Particle.SCULK_SOUL,
                            vl.clone().add(0, 1.2, 0), 22, 1.6, 1.0, 1.6, 0.03);
                    victim.getWorld().spawnParticle(Particle.SMOKE, vl,
                            14, 1.2, 0.3, 1.2, 0.02);
                }
            }, wave * 15L);
        }
        // стук когтей по камню под ногами у каждого — «оно где-то рядом»
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player victim : victims) {
                if (victim.isOnline()) {
                    victim.playSound(victim.getLocation(), Sound.ENTITY_WARDEN_STEP,
                            SoundCategory.HOSTILE, 0.9f, 0.6f);
                }
            }
        }, 50L);
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

    // ---------- эффекты 7–25: больше про стык миров, плюс стихии ----------

    /** Портальный гул: стык миров слышен в 120 блоках + столб портальных частиц. */
    private void portalHum(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (Player hearer : playersIn(at, 120)) {
            hearer.playSound(at, Sound.BLOCK_PORTAL_AMBIENT, SoundCategory.BLOCKS, 2.4f, 0.6f);
            hearer.playSound(at, Sound.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 0.7f, 1.4f);
        }
        for (int i = 0; i < 15; i++) {
            final int h = i;
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    world.spawnParticle(Particle.PORTAL, at.clone().add(0, h, 0),
                            40, 0.9, 0.4, 0.9, 0.05), i * 4L);
        }
        shakeNearby(at, 40, 2);
    }

    /** Лавовый гейзер: столб брызг 25 блоков + обожжённое кольцо магмы. */
    private void lavaGeyser(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        int bx = at.getBlockX(); int by = at.getBlockY() - 1; int bz = at.getBlockZ();
        for (int i = 0; i < 20; i++) {
            final int w = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                world.spawnParticle(Particle.LAVA, at.clone().add(0, 1 + w * 1.2, 0),
                        30, 0.4, 0.8, 0.4, 0.02);
                world.spawnParticle(Particle.FLAME, at.clone().add(0, 1 + w * 1.2, 0),
                        14, 0.3, 0.6, 0.3, 0.01);
                world.playSound(at.clone().add(0, w, 0), Sound.BLOCK_LAVA_POP,
                        SoundCategory.BLOCKS, 0.8f, 0.5f + w * 0.03f);
            }, i * 2L);
        }
        for (int i = 0; i < 12; i++) {
            double a = (Math.PI * 2 * i) / 12;
            Block ring = world.getBlockAt(bx + (int) Math.round(Math.cos(a) * 2), by,
                    bz + (int) Math.round(Math.sin(a) * 2));
            if (canTurn(ring)) ring.setType(Material.MAGMA_BLOCK);
        }
        shakeNearby(at, 50, 2);
    }

    /** Двойной разлом: две трещины в противоположные стороны по 14 блоков, рост по шагам. */
    private void doubleRift(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        int by = at.getBlockY() - 1;
        double base = Math.toRadians(player.getLocation().getYaw()) + Math.PI / 2;
        for (int side = 0; side < 2; side++) {
            double angle = base + (side == 0 ? Math.toRadians(30) : Math.toRadians(-30));
            for (int step = 1; step <= 14; step++) {
                final int fs = step;
                int px = at.getBlockX() + (int) Math.round(Math.cos(angle) * step);
                int pz = at.getBlockZ() + (int) Math.round(Math.sin(angle) * step);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Block block = world.getBlockAt(px, by, pz);
                    if (!canTurn(block)) return;
                    block.setType(Material.MAGMA_BLOCK);
                    world.spawnParticle(Particle.LAVA, px + 0.5, by + 1.2, pz + 0.5, 3, 0.2, 0.1, 0.2, 0);
                    world.spawnParticle(Particle.SMOKE, px + 0.5, by + 1.1, pz + 0.5, 4, 0.15, 0.3, 0.15, 0.01);
                    world.playSound(new Location(world, px, by, pz), Sound.BLOCK_BASALT_BREAK,
                            SoundCategory.BLOCKS, 0.8f, 0.7f + fs * 0.02f);
                }, side * 8 + step * 2L);
            }
        }
        shakeNearby(at, 60, 3);
    }

    /** Колодец пустоты: крест-провал 6 глубины, плачущий обсидиан, луч END-частиц. */
    private void voidWell(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        int bx = at.getBlockX(); int by = at.getBlockY() - 1; int bz = at.getBlockZ();
        int[][] cross = {{0,0},{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] cell : cross) {
            for (int depth = 0; depth < 6; depth++) {
                Block b = world.getBlockAt(bx + cell[0], by - depth, bz + cell[1]);
                if (canTurn(b) && b.getType() != Material.AIR) b.setType(Material.AIR);
            }
        }
        int[][] rim = {{2,0},{-2,0},{0,2},{0,-2},{1,1},{-1,1},{1,-1},{-1,-1}};
        for (int[] cell : rim) {
            Block r = world.getBlockAt(bx + cell[0], by, bz + cell[1]);
            if (canTurn(r)) r.setType(Material.CRYING_OBSIDIAN);
        }
        for (int i = 0; i < 12; i++) {
            final int h = i;
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    world.spawnParticle(Particle.END_ROD, at.clone().add(0, h * 0.8 + 0.5, 0),
                            6, 0.25, 0.25, 0.25, 0.01), i * 3L);
        }
        world.playSound(at, Sound.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1f, 0.7f);
        world.spawnParticle(Particle.PORTAL, at, 80, 1.5, 0.5, 1.5, 0.04);
        shakeNearby(at, 40, 2);
    }

    /** Столб разлома: вертикальный шов магмы/чернокамня вверх на 8 блоков. */
    private void phaseScar(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        int bx = at.getBlockX(); int by = at.getBlockY() - 1; int bz = at.getBlockZ();
        for (int h = 0; h < 8; h++) {
            final int fh = h;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Block b = world.getBlockAt(bx, by + fh, bz);
                if (b.getType().isAir() || canTurn(b)) {
                    b.setType(fh % 2 == 0 ? Material.MAGMA_BLOCK : Material.BLACKSTONE);
                }
                world.spawnParticle(Particle.FLAME, bx + 0.5, by + fh + 0.5, bz + 0.5, 6, 0.3, 0.3, 0.3, 0.01);
                world.spawnParticle(Particle.WHITE_SMOKE, bx + 0.5, by + fh + 0.5, bz + 0.5, 5, 0.25, 0.25, 0.25, 0.01);
                world.playSound(new Location(world, bx, by + fh, bz), Sound.BLOCK_BASALT_BREAK,
                        SoundCategory.BLOCKS, 0.9f, 0.6f + fh * 0.06f);
            }, h * 3L);
        }
        for (Player near : playersIn(at, 60)) {
            near.playSound(at, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.BLOCKS, 1.2f, 0.5f);
        }
    }

    /** Варп-искажение: тошнота всем в 30 блоках + портальная карусель частиц. */
    private void warpDistortion(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (Player victim : playersIn(at, 30)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 2, true, false, true));
        }
        for (int i = 0; i < 10; i++) {
            final int w = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double a = w * 0.6;
                Location spot = at.clone().add(Math.cos(a) * (2 + w * 0.3), 1 + w * 0.2, Math.sin(a) * (2 + w * 0.3));
                world.spawnParticle(Particle.PORTAL, spot, 30, 0.2, 0.2, 0.2, 0.06);
                world.spawnParticle(Particle.REVERSE_PORTAL, spot, 20, 0.2, 0.2, 0.2, 0.04);
            }, i * 3L);
        }
        world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.4f);
    }

    /** Цунами тьмы: тьма всем в 50 блоках на 8 сек + три вала скалковых душ. */
    private void darknessTsunami(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (Player victim : playersIn(at, 50)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 160, 0, true, false, true));
        }
        for (int wave = 0; wave < 3; wave++) {
            final double r = 4 + wave * 4;
            final int fw = wave;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < 48; i++) {
                    double a = (Math.PI * 2 * i) / 48;
                    world.spawnParticle(Particle.SCULK_SOUL,
                            at.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r), 2, 0.1, 0.2, 0.1, 0.005);
                }
                world.playSound(at, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.HOSTILE,
                        0.8f, 0.6f + fw * 0.1f);
            }, wave * 8L);
        }
    }

    /** Молния в небо: обратный разряд уходит вверх, сухой громовой щелчок. */
    private void skyBolt(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (int h = 0; h < 18; h++) {
            final int fh = h;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double jig = ThreadLocalRandom.current().nextDouble(-0.4, 0.4);
                world.spawnParticle(Particle.END_ROD,
                        at.getX() + jig, at.getY() + fh * 1.2, at.getZ() + jig, 2, 0.05, 0.05, 0.05, 0);
            }, h * 2L);
        }
        world.spawnParticle(Particle.FLASH, at.clone().add(0, 2, 0), 1, 0, 0, 0, 0);
        for (Player near : playersIn(at, 90)) {
            near.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 2f, 1.3f);
        }
    }

    /** Пульс пустоты: кольцо SCULK-заряда + соник-бум. */
    private void voidPulse(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (int ring = 0; ring < 4; ring++) {
            final double r = 3 + ring * 3;
            final int fr = ring;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < 40; i++) {
                    double a = (Math.PI * 2 * i) / 40;
                    world.spawnParticle(Particle.SCULK_CHARGE,
                            at.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1, 0.05, 0.1, 0.05, 0);
                }
                world.playSound(at, Sound.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE,
                        1.0f, 0.7f + fr * 0.1f);
            }, ring * 5L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player near : playersIn(at, 60)) {
                near.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.2f, 0.7f);
            }
        }, 26L);
        shakeNearby(at, 50, 3);
    }

    /** Пепельная буря: 10 секунд пепла в 25 блоках. */
    private void ashStorm(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (int t = 0; t < 20; t++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                world.spawnParticle(Particle.ASH, at.clone().add(0, 6, 0), 260, 25, 5, 25, 0.01);
                world.spawnParticle(Particle.SMOKE, at.clone().add(0, 5, 0), 60, 22, 4, 22, 0.005);
            }, t * 10L);
        }
    }

    /** Порыв гаста: крик у всех шестидесяти блоков, огненный дуговой след, хлопок взрыва. */
    private void ghastBurst(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (Player near : playersIn(at, 60)) {
            near.playSound(at, Sound.ENTITY_GHAST_WARN, SoundCategory.HOSTILE, 1.4f, 0.8f);
        }
        for (int i = 0; i < 16; i++) {
            double a = (Math.PI * 2 * i) / 16;
            world.spawnParticle(Particle.FLAME, at.clone().add(Math.cos(a) * 4, 1.5, Math.sin(a) * 4),
                    6, 0.2, 0.2, 0.2, 0.01);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location boom = at.clone().add(0, 7, 0);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, boom, 2, 0.2, 0.2, 0.2, 0);
            world.playSound(boom, Sound.ENTITY_GHAST_SHOOT, SoundCategory.HOSTILE, 1.6f, 0.7f);
            for (Player near : playersIn(at, 60)) {
                near.playSound(boom, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.0f, 0.9f);
            }
        }, 25L);
    }

    /** Огненный купол: полусфера из частиц огня-душ вокруг точки 8 секунд (без блоков). */
    private void fireDome(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (int t = 0; t < 16; t++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < 40; i++) {
                    double phi = Math.PI * i / 40;
                    double theta = Math.PI * 2 * ((i * 37) % 40) / 40.0;
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                            at.clone().add(Math.sin(phi) * Math.cos(theta) * 4,
                                    Math.abs(Math.cos(phi)) * 4, Math.sin(phi) * Math.sin(theta) * 4),
                            1, 0.05, 0.05, 0.05, 0);
                }
            }, t * 10L);
        }
        world.playSound(at, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.7f);
    }

    /** Дымовая завеса: столб дыма 12 высотой на 10 секунд. */
    private void smokeVeil(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (int t = 0; t < 20; t++) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    world.spawnParticle(Particle.WHITE_SMOKE, at.clone().add(0, 1, 0),
                            40, 0.5, 6, 0.5, 0.02), t * 10L);
        }
    }

    /** Искровой ливень: 8 секунд сыплются пламя-искры с неба в 15 блоках. */
    private void sparkRain(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (int t = 0; t < 16; t++) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    world.spawnParticle(Particle.FLAME, at.clone().add(0, 8, 0),
                            160, 15, 1.5, 15, 0.02), t * 10L);
        }
    }

    /** Огненное кольцо: три расширяющихся круга пламени, свист огня. */
    private void fireRing(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (int wave = 0; wave < 3; wave++) {
            final double r = 2 + wave * 2.5;
            final int fw = wave;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < 44; i++) {
                    double a = (Math.PI * 2 * i) / 44;
                    world.spawnParticle(Particle.FLAME,
                            at.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r), 1, 0.05, 0.15, 0.05, 0);
                }
                world.playSound(at, Sound.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS,
                        1.0f, 0.6f + fw * 0.15f);
            }, wave * 6L);
        }
        shakeNearby(at, 30, 2);
    }

    /** Кровавый дождь: тёплые капли лавы с неба в 15 блоках 8 секунд. */
    private void lavaRain(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (int t = 0; t < 16; t++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                world.spawnParticle(Particle.DRIPPING_LAVA, at.clone().add(0, 7, 0), 90, 15, 1, 15, 0.01);
                world.spawnParticle(Particle.FALLING_LAVA, at.clone().add(0, 7, 0), 60, 15, 1, 15, 0.01);
            }, t * 10L);
        }
    }

    /** Землетрясение: шесть ударов тряски + качающийся грохот гравия кругом. */
    private void quake(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        shakeNearby(at, 40, 6);
        for (int wave = 0; wave < 4; wave++) {
            final int fw = wave;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player near : playersIn(at, 40)) {
                    near.playSound(at, Sound.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 1.4f, 0.6f + fw * 0.1f);
                    near.playSound(at, Sound.BLOCK_GRAVEL_STEP, SoundCategory.BLOCKS, 1.2f, 0.5f);
                }
            }, wave * 6L);
        }
    }

    /** Хор эндерменов: шесть телепортов по кругу + фиолетовые шлейфы. */
    private void enderChoir(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (int i = 0; i < 6; i++) {
            final int fi = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double a = (Math.PI * 2 * fi) / 6;
                Location spot = at.clone().add(Math.cos(a) * 5, 1, Math.sin(a) * 5);
                world.playSound(spot, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.5f + fi * 0.12f);
                world.spawnParticle(Particle.PORTAL, spot, 40, 0.4, 0.8, 0.4, 0.04);
                world.spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 1, 0), 24, 0.3, 0.6, 0.3, 0.02);
            }, i * 8L);
        }
    }

    /** Вспышка-разряд: мгновенная вспышка, глухой гром, магма-пыль от ног. */
    private void flashDischarge(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        world.spawnParticle(Particle.FLASH, at.clone().add(0, 2, 0), 1, 0, 0, 0, 0);
        for (Player near : playersIn(at, 120)) {
            near.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 2.5f, 1.5f);
        }
        for (int i = 0; i < 36; i++) {
            double a = (Math.PI * 2 * i) / 36;
            world.spawnParticle(Particle.FLAME, at.clone().add(Math.cos(a) * 1.5, 0.4, Math.sin(a) * 1.5),
                    2, 0.1, 0.15, 0.1, 0.005);
            world.spawnParticle(Particle.ASH, at.clone().add(Math.cos(a) * 2.2, 0.8, Math.sin(a) * 2.2),
                    1, 0.1, 0.2, 0.1, 0.005);
        }
    }
}
