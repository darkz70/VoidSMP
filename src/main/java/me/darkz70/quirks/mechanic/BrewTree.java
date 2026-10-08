package me.darkz70.quirks.mechanic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.listener.CraftListener;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Nullable;
import io.papermc.paper.potion.PotionMix;

/**
 * Дерево зельеварения VoidSMP (v1.0). Все крафты — ручной матчинг сетки верстака,
 * НИКАКИХ зарегистрированных рецептов зелий: в книге рецептов их нет.
 * Варка (грибная настойка, зелье знаний) — через PotionMix (у варки нет книги рецептов).
 */
public final class BrewTree {

    private BrewTree() {}

    // ---------- утилиты ----------

    @Nullable
    public static String markOf(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(Keys.brewMark, PersistentDataType.STRING);
    }

    public static boolean tagged(@Nullable ItemStack item, String tag) {
        return tag.equals(markOf(item));
    }

    /** Семейство базового зелья (без LONG_/STRONG_, обычное/взрывное/туманное — всё одно). */
    @Nullable
    public static String baseFamily(ItemStack item) {
        if (item.getType() != Material.POTION && item.getType() != Material.SPLASH_POTION
                && item.getType() != Material.LINGERING_POTION) {
            return null;
        }
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return null;
        PotionType type = meta.getBasePotionType();
        if (type == null) return null;
        String name = type.name();
        if (name.startsWith("LONG_")) name = name.substring(5);
        if (name.startsWith("STRONG_")) name = name.substring(7);
        return name;
    }

    public static boolean isVanillaPotion(ItemStack item) {
        return baseFamily(item) != null && markOf(item) == null;
    }

    // ---------- предметы ----------

    private static ItemStack potion(String name, Color color, String tag, @Nullable PotionType base,
            @Nullable String lore) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(base == null ? PotionType.WATER : base);
        meta.displayName(me.darkz70.quirks.util.Msg.color(name));
        if (color != null) meta.setColor(color);
        if (lore != null) {
            meta.lore(List.of(me.darkz70.quirks.util.Msg.color(lore)));
        }
        meta.getPersistentDataContainer().set(Keys.brewMark, PersistentDataType.STRING, tag);
        item.setItemMeta(meta);
        return item;
    }

    /** Зелье заражения: скалк 1 уровня. */
    public static ItemStack makeInfect() {
        return potion("&k+++ &r&5Заражённая вода &k+++", Color.fromRGB(0x2E, 0x7D, 0x62), "infect", null,
                "&7Пахнет вибрациями из глубин…");
    }

    /** Самогон уровня n (1..16). */
    public static ItemStack makeSamogon(int level, boolean boosted) {
        final int lvl = Math.max(1, Math.min(16, level));
        ItemStack item = potion("&6Самогон &7(ур. " + lvl + ")", Color.fromRGB(0xC4, 0x8A, 0x1D), "samogon", null,
                boosted ? "&eПод самогонным спиртом — по двойной" : "&7Горит, но греет душу");
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(Keys.brewLevel, PersistentDataType.INTEGER, lvl);
            if (boosted) meta.getPersistentDataContainer().set(Keys.spellExtra, PersistentDataType.INTEGER, 2);
        });
        return item;
    }

    /** Зелье отрицания (5 neutral): снимает все позитивные эффекты. */
    public static ItemStack makeDeny() {
        return potion("&8Зелье отрицания", Color.fromRGB(0x33, 0x33, 0x33), "deny", null,
                "&7Смывает всё хорошее.");
    }

    /** Зелье подтверждения (6 neutral): снимает все негативные эффекты. */
    public static ItemStack makeConfirm() {
        return potion("&fЗелье подтверждения", Color.fromRGB(0xE8, 0xE8, 0xE8), "confirm", null,
                "&7Смывает всё плохое.");
    }

    /** Терминальный ингредиент → причуда. */
    @Nullable
    public static me.darkz70.quirks.Quirk quirkForTerminal(Material material) {
        return switch (material) {
            case DIAMOND_AXE -> me.darkz70.quirks.Quirk.AXE;
            case DIAMOND_HOE -> me.darkz70.quirks.Quirk.FARMER;
            case SCULK -> me.darkz70.quirks.Quirk.SCULK;
            case COD -> me.darkz70.quirks.Quirk.CAT;
            case TRIDENT -> me.darkz70.quirks.Quirk.AMPHIBIAN;
            case AMETHYST_CLUSTER -> me.darkz70.quirks.Quirk.BEDROCK;
            case REDSTONE -> me.darkz70.quirks.Quirk.ENGINEER;
            case COBWEB -> me.darkz70.quirks.Quirk.SPIDER;
            default -> null;
        };
    }

    /** Зелье отключения конкретной причуды. */
    public static ItemStack makeDisable(me.darkz70.quirks.Quirk quirk) {
        ItemStack item = potion("&7Зелье отключения", Color.fromRGB(0x66, 0x44, 0x22), "disable", null,
                "&8В нём тлеет чужой отклик…");
        item.editMeta(meta -> meta.getPersistentDataContainer()
                .set(Keys.brewTarget, PersistentDataType.STRING, quirk.id()));
        return item;
    }

    /** Зелье причуды: disable + confirm → все 8 причуд 3 ур. на 10 секунд. */
    public static ItemStack makeQuirkAll() {
        return potion("&dЗелье причуды", Color.fromRGB(0xB0, 0x50, 0xC8), "quirkall", null,
                "&7На десять секунд ты — всё сразу.");
    }

    public static ItemStack makeLife() {
        return potion("&cЗелье жизни", Color.fromRGB(0xDD, 0x33, 0x55), "life", null,
                "&7Тепло, как у мамы на кухне.");
    }

    public static ItemStack makePaces() {
        return potion("&bЗелье ускорения", Color.fromRGB(0x55, 0xBB, 0xEE), "paces", null,
                "&7Ноги сами понесут.");
    }

    public static ItemStack makeNoHarm() {
        return potion("&4Зелье никакого вреда", Color.fromRGB(0x66, 0x11, 0x11), "noharm", null,
                "&7Никому не навреди. Серьёзно.");
    }

    public static ItemStack makeNoRestore() {
        return potion("&2Зелье никакого восстановления", Color.fromRGB(0x11, 0x55, 0x22), "norestore", null,
                "&7И не пытайся лечиться.");
    }

    public static ItemStack makeFlyPot() {
        return potion("&fЗелье полёта", Color.fromRGB(0xDD, 0xEE, 0xFF), "flypot", null,
                "&7Восемь секунд неба. Осторожно с приземлением.");
    }

    public static ItemStack makeGod() {
        return potion("&6Зелье божества", Color.fromRGB(0xFF, 0xD7, 0x54), "god", null,
                "&7Две минуты всевластия — и расплата.");
    }

    public static ItemStack makeBerserk() {
        return potion("&4Зелье берсерка", Color.fromRGB(0xAA, 0x22, 0x22), "berserk", null,
                "&7Ярость без боли.");
    }

    public static ItemStack makeBasis() {
        return potion("&7Зелье основы", Color.fromRGB(0x88, 0x88, 0x99), "basis", null,
                "&7Пустая суть, готовая усилить.");
    }

    /** Усиленное зелье: длительность ×2, усилитель +1. */
    public static ItemStack makeBoosted(ItemStack source) {
        ItemStack result = source.clone();
        result.setAmount(1);
        result.editMeta(PotionMeta.class, meta -> {
            List<PotionEffect> effects = new ArrayList<>(meta.getCustomEffects());
            meta.clearCustomEffects();
            for (PotionEffect effect : effects) {
                meta.addCustomEffect(new PotionEffect(effect.getType(), effect.getDuration() * 2,
                        Math.min(effect.getAmplifier() + 1, 3), effect.isAmbient(), effect.hasParticles(),
                        effect.hasIcon()), false);
            }
            // базовые типы: HEALING→усилитель выразить кастомным эффектом
            String fam = baseFamily(source);
            if (meta.getCustomEffects().isEmpty() && fam != null) {
                PotionEffectType strong = equivalentEffect(fam);
                if (strong != null) {
                    meta.addCustomEffect(new PotionEffect(strong, strongEquivalentDuration(fam) * 1,
                            1, false, true, true), false);
                    meta.setBasePotionType(PotionType.WATER);
                }
            }
            meta.getPersistentDataContainer().set(Keys.spellExtra, PersistentDataType.INTEGER, 2);
            if (meta.displayName() != null) {
                meta.displayName(me.darkz70.quirks.util.Msg.color("&eУсиленное ")
                        .append(meta.displayName().colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.YELLOW)));
            }
        });
        return result;
    }

    @Nullable
    private static PotionEffectType equivalentEffect(String family) {
        return switch (family) {
            case "HEALING" -> PotionEffectType.INSTANT_HEALTH;
            case "HARMING" -> PotionEffectType.INSTANT_DAMAGE;
            case "SWIFTNESS" -> PotionEffectType.SPEED;
            case "LEAPING" -> PotionEffectType.JUMP_BOOST;
            case "STRENGTH" -> PotionEffectType.STRENGTH;
            case "REGENERATION" -> PotionEffectType.REGENERATION;
            case "TURTLE_MASTER" -> PotionEffectType.RESISTANCE;
            case "SLOWNESS" -> PotionEffectType.SLOWNESS;
            case "POISON" -> PotionEffectType.POISON;
            case "WEAKNESS" -> PotionEffectType.WEAKNESS;
            case "NIGHT_VISION" -> PotionEffectType.NIGHT_VISION;
            case "INVISIBILITY" -> PotionEffectType.INVISIBILITY;
            case "SLOW_FALLING" -> PotionEffectType.SLOW_FALLING;
            case "WATER_BREATHING" -> PotionEffectType.WATER_BREATHING;
            case "FIRE_RESISTANCE" -> PotionEffectType.FIRE_RESISTANCE;
            case "LUCK" -> PotionEffectType.LUCK;
            default -> null;
        };
    }

    private static int strongEquivalentDuration(String family) {
        return switch (family) {
            case "HEALING", "HARMING" -> 1;
            case "SWIFTNESS", "STRENGTH", "LEAPING" -> 3 * 60 * 20;
            case "SLOWNESS", "POISON", "WEAKNESS" -> 90 * 20;
            case "REGENERATION" -> 45 * 20;
            case "TURTLE_MASTER" -> 20 * 20;
            case "NIGHT_VISION", "INVISIBILITY", "FIRE_RESISTANCE", "WATER_BREATHING" -> 3 * 60 * 20;
            case "SLOW_FALLING" -> 90 * 20;
            case "LUCK" -> 5 * 60 * 20;
            default -> 60 * 20;
        };
    }

    public static ItemStack makeLucky() {
        ItemStack item = potion("&aЗелье удачи", Color.fromRGB(0x33, 0xAA, 0x33), "lucky", null,
                "&7Сегодня твой день.");
        item.editMeta(PotionMeta.class, meta -> meta.addCustomEffect(
                new PotionEffect(PotionEffectType.LUCK, 5 * 60 * 20, 0, false, true, true), false));
        return item;
    }

    public static ItemStack makeUnlucky() {
        ItemStack item = potion("&5Зелье никакой удачи", Color.fromRGB(0x44, 0x22, 0x66), "unlucky", null,
                "&7Просто вода. Бум. Ничего.");
        item.editMeta(PotionMeta.class, meta -> meta.addCustomEffect(
                new PotionEffect(PotionEffectType.UNLUCK, 60 * 20, 0, false, false, false), false));
        return item;
    }

    public static ItemStack makeGrandSamogon(int level) {
        ItemStack item = potion("&6&k## &r&6Великий самогон &7(ур. " + level + ") &6&k##",
                Color.fromRGB(0x99, 0x66, 0x11), "grandsam", null,
                "&7Двойная сила, слепота и трещина в голове.");
        item.editMeta(meta -> meta.getPersistentDataContainer()
                .set(Keys.brewLevel, PersistentDataType.INTEGER, Math.max(1, Math.min(16, level))));
        return item;
    }

    public static ItemStack makeReturn() {
        return potion("&f&k/o/ &r&bЗелье возврата &f&k\\o\\", Color.fromRGB(0xBD, 0xDA, 0xFF), "return", null,
                "&7Чистый лист. Причуды, магия, модификаторы — всё прочь.");
    }

    public static ItemStack makeInfusion() {
        return potion("&2Настойка гриба", Color.fromRGB(0x55, 0x77, 0x33), "infusion", null,
                "&7Горький грибной дух.");
    }

    public static ItemStack makeRetrain() {
        return potion("&eЗелье переквалификации", Color.fromRGB(0xCC, 0xAA, 0x33), "retrain", null,
                "&7Забудь стихию и уровень. Начни с чистого разума.");
    }

    public static ItemStack makeSaturation() {
        return potion("&6Зелье насыщения", Color.fromRGB(0xBB, 0x77, 0x22), "satpot", null,
                "&7Сытость на целый час… минуту.");
    }

    public static ItemStack makeKnowledge() {
        return potion("&3Зелье знаний", Color.fromRGB(0x22, 0x66, 0xAA), "knowledge", null,
                "&7Мудрость на донышке.");
    }

    public static ItemStack makeMind() {
        return potion("&bЗелье ума", Color.fromRGB(0x44, 0x99, 0xDD), "mind", null,
                "&7Две порции знаний — одна голова.");
    }

    public static ItemStack makeMiner() {
        return potion("&eЗелье шахтёра", Color.fromRGB(0xCC, 0xBB, 0x44), "miner", null,
                "&7Кирка летает сама.");
    }

    public static ItemStack makeDull() {
        return potion("&8Зелье потупления", Color.fromRGB(0x55, 0x55, 0x66), "dull", null,
                "&7Сила, туман и звон в ушах.");
    }

    public static ItemStack makeDemagic() {
        return potion("&5Зелье уменьшения магий", Color.fromRGB(0x66, 0x33, 0x88), "demagic", null,
                "&7Радиус пять блоков — уровни магии таят.");
    }

    // спектральные стрелы

    public static ItemStack makeSpectral() {
        ItemStack arrows = new ItemStack(Material.SPECTRAL_ARROW, 32);
        arrows.editMeta(meta -> meta.displayName(
                me.darkz70.quirks.util.Msg.color("&eСпектральный букет &7(32)")));
        return arrows;
    }

    // кристалл фокуса + фокусировка

    public static ItemStack makeFocusCrystal() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        item.editMeta(meta -> {
            meta.displayName(me.darkz70.quirks.util.Msg.color("&dКристалл фокуса"));
            meta.lore(List.of(me.darkz70.quirks.util.Msg.color("&7Помести в верстак с предметом —"),
                    me.darkz70.quirks.util.Msg.color("&7и предмет сможет колдовать.")));
            meta.getPersistentDataContainer().set(Keys.brewMark, PersistentDataType.STRING, "focus-crystal");
        });
        return item;
    }

    /** Предмет-фокус: любой не-блок + кристалл. */
    @Nullable
    public static ItemStack focusFusion(List<ItemStack> stacks) {
        if (stacks.size() != 2) return null;
        ItemStack crystal = null;
        ItemStack target = null;
        for (ItemStack stack : stacks) {
            if (tagged(stack, "focus-crystal")) crystal = stack;
            else target = stack;
        }
        if (crystal == null || target == null) return null;
        if (target.getType().isBlock()) return null;                  // только не-блоки
        if (markOf(target) != null) return null;                      // не зелья/книги плагина
        if (target.getType() == Material.POTION || target.getType() == Material.SPLASH_POTION
                || target.getType() == Material.LINGERING_POTION) return null;
        final ItemStack targetFinal = target;
        ItemStack result = target.clone();
        result.setAmount(1);
        if (result.getPersistentDataContainer().has(Keys.focusKey, PersistentDataType.BYTE)) return null;
        result.editMeta(meta -> {
            meta.displayName(me.darkz70.quirks.util.Msg.color("&dФокус: &f")
                    .append(net.kyori.adventure.text.Component.translatable(targetFinal)));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(me.darkz70.quirks.util.Msg.color("&7ПКМ — каст &8| &7Q/F — смена &8| &7Shift+F — меню"));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(Keys.focusKey, PersistentDataType.BYTE, (byte) 1);
        });
        return result;
    }

    // ---------- матчинг матрицы ----------

    private interface Pred {
        boolean test(ItemStack item);
    }

    private static Pred mat(Material material) {
        return item -> item.getType() == material;
    }

    private static Pred mark(String tag) {
        return item -> tagged(item, tag);
    }

    private static Pred potionFam(String family) {
        return item -> family.equals(baseFamily(item));
    }

    private static Pred anyVanillaPotion() {
        return BrewTree::isVanillaPotion;
    }

    private static Pred samogonAny() {
        return item -> tagged(item, "samogon");
    }

    private static Pred anyPotion() {
        return item -> baseFamily(item) != null;
    }

    private record Rule(List<Pred> preds, Function<List<ItemStack>, ItemStack> make) {}

    private static final List<Rule> RULES = new ArrayList<>();

    private static void rule(Function<List<ItemStack>, ItemStack> make, Pred... preds) {
        RULES.add(new Rule(List.of(preds), make));
    }

    static {
        // база дерева (раньше были видимыми рецептами — теперь скрыты)
        rule(l -> CraftListener.makeNeutral(), potionFam("HARMING"), potionFam("HEALING"));
        rule(l -> CraftListener.makeAntidote(), mark("neutral"), mat(Material.RESIN_CLUMP),
                mat(Material.HONEYCOMB));
        // заражения
        rule(l -> makeInfect(), mark("neutral"), mat(Material.SCULK_SENSOR));
        // самогон (создание и прокачка 1..15)
        rule(l -> makeSamogon(1, false), mark("neutral"), mat(Material.HONEY_BOTTLE), mat(Material.SWEET_BERRIES));
        rule(l -> makeSamogon(samogonLevel(l) + 1, false),
                samogonAny(), mat(Material.HONEY_BOTTLE), mat(Material.SWEET_BERRIES));
        // отрицание / подтверждение
        rule(l -> makeDeny(), mark("neutral"), mark("neutral"), mark("neutral"), mark("neutral"), mark("neutral"));
        rule(l -> makeConfirm(), mark("neutral"), mark("neutral"), mark("neutral"), mark("neutral"),
                mark("neutral"), mark("neutral"));
        // отключения причуды (8 терминальных)
        for (Material terminal : new Material[]{Material.DIAMOND_AXE, Material.DIAMOND_HOE, Material.SCULK,
            Material.COD, Material.TRIDENT, Material.AMETHYST_CLUSTER, Material.REDSTONE, Material.COBWEB}) {
            me.darkz70.quirks.Quirk quirk = quirkForTerminal(terminal);
            rule(l -> makeDisable(quirk), mark("neutral"), mat(terminal));
        }
        // зелье причуды
        rule(l -> makeQuirkAll(), mark("disable"), mark("confirm"));
        // жизни / ускорения / никакого вреда / никакого восстановления
        rule(l -> makeLife(), mark("neutral"), potionFam("HEALING"), potionFam("REGENERATION"));
        rule(l -> makePaces(), mark("neutral"), potionFam("SWIFTNESS"), potionFam("LEAPING"));
        rule(l -> makeNoHarm(), mark("neutral"), potionFam("HARMING"));
        rule(l -> makeNoRestore(), mark("neutral"), potionFam("HEALING"));
        // полёта
        rule(l -> makeFlyPot(), mark("neutral"), potionFam("SLOW_FALLING"), potionFam("SLOW_FALLING"),
                mark("neutral"));
        // божества
        rule(l -> makeGod(), mark("neutral"), mark("flypot"), mark("paces"), mark("life"),
                mark("noharm"), mark("norestore"), mark("quirkall"));
        // берсерка
        rule(l -> makeBerserk(), mark("neutral"), potionFam("STRENGTH"), mark("life"), samogonAny());
        // основы
        rule(l -> makeBasis(), mark("neutral"), mark("neutral"), potionFam("HEALING"), potionFam("HARMING"));
        // усиление: основа + любое НЕ-плагиновое зелье
        rule(l -> makeBoosted(l.get(1)), mark("basis"), anyVanillaPotion());
        // усиление самогона
        rule(l -> {
            ItemStack source = tagged(l.get(0), "samogon") ? l.get(0) : l.get(1);
            return makeSamogon(samogonLevel(l), true);
        }, mark("basis"), samogonAny());
        // удача
        rule(l -> makeLucky(), mark("neutral"), mark("basis"));
        // спектральные стрелы: 8 стрел + удача -> 32 спектральных
        rule(l -> makeSpectral(), mark("lucky"),
                mat(Material.ARROW), mat(Material.ARROW), mat(Material.ARROW), mat(Material.ARROW),
                mat(Material.ARROW), mat(Material.ARROW), mat(Material.ARROW), mat(Material.ARROW));
        // никакой удачи
        rule(l -> makeUnlucky(), mark("lucky"), mark("neutral"));
        // великого самогона
        rule(l -> makeGrandSamogon(samogonLevel(l)), mark("unlucky"), samogonAny());
        // возврата
        rule(l -> makeReturn(), mark("neutral"), mark("god"), mark("god"), mark("god"), mark("god"),
                mark("god"), mark("god"), mark("god"), mark("god"));
        // переквалификации / насыщения / шахтёра / потупления
        rule(l -> makeRetrain(), mark("infusion"), mark("neutral"), mat(Material.BOOK));
        rule(l -> makeSaturation(), mark("infusion"), mark("infusion"));
        rule(l -> makeMiner(), mark("knowledge"), mark("neutral"));
        rule(l -> makeDull(), mark("mind"), mark("deny"));
        // уменьшения магий (в спеке 10 позиций при сетке 3×3 — ограничили 5 алмазными блоками)
        rule(l -> makeDemagic(), mark("dull"), mark("neutral"), anyPotion(), mark("mind"),
                mat(Material.DIAMOND_BLOCK), mat(Material.DIAMOND_BLOCK), mat(Material.DIAMOND_BLOCK),
                mat(Material.DIAMOND_BLOCK), mat(Material.DIAMOND_BLOCK));
    }

    private static int samogonLevel(List<ItemStack> matched) {
        for (ItemStack item : matched) {
            if (tagged(item, "samogon")) {
                Integer lvl = item.getItemMeta().getPersistentDataContainer()
                        .get(Keys.brewLevel, PersistentDataType.INTEGER);
                return lvl == null ? 1 : Math.min(16, lvl);
            }
        }
        return 1;
    }

    /** Совпадает ли матрица с каким-либо правилом. Вернёт результат или null. */
    @Nullable
    public static ItemStack match(List<ItemStack> nonEmptyStacks) {
        outer:
        for (Rule rule : RULES) {
            if (rule.preds().size() != nonEmptyStacks.size()) continue;
            boolean[] used = new boolean[nonEmptyStacks.size()];
            List<ItemStack> order = new ArrayList<>(rule.preds().size());
            for (Pred pred : rule.preds()) {
                boolean found = false;
                for (int i = 0; i < nonEmptyStacks.size(); i++) {
                    if (used[i]) continue;
                    if (pred.test(nonEmptyStacks.get(i))) {
                        used[i] = true;
                        order.add(nonEmptyStacks.get(i));
                        found = true;
                        break;
                    }
                }
                if (!found) continue outer;
            }
            return rule.make().apply(order);
        }
        return null;
    }

    // ---------- варка ----------

    private static ItemStack waterBottle() {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        item.setItemMeta(meta);
        return item;
    }

    /** Регистрация варок (у котла нет книги рецептов — скрытность сохранена). */
    public static void registerBrewing(VoidQuirksPlugin plugin) {
        RecipeChoice water = new RecipeChoice.ExactChoice(waterBottle());
        addMix(plugin, "infuse_red", makeInfusion(1), water,
                new RecipeChoice.MaterialChoice(Material.RED_MUSHROOM));
        addMix(plugin, "infuse_brown", makeInfusion(1), water,
                new RecipeChoice.MaterialChoice(Material.BROWN_MUSHROOM));
        addMix(plugin, "infuse_crimson", makeInfusion(1), water,
                new RecipeChoice.MaterialChoice(Material.CRIMSON_FUNGUS));
        addMix(plugin, "infuse_warped", makeInfusion(1), water,
                new RecipeChoice.MaterialChoice(Material.WARPED_FUNGUS));
        addMix(plugin, "knowledge", makeKnowledge(), water,
                new RecipeChoice.MaterialChoice(Material.BOOK));
    }

    private static ItemStack makeInfusion(int dummy) {
        return makeInfusion();
    }

    private static void addMix(VoidQuirksPlugin plugin, String name, ItemStack result,
            RecipeChoice input, RecipeChoice ingredient) {
        NamespacedKey key = new NamespacedKey(plugin, "mix_" + name);
        try {
            Bukkit.getPotionBrewer().removePotionMix(key);
        } catch (Throwable ignored) { }
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, result, input, ingredient));
    }

    // ---------- админ-выдача ----------

    @Nullable
    public static ItemStack byTag(String tag, Player ignored) {
        return switch (tag.toLowerCase(java.util.Locale.ROOT)) {
            case "neutral" -> CraftListener.makeNeutral();
            case "antidote" -> CraftListener.makeAntidote();
            case "infect" -> makeInfect();
            case "samogon" -> makeSamogon(1, false);
            case "deny" -> makeDeny();
            case "confirm" -> makeConfirm();
            case "quirkall" -> makeQuirkAll();
            case "life" -> makeLife();
            case "paces" -> makePaces();
            case "noharm" -> makeNoHarm();
            case "norestore" -> makeNoRestore();
            case "flypot" -> makeFlyPot();
            case "god" -> makeGod();
            case "berserk" -> makeBerserk();
            case "basis" -> makeBasis();
            case "lucky" -> makeLucky();
            case "unlucky" -> makeUnlucky();
            case "grandsam" -> makeGrandSamogon(1);
            case "return" -> makeReturn();
            case "infusion" -> makeInfusion();
            case "retrain" -> makeRetrain();
            case "satpot" -> makeSaturation();
            case "knowledge" -> makeKnowledge();
            case "mind" -> makeMind();
            case "miner" -> makeMiner();
            case "dull" -> makeDull();
            case "demagic" -> makeDemagic();
            default -> null;
        };
    }
}
