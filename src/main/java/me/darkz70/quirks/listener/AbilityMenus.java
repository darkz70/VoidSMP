package me.darkz70.quirks.listener;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/**
 * Способности причуд со 2 уровня: менюшки по жестам (шифт+ЛКМ, шифт+ПКМ, шифт+F, шифт с топором)
 * и логика их применения: магазин древа, защиты от смерти, паучий укус, гидро-трюки, страхи Бедрока.
 */
public final class AbilityMenus implements Listener {

    private final VoidQuirksPlugin plugin;

    public AbilityMenus(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private record Holder(String page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 27, Component.empty());
        }
    }

    // ---------- общее состояние выборов ----------

    private static final Map<UUID, String> FARMER_PICK = new HashMap<>();   // speed|night
    private static final Map<UUID, String> SPIDER_PICK = new HashMap<>();   // blindturn|weak|hit5 (на 1 удар)
    private static final Map<UUID, String> AXE_PICK = new HashMap<>();      // stance|blink
    private static final Map<UUID, Set<EntityType>> BEDROCK_FEAR = new HashMap<>(); // 2 моба

    public static String farmerPassive(UUID id) {
        return FARMER_PICK.getOrDefault(id, "");
    }

    public static Set<EntityType> fearedMobs(UUID id) {
        return BEDROCK_FEAR.getOrDefault(id, Set.of());
    }

    private int lvl(Player player, Quirk quirk) {
        return plugin.quirks().levelOf(player, quirk);
    }

    private static ItemStack hand(Player player) {
        return player.getInventory().getItemInMainHand();
    }

    private static boolean isTool(ItemStack item, String suffix) {
        return item != null && item.getType().name().endsWith(suffix);
    }

    private static void deny(Player player, String text) {
        player.sendMessage(Msg.color("&8≈ &7" + text));
    }

    private static boolean cd(Player player, String key, long ms) {
        boolean ok = ScanUtil.tryUse(player.getUniqueId(), key, ms);
        if (!ok) deny(player, "Перезарежджается…");
        return ok;
    }

    // ---------- жесты ----------

    /** Инженер: шифт+ЛКМ с луком; Фермер: шифт+ЛКМ с мотыгой; Паук: шифт+ПКМ с мечом. */
    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!(event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK
                || event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        ItemStack item = hand(player);
        boolean left = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;

        if (left && item != null && item.getType() == Material.BOW && lvl(player, Quirk.ENGINEER) >= 2) {
            event.setCancelled(true);
            openEngineer(player);
            return;
        }
        if (left && isTool(item, "_HOE") && lvl(player, Quirk.FARMER) >= 2) {
            event.setCancelled(true);
            openFarmer(player);
            return;
        }
        if (!left && isTool(item, "_SWORD") && lvl(player, Quirk.SPIDER) >= 2) {
            event.setCancelled(true);
            openSpider(player);
        }
    }

    /** Топор: сел на шифт с топором в руке. */
    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (lvl(player, Quirk.AXE) < 2) return;
        ItemStack item = hand(player);
        if (item == null || !isTool(item, "_AXE")) return;
        if (!ScanUtil.tryUse(player.getUniqueId(), "axe-open", 3000)) return;
        openAxe(player);
    }

    /** Земноводный: шифт+F с трезубцем; Бедрок: шифт+F. */
    @EventHandler(ignoreCancelled = false)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        ItemStack item = hand(player);
        if (item != null && item.getType() == Material.TRIDENT && lvl(player, Quirk.AMPHIBIAN) >= 2) {
            event.setCancelled(true);
            openAmphibian(player);
            return;
        }
        if (lvl(player, Quirk.BEDROCK) >= 2) {
            event.setCancelled(true);
            openBedrock(player);
        }
    }

    // ---------- меню ----------

    private void openEngineer(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("eng"), 27, Msg.color("&8Инженер: зачехли стак стрел"));
        inv.setItem(11, btn(Material.TIPPED_ARROW, "&c16 стрел моментального урона I",
                "&7Цена: &f64 стрелы&7. Клик — обменять."));
        inv.setItem(15, btn(Material.SUGAR, "&bСкорость на 6 секунд",
                "&7Цена: &f64 стрелы&7. Клик — обменять."));
        inv.setItem(22, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    private void openFarmer(Player player) {
        String pick = farmerPassive(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(new Holder("farmer"), 27,
                Msg.color("&2Фермер: пассивка (выбери одну)"));
        inv.setItem(12, btn(pick.equals("speed") ? Material.LANTERN : Material.FEATHER,
                "&bСкорость" + (pick.equals("speed") ? " &a(выбрано)" : ""), "&7Лёгкая походка всегда."));
        inv.setItem(14, btn(pick.equals("night") ? Material.LANTERN : Material.ENDER_EYE,
                "&dНочное зрение" + (pick.equals("night") ? " &a(выбрано)" : ""), "&7Видно в темноте всегда."));
        inv.setItem(22, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    private static final Material[] LOGS = {
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
        Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG};

    private static Material nextLog(Material type) {
        for (int i = 0; i < LOGS.length; i++) {
            if (LOGS[i] == type) return LOGS[(i + 1) % LOGS.length];
        }
        return Material.OAK_LOG;
    }

    private void openAxe(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("axe"), 27, Msg.color("&6Топор: лесной сплав"));
        int slot = 0;
        for (Material log : LOGS) {
            Material next = nextLog(log);
            inv.setItem(slot, btn(log, "&f" + prettyLog(log),
                    "&7ЛКМ: 16 → 16 &f" + prettyLog(next),
                    "&7ПКМ: 64 → 64 &f" + prettyLog(next),
                    "&8Меню после обмена не закрывается."));
            slot++;
        }
        inv.setItem(22, btn(Material.ARROW, "&7Закрыть"));
        inv.setItem(25, btn(Material.TOTEM_OF_UNDYING, "&cЗащита от смерти (КД 1 ч) →"));
        player.openInventory(inv);
    }

    private void openAxeProtect(Player player) {
        String pick = AXE_PICK.getOrDefault(player.getUniqueId(), "");
        Inventory inv = Bukkit.createInventory(new Holder("axe_def"), 27,
                Msg.color("&6Топор: защита от смерти (выбери ОДНУ)"));
        inv.setItem(12, btn(pick.equals("stance") ? Material.LANTERN : Material.SHIELD,
                "&aСтойкая оборона" + (pick.equals("stance") ? " &a(выбрана)" : ""),
                "&7HP < 5: Сопротивление на 4 сек,",
                "&7но ноги вросли в землю (не двинуться)."));
        inv.setItem(14, btn(pick.equals("blink") ? Material.LANTERN : Material.ENDER_PEARL,
                "&dПрыжок на спот" + (pick.equals("blink") ? " &a(выбрана)" : ""),
                "&7HP < 5: рандомный телепорт в радиусе 80",
                "&7(не в воду и не в блок), Реген II 5 сек."));
        inv.setItem(22, btn(Material.ARROW, "&7← к магазину"));
        player.openInventory(inv);
    }

    private void openSpider(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("spider"), 27,
                Msg.color("&fПаук: эффект на один удар (КД 30 мин)"));
        inv.setItem(11, btn(Material.SPLASH_POTION, "&8Укус тьмы",
                "&7Ослепление 5 сек + разворот на 90°"));
        inv.setItem(13, btn(Material.MILK_BUCKET, "&7Слабость",
                "&7Слабость II на 3 сек"));
        inv.setItem(15, btn(Material.DIAMOND_AXE, "&cОткус",
                "&7Плюс 5 урона сверху гарантированно"));
        inv.setItem(22, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    private void openAmphibian(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("amph"), 27,
                Msg.color("&3Земноводный: приём тризубца (КД 10 мин)"));
        inv.setItem(11, btn(Material.LIGHTNING_ROD, "&eГром близкого",
                "&7Молния в ближайшего (до 30) + замедление 5 сек."));
        inv.setItem(13, btn(Material.SOUL_LANTERN, "&bИзгиб волны",
                "&7Разворот на 120° + ослепление 8 сек,",
                "&7минус 2 здоровья гарантированно."));
        inv.setItem(15, btn(Material.GLASS, "&fТень воды",
                "&7Невидимость 20 сек + телепорт за спину",
                "&7ближайшему игроку (до 30 блоков)."));
        inv.setItem(22, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    private void openBedrock(Player player) {
        Set<EntityType> picks = BEDROCK_FEAR.computeIfAbsent(player.getUniqueId(), k -> EnumSet.noneOf(EntityType.class));
        Inventory inv = Bukkit.createInventory(new Holder("bedrock"), 27,
                Msg.color("&8Бедрок: боятся меня (выбери ДВУХ; КД смены 10 мин)"));
        Object[][] mobs = {
            {Material.SKELETON_SKULL, EntityType.SKELETON, "Скелеты"},
            {Material.ZOMBIE_HEAD, EntityType.ZOMBIE, "Зомби"},
            {Material.COBWEB, EntityType.SPIDER, "Пауки"},
            {Material.PHANTOM_MEMBRANE, EntityType.PHANTOM, "Фантомы"},
            {Material.CREEPER_HEAD, EntityType.CREEPER, "Криперы"},
            {Material.BLAZE_ROD, EntityType.BLAZE, "Ифриты"},
            {Material.CROSSBOW, EntityType.PILLAGER, "Разбойники"}};
        int slot = 10;
        for (Object[] mob : mobs) {
            boolean on = picks.contains(mob[1]);
            inv.setItem(slot++, btn(on ? Material.LANTERN : (Material) mob[0],
                    (on ? "&a" : "&8") + mob[2],
                    on ? "&7Нажми — убрать из пары" : "&7Нажми — добавить в пару"));
        }
        inv.setItem(22, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    // ---------- клики по меню ----------

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        switch (holder.page()) {
            case "eng" -> {
                if (slot == 11) engPay(player, false);
                else if (slot == 15) engPay(player, true);
                else if (slot == 22) player.closeInventory();
            }
            case "farmer" -> {
                if (slot == 12) { FARMER_PICK.put(player.getUniqueId(), "speed"); msg(player, "Пассивка: Скорость."); }
                else if (slot == 14) { FARMER_PICK.put(player.getUniqueId(), "night"); msg(player, "Пассивка: Ночное зрение."); }
                else if (slot == 22) player.closeInventory();
                else return;
                if (slot == 12 || slot == 14) openFarmer(player);
            }
            case "axe" -> {
                if (slot == 25) { openAxeProtect(player); return; }
                if (slot == 22) { player.closeInventory(); return; }
                if (slot < 0 || slot >= LOGS.length) return;
                swapLogs(player, LOGS[slot], event.isRightClick() ? 64 : 16);
                openAxe(player); // меню не пропадает при обмене
            }
            case "axe_def" -> {
                if (slot == 12) { AXE_PICK.put(player.getUniqueId(), "stance"); msg(player, "Защита: Стойкая оборона."); }
                else if (slot == 14) { AXE_PICK.put(player.getUniqueId(), "blink"); msg(player, "Защита: Прыжок на спот."); }
                else if (slot == 22) { openAxe(player); return; }
                else return;
                openAxeProtect(player);
            }
            case "spider" -> {
                if (slot == 11) SPIDER_PICK.put(player.getUniqueId(), "blindturn");
                else if (slot == 13) SPIDER_PICK.put(player.getUniqueId(), "weak");
                else if (slot == 15) SPIDER_PICK.put(player.getUniqueId(), "hit5");
                else if (slot == 22) { player.closeInventory(); return; }
                else return;
                player.closeInventory();
                msg(player, "Вид удара выбран — сработает один раз в ближайшие 30 минут.");
            }
            case "amph" -> {
                if (slot == 11) amphibAct(player, 1);
                else if (slot == 13) amphibAct(player, 2);
                else if (slot == 15) amphibAct(player, 3);
                else if (slot == 22) player.closeInventory();
            }
            case "bedrock" -> {
                if (slot == 22) { player.closeInventory(); return; }
                if (slot < 10 || slot > 16) return;
                Object[][] mobs = {
                    {null, EntityType.SKELETON, "Скелеты"}, {null, EntityType.ZOMBIE, "Зомби"},
                    {null, EntityType.SPIDER, "Пауки"}, {null, EntityType.PHANTOM, "Фантомы"},
                    {null, EntityType.CREEPER, "Криперы"}, {null, EntityType.BLAZE, "Ифриты"},
                    {null, EntityType.PILLAGER, "Разбойники"}};
                EntityType type = (EntityType) mobs[slot - 10][1];
                Set<EntityType> picks = BEDROCK_FEAR.computeIfAbsent(player.getUniqueId(),
                        k -> EnumSet.noneOf(EntityType.class));
                if (picks.contains(type)) {
                    if (cd(player, "bedrock-fear", 10 * 60_000L)) {
                        picks.remove(type);
                        msg(player, "Пара пересобрана: без " + mobs[slot - 10][2] + ".");
                    }
                } else if (picks.size() >= 2) {
                    deny(player, "Уже двое — сначала сними одного (КД 10 мин).");
                } else if (picks.size() == 1 ? cd(player, "bedrock-fear", 10 * 60_000L) : true) {
                    picks.add(type);
                    msg(player, picks.size() == 2 ? "Пара готова: они тебя боятся." : "Один из двух выбран.");
                }
                openBedrock(player);
            }
            default -> { }
        }
    }

    // ---------- Инженер: обмен стрел ----------

    private void engPay(Player player, boolean speed) {
        if (lvl(player, Quirk.ENGINEER) < 2) return;
        if (!take(player, Material.ARROW, 64)) {
            deny(player, "Нужен целый стак обычных стрел (64).");
            return;
        }
        if (speed) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 0, true, false, true));
            msg(player, "Скорость на 6 секунд. (-64 стрелы)");
        } else {
            ItemStack arrows = new ItemStack(Material.TIPPED_ARROW, 16);
            if (arrows.getItemMeta() instanceof PotionMeta meta) {
                meta.setBasePotionType(PotionType.HARMING);
                arrows.setItemMeta(meta);
            }
            var leftover = player.getInventory().addItem(arrows);
            leftover.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
            msg(player, "16 стрел моментального урона I. (-64 стрелы)");
        }
        player.closeInventory();
    }

    // ---------- Топор: магазин древа ----------

    private void swapLogs(Player player, Material from, int batch) {
        if (lvl(player, Quirk.AXE) < 2) return;
        Material to = nextLog(from);
        int taken = removeFrom(player, from, Math.min(batch, countIn(player, from)));
        if (taken <= 0) {
            deny(player, "Нет таких брёвен в рюкзаке.");
            return;
        }
        // отдаём столько же; доли стака не допускаем (кратно партии)
        int give = (taken / batch) * batch;
        if (give <= 0) give = taken;
        if (give < taken) player.getInventory().addItem(new ItemStack(from, taken - give));
        var leftover = player.getInventory().addItem(new ItemStack(to, give));
        leftover.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
        msg(player, "&f" + give + " " + prettyLog(from) + " &7→ &f" + give + " " + prettyLog(to));
    }

    private static int countIn(Player player, Material type) {
        int n = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && it.getType() == type) n += it.getAmount();
        }
        return n;
    }

    private static int removeFrom(Player player, Material type, int amount) {
        int need = amount;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContents().length && need > 0; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() != type) continue;
            int takeNow = Math.min(it.getAmount(), need);
            it.setAmount(it.getAmount() - takeNow);
            need -= takeNow;
        }
        return amount - need;
    }

    private static boolean take(Player player, Material type, int amount) {
        if (countIn(player, type) < amount) return false;
        removeFrom(player, type, amount);
        return true;
    }

    private static String prettyLog(Material log) {
        String name = log.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return name.charAt(0) + name.substring(1);
    }

    // ---------- Паук: один обещанный удар + Топор: защиты ----------

    @EventHandler(ignoreCancelled = true)
    public void onEthAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof Player victim)) return;
        if (lvl(attacker, Quirk.SPIDER) < 2) return;
        String pick = SPIDER_PICK.get(attacker.getUniqueId());
        if (pick == null) return;
        if (!ScanUtil.tryUse(attacker.getUniqueId(), "spider-strike", 30 * 60_000L)) return;
        SPIDER_PICK.remove(attacker.getUniqueId());
        switch (pick) {
            case "blindturn" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, true, false, true));
                var loc = victim.getLocation().clone();
                loc.setYaw(loc.getYaw() + 90);
                victim.teleport(loc);
                msg(attacker, "&fУкус тьмы сработал.");
            }
            case "weak" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1, true, false, true));
                msg(attacker, "&fСлабость II вошла.");
            }
            case "hit5" -> {
                event.setDamage(event.getDamage() + 5.0); // плюс 5 сверху
                msg(attacker, "&cОткус: +5 урона гарантированно.");
            }
            default -> { }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.LOWEST)
    public void onAxeNearDeath(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (lvl(player, Quirk.AXE) < 2) return;
        String pick = AXE_PICK.get(player.getUniqueId());
        if (pick == null) return;
        double after = player.getHealth() - event.getFinalDamage();
        if (after > 5.0) return;
        if (!ScanUtil.tryUse(player.getUniqueId(), "axe-death", 60 * 60_000L)) return;
        if (pick.equals("stance")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 4, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 6, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 80, 128, true, false, true));
            msg(player, "&aСтойкая оборона: 4 секунды ты непробиваем, но ноги вросли.");
        } else {
            org.bukkit.Location safe = randomSafe(player.getLocation(), 80);
            if (safe != null) {
                player.teleport(safe);
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false, true));
                msg(player, "&dПрыжок на спот!");
            }
        }
    }

    /** Случайная безопасная точка в радиусе: не вода, не внутри блока. */
    private static org.bukkit.Location randomSafe(org.bukkit.Location center, int radius) {
        var world = center.getWorld();
        if (world == null) return null;
        for (int i = 0; i < 14; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double dist = ThreadLocalRandom.current().nextDouble(12, radius);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = world.getHighestBlockYAt(x, z);
            Block ground = world.getBlockAt(x, y, z);
            if (!ground.getType().isSolid()) continue;
            if (ground.getType() == Material.WATER || ground.getType() == Material.LAVA) continue;
            Block above1 = world.getBlockAt(x, y + 1, z);
            Block above2 = world.getBlockAt(x, y + 2, z);
            if (above1.getType().isSolid() || above2.getType().isSolid()) continue;
            if (above1.getType() == Material.WATER || above2.getType() == Material.WATER) continue;
            return new org.bukkit.Location(world, x + 0.5, y + 1.05, z + 0.5);
        }
        return null;
    }

    // ---------- Земноводный: приёмы тризубца ----------

    private void amphibAct(Player player, int which) {
        if (lvl(player, Quirk.AMPHIBIAN) < 2) return;
        if (!cd(player, "amph-act", 10 * 60_000L)) return;
        Player target = nearest(player, 30);
        switch (which) {
            case 1 -> {
                if (target == null) { deny(player, "Рядом никого нет (до 30 блоков)."); return; }
                target.getWorld().strikeLightningEffect(target.getLocation());
                target.damage(2.0, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, true, false, true));
                msg(player, "&eГром по " + target.getName() + "!");
            }
            case 2 -> {
                if (target == null) { deny(player, "Рядом никого нет (до 30 блоков)."); return; }
                var loc = target.getLocation().clone();
                loc.setYaw(loc.getYaw() + 120);
                target.teleport(loc);
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 160, 0, true, false, true));
                target.damage(2.0, player);
                msg(player, "&bИзлом волны по " + target.getName() + "!");
            }
            case 3 -> {
                if (target == null) { deny(player, "Рядом никого нет (до 30 блоков)."); return; }
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 400, 0, true, false, true));
                var loc = target.getLocation();
                double yawRad = Math.toRadians(loc.getYaw());
                org.bukkit.Location behind = loc.clone().add(-Math.sin(yawRad) * -1.6, 0, Math.cos(yawRad) * -1.6);
                behind.setYaw(loc.getYaw());
                behind.setPitch(0);
                player.teleport(behind);
                msg(player, "&fТы тень у него за спиной.");
            }
            default -> { }
        }
        player.closeInventory();
    }

    private static Player nearest(Player player, double radius) {
        Player best = null;
        double best2 = radius * radius;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == player || other.getWorld() != player.getWorld()) continue;
            double d2 = other.getLocation().distanceSquared(player.getLocation());
            if (d2 < best2) { best2 = d2; best = other; }
        }
        return best;
    }

    // ---------- сервис ----------

    private static void msg(Player player, String text) {
        player.sendMessage(Msg.color("&6≈ " + text));
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

}
