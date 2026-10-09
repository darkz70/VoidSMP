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
        if (data.isEmpty() && data.magicElement() == null && data.tmpQuirks() == null) {
            storage.remove(player.getUniqueId());
        } else {
            storage.save();
        }
        return true;
    }

    /** Снимает ВСЕ причуды. */
    public boolean removeAll(Player player) {
        PlayerData data = storage.get(player.getUniqueId());
        if (data == null || data.isEmpty()) {
            if (data != null && data.magicElement() == null && data.tmpQuirks() == null) {
                storage.remove(player.getUniqueId());
                return true;
            }
            return false;
        }
        java.util.List<Quirk> held = new java.util.ArrayList<>();
        for (Map.Entry<Quirk, Integer> entry : data.entries()) {
            if (entry.getKey() == Quirk.ADMIN) continue; // техпричуду «все снять» не трогает
            held.add(entry.getKey());
        }
        for (Quirk quirk : held) {
            unapplyQuirk(player, quirk);
            data.remove(quirk);
        }
        if (data.magicElement() == null && data.tmpQuirks() == null) {
            storage.remove(player.getUniqueId());
        } else {
            storage.save();
        }
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
                player.setMaximumAir(plugin.getConfig().getInt("bedrock.max-air", 150));
                if (level == 3) {
                    applyFlaggedEffect(player, PotionEffectType.SPEED, 0, Keys.effSpeed);
                    applyFlaggedEffect(player, PotionEffectType.REGENERATION, 0, Keys.effRegen);
                }
            }
            case SCULK -> {
                if (level == 3) ensureSculkHp(player);
            }
            case SPIDER -> {
                if (level == 1) ensureSpiderHp(player);
            }
            case FARMER -> {
                // рецепт супер-удобрения виден только Фермеру
                me.darkz70.quirks.listener.CraftListener.syncFertilizerRecipe(player, true);
                if (level >= 2) {
                    applyFlaggedEffect(player, PotionEffectType.HERO_OF_THE_VILLAGE, 0, Keys.effHero);
                }
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
                player.setMaximumAir(300);
            }
            case SCULK -> removeSculkHp(player);
            case SPIDER -> removeSpiderHp(player);
            case FARMER -> {
                removeFlaggedEffect(player, PotionEffectType.HERO_OF_THE_VILLAGE, Keys.effHero);
                me.darkz70.quirks.listener.CraftListener.syncFertilizerRecipe(player, false);
            }
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
                    int air = plugin.getConfig().getInt("bedrock.max-air", 150);
                    if (player.getMaximumAir() != air) player.setMaximumAir(air);
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
                case SPIDER -> {
                    if (entry.getValue() == 1) ensureSpiderHp(player);
                }
                case FARMER -> {
                    if (entry.getValue() >= 2 && !player.hasPotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE)) {
                        applyFlaggedEffect(player, PotionEffectType.HERO_OF_THE_VILLAGE, 0, Keys.effHero);
                    }
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

    /** Паук 1 ур.: максимум здоровья обрезан (по умолчанию до 18 HP = 9 сердец). */
    private void ensureSpiderHp(Player player) {
        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        double target = plugin.getConfig().getDouble("spider.max-health-1", 18.0);
        double amount = target - inst.getDefaultValue();
        AttributeModifier existing = inst.getModifier(Keys.spiderHp);
        if (existing == null && Math.abs(amount) > 0.001) {
            inst.addModifier(new AttributeModifier(Keys.spiderHp, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (player.getHealth() > inst.getValue()) {
            player.setHealth(Math.max(1.0, inst.getValue()));
        }
    }

    private void removeSpiderHp(Player player) {
        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        if (inst.getModifier(Keys.spiderHp) != null) {
            inst.removeModifier(Keys.spiderHp);
        }
    }
}
