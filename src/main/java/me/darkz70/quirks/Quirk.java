package me.darkz70.quirks;

import org.jetbrains.annotations.Nullable;

/** Причуды расколотых душ. */
public enum Quirk {

    ENGINEER("engineer", "Инженер"),
    CAT("cat", "Кот"),
    BEDROCK("bedrock", "Бедрок"),
    AXE("axe", "Топор"),
    SCULK("sculk", "Скалк");

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
