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
    /** Техническая причуда 1 уровня: доступ ко всем крафтам плагина.
     *  Не считается в списках, не снимается «все снять»/зельями. */
    ADMIN("admin", "Админ"),
    ADMIN_PRO("admin_pro", "АдминПро");

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
