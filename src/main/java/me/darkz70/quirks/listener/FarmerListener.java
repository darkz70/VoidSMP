package me.darkz70.quirks.listener;

import java.util.List;
import java.util.Random;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.MaterialLists;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Причуда «Фермер» (v0.6.0 + доп. механики сапог/человечины/семян/ведьм/пчёл):
 * урожай x2 (1 ур.), 80% x2 / 20% x3 (2-3 ур.); запрет сырой еды (2 ур.); слабость при <5 HP (2 ур.);
 * герой деревни и рост x4 рядом (2 ур.); неубиваемая мотыга и волчья агрессия (3 ур.).
 *
 * Доп.:
 * - Сапоги фермера (скрытый крафт): рост x10 в радиусе 30, -50% урон от падения — только на Фермере.
 * - Человечина: 50% с жителя/игрока, убитого Фермером; для всех — как гнилая плоть, для Фермера — как золотое яблоко.
 * - Фермер ест сырые семена как сладкие ягоды.
 * - Ведьмы не бьют Фермера, пока он не нападёт первым.
 * - Пчёлы не бьют Фермера; взятая сота — пчёлы идут за ним.
 */
public final class FarmerListener implements Listener {

    private final VoidQuirksPlugin plugin;
    private static final Random RANDOM = new Random();

    private static final java.util.EnumSet<Material> SEEDS = java.util.EnumSet.of(
            Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS,
            Material.PUMPKIN_SEEDS, Material.TORCHFLOWER_SEEDS);

    public FarmerListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    private int level(Player player) {
        return plugin.quirks().levelOf(player, Quirk.FARMER);
    }

    /** Удвоение/утроение урожая и семян со зрелых посевов. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        int lvl = level(player);
        if (lvl < 1) return;
        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Ageable age)) return;
        if (age.getAge() < age.getMaximumAge()) return;

        int extraStacks = lvl == 1 ? 1 : (RANDOM.nextInt(100) < 20 ? 2 : 1); // x2 или x3
        ItemStack tool = player.getInventory().getItemInMainHand();
        Location at = block.getLocation().add(0.5, 0.4, 0.5);
        for (ItemStack drop : block.getDrops(tool)) {
            int extra = drop.getAmount() * extraStacks;
            while (extra > 0) {
                ItemStack more = drop.clone();
                more.setAmount(Math.min(drop.getMaxStackSize(), extra));
                block.getWorld().dropItemNaturally(at, more);
                extra -= more.getAmount();
            }
        }
    }

    /** Запрет сырой еды (2 ур.). */
    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack eatenItem = event.getItem();

        // человечина: для Фермера — как золотое яблоко (ручная обработка вместо гнилой плоти)
        if (isHumanMeat(eatenItem) && level(player) >= 1) {
            event.setCancelled(true);
            consumeOneFromHand(player, eatenItem);
            player.setFoodLevel(Math.min(20, player.getFoodLevel() + 4));
            player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + 9.6f));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0, true, true, true));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
            Msg.send(player, "farmer-human-meat-eaten");
            return;
        }

        if (level(player) < 2) return;
        Material eaten = eatenItem.getType();
        if (!eaten.isEdible()) return;
        if (MaterialLists.isFarmerRaw(eaten)) {
            event.setCancelled(true);
            Msg.send(player, "farmer-raw-denied");
        }
    }

    /** Фермер ест сырые семена (ПКМ в воздух), как сладкие ягоды — семена сами по себе несъедобны. */
    @EventHandler(ignoreCancelled = true)
    public void onEatSeeds(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (level(player) < 1) return;
        ItemStack item = event.getItem();
        if (item == null || !SEEDS.contains(item.getType())) return;
        if (player.getFoodLevel() >= 20) return;

        event.setCancelled(true);
        item.setAmount(item.getAmount() - 1);
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 2));
        player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + 0.4f));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
    }

    private static void consumeOneFromHand(Player player, ItemStack referenceItem) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.isSimilar(referenceItem) && main.getAmount() > 0) {
            main.setAmount(main.getAmount() - 1);
            return;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off.isSimilar(referenceItem) && off.getAmount() > 0) {
            off.setAmount(off.getAmount() - 1);
        }
    }

    /** Мотыга не ломается (3 ур.). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToolDamage(PlayerItemDamageEvent event) {
        if (level(event.getPlayer()) < 3) return;
        if (event.getItem().getType().name().endsWith("_HOE")) {
            event.setCancelled(true);
        }
    }

    /** Человечина: 50% шанс 1-3 шт. с жителя/игрока, убитого Фермером. */
    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (level(killer) < 1) return;
        EntityType type = event.getEntityType();
        if (type != EntityType.VILLAGER && type != EntityType.PLAYER) return;
        if (RANDOM.nextInt(100) >= 50) return;
        int amount = 1 + RANDOM.nextInt(3);
        event.getDrops().add(makeHumanMeat(amount));
    }

    /** Урон от падения -50% в сапогах фермера (только на самом Фермере). */
    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (level(player) < 1) return;
        if (!isFarmerBoots(player.getInventory().getBoots())) return;
        event.setDamage(event.getDamage() * 0.5);
    }

    /** Ведьмы не наносят урон Фермеру, пока он не нападёт первым. */
    @EventHandler(ignoreCancelled = true)
    public void onWitchAttack(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (level(player) < 1) return;
        Witch witch = resolveWitch(event.getDamager());
        if (witch == null) return;
        if (!isProvoked(witch)) {
            event.setCancelled(true);
        }
    }

    /** Если Фермер первым бьёт ведьму — она становится "спровоцированной" и далее бьёт в ответ. */
    @EventHandler(ignoreCancelled = true)
    public void onFarmerHitsWitch(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Witch witch)) return;
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker == null) return;
        if (level(attacker) < 1) return;
        markProvoked(witch);
    }

    private static Witch resolveWitch(Entity damager) {
        if (damager instanceof Witch witch) return witch;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Witch witch) return witch;
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Witch witch) return witch;
        return null;
    }

    private static Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player player) return player;
        return null;
    }

    private static boolean isProvoked(Entity witch) {
        return witch.getPersistentDataContainer().has(Keys.witchProvoked, PersistentDataType.BYTE);
    }

    private static void markProvoked(Entity witch) {
        witch.getPersistentDataContainer().set(Keys.witchProvoked, PersistentDataType.BYTE, (byte) 1);
    }

    /** Фермер берёт соту с улья/гнезда — ближайшие пчёлы идут за ним некоторое время. */
    @EventHandler(ignoreCancelled = true)
    public void onShearHive(io.papermc.paper.event.block.PlayerShearBlockEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BEEHIVE && block.getType() != Material.BEE_NEST) return;
        Player player = event.getPlayer();
        if (level(player) < 1) return;
        boolean gotHoneycomb = event.getDrops().stream().anyMatch(d -> d.getType() == Material.HONEYCOMB);
        if (!gotHoneycomb) return;
        startBeeFollow(plugin, player, block.getLocation());
    }

    private static void startBeeFollow(VoidQuirksPlugin plugin, Player player, Location hiveLoc) {
        var world = hiveLoc.getWorld();
        if (world == null) return;
        List<Bee> bees = new java.util.ArrayList<>();
        for (Entity e : world.getNearbyEntities(hiveLoc, 16, 16, 16)) {
            if (e instanceof Bee bee) bees.add(bee);
        }
        if (bees.isEmpty()) return;

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks++ >= 20) { // ~20 секунд
                    cancel();
                    return;
                }
                for (Bee bee : bees) {
                    if (!bee.isValid() || bee.isDead()) continue;
                    if (bee.getLocation().distanceSquared(player.getLocation()) < 4) continue;
                    bee.getPathfinder().moveTo(player, 1.2);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Периодические пассивки Фермера (вызывается из EffectsTask раз в ~2 сек):
     * слабость при HP < 5 (2 ур.), рост посевов x4 (2 ур.), герой деревни (3 ур.),
     * рост x10 в радиусе 30 в сапогах фермера (с 1 ур., только на самом Фермере).
     */
    public static void applyPassive(VoidQuirksPlugin plugin, Player player, int level) {
        if (player.isDead()) return;

        // Сапоги фермера: рост x10 в радиусе 30 — эффект работает только на самом Фермере.
        if (isFarmerBoots(player.getInventory().getBoots())) {
            int bootsRadius = plugin.getConfig().getInt("farmer.boots-growth-radius", 30);
            double bootsChance = plugin.getConfig().getInt("farmer.boots-growth-chance-percent", 10) / 100.0;
            growCrops(player, bootsRadius, bootsChance);
        }

        // 3 уровень: герой деревни II (деревня его боготворит)
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE,
                    300, 1, true, false, true));
        }

        // 2 ур: выбранная пассивка через меню (скорость / ночное зрение)
        if (level >= 2) {
            String passiv = me.darkz70.quirks.listener.AbilityMenus.farmerPassive(player.getUniqueId());
            if ("speed".equals(passiv)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 0, true, false, true));
            } else if ("night".equals(passiv)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, true));
            }
        }

        // слабость при меньше 5 HP (2-3 ур.)
        if (level >= 2) {
            double limit = plugin.getConfig().getDouble("farmer.weakness-hp", 5.0);
            if (player.getHealth() < limit) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, true, false, true));
            }
            int radius = plugin.getConfig().getInt("farmer.growth-radius", 10);
            double chance = plugin.getConfig().getInt("farmer.growth-chance-percent", 1) / 100.0;
            growCrops(player, radius, chance);
        }

    }

    /** С 1 уровня: нейтральные мобы не трогают фермера (урон зануляется). */
    @EventHandler(ignoreCancelled = true)
    public void onNeutralDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.quirks().levelOf(player, Quirk.FARMER) < 1) return;
        if (!plugin.getConfig().getBoolean("farmer.neutral-damage-blocked", true)) return;
        if (!(event.getDamager() instanceof org.bukkit.entity.LivingEntity liv)) return;
        if (liv instanceof Player) return;
        if (NEUTRAL.contains(liv.getType())) {
            event.setCancelled(true);
        }
    }

    private static final java.util.EnumSet<EntityType> NEUTRAL = java.util.EnumSet.of(
            EntityType.WOLF, EntityType.BEE, EntityType.PANDA, EntityType.SPIDER,
            EntityType.CAVE_SPIDER, EntityType.ENDERMAN, EntityType.IRON_GOLEM,
            EntityType.DOLPHIN, EntityType.GOAT);

    /** Посевы в радиусе растут быстрее: шансом добавляем стадию роста. */
    private static void growCrops(Player player, int radius, double chance) {
        Location center = player.getLocation();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        var world = center.getWorld();
        if (world == null) return;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!world.isChunkLoaded((cx + dx) >> 4, (cz + dz) >> 4)) continue;
                for (int dy = -radius; dy <= radius; dy++) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (!(block.getBlockData() instanceof Ageable age)) continue;
                    if (age.getAge() >= age.getMaximumAge()) continue;
                    if (RANDOM.nextDouble() >= chance) continue;
                    age.setAge(age.getAge() + 1);
                    block.setBlockData(age, false);
                }
            }
        }
    }

    // ---------- предметы: сапоги фермера, человечина ----------

    public static ItemStack makeFarmerBoots() {
        ItemStack item = new ItemStack(Material.DIAMOND_BOOTS);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&a&l✦ Сапоги фермера"));
            meta.lore(List.of(
                    Msg.color("&7&oПропитаны землёй плодородных полей"),
                    Msg.color("&8—————————————"),
                    Msg.color("&dФермеру: &fрост растений x10 &7в радиусе 30 блоков"),
                    Msg.color("&dФермеру: &7урон от падения &f-50%"),
                    Msg.color("&7У других игроков эффекты не работают."),
                    Msg.color("&8VoidSMP")
            ));
            meta.getPersistentDataContainer().set(Keys.farmerBoots, PersistentDataType.BOOLEAN, true);
        });
        return item;
    }

    public static boolean isFarmerBoots(ItemStack item) {
        return item != null && item.getType() == Material.DIAMOND_BOOTS && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(Keys.farmerBoots, PersistentDataType.BOOLEAN);
    }

    public static ItemStack makeHumanMeat(int amount) {
        ItemStack item = new ItemStack(Material.ROTTEN_FLESH, amount);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&4&lЧеловечина"));
            meta.lore(List.of(
                    Msg.color("&7&oТрофей охоты Фермера"),
                    Msg.color("&8—————————————"),
                    Msg.color("&7Для всех: &fдействует как гнилая плоть."),
                    Msg.color("&dДля Фермера: &fдействует как золотое яблоко."),
                    Msg.color("&8VoidSMP")
            ));
            meta.getPersistentDataContainer().set(Keys.humanMeat, PersistentDataType.BOOLEAN, true);
        });
        return item;
    }

    public static boolean isHumanMeat(ItemStack item) {
        return item != null && item.getType() == Material.ROTTEN_FLESH && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(Keys.humanMeat, PersistentDataType.BOOLEAN);
    }
}
