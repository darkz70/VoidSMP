package me.darkz70.voidsmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Генерирует внутренний датапак с лут-таблицей обычных хранилищ:
 * {@code voidsmp:chests/regular_vault}.
 * <p>
 * Синтаксис JSON — формат датапаков Minecraft 26.2 (pack_format 107,
 * поле {@code functions} с ключом {@code function} внутри).
 */
public final class LootPackGenerator {

    /** Путь лут-таблицы внутри датапака (без расширения). */
    public static final String TABLE_PATH = "chests/regular_vault";

    private LootPackGenerator() {
    }

    /** Записать pack.mcmeta и лут-таблицу в {@code <dataDir>/pack}. */
    public static void writePack(Path dataDir, LootConfig config) throws IOException {
        Path pack = dataDir.resolve("pack");
        Path tableFile = pack.resolve("data/voidsmp/loot_table/" + TABLE_PATH + ".json");
        Files.createDirectories(tableFile.getParent());
        Files.writeString(pack.resolve("pack.mcmeta"), mcmetaJson(), StandardCharsets.UTF_8);
        Files.writeString(tableFile, lootJson(config), StandardCharsets.UTF_8);
    }

    static String mcmetaJson() {
        // Ровно как ванильный pack.mcmeta Minecraft 26.2, только description свой
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "VoidSMP — лут обычных хранилищ");
        pack.addProperty("pack_format", 107);
        JsonArray min = new JsonArray();
        min.add(107);
        min.add(0);
        JsonArray max = new JsonArray();
        max.add(107);
        max.add(1);
        pack.add("min_format", min);
        pack.add("max_format", max);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return gson().toJson(root);
    }

    static String lootJson(LootConfig config) {
        // 1) Разворачиваем group в отдельные предметы и масштабируем веса,
        //    чтобы вес каждого конкретного предмета был целым числом
        //    (вес группы делится между предметами поровну).
        record Concrete(String material, String potion, int count, int weight) {
        }
        int scale = 1;
        for (LootConfig.Entry e : config.entries) {
            if (e.isGroup()) {
                scale = lcm(scale, e.group().size());
            }
        }
        List<Concrete> concrete = new ArrayList<>();
        for (LootConfig.Entry e : config.entries) {
            if (e.isGroup()) {
                int perItem = e.weight() * scale / e.group().size();
                for (String material : e.group()) {
                    concrete.add(new Concrete(material, null, 1, perItem));
                }
            } else {
                concrete.add(new Concrete(e.material(), e.potion(), e.count(), e.weight() * scale));
            }
        }

        // 2) Собираем JSON лут-таблицы (синтаксис 26.2)
        JsonArray entries = new JsonArray();
        for (Concrete c : concrete) {
            if (c.weight() <= 0) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", "minecraft:" + c.material().toLowerCase(Locale.ROOT));
            entry.addProperty("weight", c.weight());

            JsonArray functions = new JsonArray();
            if (c.count() != 1) {
                JsonObject setCount = new JsonObject();
                setCount.addProperty("function", "minecraft:set_count");
                setCount.addProperty("count", c.count());
                functions.add(setCount);
            }
            if (c.potion() != null) {
                JsonObject setPotion = new JsonObject();
                setPotion.addProperty("function", "minecraft:set_potion");
                setPotion.addProperty("id", potionId(c.potion()));
                functions.add(setPotion);
            }
            if (functions.size() > 0) {
                entry.add("functions", functions);
            }
            entries.add(entry);
        }

        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", config.rolls);
        pool.add("entries", entries);
        JsonArray pools = new JsonArray();
        pools.add(pool);

        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:chest");
        root.add("pools", pools);
        return gson().toJson(root);
    }

    /** healing/harming/poison и произвольные id зелий → minecraft:... */
    static String potionId(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.contains(":")) {
            return v;
        }
        return switch (v) {
            case "heal", "healing", "instant_heal", "instant_healing" -> "minecraft:healing";
            case "harm", "harming", "instant_damage" -> "minecraft:harming";
            default -> "minecraft:" + v;
        };
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private static int lcm(int a, int b) {
        return a / gcd(a, b) * b;
    }

    private static Gson gson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }
}
