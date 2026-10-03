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

            int sculk = data.levelOf(Quirk.SCULK);
            int bedrock = data.levelOf(Quirk.BEDROCK);
            int spider = data.levelOf(Quirk.SPIDER);
            int amphib = data.levelOf(Quirk.AMPHIBIAN);
            int farmer = data.levelOf(Quirk.FARMER);
            int cat = data.levelOf(Quirk.CAT);

            // паутина: Скалк и Кот слепнут, Паук — наоборот, в своей стихии
            boolean web = WebPhysicsTask.inCobweb(player);
            if (web && (cat > 0 || sculk > 0)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, true, false, false));
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
            if (plugin.quirks().levelOf(other, Quirk.SCULK) < 1) continue;
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

    /** Над головой открытое небо. */
    public static boolean exposedToSky(Player player) {
        Location loc = player.getLocation();
        return player.getWorld().getHighestBlockYAt(loc) < loc.getBlockY() + 1;
    }
}
