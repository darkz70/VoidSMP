package me.darkz70.quirks;

import org.bukkit.NamespacedKey;

/** PDC-ключи плагина. */
public final class Keys {

    public static NamespacedKey lockedItem;      // пометка барьеров/бедрока
    public static NamespacedKey effWeakness;     // слабость Инженера применена нами
    public static NamespacedKey effSpeed;        // скорость Бедрока
    public static NamespacedKey effRegen;
    public static NamespacedKey effNightVision;        // регенерация Бедрока
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
    public static NamespacedKey reach;           // инженер: модификатор дальности блока
    public static NamespacedKey bedrockKbr;      // бедрок: сопротивление нокбэку (4 ур.)
    public static NamespacedKey bedrockArmor;    // бедрок: +броня (5 ур.)
    public static NamespacedKey engineerDamage;  // инженер: механическая рука (+урон)
    public static NamespacedKey zkRole;          // зайцекрол: роль (farmer/guard/elite/resident/tsar)
    public static NamespacedKey zkHome;          // зайцекрол: точка появления "x,y,z"
    public static NamespacedKey zkGen;           // зайцекрол-житель: поколение (с 60)

    private Keys() {}

    public static void init(VoidQuirksPlugin plugin) {
        lockedItem = new NamespacedKey(plugin, "locked_item");
        effWeakness = new NamespacedKey(plugin, "eff_weakness");
        effSpeed = new NamespacedKey(plugin, "eff_speed");
        effRegen = new NamespacedKey(plugin, "eff_regen");
        effNightVision = new NamespacedKey(plugin, "eff_night_vision");
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
        reach = new NamespacedKey(plugin, "engineer_reach");
        bedrockKbr = new NamespacedKey(plugin, "bedrock_kbr");
        bedrockArmor = new NamespacedKey(plugin, "bedrock_armor");
        engineerDamage = new NamespacedKey(plugin, "engineer_damage");
        zkRole = new NamespacedKey(plugin, "zk_role");
        zkHome = new NamespacedKey(plugin, "zk_home");
        zkGen = new NamespacedKey(plugin, "zk_gen");
    }
}
