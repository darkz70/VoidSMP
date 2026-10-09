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

    /** Чумная аура: uuid → конец */
    private static final Map<UUID, Long> PLAGUE_UNTIL = new HashMap<>();
    /** Эпидемия: uuid → конец (для передачи убийце при смерти). */
    private static final Map<UUID, Long> EPIDEMIC_UNTIL = new HashMap<>();
    /** Умиротворённые мобы: entity UUID → конец нейтральности. */
    private static final Map<UUID, Long> PACIFIED_UNTIL = new HashMap<>();

    /** Возвращает конец тика эпидемии (для MagicListener/смертей). */
    public static long epidemicEnd(UUID id) {
        return EPIDEMIC_UNTIL.getOrDefault(id, 0L);
    }

    /** Моб умиротворён зельем природы? */
    public static boolean isPacified(UUID id) {
        return PACIFIED_UNTIL.getOrDefault(id, 0L) > System.currentTimeMillis();
    }

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

    /** Иссушение: 15 секунд нельзя пить лечебные зелья. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void blockHealingPotions(PlayerItemConsumeEvent event) {
        if (!plugin.magic().noPotionHealActive(event.getPlayer().getUniqueId())) return;
        ItemStack item = event.getItem();
        if (item.getType() == org.bukkit.Material.POTION || item.getType() == org.bukkit.Material.SPLASH_POTION) {
            String tag = BrewTree.markOf(item);
            String fam = BrewTree.baseFamily(item);
            boolean healing = "HEALING".equals(fam) || "REGENERATION".equals(fam)
                    || "life".equals(tag) || "forestfeast".equals(tag) || "fortress".equals(tag)
                    || "druid".equals(tag);
            if (healing) {
                event.setCancelled(true);
                Msg.send(event.getPlayer(), "potion-wither-dry");
            }
        }
    }

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
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, sec(5 * mult), 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, sec(2 * mult),
                        boosted ? 1 : 0, false, true));
                int regenTicks = sec(3 * mult);
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenTicks,
                        boosted ? 1 : 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                        (int) Math.max(20, regenTicks * 0.4), 0, false, true));
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
                boolean any = false;
                for (Quirk quirk : Quirk.values()) {
                    if (quirk == Quirk.SCULK || quirk == Quirk.ADMIN) continue; // скалку — антидот; админку не трогаем
                    if (plugin.quirks().levelOf(player, quirk) > 0) {
                        plugin.quirks().remove(player, quirk);
                        any = true;
                    }
                }
                if (any) {
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

            case "doublepoison" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 400, 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 400, 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 400, 0, false, true));
            }
            case "nature" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 30 * 20, 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 10 * 20, 0, false, true));
                for (org.bukkit.entity.Entity nearby : player.getWorld().getNearbyEntities(player.getLocation(), 10, 10, 10)) {
                    if (nearby instanceof org.bukkit.entity.Mob mob) {
                        mob.setTarget(null);
                        PACIFIED_UNTIL.put(nearby.getUniqueId(), now + 20_000);
                    }
                }
                plugin.notify(player, "potion-nature");
            }
            case "druid" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30 * 20, 1, false, true));
                strip(player, false);
                growAround(player, 5);
                plugin.notify(player, "potion-druid");
            }
            case "naturepoison" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 15 * 20, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 20, 0, false, true));
                for (org.bukkit.entity.Entity nearby : player.getWorld().getNearbyEntities(player.getLocation(), 15, 15, 15)) {
                    enrage(nearby, player);
                }
                plugin.notify(player, "potion-naturepoison");
            }
            case "warrior" -> {
                if (!potionCd(player, data, tag, 5 * 60_000, now)) { event.setCancelled(true); return; }
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 20, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 10 * 20, 0, false, true));
                plugin.notify(player, "potion-warrior");
            }
            case "gladiator" -> {
                if (!potionCd(player, data, tag, 15 * 60_000, now)) { event.setCancelled(true); return; }
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 30 * 20, 2, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30 * 20, 2, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15 * 20, 1, false, true));
                magic().grantInvuln(player, 10_000);
                Bukkit.getScheduler().runTaskLater(plugin, () -> player.addPotionEffect(
                        new PotionEffect(PotionEffectType.WEAKNESS, 30 * 20, 0, false, true)), 30 * 20);
                plugin.notify(player, "potion-gladiator");
            }
            case "plague" -> {
                PLAGUE_UNTIL.put(player.getUniqueId(), now + 30_000);
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 30 * 20, 0, false, true));
                plugin.notify(player, "potion-plague");
            }
            case "epidemic" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 30 * 20, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 30 * 20, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30 * 20, 0, false, true));
                EPIDEMIC_UNTIL.put(player.getUniqueId(), now + 30_000);
                plugin.notify(player, "potion-epidemic");
            }
            case "bastion" -> {
                if (!potionCd(player, data, tag, 10 * 60_000, now)) { event.setCancelled(true); return; }
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15 * 20, 2, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 15 * 20, 0, false, true));
                magic().stampRoot(player, 3_000);
                plugin.notify(player, "potion-bastion");
            }
            case "fortress" -> {
                if (!potionCd(player, data, tag, 20 * 60_000, now)) { event.setCancelled(true); return; }
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 20, 3, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 20, 3, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 15 * 20, 1, false, true));
                plugin.notify(player, "potion-fortress");
            }
            case "weightless" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30 * 20, 3, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30 * 20, 2, false, true));
                magic().stampNoFall(player, 60_000);
                plugin.notify(player, "potion-weightless");
            }
            case "angel" -> {
                if (!potionCd(player, data, tag, 20 * 60_000, now)) { event.setCancelled(true); return; }
                magic().flight(player, 15);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60 * 20, 0, false, true));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isValid() && !magic().flightActive(player.getUniqueId())) {
                        player.damage(Math.max(0.0, Math.min(4, player.getHealth() - 0.5))); // 2 сердца
                    }
                }, 15 * 20);
                plugin.notify(player, "potion-angel");
            }
            case "witherpot" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 10 * 20, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 20, 0, false, true));
                magic().stampNoPotionHeal(player, 15_000);
                plugin.notify(player, "potion-witherpot");
            }
            case "necro" -> {
                if (!potionCd(player, data, tag, 15 * 60_000, now)) { event.setCancelled(true); return; }
                for (int i = 0; i < 3; i++) {
                    org.bukkit.Location at = player.getLocation().add(Math.random() * 2 - 1, 0, Math.random() * 2 - 1);
                    org.bukkit.entity.WitherSkeleton mob = player.getWorld().spawn(at, org.bukkit.entity.WitherSkeleton.class);
                    mob.getPersistentDataContainer().set(Keys.noLoot, PersistentDataType.BYTE, (byte) 1);
                    magic().summonGuardian(mob, 30_000);
                }
                magic().stampNecro(player, 30_000);
                plugin.notify(player, "potion-necro");
            }
            case "darkpotion" -> player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30 * 20, 0,
                    false, true));
            case "lightpotion" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 60 * 20, 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 10 * 20, 0, false, true));
            }
            case "blindpotion" -> player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30 * 20, 0,
                    false, true));
            case "nightmare" -> {
                if (!potionCd(player, data, tag, 10 * 60_000, now)) { event.setCancelled(true); return; }
                nightmarePack(player, 20 * 20);
                for (org.bukkit.entity.Entity nearby : player.getWorld().getNearbyEntities(player.getLocation(), 15, 15, 15)) {
                    if (nearby instanceof Player other && nearby != player) nightmarePack(other, 10 * 20);
                }
                plugin.notify(player, "potion-nightmare");
            }
            case "madness" -> drinkMadness(player);
            case "manapot" -> {
                if (!potionCd(player, data, tag, 40_000, now)) { event.setCancelled(true); return; }
                magic().addMana(player, 50);
                plugin.notify(player, "potion-mana");
            }
            case "archimage" -> {
                if (!potionCd(player, data, tag, 5 * 60_000, now)) { event.setCancelled(true); return; }
                magic().refillMana(player);
                magic().stampHalfMana(player, 30_000);
                plugin.notify(player, "potion-archimage");
            }
            case "greatarch" -> {
                if (!potionCd(player, data, tag, 60_000, now)) { event.setCancelled(true); return; }
                magic().refillMana(player);
                magic().stampFreeCast(player, 30_000);
                Bukkit.getScheduler().runTaskLater(plugin, () -> magic().stampNoCast(player, 40_000), 30 * 20);
                plugin.notify(player, "potion-greatarch");
            }
            case "darkmagic" -> {
                if (!potionCd(player, data, tag, 20 * 60_000, now)) { event.setCancelled(true); return; }
                magic().stampDarkBoost(player, 60_000);
                plugin.notify(player, "potion-darkmagic");
            }
            case "lightmagic" -> {
                if (!potionCd(player, data, tag, 20 * 60_000, now)) { event.setCancelled(true); return; }
                magic().stampLightBoost(player, 60_000);
                plugin.notify(player, "potion-lightmagic");
            }
            case "mushroomspirit" -> {
                if (!potionCd(player, data, tag, 60_000, now)) { event.setCancelled(true); return; }
                PotionEffectType[] mood = {PotionEffectType.SPEED, PotionEffectType.STRENGTH,
                    PotionEffectType.JUMP_BOOST, PotionEffectType.HASTE, PotionEffectType.SLOWNESS,
                    PotionEffectType.NAUSEA, PotionEffectType.POISON, PotionEffectType.WEAKNESS};
                PotionEffectType picked = mood[(int) (Math.random() * mood.length)];
                player.addPotionEffect(new PotionEffect(picked, 30 * 20,
                        (int) (Math.random() * 3), false, true));
                plugin.notify(player, "potion-mushroomspirit");
            }
            case "forestfeast" -> {
                if (!potionCd(player, data, tag, 10 * 60_000, now)) { event.setCancelled(true); return; }
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 120 * 20, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40 * 20, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 20, 3, false, true));
                plugin.notify(player, "potion-forestfeast");
            }
            case "newlife" -> {
                if (!potionCd(player, data, tag, 30 * 60_000, now)) { event.setCancelled(true); return; }
                for (PotionEffectType type : PotionEffectType.values()) player.removePotionEffect(type);
                magic().resetMagic(player);
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 10 * 20, 2, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 120 * 20, 4, false, true));
                plugin.notify(player, "potion-newlife");
            }
            case "chick" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 20, 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10 * 20, 0, false, true));
            }
            case "depths" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 45 * 20, 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 30 * 20, 0, false, true));
            }
            case "panda" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 10 * 20, 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0, false, true));
            }
            case "oceanid" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60 * 20, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 60 * 20, 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 30 * 20, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 20, 0, false, true));
            }
            case "albatross" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30 * 20, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 30 * 20, 0, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 15 * 20, 1, false, true));
            }
            case "nest" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60 * 20, 2, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30 * 20, 5, false, true));
            }
            default -> { /* neutral/basis/lucky/unlucky/infusion/knowledge/mind/boiledegg — пустышки/автоэффекты */ }

        }
    }

    /** Жёсткий кулдаун зелья (минуты/секунды в ms). false — зелье ещё остывает (пытливый пьяница). */
    private boolean potionCd(Player player, PlayerData data, String tag, long windowMs, long now) {
        if (magic().cooldownsOff(player.getUniqueId())) return true; // АдминПро без кд
        long end = data.cooldown("potcd." + tag);
        if (end > now) {
            long left = (end - now + 999) / 1000;
            String time = left >= 60 ? ((left + 59) / 60) + " мин." : left + " с.";
            Msg.send(player, "potion-cd", "%time%", time);
            return false;
        }
        long window = (long) (windowMs * magic().coolScale("potions"));
        data.setCooldown("potcd." + tag, now + window);
        plugin.storage().save();
        return true;
    }

    /** Эффект наконечной стрелы по тегу зелья (всё упирается в 5 секунд). */
    public static void applyBrewArrow(org.bukkit.entity.LivingEntity victim, String tag) {
        int t = 100; // 5 сек
        switch (tag) {
            case "doublepoison" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, t, 0));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, t, 0));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, t, 0));
            }
            case "nature" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, t, 0));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, t, 0));
            }
            case "druid" -> victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, t, 1));
            case "naturepoison", "plague" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, t, 1));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, t, 0));
            }
            case "warrior" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, t, 0));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, t, 0));
            }
            case "gladiator" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, t, 1));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, t, 1));
            }
            case "epidemic" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, t, 1));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, t, 1));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, t, 0));
            }
            case "bastion" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, t, 1));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, t, 0));
            }
            case "fortress" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, t, 2));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, t, 1));
            }
            case "weightless" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, t, 1));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, t, 1));
            }
            case "angel" -> victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, t, 1));
            case "witherpot" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, t, 1));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, t, 0));
            }
            case "necro" -> victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, t, 1));
            case "darkpotion" -> victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, t, 0));
            case "lightpotion" -> victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, t, 0));
            case "blindpotion" -> victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, t, 0));
            case "nightmare", "madness" -> nightmarePackArrow(victim, tag.equals("madness"));
            case "mushroomspirit" -> victim.addPotionEffect(new PotionEffect(
                    Math.random() < 0.5 ? PotionEffectType.POISON : PotionEffectType.SLOWNESS, t, 1));
            case "forestfeast", "newlife" -> victim.addPotionEffect(
                    new PotionEffect(PotionEffectType.REGENERATION, t, 0));
            default -> victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, t, 0));
        }
    }

    private static void nightmarePackArrow(org.bukkit.entity.LivingEntity victim, boolean stronger) {
        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, stronger ? 1 : 0));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, stronger ? 1 : 0));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, stronger ? 1 : 0));
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

    /** Мгновенный рост растений вокруг (зелье друида). */
    private static void growAround(Player player, int radius) {
        org.bukkit.Location c = player.getLocation();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    org.bukkit.block.Block block = c.clone().add(dx, dy, dz).getBlock();
                    if (block.getBlockData() instanceof org.bukkit.block.data.Ageable) {
                        block.applyBoneMeal(org.bukkit.block.BlockFace.UP);
                        block.applyBoneMeal(org.bukkit.block.BlockFace.UP);
                    }
                }
            }
        }
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation(), 80, radius, 1, radius, 0.02);
    }

    /** Нейтральные мобы звереют на 30 сек (зелье яда природы — где умеют). */
    private static void enrage(org.bukkit.entity.Entity entity, Player target) {
        if (!(entity instanceof org.bukkit.entity.LivingEntity)) return;
        PACIFIED_UNTIL.remove(entity.getUniqueId());
        if (entity instanceof org.bukkit.entity.Wolf wolf) {
            wolf.setAngry(true);
            wolf.setTarget(target);
        } else if (entity instanceof org.bukkit.entity.Bee bee) {
            bee.setTarget(target);
        } else if (entity instanceof org.bukkit.entity.Monster monster && !(entity instanceof Player)) {
            // «нейтральные» условно-враждебные (пауки/панда-помощники) предъявляют носителю
            if (entity.getLocation().distanceSquared(target.getLocation()) < 15 * 15) {
                monster.setTarget(target);
            }
        } else if (entity instanceof org.bukkit.entity.Panda panda) {
            panda.setMainGene(org.bukkit.entity.Panda.Gene.AGGRESSIVE);
        }
    }

    private static void nightmarePack(Player player, int ticks) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 1, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, ticks, 1, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 1, false, true));
    }

    /** Зелье безумия: букет дебаффов + 6 случайных телепортов каждые 5 сек. */
    private void drinkMadness(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30 * 20, 2, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 30 * 20, 2, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 30 * 20, 2, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30 * 20, 2, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30 * 20, 2, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 30 * 20, 2, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 30 * 20, 1, false, true));
        for (int i = 1; i <= 6; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isValid()) return;
                double angle = Math.random() * 2 * Math.PI;
                double dist = Math.random() * 20;
                org.bukkit.Location at = player.getLocation().add(Math.cos(angle) * dist, 3, Math.sin(angle) * dist);
                org.bukkit.block.Block top = player.getWorld().getHighestBlockAt(at);
                player.teleport(top.getLocation().add(0.5, 1, 0.5));
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 60, 0.4, 1, 0.4, 0.5);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
            }, i * 5L * 20L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isValid()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 10 * 20, 0, false, true));
                Msg.send(player, "potion-madness-end");
            }
        }, 30 * 20L);
        Msg.send(player, "potion-madness");
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

    /** Эпидемия: смертельная отдача убийце. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player dead = event.getEntity();
        if (epidemicEnd(dead.getUniqueId()) <= System.currentTimeMillis()) return;
        org.bukkit.entity.Entity killerEntity = dead.getLastDamageCause() != null
                && dead.getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity
                ? byEntity.getDamager() : null;
        org.bukkit.entity.Player killer = dead.getKiller();
        org.bukkit.entity.LivingEntity target = killer != null ? killer
                : killerEntity instanceof org.bukkit.entity.LivingEntity le ? le : null;
        if (target == null || target == dead) return;
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 15 * 20, 1, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 15 * 20, 1, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 15 * 20, 0, false, true));
        dead.getWorld().spawnParticle(Particle.SCULK_SOUL, dead.getLocation(), 60, 1, 1, 1, 0.05);
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
        // чума: каждую секунду заражаем всех в 5 блоках
        if (PLAGUE_UNTIL.getOrDefault(id, 0L) > now) {
            for (org.bukkit.entity.Entity nearby : player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5)) {
                if (nearby instanceof org.bukkit.entity.LivingEntity victim && nearby != player) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 10 * 20, 0, false, true));
                }
            }
            player.getWorld().spawnParticle(Particle.MYCELIUM, player.getLocation(), 8, 1.6, 0.8, 1.6, 0.01);
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
