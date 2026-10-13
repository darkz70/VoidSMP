package me.darkz70.quirks.mob;

import me.darkz70.quirks.Keys;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Rabbit;
import org.bukkit.persistence.PersistentDataType;

/**
 * Зайцекрол — особый ушастик по заказу: рост как 1,5 кролика (царь — 1×).
 * Роль, точка дома и поколение хранятся в PDC кролика.
 */
public final class ZayceKrol {

    /** Роль зайцекрола. */
    public enum Role {
        FARMER("Фермер"), GUARD("Охранник"), ELITE("Элитный охранник"),
        RESIDENT("Житель"), TSAR("Царь");

        public final String display;

        Role(String display) {
            this.display = display;
        }
    }

    private ZayceKrol() {}

    public static boolean isZayce(Rabbit rabbit) {
        return rabbit.getPersistentDataContainer().has(Keys.zkRole, PersistentDataType.STRING);
    }

    public static Role roleOf(Rabbit rabbit) {
        String role = rabbit.getPersistentDataContainer().get(Keys.zkRole, PersistentDataType.STRING);
        if (role == null) return Role.RESIDENT;
        try {
            return Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Role.RESIDENT;
        }
    }

    public static Location homeOf(Rabbit rabbit) {
        String raw = rabbit.getPersistentDataContainer().get(Keys.zkHome, PersistentDataType.STRING);
        if (raw == null) return rabbit.getLocation();
        try {
            String[] parts = raw.split(",");
            return new Location(rabbit.getWorld(),
                    Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        } catch (RuntimeException ex) {
            return rabbit.getLocation();
        }
    }

    public static int genOf(Rabbit rabbit) {
        Integer gen = rabbit.getPersistentDataContainer().get(Keys.zkGen, PersistentDataType.INTEGER);
        return gen != null ? gen : 60;
    }

    public static void genInc(Rabbit rabbit) {
        rabbit.getPersistentDataContainer().set(Keys.zkGen, PersistentDataType.INTEGER, genOf(rabbit) + 1);
    }

    /** Превращает кролика в зайцекрола с ролью (дом = текущая позиция). */
    public static void convert(Rabbit rabbit, Role role) {
        rabbit.getPersistentDataContainer().set(Keys.zkRole, PersistentDataType.STRING, role.name().toLowerCase());
        Location loc = rabbit.getLocation();
        rabbit.getPersistentDataContainer().set(Keys.zkHome, PersistentDataType.STRING,
                loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
        if (role == Role.RESIDENT) {
            rabbit.getPersistentDataContainer().set(Keys.zkGen, PersistentDataType.INTEGER, 60);
        }
        rabbit.customName(Msg.color("&eЗайцекрол &7· &f" + role.display));
        rabbit.setCustomNameVisible(true);
        rabbit.setRemoveWhenFarAway(false);
        // рост 1,5× (у царя — обычный кроличий)
        var scaleAttr = rabbit.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(role == Role.TSAR ? 1.0 : 1.5);
        }
        var healthAttr = rabbit.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null && role != Role.TSAR) {
            healthAttr.setBaseValue(switch (role) {
                case ELITE -> 16;
                case GUARD -> 12;
                default -> 6;
            });
            rabbit.setHealth(healthAttr.getBaseValue());
        }
    }
}
