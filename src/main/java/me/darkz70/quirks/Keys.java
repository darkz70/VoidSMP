package me.darkz70.quirks;

import org.bukkit.NamespacedKey;

/** PDC-ключи плагина. */
public final class Keys {

    public static NamespacedKey lockedItem;      // пометка барьеров/бедрока
    public static NamespacedKey effWeakness;     // слабость Инженера применена нами
    public static NamespacedKey effSpeed;        // скорость Бедрока
    public static NamespacedKey effRegen;        // регенерация Бедрока
    public static NamespacedKey sculkHp;         // +2 HP Скалка (модификатор атрибута)
    public static NamespacedKey shardQuirk;      // осколок души: id причуды
    public static NamespacedKey shardLevel;      // осколок души: уровень
    public static NamespacedKey soulMark;        // пометка аллая-души в лабиринте

    private Keys() {}

    public static void init(VoidQuirksPlugin plugin) {
        lockedItem = new NamespacedKey(plugin, "locked_item");
        effWeakness = new NamespacedKey(plugin, "eff_weakness");
        effSpeed = new NamespacedKey(plugin, "eff_speed");
        effRegen = new NamespacedKey(plugin, "eff_regen");
        sculkHp = new NamespacedKey(plugin, "sculk_hp");
        shardQuirk = new NamespacedKey(plugin, "shard_quirk");
        shardLevel = new NamespacedKey(plugin, "shard_level");
        soulMark = new NamespacedKey(plugin, "labyrinth_soul");
    }
}
