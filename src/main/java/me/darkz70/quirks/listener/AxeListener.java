package me.darkz70.quirks.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.MaterialLists;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Причуда «Топор»: запрет мечей, мясоедение, реген за убийства (10%, 2 сек, все уровни),
 * головы (3 ур.) и рывок в точку взгляда (Shift + Ctrl, 3 ур.) с показом кулдауна.
 */
public final class AxeListener implements Listener {

    private final VoidQuirksPlugin plugin;
    private final Random random = new Random();
    /** когда игрок последний раз садился на шифт (для Shift+Ctrl) */
    private final Map<UUID, Long> lastSneak = new HashMap<>();

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

    // ---------- дебафф: без мечей (1-2 ур.) ----------

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
        if (MaterialLists.isLowSword(stack.getType())) {
            event.getItem().setItemStack(new ItemStack(Material.STICK, Math.max(1, stack.getAmount())));
            if (ScanUtil.tryUse(player.getUniqueId(), "axe-stick-msg", 3000)) {
                plugin.notify(player, "axe-sword-to-stick");
            }
        }
    }

    /** Периодическая зачистка мечей в инвентаре (вызывается из EffectsTask). */
    public static void sweepSwords(VoidQuirksPlugin plugin, Player player) {
        PlayerInventory inv = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && MaterialLists.isLowSword(item.getType())) {
                inv.setItem(i, new ItemStack(Material.STICK, Math.max(1, item.getAmount())));
                changed = true;
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && MaterialLists.isLowSword(cursor.getType())) {
            player.setItemOnCursor(new ItemStack(Material.STICK, Math.max(1, cursor.getAmount())));
            changed = true;
        }
        if (changed && ScanUtil.tryUse(player.getUniqueId(), "axe-stick-msg", 3000)) {
            plugin.notify(player, "axe-sword-to-stick");
        }
    }

    // ---------- дебафф: только мясо (2-3 ур.) ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (level(player) < 2) return;
        if (MaterialLists.isMeat(event.getItem().getType())) return;

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

        // регенерация II на 2 секунды — 10% на ВСЕХ уровнях
        int regenChance = plugin.getConfig().getInt("axe.regen-chance", 10);
        if (random.nextInt(100) < regenChance) {
            int ticks = plugin.getConfig().getInt("axe.regen-duration-seconds", 2) * 20;
            killer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 1, true, false, true));
        }

        // головы мобов — только 3 уровень
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

    // ---------- рывок (3 ур.): Shift, затем Ctrl ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (level(player) < 3) return;
        if (event.isSneaking()) {
            lastSneak.put(player.getUniqueId(), System.currentTimeMillis());
            if (isAxe(player.getInventory().getItemInMainHand().getType())) {
                showHint(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (level(player) < 3 || !player.isSneaking()) return;
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item != null && isAxe(item.getType())) {
            showHint(player);
        }
    }

    private void showHint(Player player) {
        if (ScanUtil.tryUse(player.getUniqueId(), "axe-hint", 4000)) {
            plugin.notifyBar(player, "axe-teleport-hint");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) return;
        Player player = event.getPlayer();
        if (level(player) < 3) return;
        if (!isAxe(player.getInventory().getItemInMainHand().getType())) return;

        // настоящий Ctrl во время шифта: сник недавно или всё ещё зажат
        Long sneakAt = lastSneak.get(player.getUniqueId());
        boolean sneakyContext = player.isSneaking()
                || (sneakAt != null && System.currentTimeMillis() - sneakAt < 1500);
        if (!sneakyContext) return;

        long cooldownMs = plugin.getConfig().getLong("axe.teleport.cooldown-seconds", 5) * 1000L;
        long remaining = ScanUtil.remaining(player.getUniqueId(), "axe-tp", cooldownMs);
        if (remaining > 0) {
            // показываем, сколько осталось ждать
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
        Location dest = feet.getLocation().add(0.5, 0, 0.5);
        dest.setYaw(player.getLocation().getYaw());
        dest.setPitch(player.getLocation().getPitch());
        player.teleport(dest);
        player.setFallDistance(0);
        int cost = plugin.getConfig().getInt("axe.teleport.hunger-cost-drumsticks", 4) * 2;
        player.setFoodLevel(Math.max(0, player.getFoodLevel() - cost));
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
