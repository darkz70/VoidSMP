package me.darkz70.quirks.magic;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Обработка магии: каст ПКМ фокус-предметом, Q — цикл заклинаний, F — меню-выбор. */
public final class MagicListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public MagicListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    private MagicSystem magic() {
        return plugin.magic();
    }

    // ---------- иконки меню ----------

    private static final Map<Element, Material[]> ICONS = new EnumMap<>(Element.class);

    static {
        ICONS.put(Element.FIRE, new Material[]{Material.FIRE_CHARGE, Material.MAGMA_CREAM, Material.BLAZE_POWDER,
            Material.LAVA_BUCKET, Material.BLAZE_ROD, Material.NETHERRACK, Material.NETHER_STAR});
        ICONS.put(Element.WATER, new Material[]{Material.HEART_OF_THE_SEA, Material.PRISMARINE_SHARD,
            Material.PRISMARINE_CRYSTALS, Material.ICE, Material.TUBE_CORAL_FAN, Material.HEART_OF_THE_SEA,
            Material.TRIDENT});
        ICONS.put(Element.WIND, new Material[]{Material.FEATHER, Material.PAPER, Material.WIND_CHARGE,
            Material.PHANTOM_MEMBRANE, Material.GHAST_TEAR, Material.FIREWORK_ROCKET, Material.ELYTRA});
        ICONS.put(Element.EARTH, new Material[]{Material.STONE, Material.COBBLED_DEEPSLATE, Material.DIRT,
            Material.BRICK, Material.POINTED_DRIPSTONE, Material.OBSIDIAN, Material.NETHERITE_BLOCK});
        ICONS.put(Element.DARK, new Material[]{Material.SCULK_SENSOR, Material.ECHO_SHARD, Material.SCULK,
            Material.SOUL_LANTERN, Material.SCULK_CATALYST, Material.REINFORCED_DEEPSLATE, Material.DRAGON_HEAD});
        ICONS.put(Element.LIGHT, new Material[]{Material.GLOW_BERRIES, Material.TORCH, Material.GLOWSTONE,
            Material.SEA_LANTERN, Material.CANDLE, Material.END_CRYSTAL, Material.NETHER_STAR});
    }

    private static boolean isFocus(ItemStack item) {
        return item != null && !item.getType().isAir()
                && item.getPersistentDataContainer().has(Keys.focusKey, PersistentDataType.BYTE);
    }

    private static ItemStack mainFocus(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isFocus(main)) return main;
        ItemStack off = player.getInventory().getItemInOffHand();
        return isFocus(off) ? off : null;
    }

    // ---------- каст ПКМ ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (!right || item == null) return;

        // книги стихий
        String mark = item.getPersistentDataContainer().get(Keys.brewMark, PersistentDataType.STRING);
        if (mark != null && mark.startsWith("elem-book-")) {
            event.setCancelled(true);
            Element element = Element.byId(mark.substring("elem-book-".length()));
            if (element != null && magic().learnElement(player, element)) {
                item.setAmount(item.getAmount() - 1);
            }
            return;
        }
        if (mark != null && mark.startsWith("upbook-")) {
            event.setCancelled(true);
            int tier = Integer.parseInt(mark.substring("upbook-".length()));
            if (magic().useUpgradeBook(player, tier)) {
                item.setAmount(item.getAmount() - 1);
            }
            return;
        }

        // каст фокус-предметом
        if (!isFocus(item)) return;
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable()
                && clicked.getType() != Material.ENCHANTING_TABLE && !player.isSneaking()) {
            return; // двери/сундуки работают как обычно; со Shift каст всегда
        }
        event.setCancelled(true);
        magic().cast(player);
    }

    // ---------- Q — цикл заклинаний ----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (!isFocus(item)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        PlayerData data = magic().data(player);
        Element element = magic().elementOf(player);
        if (data == null || element == null) {
            Msg.send(player, "magic-no-mage");
            return;
        }
        Spell[] spells = Spell.of(element);
        int current = Math.max(0, Math.min(data.selectedSpell(), spells.length - 1));
        for (int step = 1; step <= spells.length; step++) {
            int next = (current + step) % spells.length;
            if (data.magicLevel() >= spells[next].requiredLevel()) {
                data.selectedSpell(next);
                plugin.storage().save();
                showBar(player, element, data, next);
                player.getWorld().playSound(player.getLocation(),
                        org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
                return;
            }
        }
    }

    private void showBar(Player player, Element element, PlayerData data, int idx) {
        Spell spell = Spell.of(element)[idx];
        player.sendActionBar(Msg.comp("magic-bar",
                "%emoji%", element.emoji(),
                "%mana%", String.valueOf((int) Math.floor(data.mana())),
                "%max%", String.valueOf(magic().maxMana(data.magicLevel())),
                "%element%", element.display(),
                "%spell%", spell.name()));
    }

    // ---------- F — меню заклинаний ----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        boolean focus = isFocus(event.getMainHandItem()) || isFocus(event.getOffHandItem());
        if (!focus) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Element element = magic().elementOf(player);
        if (element == null) {
            Msg.send(player, "magic-no-mage");
            return;
        }
        openMenu(player, element);
    }

    /** Меню выбора заклинания (27 слотов). */
    public void openMenu(Player player, Element element) {
        SpellMenu menu = new SpellMenu(element);
        Inventory inv = Bukkit.createInventory(menu, 27,
                Msg.comp("magic-menu-title", "%element%", element.display(), "%emoji%", element.emoji()));
        menu.inv = inv;
        fill(menu);
        player.openInventory(inv);
    }

    private void fill(SpellMenu menu) {
        Inventory inv = menu.inv;
        Player player = menu.player();
        if (player == null) return;
        PlayerData data = magic().data(player);
        Spell[] spells = Spell.of(menu.element);
        Material[] icons = ICONS.getOrDefault(menu.element, new Material[]{Material.BOOK});
        for (int i = 0; i < spells.length; i++) {
            Spell spell = spells[i];
            int slot = 10 + i;
            Material icon = icons[Math.min(i, icons.length - 1)];
            ItemStack item = new ItemStack(icon);
            item.editMeta(meta -> {
                boolean unlocked = data != null && data.magicLevel() >= spell.requiredLevel();
                boolean selected = data != null && data.selectedSpell() == i;
                meta.displayName(Msg.comp("magic-menu-item", "%emoji%", menu.element.emoji(),
                        "%spell%", spell.name(), "%state%", unlocked ? (selected ? "&a✔" : "&f") : "&8✘"));
                meta.lore(List.of(
                        Msg.color("&7Мана: &b" + spell.mana() + " &8| &7КД: &b" + spell.cooldownSeconds() + "с"),
                        Msg.color("&7Нужен уровень: &b" + spell.requiredLevel()),
                        Msg.color(unlocked ? (selected ? "&aСейчас выбрано" : "&eКлик — выбрать")
                                : "&8Закрыто до " + spell.requiredLevel() + " уровня")));
            });
            inv.setItem(slot, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpellMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 10 || slot > 16) return;
        int idx = slot - 10;
        PlayerData data = magic().data(player);
        if (data == null) return;
        Spell[] spells = Spell.of(menu.element);
        if (idx >= spells.length) return;
        Element current = magic().elementOf(player);
        if (current != menu.element) return;
        if (data.magicLevel() < spells[idx].requiredLevel()) {
            Msg.send(player, "magic-spell-locked",
                    "%level%", String.valueOf(spells[idx].requiredLevel()), "%spell%", spells[idx].name());
            return;
        }
        data.selectedSpell(idx);
        plugin.storage().save();
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        fill(menu);
        showBar(player, menu.element, data, idx);
    }

    /** Holder меню заклинаний. */
    static final class SpellMenu implements InventoryHolder {

        final Element element;
        Inventory inv;

        SpellMenu(Element element) {
            this.element = element;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        @org.jetbrains.annotations.Nullable
        Player player() {
            for (Entity viewer : inv == null ? List.<Entity>of() : inv.getViewers()) {
                if (viewer instanceof Player p) return p;
            }
            return null;
        }
    }

    // ---------- урон снарядов заклинаний ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpellHit(EntityDamageByEntityEvent event) {
        // снаряды
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player caster
                && !(event.getEntity() == caster && false)) {
            Element element = magic().projectileElement(projectile);
            double base = magic().projectileDamage(projectile);
            if (element != null && base > 0 && event.getEntity() instanceof LivingEntity victim) {
                event.setDamage(magic().computeDamage(caster, victim, base, element));
                magic().applySpellSide(caster, victim, element);
                if (element == Element.WIND) {
                    Vector dir = projectile.getVelocity().setY(0).normalize();
                    if (dir.lengthSquared() < 0.001) dir = victim.getLocation().toVector()
                            .subtract(caster.getLocation().toVector()).setY(0).normalize();
                    victim.setVelocity(victim.getVelocity().add(dir.multiply(1.4).setY(0.2)));
                }
                if (victim == caster) event.setCancelled(true); // себя не бьём
                return;
            }
        }

        // рукопашка с активными баффами
        if (!(event.getDamager() instanceof Player attacker)) {
            // у «Водяной ауры» — замедление атакующих мобов
            if (event.getEntity() instanceof Player victim && magic().waterAuraActive(victim.getUniqueId())
                    && event.getDamager() instanceof LivingEntity mob) {
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            }
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        // водяная аура жертвы замедляет атакующего
        if (victim instanceof Player vp && magic().waterAuraActive(vp.getUniqueId())) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
        }
        // огненная аура: удары поджигают
        if (magic().fireAuraActive(attacker.getUniqueId())) {
            victim.setFireTicks(Math.max(victim.getFireTicks(), 100));
        }
        // огненный профи: рукопашка ×2
        if (magic().fireMasterActive(attacker.getUniqueId())) {
            event.setDamage(event.getDamage() * 2);
        }
        // рывок ветра: +50% удара в полёте
        if (magic().windDashActive(attacker.getUniqueId())) {
            event.setDamage(event.getDamage() * 1.5);
        }
        // каменный кулак: +6 к следующему удару (разово)
        if (magic().earthFistActive(attacker.getUniqueId())) {
            magic().earthFistConsume(attacker.getUniqueId());
            event.setDamage(event.getDamage() + 6);
        }
        // теневое касание
        if (magic().darkTouchActive(attacker.getUniqueId())) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0));
        }
        // эхо-клоны: 35% урона повторно через 0.5с
        if (magic().echoActive(attacker.getUniqueId())) {
            double echo = event.getFinalDamage() * 0.35;
            Entity target = victim;
            Player echoSource = attacker;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (target instanceof LivingEntity le && le.isValid() && !le.isDead()) {
                    le.damage(echo, echoSource);
                    le.getWorld().spawnParticle(org.bukkit.Particle.SCULK_SOUL, le.getLocation().add(0, 1, 0),
                            6, 0.3, 0.5, 0.3, 0.01);
                }
            }, 10L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLand(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (magic().projectileElement(projectile) == null) return;
        org.bukkit.Location at = projectile.getLocation();
        java.util.List<Block> hit = event.getHitBlock() != null ? List.of(event.getHitBlock()) : List.of();
        if (!hit.isEmpty()) {
            at.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, at, 12, 0.3, 0.3, 0.3, 0.02);
        }
        // Большой бум: взрыв при попадании
        Integer flags = projectile.getPersistentDataContainer().get(Keys.spellExtra, PersistentDataType.INTEGER);
        if (flags != null && (flags & 1) == 1) {
            Entity shooter = projectile.getShooter() instanceof Entity e ? e : null;
            at.getWorld().createExplosion(at, 1.6f, false, false, shooter);
        }
    }

    // ---------- защита и прочее ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        MagicSystem magic = magic();
        java.util.UUID id = victim.getUniqueId();

        if (magic.isInvuln(id)) {
            event.setCancelled(true);
            return;
        }
        if (magic.darkEvadeActive(id) && Math.random() < 0.25) {
            event.setCancelled(true);
            victim.getWorld().spawnParticle(org.bukkit.Particle.SMOKE,
                    victim.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
            return;
        }
        switch (event.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> {
                if (magic.fireProtActive(id)) { event.setCancelled(true); return; }
            }
            case DROWNING -> {
                if (magic.waterProtActive(id)) { event.setCancelled(true); return; }
            }
            case FALL -> {
                if (magic.leapActive(id)) {
                    // Прыжок бури: мягкая посадка = ударная волна
                    magic.leapMap().remove(id);
                    event.setCancelled(true);
                    Player caster = victim;
                    magic.burst(caster, caster.getLocation(), 4, 6, Element.WIND, 0.9, null);
                    victim.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, victim.getLocation(), 40,
                            1.2, 0.3, 1.2, 0.03);
                    return;
                }
                if (magic.windProtActive(id)) { event.setCancelled(true); return; }
            }
            default -> { }
        }
        // световой щит
        double absorbed = magic.absorbShield(id, event.getDamage());
        if (absorbed > 0) {
            event.setDamage(Math.max(0, event.getDamage() - absorbed));
            victim.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, victim.getLocation().add(0, 1, 0),
                    8, 0.4, 0.6, 0.4, 0.02);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player victim
                && magic().darkStealthActive(victim.getUniqueId())
                && !(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!magic().lightProtActive(player.getUniqueId())) return;
        if (event.getNewEffect() == null) return;
        PotionEffectType type = event.getNewEffect().getType();
        if (type == PotionEffectType.BLINDNESS || type == PotionEffectType.WEAKNESS
                || type == PotionEffectType.DARKNESS) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!magic().isRooted(event.getPlayer().getUniqueId())) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(Keys.noLoot, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        magic().endFlightIfActive(event.getPlayer());
    }
}
