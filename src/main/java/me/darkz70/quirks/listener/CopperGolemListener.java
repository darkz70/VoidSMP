package me.darkz70.quirks.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Инженер (со 2 ур.): «прошивка» — предмет из блока редстоуна и блока меди.
 * ПКМ прошивкой по медному голему открывает меню выбора работы: добыча руды рядом
 * или атака враждебных мобов рядом. С этого момента голем — «работник» инженера:
 * периодическая задача двигает его к цели и выполняет работу.
 */
public final class CopperGolemListener implements Listener {

    private static final double MINE_RADIUS = 6.0;
    private static final double KILL_RADIUS = 10.0;
    private static final double REACH = 1.6;
    private static final double GOLEM_DAMAGE = 3.0;

    private final VoidQuirksPlugin plugin;

    public CopperGolemListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 40L, 40L);
    }

    private record Holder(UUID golemId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 27, Component.empty());
        }
    }

    // ---------- предмет «прошивка» ----------

    public static ItemStack makeFirmware() {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&cПрошивка медного голема"));
            meta.lore(List.of(
                    Msg.color("&7ПКМ по медному голему — выбрать его работу:"),
                    Msg.color("&7добыча руды или бой с враждебными мобами."),
                    Msg.color("&8Расходуется при использовании.")));
            meta.getPersistentDataContainer().set(Keys.engineerFirmware, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public static boolean isFirmware(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(Keys.engineerFirmware, PersistentDataType.BYTE);
    }

    // ---------- ПКМ по голему: меню выбора работы ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (clicked.getType() != EntityType.COPPER_GOLEM) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isFirmware(hand)) return;

        event.setCancelled(true);
        if (plugin.quirks().levelOf(player, Quirk.ENGINEER) < 2
                && plugin.quirks().levelOf(player, Quirk.ADMIN) == 0) {
            Msg.send(player, "engineer-golem-need-level");
            return;
        }
        openJobMenu(player, clicked);
    }

    private void openJobMenu(Player player, Entity golem) {
        Inventory inv = Bukkit.createInventory(new Holder(golem.getUniqueId()), 27,
                Msg.color("&8Прошивка медного голема"));
        inv.setItem(11, btn(Material.RAW_IRON, "&6Добыча руды",
                "&7Голем ищет и добывает руду рядом,",
                "&7находки летят прямо тебе в инвентарь."));
        inv.setItem(15, btn(Material.IRON_SWORD, "&cУбийство враждебных мобов",
                "&7Голем атакует враждебных мобов рядом."));
        inv.setItem(22, btn(Material.BARRIER, "&7Отмена"));
        player.openInventory(inv);
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

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot != 11 && slot != 15) {
            player.closeInventory();
            return;
        }

        Entity entity = Bukkit.getEntity(holder.golemId());
        if (entity == null || !entity.isValid() || entity.getType() != EntityType.COPPER_GOLEM) {
            Msg.send(player, "engineer-golem-gone");
            player.closeInventory();
            return;
        }
        if (plugin.quirks().levelOf(player, Quirk.ENGINEER) < 2
                && plugin.quirks().levelOf(player, Quirk.ADMIN) == 0) {
            player.closeInventory();
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isFirmware(hand)) {
            player.closeInventory();
            return;
        }

        hand.setAmount(hand.getAmount() - 1);
        String job = slot == 11 ? "mine" : "kill";
        var pdc = entity.getPersistentDataContainer();
        pdc.set(Keys.golemOwner, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(Keys.golemJob, PersistentDataType.STRING, job);
        entity.customName(Msg.color((job.equals("mine") ? "&6⚙ Шахтёр" : "&c⚙ Страж") + " &7· &f" + player.getName()));
        entity.setCustomNameVisible(true);
        Msg.send(player, job.equals("mine") ? "engineer-golem-set-mine" : "engineer-golem-set-kill");
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        player.closeInventory();
    }

    // ---------- периодика: работа прошитых големов ----------

    private void tickAll() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity.getType() != EntityType.COPPER_GOLEM) continue;
                String job = entity.getPersistentDataContainer().get(Keys.golemJob, PersistentDataType.STRING);
                if (job == null) continue;
                switch (job) {
                    case "mine" -> tickMining(entity);
                    case "kill" -> tickKilling(entity);
                    default -> { }
                }
            }
        }
    }

    private void tickMining(LivingEntity golem) {
        Block target = nearestOre(golem.getLocation().getBlock(), (int) MINE_RADIUS);
        if (target == null) return;

        if (golem.getLocation().distanceSquared(target.getLocation().add(0.5, 0.5, 0.5)) > REACH * REACH) {
            moveToward(golem, target.getLocation().add(0.5, 0, 0.5));
            return;
        }

        ItemStack drop = oreDrop(target.getType());
        golem.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.5, 0.5, 0.5), 18,
                0.3, 0.3, 0.3, 0.05);
        golem.getWorld().playSound(target.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 1f);
        target.setType(Material.AIR);

        Player owner = ownerOf(golem);
        if (owner != null && owner.isOnline()) {
            var leftover = owner.getInventory().addItem(drop);
            leftover.values().forEach(it -> owner.getWorld().dropItemNaturally(owner.getLocation(), it));
        } else {
            golem.getWorld().dropItemNaturally(golem.getLocation(), drop);
        }
    }

    private void tickKilling(LivingEntity golem) {
        Monster target = nearestMonster(golem, KILL_RADIUS);
        if (target == null) return;

        if (golem.getLocation().distanceSquared(target.getLocation()) > REACH * REACH) {
            moveToward(golem, target.getLocation());
            return;
        }

        target.damage(GOLEM_DAMAGE, golem);
        golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.7f, 1.6f);
    }

    private void moveToward(LivingEntity golem, org.bukkit.Location dest) {
        Vector dir = dest.toVector().subtract(golem.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.0001) return;
        dir.normalize().multiply(0.18);
        Vector velocity = golem.getVelocity();
        golem.setVelocity(new Vector(dir.getX(), velocity.getY(), dir.getZ()));
    }

    @Nullable
    private Player ownerOf(LivingEntity golem) {
        String raw = golem.getPersistentDataContainer().get(Keys.golemOwner, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return Bukkit.getPlayer(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Nullable
    private Block nearestOre(Block center, int radius) {
        Block best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block candidate = center.getRelative(dx, dy, dz);
                    if (!isOre(candidate.getType())) continue;
                    int dist = dx * dx + dy * dy + dz * dz;
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isOre(Material material) {
        return material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }

    private static ItemStack oreDrop(Material ore) {
        int r = ThreadLocalRandom.current().nextInt(3);
        return switch (ore) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> new ItemStack(Material.COAL, 1 + r);
            case IRON_ORE, DEEPSLATE_IRON_ORE -> new ItemStack(Material.RAW_IRON, 1);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> new ItemStack(Material.RAW_GOLD, 1);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> new ItemStack(Material.RAW_COPPER, 2 + r);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> new ItemStack(Material.DIAMOND, 1);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> new ItemStack(Material.EMERALD, 1);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> new ItemStack(Material.LAPIS_LAZULI, 4 + r);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> new ItemStack(Material.REDSTONE, 4 + r);
            case NETHER_QUARTZ_ORE -> new ItemStack(Material.QUARTZ, 1 + r);
            case ANCIENT_DEBRIS -> new ItemStack(Material.NETHERITE_SCRAP, 1);
            default -> new ItemStack(ore, 1);
        };
    }

    @Nullable
    private Monster nearestMonster(LivingEntity golem, double radius) {
        Monster best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : golem.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Monster monster)) continue;
            double dist = monster.getLocation().distanceSquared(golem.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = monster;
            }
        }
        return best;
    }
}
