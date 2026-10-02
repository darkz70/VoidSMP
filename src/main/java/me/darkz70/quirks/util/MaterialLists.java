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
    public static Set<Material> lowSwords = EnumSet.noneOf(Material.class);

    /** допустимая обычная еда Скалка по уровням (1..3) */
    public static final Map<Integer, Set<Material>> sculkFood = new HashMap<>();
    /** необычная "еда" Скалка: уровень -> (материал -> {food, saturation}) */
    public static final Map<Integer, Map<Material, int[]>> sculkCustom = new HashMap<>();

    private static Logger logger;

    private MaterialLists() {}

    public static void load(FileConfiguration cfg, Logger log) {
        logger = log;
        engineerBase = parse(cfg, "materials.engineer-base");
        engineerExtra = parse(cfg, "materials.engineer-extra");
        engineerTnt = parse(cfg, "materials.engineer-tnt-blocks");
        engineerSigns = cfg.getBoolean("materials.engineer-include-signs", true);
        meat = parse(cfg, "materials.meat");
        fish = parse(cfg, "materials.fish");
        lowSwords = parse(cfg, "materials.axe-low-swords");

        sculkFood.clear();
        for (int level = 1; level <= 3; level++) {
            sculkFood.put(level, parse(cfg, "materials.sculk-food-" + level));
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

    private static Set<Material> parse(FileConfiguration cfg, String path) {
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        for (String name : cfg.getStringList(path)) {
            Material mat = Material.matchMaterial(name);
            if (mat == null) {
                logger.warning("Неизвестный материал в " + path + ": " + name);
            } else {
                set.add(mat);
            }
        }
        return set;
    }

    /** Мясо по конфигу + то, что ваниль считает мясом, можно расширить тут. */
    public static boolean isMeat(Material mat) {
        return meat.contains(mat);
    }

    public static boolean isFish(Material mat) {
        return fish.contains(mat);
    }

    public static boolean isLowSword(Material mat) {
        return lowSwords.contains(mat);
    }

    public static boolean isSculkFood(int level, Material mat) {
        Set<Material> set = sculkFood.getOrDefault(level, sculkFood.get(1));
        return set != null && set.contains(mat);
    }
}
