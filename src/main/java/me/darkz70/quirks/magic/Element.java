package me.darkz70.quirks.magic;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

/** Шесть стихий магии VoidSMP. */
public enum Element {

    FIRE("fire", "Огонь", "🔥", Material.MAGMA_CREAM),
    WATER("water", "Вода", "💧", Material.PRISMARINE_CRYSTALS),
    WIND("wind", "Ветер", "🌪", Material.PHANTOM_MEMBRANE),
    EARTH("earth", "Земля", "🪨", Material.COBBLED_DEEPSLATE),
    DARK("dark", "Тьма", "🌑", Material.SCULK),
    LIGHT("light", "Свет", "✨", Material.GLOWSTONE);

    private final String id;
    private final String display;
    private final String emoji;
    private final Material catalyst;

    Element(String id, String display, String emoji, Material catalyst) {
        this.id = id;
        this.display = display;
        this.emoji = emoji;
        this.catalyst = catalyst;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public String emoji() {
        return emoji;
    }

    /** Катализатор для крафта книги стихии; у тьмы и света — ещё и условие изучения. */
    public Material catalyst() {
        return catalyst;
    }

    /** Свет и тьма требуют 4 книги других стихий при изучении. */
    public boolean requiresFourBooks() {
        return this == DARK || this == LIGHT;
    }

    @Nullable
    public static Element byId(String id) {
        if (id == null) return null;
        for (Element element : values()) {
            if (element.id.equalsIgnoreCase(id)) return element;
        }
        return null;
    }

    @Nullable
    public static Element byName(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase();
        for (Element element : values()) {
            if (element.id.equals(s) || element.display.toLowerCase().equals(s)) return element;
        }
        return null;
    }
}
