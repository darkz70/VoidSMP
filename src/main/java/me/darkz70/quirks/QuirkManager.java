package me.darkz70.quirks;

import java.util.Map;
import me.darkz70.quirks.mechanic.BedrockLogic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

/** Назначение/снятие причуд (поддерживается НЕСКОЛЬКО причуд на игрока), пассивки. */
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

    /** Уровень причуды у игрока; 0 — нет такой. */
    public int levelOf(Player player, Quirk quirk) {
        PlayerData data = storage.get(player.getUniqueId());
        return data == null ? 0 : data.levelOf(quirk);
    }

    /** Добавляет причуду в набор игрока (или меняет её уровень). */
    public void assign(Player player, Quirk quirk, int level) {
        PlayerData data = storage.get(player.getUniqueId());
        if (data == null) {
            data = new PlayerData();
        } else if (data.has(quirk)) {
            unapplyQuirk(player, quirk); // снять эффекты старого уровня
        }
        data.put(quirk, level);
        storage.put(player.getUniqueId(), data);
        applyQuirk(player, quirk, level);
        storage.save();
    }

    /** Снимает ОДНУ причуду из набора. */
    public boolean remove(Player player, Quirk quirk) {
        PlayerData data = storage.get(player.getUniqueId());
        if (data == null || !data.has(quirk)) return false;
        unapplyQuirk(player, quirk);
        data.remove(quirk);
        if (data.isEmpty()) {
            storage.remove(player.getUniqueId());
        } else {
            storage.save();
        }
        return true;
    }

    /** Снимает ВСЕ причуды. */
    public boolean removeAll(Player player) {
        PlayerData data = storage.get(player.getUniqueId());
        if (data == null || data.isEmpty()) return false;
        for (Map.Entry<Quirk, Integer> entry : data.entries()) {
            unapplyQuirk(player, entry.getKey());
        }
        storage.remove(player.getUniqueId());
        return true;
    }

    /** Применить эффекты всего набора (заход/респаун). */
    public void applyAll(Player player, PlayerData data) {
        for (Map.Entry<Quirk, Integer> entry : data.entries()) {
            applyQuirk(player, entry.getKey(), entry.getValue());
        }
    }

    private void applyQuirk(Player player, Quirk quirk, int level) {
        switch (quirk) {
            case ENGINEER -> {
                if (level == 1) {
                    applyFlaggedEffect(player, PotionEffectType.WEAKNESS, 0, Keys.effWeakness);
                }
            }
            case BEDROCK -> {
                BedrockLogic.applyLayout(plugin, player, level);
                if (level == 3) {
                    applyFlaggedEffect(player, PotionEffectType.SPEED, 0, Keys.effSpeed);
                    applyFlaggedEffect(player, PotionEffectType.REGENERATION, 0, Keys.effRegen);
                }
            }
            case SCULK -> {
                if (level == 3) ensureSculkHp(player);
            }
            default -> { /* кот и топор не имеют пассивок при выдаче */ }
        }
    }

    /** Снять всё, что наложила конкретная причуда. */
    private void unapplyQuirk(Player player, Quirk quirk) {
        switch (quirk) {
            case ENGINEER -> removeFlaggedEffect(player, PotionEffectType.WEAKNESS, Keys.effWeakness);
            case BEDROCK -> {
                removeFlaggedEffect(player, PotionEffectType.SPEED, Keys.effSpeed);
                removeFlaggedEffect(player, PotionEffectType.REGENERATION, Keys.effRegen);
                BedrockLogic.clearLayout(player);
            }
            case SCULK -> removeSculkHp(player);
            default -> { }
        }
    }

    /** Периодическая гарантия пассивок (защита от молока). Вызывается из EffectsTask. */
    public void ensurePassives(Player player, PlayerData data) {
        for (Map.Entry<Quirk, Integer> entry : data.entries()) {
            switch (entry.getKey()) {
                case ENGINEER -> {
                    if (entry.getValue() == 1 && !player.hasPotionEffect(PotionEffectType.WEAKNESS)) {
                        applyFlaggedEffect(player, PotionEffectType.WEAKNESS, 0, Keys.effWeakness);
                    }
                }
                case BEDROCK -> {
                    if (entry.getValue() == 3) {
                        if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
                            applyFlaggedEffect(player, PotionEffectType.SPEED, 0, Keys.effSpeed);
                        }
                        if (!player.hasPotionEffect(PotionEffectType.REGENERATION)) {
                            applyFlaggedEffect(player, PotionEffectType.REGENERATION, 0, Keys.effRegen);
                        }
                    }
                }
                case SCULK -> {
                    if (entry.getValue() == 3) ensureSculkHp(player);
                }
                default -> { }
            }
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
