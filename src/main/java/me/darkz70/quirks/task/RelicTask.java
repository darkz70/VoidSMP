package me.darkz70.quirks.task;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mechanic.RelicBlocks;
import me.darkz70.quirks.mechanic.RelicItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Периодика реликвий (#98, тик раз в секунду):
 * — «Обложка конструктора миров»: Сопротивление II + Замедление II в инвентаре;
 * — «Блок пустоты»: сброс обморожения (защита от заморозки);
 * — «Лавовое ядро»: купол 32 блока (пар изо льда в инвентарях, толчок носителей
 *   Блока пустоты, испарение воды/льда, розжиг костров);
 * — «Призма»: купол 32 блока (лава → обсидиан, магма → камень, баф носителям
 *   Блока пустоты);
 * — «Яйцо дракона»: баф раз в 5 мин держателю, баф+частица раз в 30 сек рядом с блоком.
 */
public final class RelicTask extends BukkitRunnable {

    private static final int DOME_RADIUS = 32;
    private static final long DOME_BLOCKS_PERIOD_MS = 5_000L; // испарение/конверсия блоков — раз в 5 сек (дорогая операция)
    private static final long EGG_SCAN_PERIOD_MS = 3_000L;    // поиск яйца дракона рядом с игроком — раз в 3 сек
    private static final long EGG_PULSE_PERIOD_MS = 30_000L;  // пульс конкретного яйца — раз в 30 сек
    private static final long EGG_HOLD_PERIOD_MS = 5 * 60_000L; // баф держателю яйца в инвентаре — раз в 5 мин
    private static final int EGG_BLOCK_RADIUS = 10;
    private static final int EGG_SCAN_RADIUS = 12; // чуть больше EGG_BLOCK_RADIUS, с запасом

    private static final Set<Material> ICE_TYPES = EnumSet.of(
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE);

    private static final PotionEffectType[] POSITIVE_EFFECTS = {
            PotionEffectType.SPEED, PotionEffectType.JUMP_BOOST, PotionEffectType.REGENERATION,
            PotionEffectType.STRENGTH, PotionEffectType.HASTE, PotionEffectType.RESISTANCE,
            PotionEffectType.FIRE_RESISTANCE, PotionEffectType.WATER_BREATHING,
            PotionEffectType.NIGHT_VISION, PotionEffectType.SATURATION, PotionEffectType.HEALTH_BOOST,
            PotionEffectType.ABSORPTION, PotionEffectType.LUCK, PotionEffectType.SLOW_FALLING,
            PotionEffectType.DOLPHINS_GRACE, PotionEffectType.INVISIBILITY
    };

    private final VoidQuirksPlugin plugin;

    /** Кому уже «толкнуло» у конкретного ядра — чтобы не пихать каждый тик, пока игрок стоит в радиусе. */
    private final Set<String> pushedInside = new HashSet<>();
    /** Локальные (не игровые) кулдауны: разрушительные сканы куполов, пульсы яиц дракона. */
    private final Map<String, Long> cooldowns = new HashMap<>();
    /** Личные кулдауны игроков (держание яйца/скан рядом). */
    private final Map<UUID, Long> eggHoldCooldown = new HashMap<>();
    private final Map<UUID, Long> eggScanCooldown = new HashMap<>();

    public RelicTask(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean ready(Map<String, Long> store, String key, long periodMs) {
        long now = System.currentTimeMillis();
        Long last = store.get(key);
        if (last != null && now - last < periodMs) return false;
        store.put(key, now);
        return true;
    }

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    @Override
    public void run() {
        tickRelicBlocks();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();
            if (RelicItems.hasCover(inv)) {
                EffectsTask.ambient(player, PotionEffectType.RESISTANCE, 1);
                EffectsTask.ambient(player, PotionEffectType.SLOWNESS, 1);
            }
            if (RelicItems.hasVoidBlock(inv)) {
                player.setFreezeTicks(0);
            }
            dragonEggInventory(player, inv);
            dragonEggNearby(player);
        }
    }

    // ---------------------------------------------------------------- Лавовое ядро / Призма

    private void tickRelicBlocks() {
        for (Map.Entry<Location, RelicBlocks.Type> entry : plugin.relicBlocks().all().entrySet()) {
            Location loc = entry.getKey();
            World world = loc.getWorld();
            if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            if (entry.getValue() == RelicBlocks.Type.LAVA_CORE) {
                lavaCoreTick(loc);
            } else {
                prismTick(loc);
            }
        }
    }

    private void lavaCoreTick(Location loc) {
        String key = locKey(loc);
        for (Player player : nearby(loc, DOME_RADIUS)) {
            vaporizeIce(player);
            pushVoidCarrier(player, loc, key);
        }
        if (ready(cooldowns, key + "|blocks", DOME_BLOCKS_PERIOD_MS)) {
            evaporateAndIgnite(loc);
        }
    }

    private void prismTick(Location loc) {
        for (Player player : nearby(loc, DOME_RADIUS)) {
            if (RelicItems.hasVoidBlock(player.getInventory())) {
                EffectsTask.ambient(player, PotionEffectType.REGENERATION, 0);
                EffectsTask.ambient(player, PotionEffectType.SPEED, 1);
            }
        }
        if (ready(cooldowns, locKey(loc) + "|blocks", DOME_BLOCKS_PERIOD_MS)) {
            prismConvert(loc);
        }
    }

    private Iterable<Player> nearby(Location loc, int radius) {
        Set<Player> out = new HashSet<>();
        World world = loc.getWorld();
        double rr = (double) radius * radius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != world) continue;
            if (player.getLocation().distanceSquared(loc) <= rr) out.add(player);
        }
        return out;
    }

    /** Лёд в инвентаре (хотбар/рюкзак/броня/офф-рука) превращается в пар — частицы у игрока. */
    private void vaporizeIce(Player player) {
        boolean vaporized = false;
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && ICE_TYPES.contains(stack.getType())) {
                inv.setItem(i, null);
                vaporized = true;
            }
        }
        if (ICE_TYPES.contains(inv.getItemInOffHand().getType())) {
            inv.setItemInOffHand(null);
            vaporized = true;
        }
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && ICE_TYPES.contains(armor[i].getType())) {
                armor[i] = null;
                vaporized = true;
            }
        }
        if (vaporized) inv.setArmorContents(armor);
        if (vaporized) {
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.03);
        }
    }

    /** Носитель Блока пустоты отталкивается в момент входа в радиус (не каждый тик, пока стоит внутри). */
    private void pushVoidCarrier(Player player, Location core, String coreKey) {
        String mark = coreKey + "|" + player.getUniqueId();
        if (!RelicItems.hasVoidBlock(player.getInventory())) {
            pushedInside.remove(mark);
            return;
        }
        if (pushedInside.contains(mark)) return;
        pushedInside.add(mark);
        var away = player.getLocation().toVector().subtract(core.toVector());
        away.setY(0);
        if (away.lengthSquared() < 0.0001) away = new org.bukkit.util.Vector(1, 0, 0);
        away.normalize().multiply(1.4);
        player.setVelocity(away.setY(0.35));
    }

    /** Вода и лёд в радиусе испаряются, незажжённые костры загораются. Реликвии-блоки не трогаем. */
    private void evaporateAndIgnite(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        int rr = DOME_RADIUS * DOME_RADIUS;
        for (int dx = -DOME_RADIUS; dx <= DOME_RADIUS; dx++) {
            int cxx = cx + dx;
            if (!world.isChunkLoaded(cxx >> 4, cz >> 4)) continue;
            for (int dz = -DOME_RADIUS; dz <= DOME_RADIUS; dz++) {
                if (dx * dx + dz * dz > rr) continue;
                int czz = cz + dz;
                if (!world.isChunkLoaded(cxx >> 4, czz >> 4)) continue;
                for (int dy = -DOME_RADIUS; dy <= DOME_RADIUS; dy++) {
                    if (dx * dx + dy * dy + dz * dz > rr) continue;
                    Block block = world.getBlockAt(cxx, cy + dy, czz);
                    if (plugin.relicBlocks().isRelic(block.getLocation())) continue;
                    Material type = block.getType();
                    if (type == Material.WATER || ICE_TYPES.contains(type)) {
                        block.setType(Material.AIR, false);
                    } else if (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE) {
                        BlockData data = block.getBlockData();
                        if (data instanceof Lightable lightable && !lightable.isLit()) {
                            lightable.setLit(true);
                            block.setBlockData(lightable);
                        }
                    }
                }
            }
        }
    }

    /** Лава → обсидиан, магма → камень в радиусе. Реликвии-блоки не трогаем. */
    private void prismConvert(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        int rr = DOME_RADIUS * DOME_RADIUS;
        for (int dx = -DOME_RADIUS; dx <= DOME_RADIUS; dx++) {
            int cxx = cx + dx;
            if (!world.isChunkLoaded(cxx >> 4, cz >> 4)) continue;
            for (int dz = -DOME_RADIUS; dz <= DOME_RADIUS; dz++) {
                if (dx * dx + dz * dz > rr) continue;
                int czz = cz + dz;
                if (!world.isChunkLoaded(cxx >> 4, czz >> 4)) continue;
                for (int dy = -DOME_RADIUS; dy <= DOME_RADIUS; dy++) {
                    if (dx * dx + dy * dy + dz * dz > rr) continue;
                    Block block = world.getBlockAt(cxx, cy + dy, czz);
                    if (plugin.relicBlocks().isRelic(block.getLocation())) continue;
                    Material type = block.getType();
                    if (type == Material.LAVA) {
                        block.setType(Material.OBSIDIAN, false);
                    } else if (type == Material.MAGMA_BLOCK) {
                        block.setType(Material.STONE, false);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- Яйцо дракона

    /** Яйцо дракона в инвентаре: раз в 5 мин случайный положительный эффект до IV ур. на 10 сек. */
    private void dragonEggInventory(Player player, PlayerInventory inv) {
        if (!RelicItems.carries(inv, item -> item != null && item.getType() == Material.DRAGON_EGG)) return;
        long now = System.currentTimeMillis();
        Long last = eggHoldCooldown.get(player.getUniqueId());
        if (last != null && now - last < EGG_HOLD_PERIOD_MS) return;
        eggHoldCooldown.put(player.getUniqueId(), now);

        PotionEffectType type = randomPositive();
        int amplifier = ThreadLocalRandom.current().nextInt(4); // 0..3 = I..IV
        player.addPotionEffect(new PotionEffect(type, 200, amplifier, true, true, true));
        player.sendMessage(me.darkz70.quirks.util.Msg.color(
                "&5✦ Яйцо дракона в инвентаре дарует: &d" + type.getKey().getKey() + " " + (amplifier + 1)));
    }

    /** Яйцо дракона, поставленное как блок: раз в 30 сек частица на блоке + баф игрокам в радиусе 10. */
    private void dragonEggNearby(Player player) {
        long now = System.currentTimeMillis();
        Long lastScan = eggScanCooldown.get(player.getUniqueId());
        if (lastScan != null && now - lastScan < EGG_SCAN_PERIOD_MS) return;
        eggScanCooldown.put(player.getUniqueId(), now);

        World world = player.getWorld();
        Location center = player.getLocation();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        for (int dx = -EGG_SCAN_RADIUS; dx <= EGG_SCAN_RADIUS; dx++) {
            int xx = cx + dx;
            for (int dz = -EGG_SCAN_RADIUS; dz <= EGG_SCAN_RADIUS; dz++) {
                int zz = cz + dz;
                if (!world.isChunkLoaded(xx >> 4, zz >> 4)) continue; // не подгружаем чанки ради скана
                for (int dy = -EGG_SCAN_RADIUS; dy <= EGG_SCAN_RADIUS; dy++) {
                    Block block = world.getBlockAt(xx, cy + dy, zz);
                    if (block.getType() != Material.DRAGON_EGG) continue;
                    pulseDragonEgg(block);
                }
            }
        }
    }

    private void pulseDragonEgg(Block block) {
        String key = locKey(block.getLocation());
        if (!ready(cooldowns, key + "|egg", EGG_PULSE_PERIOD_MS)) return;

        Location at = block.getLocation().add(0.5, 0.7, 0.5);
        Particle[] choices = {Particle.DRAGON_BREATH, Particle.END_ROD, Particle.PORTAL,
                Particle.TOTEM_OF_UNDYING, Particle.HAPPY_VILLAGER, Particle.CRIT, Particle.GLOW};
        Particle particle = choices[ThreadLocalRandom.current().nextInt(choices.length)];
        block.getWorld().spawnParticle(particle, at, 20, 0.5, 0.5, 0.5, 0.05);

        double rr = (double) EGG_BLOCK_RADIUS * EGG_BLOCK_RADIUS;
        for (Player nearby : block.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(at) > rr) continue;
            PotionEffectType type = randomPositive();
            int amplifier = ThreadLocalRandom.current().nextInt(2); // 0..1 = I..II — слабее личного эффекта
            nearby.addPotionEffect(new PotionEffect(type, 200, amplifier, true, true, true));
        }
    }

    private static PotionEffectType randomPositive() {
        return POSITIVE_EFFECTS[ThreadLocalRandom.current().nextInt(POSITIVE_EFFECTS.length)];
    }
}
