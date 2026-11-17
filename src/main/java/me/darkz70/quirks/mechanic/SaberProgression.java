package me.darkz70.quirks.mechanic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Дерево прокачки светового меча (ивент «Недрочабрь»).
 *
 * Открывается клавишей Q, когда световой меч в руке (вместо обычного выброса).
 * Шифт+Q на мече с «Путём Стороны» ур.1+ открывает отдельное меню способностей.
 *
 * По центру — разовый узел «Чувствительность силы» (64 конфеты): луч молнии
 * раз в 3 выстрела вместо 5 + Острота I. Дальше 4 ветки по 4 уровня, качать
 * можно одновременно только 2 (первая трата конфет в ветке её «запирает»;
 * когда ветка пройдена до 4 уровня — освобождается слот под новую).
 *
 * Валюта — «Конфетка» ({@link me.darkz70.quirks.listener.CraftListener#makeCandy()}),
 * определяется по PDC-метке {@link Keys#brewMark} = "candy".
 */
public final class SaberProgression implements Listener {

    /** Четыре ветки дерева. Путь Стороны переименовывается/перекрашивается по стороне игрока. */
    public enum Path {
        DEFENDER("&2Путь Защитника", new int[]{128, 256, 320, 500}),
        POWER("&5Путь Силы", new int[]{128, 192, 256, 500}),
        DUELIST("&6Путь Дуэлянта", new int[]{128, 256, 320, 500}),
        SIDE("&dПуть Стороны", new int[]{128, 192, 256, 500});

        final String baseDisplay;
        final int[] cost;

        Path(String baseDisplay, int[] cost) {
            this.baseDisplay = baseDisplay;
            this.cost = cost;
        }

        int costFor(int level) { // level 1..4
            return cost[Math.max(0, Math.min(3, level - 1))];
        }
    }

    private static final int FORCE_NODE_COST = 64;

    private record MainHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record PathHolder(Path path) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record AbilityHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final VoidQuirksPlugin plugin;
    private final SaberSystem sabers;

    /** Оставшиеся «заряды» способности «Натиск»/«Прилив»: следующие N ударов станят/ослепляют цель. */
    private final Map<UUID, Integer> pendingStunHits = new HashMap<>();
    private final Map<UUID, Integer> pendingDebuffHits = new HashMap<>();
    /** Щит (ур.3 Пути Стороны): до какого момента (мс) отражает снаряды/луч. */
    private final Map<UUID, Long> shieldUntil = new HashMap<>();
    /** Станятся до какого момента (мс) — станящиеся не могут бить мечом/лучом. */
    private final Map<UUID, Long> stunnedUntil = new HashMap<>();

    public SaberProgression(VoidQuirksPlugin plugin, SaberSystem sabers) {
        this.plugin = plugin;
        this.sabers = sabers;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        sabers.setProgressionHook(new SaberSystem.ProgressionHook() {
            @Override public void enhanceSaber(Player player, ItemStack saber) { applyEnchants(player, saber); }
            @Override public int lightningEveryNShots(Player player) { return lightningThreshold(player); }
            @Override public double beamDamageBonus(Player player) { return beamBonus(player); }
            @Override public boolean reflectBeamIfShielded(Player shooter, LivingEntity target, double damage) {
                return reflectBeamIfShielded0(shooter, target, damage);
            }
        });
        // пассивки веток (сопротивление Защитника) — раз в 3 сек, пока игрок онлайн и выбрал сторону
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPassives, 40L, 60L);
    }

    // ---------- хранение прогресса ----------

    public boolean hasForceNode(Player p) {
        Byte v = p.getPersistentDataContainer().get(Keys.saberForceNode, PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    private void setForceNode(Player p, boolean v) {
        p.getPersistentDataContainer().set(Keys.saberForceNode, PersistentDataType.BYTE, (byte) (v ? 1 : 0));
    }

    public int levelOf(Player p, Path path) {
        Integer v = p.getPersistentDataContainer().get(keyOf(path), PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    private void setLevel(Player p, Path path, int level) {
        p.getPersistentDataContainer().set(keyOf(path), PersistentDataType.INTEGER, level);
    }

    private org.bukkit.NamespacedKey keyOf(Path path) {
        return switch (path) {
            case DEFENDER -> Keys.saberPathDefender;
            case POWER -> Keys.saberPathPower;
            case DUELIST -> Keys.saberPathDuelist;
            case SIDE -> Keys.saberPathSide;
        };
    }

    /** Сброс всего дерева (используется админкой /ivent reset). */
    public void reset(Player p) {
        setForceNode(p, false);
        for (Path path : Path.values()) setLevel(p, path, 0);
        p.getPersistentDataContainer().remove(Keys.saberForceNode);
        p.getPersistentDataContainer().remove(Keys.saberPathDefender);
        p.getPersistentDataContainer().remove(Keys.saberPathPower);
        p.getPersistentDataContainer().remove(Keys.saberPathDuelist);
        p.getPersistentDataContainer().remove(Keys.saberPathSide);
    }

    /** Можно ли начать КАЧАТЬ (с нуля) эту ветку — не больше 2 незавершённых одновременно. */
    private boolean canStartNewPath(Player p, Path path) {
        if (levelOf(p, path) > 0) return true; // уже качается/завершена — не "новая"
        long active = Arrays.stream(Path.values())
                .filter(pp -> { int lvl = levelOf(p, pp); return lvl > 0 && lvl < 4; })
                .count();
        return active < 2;
    }

    // ---------- конфеты ----------

    private boolean isCandy(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return "candy".equals(item.getItemMeta().getPersistentDataContainer()
                .get(Keys.brewMark, PersistentDataType.STRING));
    }

    public int countCandy(Player p) {
        int total = 0;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (isCandy(item)) total += item.getAmount();
        }
        return total;
    }

    private boolean spendCandy(Player p, int amount) {
        if (countCandy(p) < amount) return false;
        int remaining = amount;
        for (int i = 0; i < p.getInventory().getSize() && remaining > 0; i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (!isCandy(item)) continue;
            int take = Math.min(item.getAmount(), remaining);
            remaining -= take;
            if (take >= item.getAmount()) {
                p.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - take);
            }
        }
        return true;
    }

    // ---------- зачарования/эффекты меча по прокачке ----------

    private int sharpnessForDuelist(int lvl) {
        return switch (lvl) { case 1 -> 2; case 2 -> 4; case 3 -> 5; case 4 -> 6; default -> 0; };
    }

    private int baneForDefender(int lvl) {
        return switch (lvl) { case 1 -> 1; case 2 -> 3; case 3 -> 5; case 4 -> 6; default -> 0; };
    }

    private void applyEnchants(Player owner, ItemStack saber) {
        int defender = levelOf(owner, Path.DEFENDER);
        int power = levelOf(owner, Path.POWER);
        int duelist = levelOf(owner, Path.DUELIST);

        int sharpness = Math.max(hasForceNode(owner) ? 1 : 0, sharpnessForDuelist(duelist));
        int baneSmite = baneForDefender(defender);
        int fireAspect = power >= 4 ? 7 : power >= 2 ? 5 : 4;

        saber.editMeta(meta -> {
            if (sharpness > 0) meta.addEnchant(Enchantment.SHARPNESS, sharpness, true);
            if (baneSmite > 0) {
                meta.addEnchant(Enchantment.BANE_OF_ARTHROPODS, baneSmite, true);
                meta.addEnchant(Enchantment.SMITE, baneSmite, true);
            }
            meta.addEnchant(Enchantment.FIRE_ASPECT, fireAspect, true);
        });
    }

    private int lightningThreshold(Player p) {
        int threshold = 5;
        if (hasForceNode(p)) threshold = Math.min(threshold, 3);
        int power = levelOf(p, Path.POWER);
        if (power >= 1) threshold = Math.min(threshold, 2);
        if (power >= 4) threshold = 1;
        return threshold;
    }

    private double beamBonus(Player p) {
        return levelOf(p, Path.POWER) >= 3 ? 3.0 : 0.0;
    }

    /** +% к шансу дропа конфетки с моба (вызывается из CraftListener при убийстве). */
    public double candyDropBonus(Player killer) {
        if (killer == null) return 0.0;
        int defender = levelOf(killer, Path.DEFENDER);
        int power = levelOf(killer, Path.POWER);
        int duelist = levelOf(killer, Path.DUELIST);
        double bonus = 0.0;
        bonus += defender >= 4 ? 0.20 : defender >= 2 ? 0.10 : 0.0;
        bonus += power >= 2 ? 0.05 : 0.0;
        bonus += duelist >= 4 ? 0.10 : duelist >= 2 ? 0.05 : 0.0;
        return bonus;
    }

    private void refreshSaber(Player p) {
        sabers.ensureSaber(p);
    }

    private void tickPassives() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sabers.side(p).isEmpty()) continue;
            int defender = levelOf(p, Path.DEFENDER);
            if (defender >= 2) {
                int amp = defender >= 4 ? 1 : 0;
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 140, amp, true, false, false));
            }
        }
    }

    // ---------- цвета частиц по стороне ----------

    private Particle.DustOptions[] sideColors(Player p) {
        boolean dark = sabers.side(p).equals("dark");
        return dark
                ? new Particle.DustOptions[]{
                        new Particle.DustOptions(Color.fromRGB(220, 20, 20), 1.1f),
                        new Particle.DustOptions(Color.fromRGB(10, 10, 10), 1.1f)}
                : new Particle.DustOptions[]{
                        new Particle.DustOptions(Color.fromRGB(40, 110, 255), 1.1f),
                        new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.1f)};
    }

    private void burst(Player p, Location at, int count) {
        Particle.DustOptions[] colors = sideColors(p);
        for (int i = 0; i < count; i++) {
            Particle.DustOptions c = colors[i % 2];
            at.getWorld().spawnParticle(Particle.DUST,
                    at.clone().add((Math.random() - 0.5) * 1.6, Math.random() * 1.8, (Math.random() - 0.5) * 1.6),
                    1, 0, 0, 0, 0, c);
        }
    }

    private String sideWord(Player p, String darkWord, String lightWord) {
        return sabers.side(p).equals("dark") ? darkWord : lightWord;
    }

    // ---------- Q / Shift+Q ----------

    /**
     * Перехватывает выброс (Q) светового меча — ВМЕСТО блокировки с сообщением теперь
     * открывает меню прокачки (или меню способностей на шифт+Q). Должен сработать РАНЬШЕ
     * {@link SaberSystem#onDrop}, поэтому приоритет LOW (тот слушает ignoreCancelled=true
     * на NORMAL и просто не сработает, раз событие тут уже отменено).
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDropOpensMenu(PlayerDropItemEvent event) {
        if (!sabers.isSaber(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            openAbilities(player);
        } else {
            openMain(player);
        }
    }

    // ---------- главное меню ----------

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(new MainHolder(), 27, Msg.color("&5&lПрокачка светового меча"));

        boolean force = hasForceNode(player);
        inv.setItem(13, button(force ? Material.NETHER_STAR : Material.GLOWSTONE_DUST,
                "&e&lЧувствительность силы",
                force ? List.of(Msg.color("&a✔ Куплено"),
                                Msg.color("&7Молния каждый 3-й выстрел луча, Острота I."))
                        : List.of(Msg.color("&7Молния каждый 3-й выстрел луча (вместо 5-го),"),
                                Msg.color("&7а также Острота I."),
                                Msg.color("&6Стоимость: &f" + FORCE_NODE_COST + " конфет"),
                                Msg.color("&eКлик — купить"))));

        inv.setItem(10, pathIcon(player, Path.DEFENDER, Material.SHIELD));
        inv.setItem(12, pathIcon(player, Path.POWER, Material.BLAZE_ROD));
        inv.setItem(14, pathIcon(player, Path.DUELIST, Material.IRON_SWORD));
        inv.setItem(16, pathIcon(player, Path.SIDE, Material.BEACON));

        inv.setItem(4, button(Material.SUGAR, "&dУ тебя конфет: &f" + countCandy(player),
                List.of(Msg.color("&7Конфетки — валюта прокачки."),
                        Msg.color("&7Редкий дроп с убитых мобов."))));

        player.openInventory(inv);
    }

    private String pathDisplay(Player player, Path path) {
        if (path == Path.SIDE) {
            return sideWord(player, "&4Путь Ситха", "&bПуть Джедая");
        }
        return path.baseDisplay;
    }

    private ItemStack pathIcon(Player player, Path path, Material icon) {
        int level = levelOf(player, path);
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.color("&7Уровень: &f" + level + "/4"));
        if (level >= 4) {
            lore.add(Msg.color("&a✔ Ветка пройдена"));
        } else if (level == 0 && !canStartNewPath(player, path)) {
            lore.add(Msg.color("&cЗАБЛОКИРОВАНО &7— уже качаются 2 ветки."));
            lore.add(Msg.color("&7Заверши одну из них, чтобы открыть эту."));
        } else {
            lore.add(Msg.color("&6Цена след. уровня: &f" + path.costFor(level + 1) + " конфет"));
            lore.add(Msg.color("&eКлик — открыть ветку"));
        }
        return button(icon, (level >= 4 ? "&a" : "&f") + pathDisplay(player, path), lore);
    }

    // ---------- подменю ветки ----------

    public void openPath(Player player, Path path) {
        Inventory inv = Bukkit.createInventory(new PathHolder(path), 27,
                Msg.color(pathDisplay(player, path)));
        int level = levelOf(player, path);
        int[] slots = {10, 12, 14, 16};
        for (int i = 0; i < 4; i++) {
            int lvl = i + 1;
            inv.setItem(slots[i], levelButton(player, path, lvl, level));
        }
        inv.setItem(22, button(Material.ARROW, "&7« Назад", List.of()));
        player.openInventory(inv);
    }

    private ItemStack levelButton(Player player, Path path, int lvl, int currentLevel) {
        List<Component> lore = new ArrayList<>();
        for (String line : levelDescription(player, path, lvl)) lore.add(Msg.color(line));
        lore.add(Msg.color("&6Стоимость: &f" + path.costFor(lvl) + " конфет"));

        Material icon;
        String name;
        if (currentLevel >= lvl) {
            icon = Material.LIME_DYE;
            name = "&a✔ Уровень " + lvl + " — куплено";
        } else if (currentLevel == lvl - 1) {
            boolean canBuy = currentLevel > 0 || canStartNewPath(player, path);
            icon = canBuy ? Material.YELLOW_DYE : Material.BARRIER;
            name = canBuy ? "&eУровень " + lvl + " — купить" : "&cЗаблокировано (2 ветки максимум)";
            if (canBuy) lore.add(Msg.color("&eКлик — купить"));
        } else {
            icon = Material.GRAY_DYE;
            name = "&7Уровень " + lvl + " — сначала предыдущий";
        }
        return button(icon, name, lore);
    }

    private List<String> levelDescription(Player player, Path path, int lvl) {
        return switch (path) {
            case DEFENDER -> switch (lvl) {
                case 1 -> List.of("&7Бич Членистоногих I, Небесная кара I");
                case 2 -> List.of("&7Бич Членистоногих III, Небесная кара III,", "&7шанс дропа конфет +10%, Сопротивление I");
                case 3 -> List.of("&7Бич Членистоногих V, Небесная кара V");
                default -> List.of("&7Бич Членистоногих VI, Небесная кара VI,", "&7шанс дропа конфет +20%, Сопротивление II");
            };
            case POWER -> switch (lvl) {
                case 1 -> List.of("&7Молния каждые 2 выстрела луча");
                case 2 -> List.of("&7Облик огня V, шанс дропа конфет +5%");
                case 3 -> List.of("&7Урон луча +3");
                default -> List.of("&7Каждый выстрел призывает молнию,", "&7Облик огня VII");
            };
            case DUELIST -> switch (lvl) {
                case 1 -> List.of("&7Острота II");
                case 2 -> List.of("&7Острота IV, шанс дропа конфет +5%");
                case 3 -> List.of("&7Острота V, после крит. удара —", "&7Скорость II на 10 сек и Сила I");
                default -> List.of("&7Острота VI, шанс дропа конфет +10%");
            };
            case SIDE -> switch (lvl) {
                case 1 -> List.of("&7Шифт+Q открывает меню способностей", "&7(пока все заблокированы)");
                case 2 -> List.of("&7«Натиск»: телепорт к ближайшему игроку", "&7в 30 блоках, следующие 2 удара", "&7станят врага на 3 сек. КД 10 мин");
                case 3 -> List.of("&7«Щит»: частицы 15 сек отражают", "&7любые снаряды, включая луч меча");
                default -> List.of("&7«Прилив»: Сила III, Прыгучесть I,", "&7Регенерация II на 20 сек + след. 5 ударов", "&7слепят и замедляют врага на 5 сек. КД 30 мин");
            };
        };
    }

    // ---------- меню способностей (Путь Стороны) ----------

    public void openAbilities(Player player) {
        int side = levelOf(player, Path.SIDE);
        if (side < 1) {
            player.sendActionBar(Msg.color("&cСпособности пока не открыты — нужен 1 ур. " + stripColor(pathDisplay(player, Path.SIDE)) + "."));
            return;
        }
        Inventory inv = Bukkit.createInventory(new AbilityHolder(), 27, Msg.color("&d&lСпособности стороны"));
        inv.setItem(11, abilityButton(player, "Натиск", Material.ENDER_PEARL, 2,
                "natisk", 10 * 60_000L,
                List.of("&7Телепорт к ближайшему игроку в 30 блоках,", "&7след. 2 удара станят врага на 3 сек.")));
        inv.setItem(13, abilityButton(player, "Щит", Material.TOTEM_OF_UNDYING, 3,
                "shield", 5 * 60_000L,
                List.of("&715 сек отражает любые снаряды,", "&7включая луч светового меча.")));
        inv.setItem(15, abilityButton(player, "Прилив", Material.BEACON, 4,
                "surge", 30 * 60_000L,
                List.of("&7Сила III, Прыгучесть I, Регенерация II на 20 сек,", "&7след. 5 ударов слепят и замедляют врага на 5 сек.")));
        player.openInventory(inv);
    }

    private ItemStack abilityButton(Player player, String name, Material icon, int requiredLevel,
                                     String abilityId, long cooldownMs, List<String> desc) {
        int side = levelOf(player, Path.SIDE);
        List<Component> lore = new ArrayList<>();
        for (String line : desc) lore.add(Msg.color(line));
        if (side < requiredLevel) {
            lore.add(Msg.color("&cТребуется " + requiredLevel + " ур. Пути Стороны"));
            return button(Material.BARRIER, "&7" + name + " — заблокировано", lore);
        }
        long remain = ScanUtil.remaining(player.getUniqueId(), "saber-" + abilityId, cooldownMs);
        if (remain > 0) {
            lore.add(Msg.color("&cПерезарядка: &f" + (remain / 1000 / 60) + " мин " + ((remain / 1000) % 60) + " сек"));
            return button(Material.CLOCK, "&7" + name + " — на кулдауне", lore);
        }
        lore.add(Msg.color("&eКлик — активировать"));
        return button(icon, "&d" + name, lore);
    }

    // ---------- вспомогательное ----------

    private static String stripColor(String s) {
        return s.replace("&", "");
    }

    private static ItemStack button(Material mat, String name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(meta -> {
            meta.displayName(Msg.color(name));
            if (!lore.isEmpty()) meta.lore(lore);
        });
        return item;
    }

    // ---------- клики ----------

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (!(holder instanceof MainHolder) && !(holder instanceof PathHolder) && !(holder instanceof AbilityHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (holder instanceof MainHolder) {
            if (slot == 13) {
                buyForceNode(player);
            } else if (slot == 10) {
                openPath(player, Path.DEFENDER);
            } else if (slot == 12) {
                openPath(player, Path.POWER);
            } else if (slot == 14) {
                openPath(player, Path.DUELIST);
            } else if (slot == 16) {
                openPath(player, Path.SIDE);
            }
            return;
        }

        if (holder instanceof PathHolder ph) {
            if (slot == 22) {
                openMain(player);
                return;
            }
            int[] slots = {10, 12, 14, 16};
            for (int i = 0; i < 4; i++) {
                if (slots[i] == slot) {
                    buyLevel(player, ph.path(), i + 1);
                    return;
                }
            }
            return;
        }

        if (holder instanceof AbilityHolder) {
            if (slot == 11) activateNatisk(player);
            else if (slot == 13) activateShield(player);
            else if (slot == 15) activateSurge(player);
        }
    }

    private void buyForceNode(Player player) {
        if (hasForceNode(player)) {
            player.sendActionBar(Msg.color("&7Уже куплено."));
            return;
        }
        if (!spendCandy(player, FORCE_NODE_COST)) {
            player.sendMessage(Msg.color("&cНе хватает конфет (" + countCandy(player) + "/" + FORCE_NODE_COST + ")."));
            return;
        }
        setForceNode(player, true);
        refreshSaber(player);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        player.sendMessage(Msg.color("&e✦ Чувствительность силы пробуждена: Острота I и молния каждый 3-й выстрел."));
        openMain(player);
    }

    private void buyLevel(Player player, Path path, int lvl) {
        int current = levelOf(player, path);
        if (current != lvl - 1) return; // можно купить только следующий по порядку уровень
        if (current == 0 && !canStartNewPath(player, path)) {
            player.sendMessage(Msg.color("&cМожно качать только 2 ветки одновременно."));
            return;
        }
        int cost = path.costFor(lvl);
        if (!spendCandy(player, cost)) {
            player.sendMessage(Msg.color("&cНе хватает конфет (" + countCandy(player) + "/" + cost + ")."));
            return;
        }
        setLevel(player, path, lvl);
        refreshSaber(player);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.0f + lvl * 0.1f);
        player.sendMessage(Msg.color("&a✦ " + stripColor(pathDisplay(player, path)) + " — уровень " + lvl + " куплен!"));
        openPath(player, path);
    }

    // ---------- способности ----------

    private void activateNatisk(Player player) {
        if (levelOf(player, Path.SIDE) < 2) return;
        if (!ScanUtil.tryUse(player.getUniqueId(), "saber-natisk", 10 * 60_000L)) {
            player.sendActionBar(Msg.color("&cЕщё на перезарядке."));
            return;
        }
        player.closeInventory();
        Player nearest = null;
        double best = 30.0 * 30.0;
        for (Player other : player.getWorld().getPlayers()) {
            if (other == player) continue;
            double d = other.getLocation().distanceSquared(player.getLocation());
            if (d < best) { best = d; nearest = other; }
        }
        if (nearest == null) {
            player.sendMessage(Msg.color("&7Рядом (в 30 блоках) никого нет — способность не активирована."));
            return; // кулдаун уже пошёл (tryUse его поставил), как и было бы в реальном бою
        }
        Location dest = nearest.getLocation().clone();
        dest.setDirection(player.getLocation().toVector().subtract(dest.toVector()));
        burst(player, player.getLocation(), 20);
        player.teleport(dest);
        burst(player, dest, 20);
        player.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        pendingStunHits.put(player.getUniqueId(), 2);
        player.sendMessage(Msg.color("&d✦ Натиск! Следующие 2 удара станят врага на 3 сек."));
    }

    private void activateShield(Player player) {
        if (levelOf(player, Path.SIDE) < 3) return;
        if (!ScanUtil.tryUse(player.getUniqueId(), "saber-shield", 5 * 60_000L)) {
            player.sendActionBar(Msg.color("&cЕщё на перезарядке."));
            return;
        }
        player.closeInventory();
        shieldUntil.put(player.getUniqueId(), System.currentTimeMillis() + 15_000L);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.5f);
        player.sendMessage(Msg.color("&d✦ Щит! 15 секунд отражает снаряды и луч меча."));
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                Long until = shieldUntil.get(player.getUniqueId());
                if (until == null || System.currentTimeMillis() > until || !player.isOnline() || ticks++ > 300) {
                    cancel();
                    return;
                }
                burst(player, player.getLocation().add(0, 1, 0), 4);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void activateSurge(Player player) {
        if (levelOf(player, Path.SIDE) < 4) return;
        if (!ScanUtil.tryUse(player.getUniqueId(), "saber-surge", 30 * 60_000L)) {
            player.sendActionBar(Msg.color("&cЕщё на перезарядке."));
            return;
        }
        player.closeInventory();
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 400, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 400, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 1));
        pendingDebuffHits.put(player.getUniqueId(), 5);
        burst(player, player.getLocation(), 40);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.6f);
        player.sendMessage(Msg.color("&d✦ Прилив! Сила III / Прыгучесть I / Регенерация II на 20 сек,"
                + " следующие 5 ударов слепят и замедляют врага."));
    }

    // ---------- боёвка: крит-бонус дуэлянта, станящие/ослепляющие удары, щит ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        // щит: отражение СНАРЯДОВ (стрелы, трезубцы и т.п.) от игрока со щитом
        if (event.getEntity() instanceof Player victim && hasShield(victim) && event.getDamager() instanceof Projectile proj) {
            event.setCancelled(true);
            burst(victim, victim.getLocation().add(0, 1, 0), 10);
            if (proj.getShooter() instanceof LivingEntity shooter) {
                shooter.damage(event.getDamage());
            }
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // станящийся/ослеплённый атакующий не может бить мечом вовсе, пока эффект активен
        if (isStunned(attacker)) {
            event.setCancelled(true);
            return;
        }

        // дуэлянт ур.3: после крит. удара — Скорость II (10 сек) и Сила I
        if (levelOf(attacker, Path.DUELIST) >= 3 && isApproxCritical(attacker)) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0));
        }

        // расход зарядов «Натиск» (станить) и «Прилив» (ослепить+замедлить)
        Integer stunLeft = pendingStunHits.get(attacker.getUniqueId());
        if (stunLeft != null && stunLeft > 0) {
            pendingStunHits.put(attacker.getUniqueId(), stunLeft - 1);
            if (stunLeft - 1 <= 0) pendingStunHits.remove(attacker.getUniqueId());
            applyStun(target, 3_000L);
        }
        Integer debuffLeft = pendingDebuffHits.get(attacker.getUniqueId());
        if (debuffLeft != null && debuffLeft > 0) {
            pendingDebuffHits.put(attacker.getUniqueId(), debuffLeft - 1);
            if (debuffLeft - 1 <= 0) pendingDebuffHits.remove(attacker.getUniqueId());
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
        }
    }

    private boolean hasShield(Player p) {
        Long until = shieldUntil.get(p.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    private boolean isStunned(Player p) {
        Long until = stunnedUntil.get(p.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    private void applyStun(LivingEntity target, long durationMs) {
        int ticks = (int) (durationMs / 50L);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 250, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, ticks, 250, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 250, false, true, true));
        if (target instanceof Player tp) {
            stunnedUntil.put(tp.getUniqueId(), System.currentTimeMillis() + durationMs);
        }
    }

    /**
     * Приближённая проверка крит. удара без NMS: игрок падает, не на земле,
     * не спринтует (ванильные крит-удары со спринтом не считаются).
     */
    private boolean isApproxCritical(Player attacker) {
        return attacker.getFallDistance() > 0.0F
                && !attacker.isOnGround()
                && !attacker.isSprinting()
                && !attacker.isInsideVehicle()
                && attacker.getVelocity().getY() < 0;
    }

    /**
     * Луч меча (SaberSystem) при попадании в игрока со щитом — урон уходит обратно стрелку
     * вместо цели. Вызывается прямо из {@link SaberSystem}, не через событие (урон луча
     * наносится программно через {@code LivingEntity#damage}, а не настоящим снарядом).
     */
    private boolean reflectBeamIfShielded0(Player shooter, LivingEntity target, double damage) {
        if (!(target instanceof Player victim) || !hasShield(victim)) return false;
        burst(victim, victim.getLocation().add(0, 1, 0), 10);
        shooter.damage(damage);
        return true;
    }
}
