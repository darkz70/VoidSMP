package me.darkz70.quirks;

import org.jetbrains.annotations.Nullable;

/** Причуды расколотых душ. */
public enum Quirk {

    ENGINEER("engineer", "Инженер"),
    CAT("cat", "Кот"),
    BEDROCK("bedrock", "Бедрок"),
    AXE("axe", "Топор"),
    SCULK("sculk", "Скалк"),
    FARMER("farmer", "Фермер"),
    AMPHIBIAN("amphibian", "Земноводный"),
    SPIDER("spider", "Паук"),
    SCULK_LEADER("sculk_leader", "Глава скалка"),
    /** Отдельная доп. причуда без уровней: открывает доступ к магии.
     *  С #99 (по просьбе) MAGE открывает НОВУЮ систему магии Arcana (12 стихий,
     *  посох Арканы + фолианты стихий, Shift+Q/Q/F/Shift+F) — это магия для
     *  обычных игроков. Старая 6-стихийная система (Element/MagicSystem, меню
     *  /mage, фолианты «elem-book-») переехала на техническую причуду ADMIN_MAG
     *  и выдаётся только через /gaid — теперь это админский/тестовый вариант.
     *  Игрок может иметь обе причуды сразу — системы по-прежнему независимы. */
    MAGE("mage", "Маг"),
    /** Техническая причуда 1 уровня: доступ ко всем крафтам плагина.
     *  Не считается в списках, не снимается «все снять»/зельями. */
    ADMIN("admin", "Админ"),
    ADMIN_PRO("admin_pro", "АдминПро"),
    /** Техническая причуда (#99, выдаётся только через /gaid или /quirk set):
     *  доступ к СТАРОЙ 6-стихийной системе магии (Element/MagicSystem, меню /mage,
     *  фолианты «elem-book-», старый фокус-кристалл). НОВАЯ система магии Arcana
     *  (12 стихий, посох Арканы) теперь на причуде MAGE — доступна обычным игрокам.
     *  Обе системы полностью независимы: разные предметы, разные поля PlayerData,
     *  разные слушатели (ArcanaListener vs. MagicListener). */
    ADMIN_MAG("admin_mag", "АдминМаг");

    private final String id;
    private final String display;

    Quirk(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    /** Ищет причуду по id на английском или русском. */
    @Nullable
    public static Quirk byName(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase();
        for (Quirk q : values()) {
            if (q.id.equals(s) || q.display.toLowerCase().equals(s)) return q;
        }
        return null;
    }
}
