package ru.voidsmp.spawnrates.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.EntityType;

/**
 * Разбор имён мобов и категорий: игрок пишет «zombie» или «монстры», а нам
 * нужны {@link EntityType} и имена {@code SpawnCategory}.
 */
public final class MobTypes {

    /** Псевдонимы категорий: как их понимает игрок → какие SpawnCategory за этим стоят. */
    private static final Map<String, Set<String>> ALIASES = buildAliases();

    private MobTypes() {
    }

    private static Map<String, Set<String>> buildAliases() {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        map.put("all", Set.of());
        map.put("monsters", Set.of("MONSTER"));
        map.put("hostile", Set.of("MONSTER"));
        map.put("animals", Set.of("ANIMAL", "AXOLOTL"));
        map.put("passive", Set.of("ANIMAL", "AXOLOTL"));
        map.put("water", Set.of("WATER_ANIMAL", "WATER_AMBIENT", "WATER_UNDERGROUND_CREATURE"));
        map.put("ambient", Set.of("AMBIENT"));
        map.put("misc", Set.of("MISC"));
        return Map.copyOf(map);
    }

    /** @return null, если имя не похоже ни на один EntityType */
    public static EntityType resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        if (name.isEmpty()) {
            return null;
        }
        EntityType byName = EntityType.fromName(name);
        if (byName != null) {
            return byName;
        }
        try {
            return EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Разбирает фильтр категорий. Пустое множество — «все категории».
     *
     * @throws IllegalArgumentException на неизвестное слово (команда отдаёт сообщение с вариантами)
     */
    public static Set<String> parseCategories(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("all") || raw.equalsIgnoreCase("все")) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : raw.split("[,;+]")) {
            String key = part.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            Set<String> alias = ALIASES.get(key);
            if (alias != null) {
                out.addAll(alias);
                continue;
            }
            String upper = key.toUpperCase(Locale.ROOT);
            if (isCategory(upper)) {
                out.add(upper);
                continue;
            }
            throw new IllegalArgumentException(key);
        }
        return Set.copyOf(out);
    }

    private static boolean isCategory(String name) {
        for (org.bukkit.entity.SpawnCategory category : org.bukkit.entity.SpawnCategory.values()) {
            if (category.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Подсказка для сообщения об ошибке. */
    public static String categoryOptions() {
        return String.join(", ", "all", "monsters", "animals", "water", "ambient", "misc");
    }

    public static String categoriesToString(Set<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return "все";
        }
        return String.join(",", categories);
    }

    /** Автодополнение имён мобов по префиксу. */
    public static List<String> suggest(String prefix, int limit) {
        String key = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            if (type == EntityType.UNKNOWN || type == EntityType.PLAYER) {
                continue;
            }
            if (!type.isAlive()) {
                continue;
            }
            String name = type.name().toLowerCase(Locale.ROOT);
            if (key.isEmpty() || name.contains(key)) {
                out.add(name);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }
}
