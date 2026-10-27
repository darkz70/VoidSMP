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

    /** Скалк-уровень игрока: максимум из обычного Скалка и Главы скалка. */
    public int sculkOf(Player player) {
        return Math.max(levelOf(player, Quirk.SCULK), levelOf(player, Quirk.SCULK_LEADER));
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
            if (entry.getKey() == Quirk.ADMIN || entry.getKey() == Quirk.ADMIN_PRO) continue; // техпричуды «все снять» не трогает
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
                applyReach(player, engineerReach(level)); // дальность блоков по уровню
                if (level >= 4) applyAttr(player, Keys.engineerDamage,      // механическая рука
                        org.bukkit.attribute.Attribute.ATTACK_DAMAGE, level >= 5 ? 4.0 : 2.0);
            }
            case BEDROCK -> {
                BedrockLogic.applyLayout(plugin, player, level);
                player.setMaximumAir(plugin.getConfig().getInt("bedrock.max-air", 150));
                if (level == 3) {
                    applyFlaggedEffect(player, PotionEffectType.SPEED, 0, Keys.effSpeed);
                    applyFlaggedEffect(player, PotionEffectType.REGENERATION, 0, Keys.effRegen);
                }
                if (level >= 4) applyAttr(player, Keys.bedrockKbr,   // 4 ур.: почти без нокбэка
                        org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE, 0.9);
                if (level >= 5) applyAttr(player, Keys.bedrockArmor, // 5 ур.: +4 брони
                        org.bukkit.attribute.Attribute.ARMOR, 4.0);
            }
            case SCULK -> {
                if (level >= 4) applyFlaggedEffect(player, PotionEffectType.NIGHT_VISION, 0, Keys.effNightVision);
                if (level == 3) ensureSculkHp(player);
            }
            case SCULK_LEADER -> {
                if (level >= 4) applyFlaggedEffect(player, PotionEffectType.NIGHT_VISION, 0, Keys.effNightVision);
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
            case ENGINEER -> {
                removeFlaggedEffect(player, PotionEffectType.WEAKNESS, Keys.effWeakness);
                removeAttr(player, Keys.engineerDamage, org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
                removeReach(player);
            }
            case BEDROCK -> {
                removeFlaggedEffect(player, PotionEffectType.SPEED, Keys.effSpeed);
                removeFlaggedEffect(player, PotionEffectType.REGENERATION, Keys.effRegen);
                removeAttr(player, Keys.bedrockKbr, org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
                removeAttr(player, Keys.bedrockArmor, org.bukkit.attribute.Attribute.ARMOR);
                BedrockLogic.clearLayout(player);
                player.setMaximumAir(300);
            }
            case SCULK -> {
                removeFlaggedEffect(player, PotionEffectType.NIGHT_VISION, Keys.effNightVision);
                removeSculkHp(player);
            }
            case SCULK_LEADER -> {
                removeFlaggedEffect(player, PotionEffectType.NIGHT_VISION, Keys.effNightVision);
                removeSculkHp(player);
            }
            case SPIDER -> removeSpiderHp(player);
            case FARMER -> {
                removeFlaggedEffect(player, PotionEffectType.HERO_OF_THE_VILLAGE, Keys.effHero);
                me.darkz70.quirks.listener.CraftListener.syncFertilizerRecipe(player, false);
            }
            default -> { }
        }
    }

    /** Инженер: ставит/бьёт блоки дальше (модификатор BLOCK_INTERACTION_RANGE). */
    private void applyReach(Player player, int bonus) {
        org.bukkit.attribute.AttributeInstance inst = player.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE);
        if (inst == null) return;
        org.bukkit.attribute.AttributeModifier mod = new org.bukkit.attribute.AttributeModifier(
                Keys.reach, bonus, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER);
        if (!inst.getModifiers().contains(mod)) inst.addModifier(mod);
    }

    /** Дальность блоков инженера по уровню: 1-2 ур +2, 3 +3, 4 +4, 5 +5. */
    private static int engineerReach(int level) {
        return switch (level) {
            case 1, 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            default -> level >= 5 ? 5 : 0;
        };
    }

    /** Общий именованный атрибут-модификатор (идемпотентно). */
    private void applyAttr(Player player, org.bukkit.NamespacedKey key,
                          org.bukkit.attribute.Attribute attribute, double amount) {
        var inst = player.getAttribute(attribute);
        if (inst == null) return;
        var mod = new org.bukkit.attribute.AttributeModifier(key, amount,
                org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER);
        inst.getModifiers().removeIf(m -> m.getKey().equals(key));
        inst.addModifier(mod);
    }

    private void removeAttr(Player player, org.bukkit.NamespacedKey key,
                           org.bukkit.attribute.Attribute attribute) {
        var inst = player.getAttribute(attribute);
        if (inst != null) inst.getModifiers().removeIf(m -> m.getKey().equals(key));
    }

    private void removeReach(Player player) {
        org.bukkit.attribute.AttributeInstance inst = player.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE);
        if (inst == null) return;
        inst.getModifiers().removeIf(m -> m.getKey().equals(Keys.reach));
    }

    /** Периодическая гарантия пассивок (защита от молока). Вызывается из EffectsTask. */
    public void ensurePassives(Player player, PlayerData data) {
        for (Map.Entry<Quirk, Integer> entry : data.entries()) {
            switch (entry.getKey()) {
                case ENGINEER -> {
                    if (entry.getValue() == 1 && !player.hasPotionEffect(PotionEffectType.WEAKNESS)) {
                        applyFlaggedEffect(player, PotionEffectType.WEAKNESS, 0, Keys.effWeakness);
                    }
                    applyReach(player, engineerReach(entry.getValue()));
                    if (entry.getValue() >= 4) applyAttr(player, Keys.engineerDamage,
                            org.bukkit.attribute.Attribute.ATTACK_DAMAGE,
                            entry.getValue() >= 5 ? 4.0 : 2.0);
                }
                case BEDROCK -> {
                    if (entry.getValue() >= 4) applyAttr(player, Keys.bedrockKbr,
                            org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE, 0.9);
                    if (entry.getValue() >= 5) applyAttr(player, Keys.bedrockArmor,
                            org.bukkit.attribute.Attribute.ARMOR, 4.0);
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
                    if (entry.getValue() >= 4 && !player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                        applyFlaggedEffect(player, PotionEffectType.NIGHT_VISION, 0, Keys.effNightVision);
                    }
                    if (entry.getValue() == 3) ensureSculkHp(player);
                }
                case SCULK_LEADER -> {
                    if (entry.getValue() >= 4 && !player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                        applyFlaggedEffect(player, PotionEffectType.NIGHT_VISION, 0, Keys.effNightVision);
                    }
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
