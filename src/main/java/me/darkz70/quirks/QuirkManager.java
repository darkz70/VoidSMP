package me.darkz70.quirks;

import me.darkz70.quirks.mechanic.BedrockLogic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

/** Назначение/снятие причуд, применение пассивок. */
public final class QuirkManager {

    private final VoidQuirksPlugin plugin;
    private final QuirkStorage storage;

    public QuirkManager(VoidQuirksPlugin plugin, QuirkStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public QuirkStorage storage() {
        return storage;
    }

    @Nullable
    public PlayerData data(Player player) {
        return storage.get(player.getUniqueId());
    }

    /** Назначает причуду (уровень 1-3). Старую корректно снимает. */
    public void assign(Player player, Quirk quirk, int level) {
        PlayerData old = storage.get(player.getUniqueId());
        if (old != null) {
            unapply(player, old);
        }
        PlayerData data = new PlayerData(quirk, Math.max(1, Math.min(3, level)));
        storage.put(player.getUniqueId(), data);
        apply(player, data);
        storage.save();
    }

    /** Полностью снимает причуду. */
    public boolean remove(Player player) {
        PlayerData old = storage.get(player.getUniqueId());
        if (old == null) return false;
        unapply(player, old);
        storage.remove(player.getUniqueId());
        storage.save();
        return true;
    }

    /** Применить все эффекты причуды (при назначении/заходе/респауне). */
    public void apply(Player player, PlayerData data) {
        switch (data.quirk()) {
            case ENGINEER -> {
                if (data.level() == 1) {
                    applyFlaggedEffect(player, PotionEffectType.WEAKNESS, 0, Keys.effWeakness);
                }
            }
            case BEDROCK -> {
                BedrockLogic.applyLayout(plugin, player, data.level());
                if (data.level() == 3) {
                    applyFlaggedEffect(player, PotionEffectType.SPEED, 0, Keys.effSpeed);
                    applyFlaggedEffect(player, PotionEffectType.REGENERATION, 0, Keys.effRegen);
                }
            }
            case SCULK -> {
                if (data.level() == 3) ensureSculkHp(player);
            }
            default -> { /* кот и топор не имеют пассивных эффектов при выдаче */ }
        }
    }

    /** Снять всё, что мы наложили (любую причуду — безопасно). */
    public void unapply(Player player, PlayerData old) {
        removeFlaggedEffect(player, PotionEffectType.WEAKNESS, Keys.effWeakness);
        removeFlaggedEffect(player, PotionEffectType.SPEED, Keys.effSpeed);
        removeFlaggedEffect(player, PotionEffectType.REGENERATION, Keys.effRegen);
        removeSculkHp(player);
        if (old.quirk() == Quirk.BEDROCK) {
            BedrockLogic.clearLayout(player);
        }
    }

    /** Периодическая гарантия пассивок (защита от молока и т.п.). Вызывается из EffectsTask. */
    public void ensurePassives(Player player, PlayerData data) {
        switch (data.quirk()) {
            case ENGINEER -> {
                if (data.level() == 1 && !player.hasPotionEffect(PotionEffectType.WEAKNESS)) {
                    applyFlaggedEffect(player, PotionEffectType.WEAKNESS, 0, Keys.effWeakness);
                }
            }
            case BEDROCK -> {
                if (data.level() == 3) {
                    if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
                        applyFlaggedEffect(player, PotionEffectType.SPEED, 0, Keys.effSpeed);
                    }
                    if (!player.hasPotionEffect(PotionEffectType.REGENERATION)) {
                        applyFlaggedEffect(player, PotionEffectType.REGENERATION, 0, Keys.effRegen);
                    }
                }
            }
            case SCULK -> {
                if (data.level() == 3) ensureSculkHp(player);
            }
            default -> { }
        }
    }

    private void applyFlaggedEffect(Player player, PotionEffectType type, int amplifier, org.bukkit.NamespacedKey flag) {
        player.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, true, false, true));
        player.getPersistentDataContainer().set(flag, PersistentDataType.BYTE, (byte) 1);
    }

    private void removeFlaggedEffect(Player player, PotionEffectType type, org.bukkit.NamespacedKey flag) {
        if (player.getPersistentDataContainer().has(flag)) {
            player.removePotionEffect(type);
            player.getPersistentDataContainer().remove(flag);
        }
    }

    private void ensureSculkHp(Player player) {
        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        if (inst.getModifier(Keys.sculkHp) == null) {
            inst.addModifier(new AttributeModifier(Keys.sculkHp, 2.0, AttributeModifier.Operation.ADD_NUMBER));
            player.setHealth(Math.min(inst.getValue(), player.getHealth() + 2.0));
        }
    }

    private void removeSculkHp(Player player) {
        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        if (inst.getModifier(Keys.sculkHp) != null) {
            inst.removeModifier(Keys.sculkHp);
            if (player.getHealth() > inst.getValue()) {
                player.setHealth(Math.max(1.0, inst.getValue()));
            }
        }
    }
}
