package me.darkz70.quirks.arcana;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

/**
 * #99: 12 стихий НОВОЙ системы магии (причуда ADMIN_MAG). Отдельно и независимо
 * от старой {@link me.darkz70.quirks.magic.Element} (6 стихий, причуда MAGE) —
 * старая система не трогается вообще.
 */
public enum Arcana {

    WATER("water", "Вода", "Водяной", "💧", Material.PRISMARINE_SHARD,
            Particle.DRIPPING_WATER, null, Sound.BLOCK_WATER_AMBIENT, Sound.ENTITY_PLAYER_SPLASH,
            PotionEffectType.DOLPHINS_GRACE, PotionEffectType.WATER_BREATHING),
    EARTH("earth", "Земля", "Земляной", "🪨", Material.DIRT,
            Particle.DUST, Color.fromRGB(110, 80, 40), Sound.BLOCK_GRAVEL_BREAK, Sound.BLOCK_GRAVEL_STEP,
            PotionEffectType.RESISTANCE, PotionEffectType.HASTE),
    FIRE("fire", "Огонь", "Огненный", "🔥", Material.BLAZE_POWDER,
            Particle.FLAME, null, Sound.ENTITY_BLAZE_SHOOT, Sound.BLOCK_FIRE_AMBIENT,
            PotionEffectType.FIRE_RESISTANCE, PotionEffectType.STRENGTH),
    WIND("wind", "Ветер", "Ветряной", "🌪", Material.FEATHER,
            Particle.CLOUD, null, Sound.ITEM_ELYTRA_FLYING, Sound.ENTITY_ENDER_DRAGON_FLAP,
            PotionEffectType.SPEED, PotionEffectType.JUMP_BOOST),
    ICE("ice", "Лёд", "Ледяной", "❄", Material.ICE,
            Particle.DUST, Color.fromRGB(190, 225, 255), Sound.BLOCK_WATER_AMBIENT, Sound.ENTITY_GUARDIAN_ATTACK,
            PotionEffectType.RESISTANCE, PotionEffectType.SLOWNESS),
    STONE("stone", "Камень", "Каменный", "⛰", Material.STONE,
            Particle.CRIT, null, Sound.BLOCK_STONE_BREAK, Sound.BLOCK_BASALT_BREAK,
            PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE),
    BLOOD("blood", "Кровь", "Кровавый", "🩸", Material.REDSTONE,
            Particle.DUST, Color.fromRGB(150, 0, 0), Sound.ENTITY_PLAYER_ATTACK_SWEEP, Sound.ENTITY_WITHER_AMBIENT,
            PotionEffectType.STRENGTH, PotionEffectType.SATURATION),
    SOUL("soul", "Душа", "Призрачный", "👻", Material.SOUL_LANTERN,
            Particle.SOUL, null, Sound.ENTITY_WARDEN_HEARTBEAT, Sound.PARTICLE_SOUL_ESCAPE,
            PotionEffectType.INVISIBILITY, PotionEffectType.NIGHT_VISION),
    PLANTS("plants", "Растения", "Растительный", "🌿", Material.OAK_SAPLING,
            Particle.COMPOSTER, null, Sound.ITEM_NETHER_WART_PLANT, Sound.BLOCK_GRAVEL_STEP,
            PotionEffectType.REGENERATION, PotionEffectType.HASTE),
    ANIMALS("animals", "Животные", "Звериный", "🐾", Material.BONE,
            Particle.DUST, Color.fromRGB(200, 170, 110), Sound.ENTITY_IRON_GOLEM_ATTACK, Sound.ENTITY_IRON_GOLEM_REPAIR,
            PotionEffectType.JUMP_BOOST, PotionEffectType.SPEED),
    LIGHT("light", "Свет", "Световой", "✨", Material.GLOWSTONE,
            Particle.END_ROD, null, Sound.BLOCK_BEACON_AMBIENT, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
            PotionEffectType.GLOWING, PotionEffectType.REGENERATION),
    DARK("dark", "Тьма", "Тёмный", "🌑", Material.SCULK,
            Particle.SCULK_SOUL, null, Sound.ENTITY_WARDEN_ROAR, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK,
            PotionEffectType.BLINDNESS, PotionEffectType.DARKNESS);

    private final String id;
    private final String display;
    private final String adjective;
    private final String emoji;
    private final Material icon;
    private final Particle particle;
    @Nullable private final Color color;
    private final Sound sound1;
    private final Sound sound2;
    /** Положительный баф (эффект способностей 4-5 уровня и ультимейта). */
    private final PotionEffectType buffA;
    private final PotionEffectType buffB;

    Arcana(String id, String display, String adjective, String emoji, Material icon,
            Particle particle, @Nullable Color color, Sound sound1, Sound sound2,
            PotionEffectType buffA, PotionEffectType buffB) {
        this.id = id;
        this.display = display;
        this.adjective = adjective;
        this.emoji = emoji;
        this.icon = icon;
        this.particle = particle;
        this.color = color;
        this.sound1 = sound1;
        this.sound2 = sound2;
        this.buffA = buffA;
        this.buffB = buffB;
    }

    public String id() { return id; }
    public String display() { return display; }
    /** Прилагательное для имён заклинаний: «Огненный снаряд», «Водяной щит» и т.д. */
    public String adjective() { return adjective; }
    public String emoji() { return emoji; }
    public Material icon() { return icon; }
    public Particle particle() { return particle; }
    @Nullable public Color color() { return color; }
    public Sound sound1() { return sound1; }
    public Sound sound2() { return sound2; }
    public PotionEffectType buffA() { return buffA; }
    public PotionEffectType buffB() { return buffB; }

    @Nullable
    public static Arcana byId(String id) {
        if (id == null) return null;
        for (Arcana a : values()) {
            if (a.id.equalsIgnoreCase(id)) return a;
        }
        return null;
    }

    @Nullable
    public static Arcana byName(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase();
        for (Arcana a : values()) {
            if (a.id.equals(s) || a.display.equalsIgnoreCase(s)) return a;
        }
        return null;
    }
}
