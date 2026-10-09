package me.darkz70.quirks.listener;

import java.util.Random;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.MaterialLists;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Причуда «Топор» (v0.6.0):
 * 1 ур.: без крафта мечей, мечи слабее незеритового — палки; реген II за убийство топором (5%, 1 сек).
 * 2 ур.: + мечи слабее алмазного — палки; только мясо (иначе яд); режим ярости (Shift, затем Ctrl).
 * 3 ур.: + разрыв пространства (Shift + ПКМ, рывок до 20 блоков, 4 голода, кд 2 сек) и головы.
 */
public final class AxeListener implements Listener {

    private final VoidQuirksPlugin plugin;
    private final Random random = new Random();

    public AxeListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    /** 0 = причуды нет. */
    private int level(Player player) {
        return plugin.quirks().levelOf(player, Quirk.AXE);
    }

    private static boolean isSword(Material material) {
        return material.name().endsWith("_SWORD");
    }

    private static boolean isAxe(Material material) {
        return material.name().endsWith("_AXE");
    }

    // ---------- дебафф: без крафта мечей (1-2 ур.) ----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        int lvl = level(player);
        if (lvl == 0 || lvl > 2) return;
        ItemStack result = event.getInventory().getResult();
        if (result != null && isSword(result.getType())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int lvl = level(player);
        if (lvl == 0 || lvl > 2) return;
        ItemStack result = event.getInventory().getResult();
        if (result != null && isSword(result.getType())) {
            event.setCancelled(true);
            Msg.send(player, "axe-sword-craft-denied");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        int lvl = level(player);
        if (lvl == 0 || lvl > 2) return;
        ItemStack stack = event.getItem().getItemStack();
        if (MaterialLists.isLowSword(lvl, stack.getType())) {
            event.getItem().setItemStack(new ItemStack(Material.STICK, Math.max(1, stack.getAmount())));
            if (ScanUtil.tryUse(player.getUniqueId(), "axe-stick-msg", 3000)) {
                plugin.notify(player, "axe-sword-to-stick");
            }
        }
    }

    /** Периодическая зачистка мечей в инвентаре (вызывается из EffectsTask). */
    public static void sweepSwords(VoidQuirksPlugin plugin, Player player, int level) {
        PlayerInventory inv = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && MaterialLists.isLowSword(level, item.getType())) {
                inv.setItem(i, new ItemStack(Material.STICK, Math.max(1, item.getAmount())));
                changed = true;
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && MaterialLists.isLowSword(level, cursor.getType())) {
            player.setItemOnCursor(new ItemStack(Material.STICK, Math.max(1, cursor.getAmount())));
            changed = true;
        }
        if (changed && ScanUtil.tryUse(player.getUniqueId(), "axe-stick-msg", 3000)) {
            plugin.notify(player, "axe-sword-to-stick");
        }
    }

    // ---------- дебафф: только мясо (2-3 ур.), иначе отравление ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (level(player) < 2) return;
        // зелья — не еда: их пьют все расы без наказаний
        Material eaten = event.getItem().getType();
        if (eaten == Material.POTION || eaten == Material.OMINOUS_BOTTLE) return;
        if (MaterialLists.isMeat(eaten)) return;

        // еда съедается, но тело мстит
        Msg.send(player, "axe-no-meat");
        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 1, true, false, true));
    }

    // ---------- баффы за убийства топором ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        int lvl = level(killer);
        if (lvl == 0) return;
        if (!isAxe(killer.getInventory().getItemInMainHand().getType())) return;

        LivingEntity dead = event.getEntity();

        // регенерация II на 1 секунду, 5% — только 1 уровень
        if (lvl == 1) {
            int regenChance = plugin.getConfig().getInt("axe.regen-chance", 5);
            if (random.nextInt(100) < regenChance) {
                int ticks = plugin.getConfig().getInt("axe.regen-duration-seconds", 1) * 20;
                killer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 1, true, false, true));
            }
        }

        // головы мобов — только 3 уровень (легаси-бонус)
        if (lvl >= 3) {
            int headChance = plugin.getConfig().getInt("axe.head-chance", 2);
            if (random.nextInt(100) < headChance) {
                ItemStack head = headItem(dead);
                if (head != null) {
                    dead.getWorld().dropItemNaturally(dead.getLocation(), head);
                }
            }
        }
    }

    @Nullable
    private ItemStack headItem(LivingEntity entity) {
        Material head = switch (entity.getType()) {
            case ZOMBIE, HUSK, DROWNED, ZOMBIE_VILLAGER -> Material.ZOMBIE_HEAD;
            case SKELETON, STRAY, BOGGED -> Material.SKELETON_SKULL;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case CREEPER -> Material.CREEPER_HEAD;
            case PIGLIN, ZOMBIFIED_PIGLIN, PIGLIN_BRUTE -> Material.PIGLIN_HEAD;
            case ENDER_DRAGON -> Material.DRAGON_HEAD;
            case PLAYER -> plugin.getConfig().getBoolean("axe.head-include-players", true)
                    ? Material.PLAYER_HEAD : null;
            default -> null;
        };
        if (head == null) return null;
        ItemStack item = new ItemStack(head);
        if (head == Material.PLAYER_HEAD && entity instanceof Player victim) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setOwningPlayer(victim);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ---------- подсказки (2-3 ур.): при взятии топора в руку; на 3 ур. ещё и по Shift ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        int lvl = level(player);
        if (lvl < 3) return;
        if (event.isSneaking()
                && isAxe(player.getInventory().getItemInMainHand().getType())) {
            showHint(player, lvl);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        int lvl = level(player);
        if (lvl < 2) return;
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item != null && isAxe(item.getType())) {
            showHint(player, lvl);
        }
    }

    private void showHint(Player player, int lvl) {
        if (ScanUtil.tryUse(player.getUniqueId(), "axe-hint", 4000)) {
            plugin.notifyBar(player, lvl >= 3 ? "axe-hint-3" : "axe-hint-2");
        }
    }

    // ---------- режим ярости (2-3 ур.): Shift + ЛКМ топором ----------

    private void tryRage(Player player) {
        int lvl = level(player);
        if (lvl < 2) return;

        long cooldownMs = plugin.getConfig().getLong("axe.rage-cooldown-minutes", 30) * 60_000L;
        if (plugin.magic().cooldownsOff(player.getUniqueId())) cooldownMs = 0;
        else cooldownMs = (long) (cooldownMs * plugin.magic().coolScale("items"));
        long remaining = ScanUtil.remaining(player.getUniqueId(), "axe-rage", cooldownMs);
        if (remaining > 0) {
            if (ScanUtil.tryUse(player.getUniqueId(), "axe-rage-msg", 1000)) {
                long seconds = (remaining + 999) / 1000;
                String time = seconds >= 60 ? ((seconds + 59) / 60) + " мин." : seconds + " с.";
                player.sendActionBar(Msg.comp("axe-rage-cooldown", "%time%", time));
            }
            return;
        }

        ScanUtil.stamp(player.getUniqueId(), "axe-rage");
        // ярость: сила 4 сек; скорость II 2 сек; реген 2 сек (уровень зависит от причуды)
        int strengthAmp = lvl >= 3 ? 1 : 0;
        int regenAmp = lvl >= 3 ? 2 : 1;
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, strengthAmp, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, regenAmp, true, false, true));
        Msg.send(player, "axe-rage-on");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.2f);
    }

    // ---------- разрыв пространства (3 ур.): Shift + ПКМ топором; ярость: Shift + ЛКМ ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        Player player = event.getPlayer();

        // ярость: Shift + ЛКМ топором (2+ ур.)
        if ((action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)
                && player.isSneaking()
                && level(player) >= 2
                && isAxe(player.getInventory().getItemInMainHand().getType())) {
            tryRage(player);
            return;
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (level(player) < 3) return;
        if (!player.isSneaking()) return;
        if (!isAxe(player.getInventory().getItemInMainHand().getType())) return;

        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        long cooldownMs = plugin.getConfig().getLong("axe.teleport.cooldown-seconds", 2) * 1000L;
        long remaining = ScanUtil.remaining(player.getUniqueId(), "axe-tp", cooldownMs);
        if (remaining > 0) {
            if (ScanUtil.tryUse(player.getUniqueId(), "axe-tp-msg", 1000)) {
                player.sendActionBar(Msg.comp("axe-teleport-cooldown",
                        "%time%", String.valueOf((remaining + 999) / 1000)));
            }
            return;
        }

        double maxDistance = plugin.getConfig().getDouble("axe.teleport.max-distance", 20.0);
        Block feet = findTeleportSpot(player, maxDistance);
        if (feet == null) {
            Msg.send(player, "axe-teleport-fail");
            return;
        }

        ScanUtil.stamp(player.getUniqueId(), "axe-tp");
        Location from = player.getLocation().add(0, 1, 0);
        Location dest = feet.getLocation().add(0.5, 0, 0.5);
        dest.setYaw(player.getLocation().getYaw());
        dest.setPitch(player.getLocation().getPitch());
        player.teleport(dest);
        player.setFallDistance(0);
        int cost = plugin.getConfig().getInt("axe.teleport.hunger-cost-drumsticks", 4) * 2;
        player.setFoodLevel(Math.max(0, player.getFoodLevel() - cost));
        // частицы как у эндермена — и на старте, и в пункте назначения
        player.getWorld().spawnParticle(Particle.PORTAL, from, 40, 0.3, 0.6, 0.3, 0.5);
        player.getWorld().spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.5);
        player.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
    }

    /** Точка взгляда: блок по рейтрейсу, либо последняя проходимая точка луча. */
    @Nullable
    private Block findTeleportSpot(Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().clone().normalize();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                eye, direction, maxDistance, FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitBlock() != null) {
            BlockFace face = hit.getHitBlockFace() != null ? hit.getHitBlockFace() : BlockFace.UP;
            Block feet = standable(hit.getHitBlock().getRelative(face));
            if (feet == null && face != BlockFace.UP) {
                feet = standable(hit.getHitBlock().getRelative(BlockFace.UP));
            }
            if (feet != null) return feet;
        }
        // иначе — летим по лучу и ищем последнее безопасное место
        for (double d = maxDistance; d >= 1; d -= 0.5) {
            Block candidate = eye.clone().add(direction.clone().multiply(d)).getBlock();
            Block feet = standable(candidate);
            if (feet != null) return feet;
        }
        return null;
    }

    /** Ищет проходимое место (ноги+голова воздухоподобные) рядом с блоком (до +2 вверх). */
    @Nullable
    private Block standable(Block base) {
        Block current = base;
        for (int i = 0; i <= 2 && current != null; i++) {
            if (current.isPassable() && current.getRelative(BlockFace.UP).isPassable()) {
                return current;
            }
            current = current.getRelative(BlockFace.UP);
        }
        return null;
    }
}
