package me.darkz70.quirks.task;

import java.util.Map;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.listener.AmphibianListener;
import me.darkz70.quirks.listener.AxeListener;
import me.darkz70.quirks.listener.FarmerListener;
import me.darkz70.quirks.listener.SpiderListener;
import me.darkz70.quirks.mechanic.BedrockLogic;
import me.darkz70.quirks.util.ScanUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Периодическая гарантия (раз в ~2 сек): пассивные эффекты, раскладка Бедрока,
 * голодный триггер Бедрока, зачистка мечей Топора, паутинная слепота Кота/Скалка,
 * вспышки Скалка, зелёное свечение Бедрок+Скалк, пассивки Паука/Земноводного/Фермера.
 */
public final class EffectsTask extends BukkitRunnable {

    /** Инженер: метка начала контакта с огнём/магмой/лавой (для 2-сек планки). */
    private final java.util.Map<java.util.UUID, Long> engFireStart = new java.util.HashMap<>();

    private final VoidQuirksPlugin plugin;

    public EffectsTask(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.storage().get(player.getUniqueId());
            if (data == null || data.isEmpty()) continue;

            plugin.quirks().ensurePassives(player, data);

            for (Map.Entry<Quirk, Integer> entry : data.entries()) {
                switch (entry.getKey()) {
                    case BEDROCK -> {
                        BedrockLogic.applyLayout(plugin, player, entry.getValue());
                        BedrockLogic.hungerCheck(plugin, player, data, entry.getValue());
                    }
                    case AXE -> {
                        if (entry.getValue() <= 2) {
                            AxeListener.sweepSwords(plugin, player, entry.getValue());
                        }
                    }
                    default -> { }
                }
            }

            int sculk = plugin.quirks().sculkOf(player);
            int bedrock = data.levelOf(Quirk.BEDROCK);
            int spider = data.levelOf(Quirk.SPIDER);
            int amphib = data.levelOf(Quirk.AMPHIBIAN);
            int farmer = data.levelOf(Quirk.FARMER);
            int cat = data.levelOf(Quirk.CAT);

            // паутина: Скалк и Кот слепнут, Паук — наоборот, в своей стихии
            boolean web = WebPhysicsTask.inCobweb(player);
            if (web && (cat > 0 || sculk > 0)) {
                ambient(player, PotionEffectType.BLINDNESS, 0);
            }

            // отдельные периодики классов
            if (spider > 0) SpiderListener.applyPassive(plugin, player, spider);
            if (amphib > 0) AmphibianListener.applyPassive(plugin, player, amphib);
            if (farmer > 0) FarmerListener.applyPassive(plugin, player, farmer);

            // скалковое свечение (2-3 ур.): немного частиц раз в N минут
            if (sculk >= 2) {
                sculkFlicker(player);
            }

            // зелёное свечение: рядом с Бедроком есть Скалк (в 3 блоках)
            if (bedrock > 0) {
                greenAura(player);
            }

            // Кот (с 2 ур.): голодный (food<=6) — получает треску, КД 30 мин
            if (cat >= 2 && player.getFoodLevel() <= 6
                    && ScanUtil.tryUse(player.getUniqueId(), "cat-fish", 30 * 60_000L)) {
                var leftover = player.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.COD, 1));
                leftover.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
            }

            // Бедрок (с 2 ур.): выбранная пара злодейских мобов бежит прочь
            if (bedrock >= 2) {
                var feared = me.darkz70.quirks.listener.AbilityMenus.fearedMobs(player.getUniqueId());
                if (!feared.isEmpty()) {
                    for (var entity : player.getNearbyEntities(8, 5, 8)) {
                        if (!feared.contains(entity.getType())) continue;
                        if (!(entity instanceof org.bukkit.entity.Monster monster)) continue;
                        if (!player.equals(monster.getTarget())) continue;
                        monster.setTarget(null);
                        var away = monster.getLocation().toVector()
                                .subtract(player.getLocation().toVector());
                        away.setY(0);
                        if (away.lengthSquared() > 0.0001) {
                            monster.setVelocity(away.normalize().multiply(0.7).setY(0.25));
                        }
                    }
                }
            }

            // Фермер (с 1 ур.): стоя на мхе — регенерация 10 сек, КД 5 мин
            if (farmer > 0
                    && player.getLocation().subtract(0, 1, 0).getBlock().getType() == org.bukkit.Material.MOSS_BLOCK
                    && ScanUtil.tryUse(player.getUniqueId(), "farmer-moss",
                            plugin.getConfig().getInt("farmer.moss-regen-cooldown-minutes", 5) * 60_000L)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0, true, false, true));
            }

            // Инженер (с 1 ур.): 2 секунды в огне/лаве/на магме → защита от огня, КД 30 мин
            int engineer = data.levelOf(Quirk.ENGINEER);
            if (engineer >= 1) {
                org.bukkit.block.Block feet = player.getLocation().getBlock();
                org.bukkit.block.Block below = player.getLocation().subtract(0, 1, 0).getBlock();
                boolean hot = switch (feet.getType()) {
                    case FIRE, SOUL_FIRE, LAVA -> true;
                    default -> below.getType() == org.bukkit.Material.MAGMA_BLOCK
                            || below.getType() == org.bukkit.Material.LAVA;
                };
                java.util.UUID uid = player.getUniqueId();
                if (hot) {
                    long start = engFireStart.computeIfAbsent(uid, k -> System.currentTimeMillis());
                    long contactMs = plugin.getConfig().getLong("engineer.fire-contact-ms", 2000);
                    if (System.currentTimeMillis() - start >= contactMs
                            && ScanUtil.tryUse(uid, "eng-fire",
                                    plugin.getConfig().getLong("engineer.fire-cooldown-minutes", 30) * 60_000L)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                                plugin.getConfig().getInt("engineer.fire-resist-seconds", 30) * 20, 0,
                                true, false, true));
                        engFireStart.remove(uid);
                    }
                } else {
                    engFireStart.remove(uid);
                }
            }
        }
    }

    /** Раз в flicker-minutes — ненадолго вспыхивают частицы скалка вокруг игрока. */
    private void sculkFlicker(Player player) {
        long periodMs = plugin.getConfig().getLong("sculk.flicker-minutes", 20) * 60_000L;
        if (!ScanUtil.tryUse(player.getUniqueId(), "sculk-flicker", periodMs)) return;
        Location at = player.getLocation().add(0, 0.6, 0);
        player.getWorld().spawnParticle(Particle.SCULK_SOUL, at, 8, 0.35, 0.5, 0.35, 0.02);
    }

    /** 50 зелёных частиц вокруг Бедрока, пока рядом скалчанин. */
    private void greenAura(Player player) {
        double radius = plugin.getConfig().getDouble("bedrock.green-radius", 3.0);
        boolean sculkNear = false;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == player || other.getWorld() != player.getWorld()) continue;
            if (plugin.quirks().sculkOf(other) < 1) continue;
            if (other.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                sculkNear = true;
                break;
            }
        }
        if (!sculkNear) return;
        if (!ScanUtil.tryUse(player.getUniqueId(), "bedrock-green-aura", 2000)) return;
        int count = plugin.getConfig().getInt("bedrock.green-count", 50);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), count, 0.5, 0.8, 0.5, 0.02);
    }

    /** День и нет грозы/дождя. */
    public static boolean isClearDay(World world) {
        long time = world.getTime() % 24000L;
        if (time < 0) time += 24000L;
        return time < 12300L && !world.hasStorm();
    }

    /**
     * Поддерживает эффект без «мигания»: перевешивает, только если его нет
     * или почти сгорел, и с хорошим запасом по времени (не сбрасывает туман слепоты).
     */
    public static void ambient(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getDuration() > 60) return;
        player.addPotionEffect(new PotionEffect(type, 200, amplifier, true, false, false));
    }

    /** Над головой открытое небо. */
    public static boolean exposedToSky(Player player) {
        Location loc = player.getLocation();
        return player.getWorld().getHighestBlockYAt(loc) < loc.getBlockY() + 1;
    }
}
