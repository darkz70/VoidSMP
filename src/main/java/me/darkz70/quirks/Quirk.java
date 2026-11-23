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
     *  Даёт меню прямого выбора стихии (/mage, без крафта и без ограничения
     *  в 4 книги для тьмы/света), а крафт фолиантов стихий (CraftListener)
     *  тоже требует эту причуду — так магия ограничена только теми, кому её
     *  выдали, а не доступна всем новичкам по умолчанию. Сама механика магии
     *  (мана/заклинания/уровни/прокачка) не меняется — только доступ к ней. */
    MAGE("mage", "Маг"),
    /** Техническая причуда 1 уровня: доступ ко всем крафтам плагина.
     *  Не считается в списках, не снимается «все снять»/зельями. */
    ADMIN("admin", "Админ"),
    ADMIN_PRO("admin_pro", "АдминПро"),
    /** Техническая причуда (#99): доступ к НОВОЙ системе магии (12 стихий, Arcana).
     *  Старая система (Element/MagicSystem, 6 стихий) остаётся на причуде MAGE без
     *  изменений — обе системы полностью независимы: разные предметы (Посох Арканы
     *  vs. старый фокус-кристалл), разные поля PlayerData, разные слушатели
     *  (ArcanaListener vs. MagicListener). Игрок может иметь обе причуды сразу. */
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
