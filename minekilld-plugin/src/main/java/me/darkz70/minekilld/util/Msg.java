package me.darkz70.minekilld.util;

import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.potion.PotionEffectType;

public final class Msg {

    private static final Map<String, String> EFFECT_NAMES = Map.ofEntries(
            Map.entry("speed", "Скорость"),
            Map.entry("slowness", "Замедление"),
            Map.entry("haste", "Спешка"),
            Map.entry("mining_fatigue", "Усталость"),
            Map.entry("strength", "Сила"),
            Map.entry("instant_health", "Мгновенное лечение"),
            Map.entry("instant_damage", "Мгновенный урон"),
            Map.entry("jump_boost", "Прыгучесть"),
            Map.entry("nausea", "Тошнота"),
            Map.entry("regeneration", "Регенерация"),
            Map.entry("resistance", "Сопротивление"),
            Map.entry("fire_resistance", "Огнестойкость"),
            Map.entry("water_breathing", "Подводное дыхание"),
            Map.entry("invisibility", "Невидимость"),
            Map.entry("blindness", "Слепота"),
            Map.entry("night_vision", "Ночное зрение"),
            Map.entry("hunger", "Голод"),
            Map.entry("weakness", "Слабость"),
            Map.entry("poison", "Отравление"),
            Map.entry("wither", "Иссушение"),
            Map.entry("health_boost", "Прилив здоровья"),
            Map.entry("absorption", "Поглощение"),
            Map.entry("saturation", "Насыщение"),
            Map.entry("glowing", "Свечение"),
            Map.entry("levitation", "Левитация"),
            Map.entry("luck", "Везение"),
            Map.entry("unluck", "Невезение"),
            Map.entry("slow_falling", "Плавное падение"),
            Map.entry("conduit_power", "Сила источника"),
            Map.entry("dolphins_grace", "Грация дельфина"),
            Map.entry("bad_omen", "Дурное знамение"),
            Map.entry("hero_of_the_village", "Герой деревни"),
            Map.entry("darkness", "Тьма"),
            Map.entry("trial_omen", "Знамение испытаний"),
            Map.entry("raid_omen", "Знамение набега"),
            Map.entry("wind_charged", "Заряд ветра"),
            Map.entry("weaving", "Плетение"),
            Map.entry("oozing", "Слизь"),
            Map.entry("infested", "Заражение"),
            Map.entry("breath_of_the_nautilus", "Дыхание наутилуса")
    );

    private Msg() {}

    public static Component color(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
    }

    public static String effectName(PotionEffectType type) {
        String key = type.getKey().getKey().toLowerCase(Locale.ROOT);
        String ru = EFFECT_NAMES.get(key);
        if (ru != null) return ru;
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(level);
        };
    }
}
