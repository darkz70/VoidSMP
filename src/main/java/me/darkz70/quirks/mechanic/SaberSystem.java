package me.darkz70.quirks.mechanic;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Световые мечи (ивент до конца ноября).
 *
 * Через минуту после захода игроку открывается выбор стороны:
 * светлая (синий меч) / тёмная (красный меч). Меч живёт в инвентаре
 * до конца ноября (таймер в описании), неразрушим, нельзя зачаровать,
 * нельзя выбросить, при смерти не падает — возвращается сам.
 *
 * Характеристики: 7 урона, «облик огня IV» (зачарование, не снимается).
 * ПКМ светлым мечом — предложение перейти на тёмную сторону;
 * отказ — следующее предложение через 24 часа. Тёмная сторона
 * обратно на светлую перейти НЕ может.
 */
public final class SaberSystem implements Listener {

    private record ChoiceHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record SwitchHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final VoidQuirksPlugin plugin;

    public SaberSystem(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ---------- конфиг/время ----------

    private boolean enabled() {
        return plugin.getConfig().getBoolean("saber.enabled", true);
    }

    /** Ивент горит до этой даты включительно-исключительно (по умолчанию 1 декабря). */
    private LocalDate untilDate() {
        try {
            return LocalDate.parse(plugin.getConfig().getString("saber.until-date", "2026-12-01"));
        } catch (Exception ex) {
            return LocalDate.of(2026, 12, 1);
        }
    }

    private boolean expired() {
        return !LocalDate.now().isBefore(untilDate());
    }

    private long declineCooldownMillis() {
        return plugin.getConfig().getLong("saber.decline-cooldown-hours", 24) * 3_600_000L;
    }

    // ---------- сторона игрока ----------

    public String side(Player player) {
        String s = player.getPersistentDataContainer().get(Keys.saberSide, PersistentDataType.STRING);
        return s == null ? "" : s;
    }

    private void setSide(Player player, String side) {
        player.getPersistentDataContainer().set(Keys.saberSide, PersistentDataType.STRING, side);
    }

    private long declinedUntil(Player player) {
        Long v = player.getPersistentDataContainer().get(Keys.saberDecline, PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    // ---------- предмет ----------

    public boolean isSaber(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer()
                .get(Keys.saberItem, PersistentDataType.BOOLEAN));
    }

    /** Собирает меч выбранной стороны (таймер пересчитывается при каждой выдаче). */
    public ItemStack makeSaber(boolean dark) {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        sword.editMeta(meta -> {
            meta.displayName(Msg.color(dark
                    ? "&c&lТёмный световой меч"
                    : "&b&lСветовой меч"));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Msg.color(dark ? "&5Сторона: ТЁМНАЯ (красный клинок)" : "&3Сторона: СВЕТЛАЯ (синий клинок)"));
            lore.add(Msg.color("&77 урона · огненный облик IV · неразрушим"));
            lore.add(Msg.color("&cНельзя зачаровать · нельзя выбросить"));
            lore.add(Msg.color("&6Осталось до конца ноября: &f" + countdownText()));
            lore.add(Msg.color(dark ? "&8Тёмная сторона не отпускает."
                    : "&8ПКМ — тихий зов тёмной стороны…"));
            meta.lore(lore);
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.FIRE_ASPECT, 4, true); // облик огня IV + мерцание зачарования
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    Keys.saberDamage, 6.0, AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND)); // 1 базовый + 6 = ровно 7 урона
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    Keys.saberSpeed, -2.4, AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND));
            meta.getPersistentDataContainer().set(Keys.saberItem, PersistentDataType.BOOLEAN, true);
        });
        return sword;
    }

    private String countdownText() {
        Duration left = Duration.between(LocalDateTime.now(), untilDate().atStartOfDay());
        if (left.isNegative()) return "время вышло";
        long days = left.toDays();
        long hours = left.minusDays(days).toHours();
        return days + " дн. " + hours + " ч.";
    }

    /** Выдаёт меч, если его нет в инвентаре, или обновляет таймер на месте. */
    public void ensureSaber(Player player) {
        if (!enabled() || expired()) return;
        String side = side(player);
        if (side.isEmpty()) return;
        boolean dark = side.equals("dark");
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isSaber(item)) {
                player.getInventory().setItem(i, makeSaber(dark)); // свежий таймер
                return;
            }
        }
        player.getInventory().addItem(makeSaber(dark)).values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    /** По окончании ивента мечи сгорают и стороны обнуляются. */
    private void purgeIfExpired(Player player) {
        if (!expired()) return;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isSaber(player.getInventory().getItem(i))) player.getInventory().setItem(i, null);
        }
        if (!side(player).isEmpty()) {
            setSide(player, "");
            player.sendMessage(Msg.color("&7Световой меч рассыпался прахом — ноябрь кончился."));
        }
    }

    // ---------- меню выбора ----------

    /** Меню: светлая или тёмная сторона (минутный выбор после захода). */
    public void openChoice(Player player) {
        Inventory inv = Bukkit.createInventory(new ChoiceHolder(), 27,
                Msg.color("&8Выбери сторону силы"));
        inv.setItem(11, btn(Material.BLUE_CONCRETE, "&b&lСветлая сторона",
                "&7Выдаётся СИНИЙ световой меч: 7 урона,",
                "&7огненный облик IV, неразрушим.",
                "&7ПКМ мечом — можно выслушать зов",
                "&7тёмной стороны (отказ — раз в 24 часа)."));
        inv.setItem(15, btn(Material.RED_CONCRETE, "&c&lТёмная сторона",
                "&7Выдаётся КРАСНЫЙ световой меч: 7 урона,",
                "&7огненный облик IV, неразрушим.",
                "&4Осторожно: назад дороги нет."));
        player.openInventory(inv);
    }

    /** Меню-вызов: перейти на тёмную сторону? (только со светлой). */
    public void openDarkCall(Player player) {
        Inventory inv = Bukkit.createInventory(new SwitchHolder(), 27,
                Msg.color("&8Зов тёмной стороны"));
        inv.setItem(11, btn(Material.RED_STAINED_GLASS, "&c&lПринять тьму",
                "&7Синий клинок обратится в красный.",
                "&4Обратно на светлую сторону — никогда."));
        inv.setItem(15, btn(Material.LIME_STAINED_GLASS, "&a&lОтказаться",
                "&7Следующий зов только через 24 часа."));
        player.openInventory(inv);
    }

    private static ItemStack btn(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(meta -> {
            meta.displayName(Msg.color(name));
            List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
            for (String line : lore) lines.add(Msg.color(line));
            meta.lore(lines);
        });
        return item;
    }

    // ---------- события ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            purgeIfExpired(player);
            if (!enabled() || expired()) return;
            if (side(player).isEmpty()) {
                openChoice(player); // через минуту после захода — выбор стороны
            } else {
                ensureSaber(player); // выбранному — меч обратно, если пропал
            }
        }, 60L * 20L);
        // и сразу на всякий случай: если меч потерялся между сессиями
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !side(player).isEmpty()) ensureSaber(player);
        }, 25L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) ensureSaber(player);
        }, 20L);
    }

    /** Меч не падает при смерти — он переждёт тебя. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isSaber);
    }

    /** Запрет выброса. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isSaber(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            player_note(event.getPlayer(), "&cМеч нельзя выбросить — он твой до конца ноября.");
        }
    }

    /** Запрет зачарования (стол зачарований). */
    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (isSaber(event.getItem())) {
            event.setCancelled(true);
            player_note(event.getEnchanter(), "&cСветовой меч не принимает чужие чары.");
        }
    }

    /** Запрет наковальни (не докрутить чарами/именем). */
    @EventHandler(ignoreCancelled = true)
    public void onAnvil(PrepareAnvilEvent event) {
        if (isSaber(event.getInventory().getFirstItem())
                || isSaber(event.getInventory().getSecondItem())) {
            event.setResult(null);
        }
    }

    /** ПКМ мечом: светлая сторона слышит зов тьмы; тёмная молчит навечно. */
    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (!isSaber(event.getItem())) return;
        Player player = event.getPlayer();
        event.setCancelled(true);
        String side = side(player);
        if (side.equals("dark")) {
            player_note(player, "&4Тёмная сторона не отпускает. Обратного пути нет.");
            return;
        }
        if (!side.equals("light")) return;
        long until = declinedUntil(player);
        if (until > System.currentTimeMillis()) {
            long hoursLeft = (until - System.currentTimeMillis() + 3_599_999L) / 3_600_000L;
            player_note(player, "&7Зов тёмной стороны умолк. Следующий — через &f" + hoursLeft + " ч.");
            return;
        }
        openDarkCall(player);
    }

    /** Клики по меню выбора/зова. */
    @EventHandler(ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        boolean choice = holder instanceof ChoiceHolder;
        boolean sw = holder instanceof SwitchHolder;
        if (!choice && !sw) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (choice) {
            if (slot == 11) { // светлая
                setSide(player, "light");
                ensureSaber(player);
                player.closeInventory();
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
                player.sendMessage(Msg.color("&bСветлая сторона приняла тебя. Синий клинок в инвентаре."));
            } else if (slot == 15) { // тёмная
                setSide(player, "dark");
                ensureSaber(player);
                player.closeInventory();
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.4f);
                player.sendMessage(Msg.color("&cТёмная сторона приняла тебя. Красный клинок в инвентаре."));
            }
            return;
        }

        // зов тьмы
        if (slot == 11) { // принять тьму
            setSide(player, "dark");
            player.getPersistentDataContainer().remove(Keys.saberDecline);
            player.closeInventory();
            // заменить меч на месте
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                if (isSaber(player.getInventory().getItem(i))) {
                    player.getInventory().setItem(i, makeSaber(true));
                }
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.2f);
            player.sendMessage(Msg.color("&5Тьма обняла твой клинок. Теперь он красный — и навсегда."));
        } else if (slot == 15) { // отказ — 24 часа тишины
            player.getPersistentDataContainer().set(Keys.saberDecline, PersistentDataType.LONG,
                    System.currentTimeMillis() + declineCooldownMillis());
            player.closeInventory();
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.0f);
            player.sendMessage(Msg.color("&7Ты отмахнулся от тьмы. Зов вернётся через 24 часа."));
        }
    }

    private static void player_note(Player player, String text) {
        player.sendActionBar(Msg.color(text));
    }
}
