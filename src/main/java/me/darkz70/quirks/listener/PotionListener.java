package me.darkz70.quirks.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.magic.MagicSystem;
import me.darkz70.quirks.mechanic.BrewTree;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Питьё плагиновых зелий: эффекты, лимиты, вотчеры, счётчики «зелья причуды». */
public final class PotionListener implements Listener {

    private static final long QP_WINDOW = 30 * 60_000L;          // окно 30 мин
    private static final long PACE_WINDOW = 60 * 60_000L;        // 2 зелья ускорения в час
    private static final long BERSERK_WINDOW = 20 * 60_000L;     // 5 берсерков в 20 мин
    private static final long QP_HP_DURATION = 5 * 60_000L;      // временное −2 HP на 5 мин
    private static final long DIVINE_PENALTY_DELAY = 2 * 60_000L;
    private static final long DIVINE_PENALTY = 7 * 24 * 60_000L; // 7 игровых суток ≈ 140 мин реального

    /** вотчеры «никакого вреда/восстановления»: uuid → [конец, накопленный урон] */
    private static final Map<UUID, long[]> WATCH_HARM = new HashMap<>();
    private static final Map<UUID, long[]> WATCH_HEAL = new HashMap<>();

    private final VoidQuirksPlugin plugin;

    public PotionListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    private MagicSystem magic() {
        return plugin.magic();
    }

    // ---------- выпивание ----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        String tag = BrewTree.markOf(item);
        if (tag == null) return;
        Player player = event.getPlayer();
        PlayerData data = magic().dataOrCreate(player);
        long now = System.currentTimeMillis();

        switch (tag) {
            case "antidote" -> {
                if (plugin.quirks().levelOf(player, Quirk.SCULK) > 0) {
                    plugin.quirks().remove(player, Quirk.SCULK);
                    player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0, 1, 0),
                            30, 0.4, 0.8, 0.4, 0.05);
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.8f, 1.4f);
                    plugin.notify(player, "potion-quirk-away");
                } else {
                    plugin.notify(player, "antidote-none");
                }
            }
            case "infect" -> {
                if (plugin.quirks().levelOf(player, Quirk.SCULK) == 0) {
                    plugin.quirks().assign(player, Quirk.SCULK, 1);
                    plugin.notify(player, "potion-soul-stir");
                }
            }
            case "samogon" -> {
                int lvl = levelOf(item);
                boolean boosted = item.getPersistentDataContainer().has(Keys.spellExtra, PersistentDataType.INTEGER);
                double mult = Math.pow(1.5, lvl - 1) * (boosted ? 2 : 1);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, sec(5 * mult), 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, sec(2 * mult),
                        boosted ? 1 : 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, sec(3 * mult),
                        boosted ? 1 : 0, false, true));
                plugin.notify(player, "potion-samogon", "%level%", String.valueOf(lvl));
            }
            case "deny" -> {
                strip(player, true);
                plugin.notify(player, "potion-deny");
            }
            case "confirm" -> {
                strip(player, false);
                plugin.notify(player, "potion-confirm");
            }
            case "disable" -> {
                String target = item.getPersistentDataContainer().get(Keys.brewTarget, PersistentDataType.STRING);
                Quirk quirk = target == null ? null : Quirk.byName(target);
                if (quirk != null && plugin.quirks().remove(player, quirk)) {
                    player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0),
                            24, 0.4, 0.7, 0.4, 0.04);
                    plugin.notify(player, "potion-quirk-away");
                } else {
                    plugin.notify(player, "potion-nothing");
                }
            }
            case "quirkall" -> drinkQuirkAll(player, data, event, now);
            case "life" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 50 * 20, 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 50 * 20, 2, false, true));
                plugin.notify(player, "potion-life");
            }
            case "paces" -> {
                if (!limitedOk(data, "paces", PACE_WINDOW, 2, now)) {
                    event.setCancelled(true);
                    Msg.send(player, "potion-limit");
                    return;
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 5 * 20, 24, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 7 * 20, 0, false, true));
                plugin.notify(player, "potion-paces");
            }
            case "noharm" -> {
                WATCH_HARM.put(player.getUniqueId(), new long[]{now + 60_000, 0});
                plugin.notify(player, "potion-watch");
            }
            case "norestore" -> {
                WATCH_HEAL.put(player.getUniqueId(), new long[]{now + 60_000, 0});
                plugin.notify(player, "potion-watch");
            }
            case "flypot" -> {
                player.damage(Math.max(0.0, Math.min(8, player.getHealth() - 0.5)));
                magic().flight(player, 8);
                plugin.notify(player, "potion-fly");
            }
            case "god" -> drinkGod(player, data, now);
            case "berserk" -> {
                if (!limitedOk(data, "berserk", BERSERK_WINDOW, 5, now)) {
                    event.setCancelled(true);
                    Msg.send(player, "potion-limit");
                    return;
                }
                magic().grantInvuln(player, 5_000);
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30 * 20, 1, false, true));
                plugin.notify(player, "potion-berserk");
            }
            case "grandsam" -> {
                int lvl = levelOf(item);
                double mult = Math.pow(1.5, lvl - 1);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, sec(5 * mult), 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, sec(2 * mult), 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, sec(3 * mult), 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 10 * 20, 0, false, true));
                player.damage(6);
                plugin.notify(player, "potion-grandsam");
            }
            case "return" -> drinkReturn(player, data);
            case "retrain" -> {
                magic().resetMagic(player);
                plugin.notify(player, "potion-retrain");
            }
            case "satpot" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 60 * 20, 0,
                    false, true));
            case "miner" -> player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60 * 20, 1,
                    false, true));
            case "dull" -> {
                magic().grantInvuln(player, 5_000);
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30 * 20, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120 * 20, 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120 * 20, 0, false, true));
            }
            case "demagic" -> {
                for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5)) {
                    if (entity instanceof Player other) {
                        PlayerData od = magic().data(other);
                        if (od != null && od.magicLevel() > 1) {
                            od.magicLevel(od.magicLevel() - 1);
                            Msg.send(other, "potion-demagic-hit");
                        }
                    }
                }
                PlayerData own = magic().data(player);
                if (own != null && own.magicLevel() > 1) {
                    own.magicLevel(own.magicLevel() - 1);
                }
                plugin.storage().save();
                plugin.notify(player, "potion-demagic");
            }
            default -> { /* neutral/basis/lucky/unlucky/infusion/knowledge/mind — пустышки/автоэффекты */ }
        }
    }

    private static int sec(double seconds) {
        return Math.max(20, (int) Math.round(seconds * 20));
    }

    private static int levelOf(ItemStack item) {
        Integer lvl = item.getPersistentDataContainer().get(Keys.brewLevel, PersistentDataType.INTEGER);
        return lvl == null ? 1 : Math.max(1, Math.min(16, lvl));
    }

    // ---------- позитивные/негативные эффекты ----------

    private static final PotionEffectType[] POSITIVE = {
        PotionEffectType.SPEED, PotionEffectType.HASTE, PotionEffectType.STRENGTH,
        PotionEffectType.REGENERATION, PotionEffectType.RESISTANCE, PotionEffectType.FIRE_RESISTANCE,
        PotionEffectType.WATER_BREATHING, PotionEffectType.NIGHT_VISION, PotionEffectType.INVISIBILITY,
        PotionEffectType.JUMP_BOOST, PotionEffectType.SLOW_FALLING, PotionEffectType.ABSORPTION,
        PotionEffectType.HEALTH_BOOST, PotionEffectType.LUCK, PotionEffectType.HERO_OF_THE_VILLAGE,
        PotionEffectType.CONDUIT_POWER, PotionEffectType.DOLPHINS_GRACE, PotionEffectType.GLOWING,
        PotionEffectType.SATURATION
    };

    private static final PotionEffectType[] NEGATIVE = {
        PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.NAUSEA,
        PotionEffectType.BLINDNESS, PotionEffectType.HUNGER, PotionEffectType.WEAKNESS,
        PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.DARKNESS,
        PotionEffectType.UNLUCK, PotionEffectType.BAD_OMEN, PotionEffectType.LEVITATION,
        PotionEffectType.WIND_CHARGED, PotionEffectType.WEAVING, PotionEffectType.OOZING,
        PotionEffectType.INFESTED
    };

    private static void strip(Player player, boolean positive) {
        for (PotionEffectType type : (positive ? POSITIVE : NEGATIVE)) {
            player.removePotionEffect(type);
        }
    }

    // ---------- лимиты ----------

    /** Лимит «N зелий за window». Возвращает true, если пить ещё можно (и фиксирует глоток). */
    private boolean limitedOk(PlayerData data, String key, long window, int limit, long now) {
        long winEnd = data.cooldown(key + ".win");
        int count = (int) data.cooldown(key + ".count");
        if (winEnd < now) {
            winEnd = now + window;
            count = 0;
        }
        count++;
        data.setCooldown(key + ".win", winEnd);
        data.setCooldown(key + ".count", count);
        plugin.storage().save();
        return count <= limit;
    }

    // ---------- зелье причуды ----------

    private void drinkQuirkAll(Player player, PlayerData data, PlayerItemConsumeEvent event, long now) {
        if (data.cooldown("qp.ban") == 1) {
            event.setCancelled(true);
            Msg.send(player, "potion-qp-ban");
            return;
        }
        long winEnd = data.cooldown("qp.win");
        int count = (int) data.cooldown("qp.count");
        if (winEnd < now) {
            winEnd = now + QP_WINDOW;
            count = 0;
        }
        count++;
        data.setCooldown("qp.win", winEnd);
        data.setCooldown("qp.count", count);
        plugin.storage().save();

        if (count <= 5) {
            magic().tempAllQuirks(player, 10_000);
        }
        if (count >= 3 && count <= 5) {
            // временное −2 макс. HP на 5 минут (накапливается)
            magic().addTempMaxHp(player, Keys.qpHp, -2);
            data.setCooldown("qp.hp-end", now + QP_HP_DURATION);
            Msg.send(player, "potion-qp-warn", "%count%", String.valueOf(count));
        } else if (count <= 2) {
            plugin.notify(player, "potion-qp-short");
        }
        if (count >= 6) {
            // смерть и перманентное −1 сердце; вторая такая смерть — бан зелья навсегда
            Msg.send(player, "potion-qp-death");
            player.setHealth(0);
            magic().addPermHeartLoss(player);
            int deaths = (int) data.cooldown("qp.deaths") + 1;
            data.setCooldown("qp.deaths", deaths);
            if (deaths >= 2) {
                data.setCooldown("qp.ban", 1);
            }
            plugin.storage().save();
        }
    }

    // ---------- зелье божества ----------

    private void drinkGod(Player player, PlayerData data, long now) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2 * 60 * 20, 9, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 2 * 60 * 20, 4, false, true));
        magic().flight(player, 120);
        magic().tempAllQuirks(player, 60_000);
        plugin.notify(player, "potion-god");
        // расплата: −2 макс. HP на 7 игровых суток — наступает после окончания двухминутного расцвета
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isValid() && !player.isOnline()) return;
            magic().addTempMaxHp(player, Keys.divineHp, -2);
            Msg.send(player, "potion-god-price");
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> magic().removeModifier(player, Keys.divineHp), DIVINE_PENALTY / 50);
        }, DIVINE_PENALTY_DELAY / 50);
    }

    // ---------- зелье возврата ----------

    private void drinkReturn(Player player, PlayerData data) {
        plugin.quirks().removeAll(player);
        for (PotionEffectType type : PotionEffectType.values()) {
            player.removePotionEffect(type);
        }
        magic().resetMagic(player);
        magic().removeModifier(player, Keys.qpHp);
        magic().removeModifier(player, Keys.qpPerm);
        magic().removeModifier(player, Keys.divineHp);
        magic().endFlightIfActive(player);
        player.setHealth(Math.min(MagicSystem.maxHealth(player), 20.0));
        player.setLevel(0);
        player.setExp(0);
        plugin.notify(player, "potion-return");
    }

    // ---------- вотчеры ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageWatch(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        long[] watch = WATCH_HARM.get(player.getUniqueId());
        if (watch != null) watch[1] += Math.round(event.getFinalDamage() * 2); // полусердца
        long[] healWatch = WATCH_HEAL.get(player.getUniqueId());
        if (healWatch != null) healWatch[1] += Math.round(event.getFinalDamage() * 2);
    }

    /** Тикает из MagicTask раз в секунду. Также чистит временный −2 HP от зелья причуды. */
    public static void tickWatchers(VoidQuirksPlugin plugin, Player player, long now) {
        UUID id = player.getUniqueId();
        long[] harm = WATCH_HARM.get(id);
        if (harm != null && harm[0] <= now) {
            WATCH_HARM.remove(id);
            if (harm[1] <= 2) { // ≤1 сердца урона за минуту
                player.damage(18);
                Msg.send(player, "potion-noharm-strike");
            }
        }
        long[] heal = WATCH_HEAL.get(id);
        if (heal != null && heal[0] <= now) {
            WATCH_HEAL.remove(id);
            if (heal[1] <= 2) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120 * 20, 1, false, true));
                Msg.send(player, "potion-norestore-gift");
            }
        }
        PlayerData data = plugin.storage().get(id);
        if (data != null && data.cooldown("qp.hp-end") != 0 && data.cooldown("qp.hp-end") <= now) {
            magicRemove(plugin, player);
            data.setCooldown("qp.hp-end", 0);
        }
    }

    private static void magicRemove(VoidQuirksPlugin plugin, Player player) {
        plugin.magic().removeModifier(player, Keys.qpHp);
    }
}
