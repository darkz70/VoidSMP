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
    public static NamespacedKey brewMark;        // зелья/предметы крафтов: neutral / antidote / fertilizer
    public static NamespacedKey brewLevel;       // самогон: уровень; усиленное зелье: пометка
    public static NamespacedKey brewTarget;      // зелье отключения: id целевой причуды
    public static NamespacedKey focusKey;        // предмет с фокусировкой (можно колдовать)
    public static NamespacedKey spellDmg;        // снаряд заклинания: базовый урон
    public static NamespacedKey spellElem;       // снаряд заклинания: стихия
    public static NamespacedKey spellExtra;      // снаряд заклинания: бит-флаги эффектов
    public static NamespacedKey noLoot;          // призванный моб: без дропа
    public static NamespacedKey qpHp;            // временный −2 макс. HP от зелья причуды
    public static NamespacedKey qpPerm;          // перманентный −1 сердце от смерти зелья причуды
    public static NamespacedKey divineHp;        // временный −1 сердце от зелья божества (7 дней)
    public static NamespacedKey spiderHp;        // -2 HP Паука 1 ур. (модификатор атрибута)
    public static NamespacedKey effHero;         // герой деревни Фермера применён нами

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
        brewMark = new NamespacedKey(plugin, "brew_mark");
        brewLevel = new NamespacedKey(plugin, "brew_level");
        brewTarget = new NamespacedKey(plugin, "brew_target");
        focusKey = new NamespacedKey(plugin, "focus_item");
        spellDmg = new NamespacedKey(plugin, "spell_dmg");
        spellElem = new NamespacedKey(plugin, "spell_elem");
        spellExtra = new NamespacedKey(plugin, "spell_extra");
        noLoot = new NamespacedKey(plugin, "summon_no_loot");
        qpHp = new NamespacedKey(plugin, "qp_hp");
        qpPerm = new NamespacedKey(plugin, "qp_perm");
        divineHp = new NamespacedKey(plugin, "divine_hp");
        spiderHp = new NamespacedKey(plugin, "spider_hp");
        effHero = new NamespacedKey(plugin, "eff_hero");
    }
}
