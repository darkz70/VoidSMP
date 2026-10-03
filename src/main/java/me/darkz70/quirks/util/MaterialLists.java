package me.darkz70.quirks.util;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Списки материалов из config.yml (перечитываются при /quirk reload). */
public final class MaterialLists {

    public static Set<Material> engineerBase = EnumSet.noneOf(Material.class);
    public static Set<Material> engineerExtra = EnumSet.noneOf(Material.class);
    public static Set<Material> engineerTnt = EnumSet.noneOf(Material.class);
    public static boolean engineerSigns = true;
    public static Set<Material> meat = EnumSet.noneOf(Material.class);
    public static Set<Material> fish = EnumSet.noneOf(Material.class);
    /** мечи -> палка для Топора 1 ур. (всё слабее незеритового) */
    public static Set<Material> lowSwords1 = EnumSet.noneOf(Material.class);
    /** мечи -> палка для Топора 2 ур. (всё слабее алмазного) */
    public static Set<Material> lowSwords2 = EnumSet.noneOf(Material.class);
    /** сырая еда, запрещённая Фермеру (всё, что жарится) */
    public static Set<Material> farmerRaw = EnumSet.noneOf(Material.class);

    /** допустимая обычная еда Скалка по уровням (1..3) */
    public static final Map<Integer, Set<Material>> sculkFood = new HashMap<>();
    /** необычная "еда" Скалка: уровень -> (материал -> {food, saturation}) */
    public static final Map<Integer, Map<Material, int[]>> sculkCustom = new HashMap<>();

    /* ---- дефолты (на случай старых конфигов без этих секций) ---- */
    private static final Material[] DEF_ENGINEER_BASE = {Material.REDSTONE_WIRE, Material.REDSTONE_BLOCK, Material.PISTON, Material.STICKY_PISTON};
    private static final Material[] DEF_ENGINEER_EXTRA = {Material.TRIPWIRE};
    private static final Material[] DEF_ENGINEER_TNT = {Material.TNT};
    private static final Material[] DEF_MEAT = {
        Material.BEEF, Material.COOKED_BEEF, Material.PORKCHOP, Material.COOKED_PORKCHOP,
        Material.CHICKEN, Material.COOKED_CHICKEN, Material.MUTTON, Material.COOKED_MUTTON,
        Material.RABBIT, Material.COOKED_RABBIT, Material.ROTTEN_FLESH, Material.RABBIT_STEW
    };
    private static final Material[] DEF_FISH = {
        Material.COD, Material.COOKED_COD, Material.SALMON, Material.COOKED_SALMON,
        Material.TROPICAL_FISH, Material.PUFFERFISH
    };
    private static final Material[] DEF_SWORDS_1 = {
        Material.WOODEN_SWORD, Material.GOLDEN_SWORD, Material.STONE_SWORD,
        Material.IRON_SWORD, Material.DIAMOND_SWORD
    };
    private static final Material[] DEF_SWORDS_2 = {
        Material.WOODEN_SWORD, Material.GOLDEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD
    };
    /** сырая еда Скалка + сырая рыба (v0.6.0) */
    private static final Material[] DEF_SCULK_FOOD = {
        Material.BEEF, Material.PORKCHOP, Material.CHICKEN, Material.MUTTON, Material.RABBIT,
        Material.ROTTEN_FLESH, Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH
    };
    /** всё, что можно пожарить (Фермеру есть нельзя) */
    private static final Material[] DEF_FARMER_RAW = {
        Material.BEEF, Material.PORKCHOP, Material.CHICKEN, Material.MUTTON, Material.RABBIT,
        Material.COD, Material.SALMON, Material.POTATO, Material.KELP
    };

    private static Logger logger;

    private MaterialLists() {}

    public static void load(FileConfiguration cfg, Logger log) {
        logger = log;
        engineerBase = parse(cfg, "materials.engineer-base", DEF_ENGINEER_BASE);
        engineerExtra = parse(cfg, "materials.engineer-extra", DEF_ENGINEER_EXTRA);
        engineerTnt = parse(cfg, "materials.engineer-tnt-blocks", DEF_ENGINEER_TNT);
        engineerSigns = cfg.getBoolean("materials.engineer-include-signs", true);
        meat = parse(cfg, "materials.meat", DEF_MEAT);
        fish = parse(cfg, "materials.fish", DEF_FISH);
        lowSwords1 = parse(cfg, "materials.axe-low-swords-1", DEF_SWORDS_1);
        lowSwords2 = parse(cfg, "materials.axe-low-swords-2", DEF_SWORDS_2);
        farmerRaw = parse(cfg, "materials.farmer-raw-denied", DEF_FARMER_RAW);

        sculkFood.clear();
        for (int level = 1; level <= 3; level++) {
            sculkFood.put(level, parse(cfg, "materials.sculk-food-" + level, DEF_SCULK_FOOD));
        }

        sculkCustom.clear();
        ConfigurationSection root = cfg.getConfigurationSection("sculk-custom-food");
        if (root != null) {
            for (String lvlKey : root.getKeys(false)) {
                int level;
                try {
                    level = Integer.parseInt(lvlKey);
                } catch (NumberFormatException ex) {
                    continue;
                }
                Map<Material, int[]> map = new EnumMap<>(Material.class);
                ConfigurationSection lvlSection = root.getConfigurationSection(lvlKey);
                if (lvlSection != null) {
                    for (String matKey : lvlSection.getKeys(false)) {
                        Material mat = Material.matchMaterial(matKey);
                        if (mat == null) {
                            logger.warning("Неизвестный материал sculk-custom-food." + lvlKey + "." + matKey);
                            continue;
                        }
                        map.put(mat, new int[]{
                            lvlSection.getInt(matKey + ".food", 2),
                            lvlSection.getInt(matKey + ".saturation", 0)
                        });
                    }
                }
                sculkCustom.put(level, map);
            }
        }
    }

    private static Set<Material> parse(FileConfiguration cfg, String path, Material[] fallback) {
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        java.util.List<String> names = cfg.getStringList(path);
        if (names.isEmpty()) {
            // секции нет (старый конфиг) — используем встроенный дефолт
            set.addAll(java.util.List.of(fallback));
            return set;
        }
        for (String name : names) {
            Material mat = Material.matchMaterial(name);
            if (mat == null) {
                logger.warning("Неизвестный материал в " + path + ": " + name);
            } else {
                set.add(mat);
            }
        }
        return set;
    }

    /** Мясо по конфигу — для дебаффов Инженера, Топора и Паука. */
    public static boolean isMeat(Material mat) {
        return meat.contains(mat);
    }

    public static boolean isFish(Material mat) {
        return fish.contains(mat);
    }

    /** Меч, который превращается в палку у Топора данного уровня (1 или 2). */
    public static boolean isLowSword(int level, Material mat) {
        return (level <= 1 ? lowSwords1 : lowSwords2).contains(mat);
    }

    /** Сырая еда, которую Фермер должен пожарить. */
    public static boolean isFarmerRaw(Material mat) {
        return farmerRaw.contains(mat);
    }

    public static boolean isSculkFood(int level, Material mat) {
        Set<Material> set = sculkFood.getOrDefault(level, sculkFood.get(1));
        return set != null && set.contains(mat);
    }
}
