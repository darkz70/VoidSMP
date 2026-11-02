package me.darkz70.quirks.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mob.ZayceKrol;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Админ: Shift+ПКМ по обычному кролику (не зайцекролу) — «вселение» в него.
 * Пока админ вселён: он невидим, настоящий кролик следует за ним каждый тик
 * (визуально это выглядит так, будто игрок и есть кролик), а хотбар занят
 * шестью способностями + кнопкой выхода.
 */
public final class RabbitPossessionListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public RabbitPossessionListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private static final class State {
        final UUID rabbitId;
        final ItemStack[] savedHotbar;
        final boolean savedAllowFlight;
        final boolean savedFlying;
        BukkitTask followTask;

        State(UUID rabbitId, ItemStack[] savedHotbar, boolean savedAllowFlight, boolean savedFlying) {
            this.rabbitId = rabbitId;
            this.savedHotbar = savedHotbar;
            this.savedAllowFlight = savedAllowFlight;
            this.savedFlying = savedFlying;
        }
    }

    private final Map<UUID, State> active = new HashMap<>();

    public boolean isPossessing(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    // ---------- вход/выход ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent event) {
        Player admin = event.getPlayer();
        if (!(event.getRightClicked() instanceof Rabbit rabbit)) return;
        if (!admin.isSneaking()) return;
        if (!admin.isOp() && !admin.hasPermission("quirks.admin")) return;
        if (ZayceKrol.isZayce(rabbit)) return; // у зайцекролов своё меню (ZayceMobMenu)
        if (rabbit.getPersistentDataContainer().has(Keys.rabbitPossessedBy, PersistentDataType.STRING)) {
            admin.sendMessage(Msg.color("&cЭтого кролика уже кто-то занял."));
            return;
        }
        event.setCancelled(true);
        if (isPossessing(admin)) {
            admin.sendMessage(Msg.color("&cТы уже в ком-то сидишь — сначала выйди (слот 9)."));
            return;
        }
        enter(admin, rabbit);
    }

    private void enter(Player admin, Rabbit rabbit) {
        ItemStack[] saved = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack item = admin.getInventory().getItem(i);
            saved[i] = item == null ? null : item.clone();
            admin.getInventory().setItem(i, null);
        }
        admin.getInventory().setItem(0, abilityItem(Material.BONE, "bite",
                "&cУкус", "&7Ближний бой: 6 урона + яд."));
        admin.getInventory().setItem(1, abilityItem(Material.RABBIT_FOOT, "ram",
                "&6Таран-прыжок", "&7Рывок вперёд, сбивает всех на пути."));
        admin.getInventory().setItem(2, abilityItem(Material.PHANTOM_MEMBRANE, "claw",
                "&dКоготь ярости", "&7Широкий удар по всем рядом + замедление."));
        admin.getInventory().setItem(3, abilityItem(Material.FEATHER, "fly",
                "&bПолёт", "&7Как в креативе, пока ты в теле кролика."));
        admin.getInventory().setItem(4, abilityItem(Material.BLAZE_POWDER, "boom_small",
                "&6Малый взрыв", "&73×3, 10 урона.", "&8Блоки не трогает."));
        admin.getInventory().setItem(5, abilityItem(Material.NETHER_STAR, "boom_big",
                "&4Большой взрыв", "&79×9, 15 урона.", "&8Ломает 5×5 блоков и поджигает."));
        admin.getInventory().setItem(8, abilityItem(Material.BARRIER, "exit",
                "&cВыйти из кролика", "&7Вернёт тебе инвентарь."));

        admin.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE, 0, true, false, false));

        rabbit.setAI(false);
        rabbit.setInvulnerable(true);
        rabbit.setSilent(true);
        rabbit.setPersistent(true);
        rabbit.setCollidable(false);
        rabbit.getPersistentDataContainer().set(Keys.rabbitPossessedBy, PersistentDataType.STRING,
                admin.getUniqueId().toString());

        State state = new State(rabbit.getUniqueId(), saved, admin.getAllowFlight(), admin.isFlying());
        active.put(admin.getUniqueId(), state);

        state.followTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Entity e = Bukkit.getEntity(state.rabbitId);
            if (!admin.isOnline() || !(e instanceof Rabbit r) || !r.isValid()) {
                exit(admin);
                return;
            }
            Location loc = admin.getLocation();
            r.teleport(loc);
        }, 0L, 1L);

        admin.sendMessage(Msg.color("&a✦ Ты вселился в кролика! &7Слоты 1-3 — удары, 4 — полёт,"
                + " 5-6 — взрывы, 9 — выйти."));
    }

    private void exit(Player admin) {
        State state = active.remove(admin.getUniqueId());
        if (state == null) return;
        if (state.followTask != null) state.followTask.cancel();

        for (int i = 0; i < 9; i++) {
            admin.getInventory().setItem(i, state.savedHotbar[i]);
        }
        admin.removePotionEffect(PotionEffectType.INVISIBILITY);
        admin.setAllowFlight(state.savedAllowFlight);
        admin.setFlying(state.savedFlying && state.savedAllowFlight);

        Entity e = Bukkit.getEntity(state.rabbitId);
        if (e instanceof Rabbit rabbit && rabbit.isValid()) {
            rabbit.getPersistentDataContainer().remove(Keys.rabbitPossessedBy);
            rabbit.setAI(true);
            rabbit.setInvulnerable(false);
            rabbit.setSilent(false);
            rabbit.setCollidable(true);
        }
        admin.sendMessage(Msg.color("&7Ты вышел из тела кролика."));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isPossessing(event.getPlayer())) exit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isPossessing(player)) exit(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isAbilityItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isPossessing(player)) return;
        if (isAbilityItem(event.getCurrentItem()) || isAbilityItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    // ---------- использование способностей ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!isPossessing(player)) return;
        ItemStack item = event.getItem();
        String ability = abilityId(item);
        if (ability == null) return;
        event.setCancelled(true);

        switch (ability) {
            case "bite" -> doBite(player);
            case "ram" -> doRam(player);
            case "claw" -> doClaw(player);
            case "fly" -> doFly(player);
            case "boom_small" -> doBoomSmall(player);
            case "boom_big" -> doBoomBig(player);
            case "exit" -> exit(player);
            default -> { }
        }
    }

    private void doBite(Player player) {
        if (!ScanUtil.tryUse(player.getUniqueId(), "rabbit_bite", 1000)) return;
        LivingEntity target = nearestTarget(player, 3.0);
        if (target != null && !(target instanceof Rabbit)) {
            target.damage(6.0, player);
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RABBIT_ATTACK, 1f, 1.2f);
        player.swingMainHand();
    }

    private void doRam(Player player) {
        if (!ScanUtil.tryUse(player.getUniqueId(), "rabbit_ram", 3000)) return;
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        player.setVelocity(dir.multiply(1.6).setY(0.25));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RABBIT_JUMP, 1f, 0.7f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Entity e : player.getNearbyEntities(2.5, 2.5, 2.5)) {
                if (e instanceof LivingEntity target && e != player && !(target instanceof Rabbit)) {
                    target.damage(8.0, player);
                    Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector())
                            .normalize().setY(0.3);
                    target.setVelocity(push.multiply(1.2));
                }
            }
        }, 3L);
    }

    private void doClaw(Player player) {
        if (!ScanUtil.tryUse(player.getUniqueId(), "rabbit_claw", 2000)) return;
        for (Entity e : player.getNearbyEntities(4, 4, 4)) {
            if (e instanceof LivingEntity target && !(target instanceof Rabbit)) {
                target.damage(5.0, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
            }
        }
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1, 0), 6, 1, 0.5, 1, 0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
    }

    private void doFly(Player player) {
        if (!ScanUtil.tryUse(player.getUniqueId(), "rabbit_fly", 500)) return;
        boolean on = !player.isFlying();
        player.setAllowFlight(on);
        player.setFlying(on);
        player.sendMessage(Msg.color(on ? "&bПолёт включён." : "&7Полёт выключен."));
    }

    private void doBoomSmall(Player player) {
        if (!ScanUtil.tryUse(player.getUniqueId(), "rabbit_boom_small", 4000)) return;
        Location point = impactPoint(player, 20);
        for (Entity e : point.getWorld().getNearbyEntities(point, 1.5, 1.5, 1.5)) {
            if (e instanceof LivingEntity target && e != player && !(target instanceof Rabbit)) {
                target.damage(10.0, player);
            }
        }
        point.getWorld().spawnParticle(Particle.EXPLOSION, point, 1);
        point.getWorld().playSound(point, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.4f);
    }

    private void doBoomBig(Player player) {
        if (!ScanUtil.tryUse(player.getUniqueId(), "rabbit_boom_big", 8000)) return;
        Location point = impactPoint(player, 30);
        for (Entity e : point.getWorld().getNearbyEntities(point, 4.5, 4.5, 4.5)) {
            if (e instanceof LivingEntity target && e != player && !(target instanceof Rabbit)) {
                target.damage(15.0, player);
            }
        }
        breakCrater(point);
        point.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, point, 1);
        point.getWorld().playSound(point, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.8f);
    }

    /** Ломает площадку 5×5 вокруг точки и поджигает часть расчищенной поверхности. */
    private void breakCrater(Location point) {
        Block center = point.getBlock();
        if (center.getType().isAir()) {
            for (int dy = 0; dy > -6; dy--) {
                Block below = point.getWorld().getBlockAt(center.getX(), center.getY() + dy, center.getZ());
                if (!below.getType().isAir()) { center = below; break; }
            }
        }
        List<Block> cleared = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block b = center.getRelative(dx, 0, dz);
                if (!canBreak(b)) continue;
                b.setType(Material.AIR);
                cleared.add(b);
                Block above = b.getRelative(0, 1, 0);
                if (canBreak(above) && !above.getType().isAir()) {
                    above.setType(Material.AIR);
                }
            }
        }
        for (Block b : cleared) {
            if (Math.random() < 0.3) {
                Block above = b.getRelative(0, 1, 0);
                if (above.getType().isAir()) above.setType(Material.FIRE);
            }
        }
    }

    private static boolean canBreak(Block block) {
        return switch (block.getType()) {
            case BEDROCK, BARRIER, COMMAND_BLOCK, STRUCTURE_BLOCK, STRUCTURE_VOID,
                    CHEST, TRAPPED_CHEST, ENDER_CHEST, SPAWNER, END_PORTAL_FRAME, END_PORTAL,
                    NETHER_PORTAL, WATER, LAVA, REINFORCED_DEEPSLATE -> false;
            default -> true;
        };
    }

    /** Точка попадания: блок/сущность по лучу взгляда, иначе фиксированная точка впереди. */
    private Location impactPoint(Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, dir, maxDistance, FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitPosition() != null) {
            return hit.getHitPosition().toLocation(player.getWorld());
        }
        return eye.clone().add(dir.clone().multiply(Math.min(maxDistance, 10)));
    }

    private LivingEntity nearestTarget(Player player, double radius) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity target) || target instanceof Rabbit) continue;
            double dist = target.getLocation().distanceSquared(player.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = target;
            }
        }
        return best;
    }

    // ---------- хотбар-предметы способностей ----------

    private ItemStack abilityItem(Material material, String id, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Msg.color(name));
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(Msg.color(l));
            meta.lore(lines);
            meta.getPersistentDataContainer().set(Keys.rabbitAbility, PersistentDataType.STRING, id);
        });
        return item;
    }

    private static boolean isAbilityItem(ItemStack item) {
        return abilityId(item) != null;
    }

    private static String abilityId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(Keys.rabbitAbility, PersistentDataType.STRING);
    }
}
