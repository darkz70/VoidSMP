package me.darkz70.quirks.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Два ГЛОБАЛЬНЫХ режима «страшного неба» (переключаются в /gaid → Эффекты,
 * слоты 0/1 — действуют на ВСЕХ игроков сервера, пока админ не выключит):
 * <p>
 * - {@code sky.eyes.enabled} — 9 гигантских красных глаз вместо звёзд по кругу
 *   полусферы + 1 ещё больший глаз строго в зените (по центру, над головой).
 *   Технически это {@link ItemDisplay} с кастомной текстурой (void_eye,
 *   оверрайд на обычную бумагу по custom model data) и
 *   {@link Display.Billboard#CENTER} — глаз всегда развёрнут на игрока,
 *   куда бы тот ни посмотрел.
 * <p>
 * - {@code sky.red.enabled} — багровое небо. Технически гигантская тонкая
 *   светящаяся «крыша» из красного стекла ({@link BlockDisplay}) высоко над
 *   игроком + медленный красный пепельный дождь частиц.
 * <p>
 * ВАЖНО: Paper/Bukkit API не даёт по-настоящему подменить рендер неба
 * (цвет/звёзды) без клиент-мода — это рабочая иллюзия через ванильные
 * Display-сущности, которые плагин сам передвигает вслед за каждым игроком
 * (чтобы не терялись за пределами прогруза чанков).
 */
public final class SkyEffectsTask extends BukkitRunnable implements Listener {

    private static final int EYE_COUNT = 9;
    private static final double EYE_RADIUS = 220;
    private static final float EYE_SCALE = 38f;
    /** Большой центральный глаз в зените полусферы (10-й, самый заметный). */
    private static final float CENTER_EYE_SCALE = 70f;
    private static final int TOTAL_EYE_COUNT = EYE_COUNT + 1;
    private static final double PLANE_Y_OFFSET = 170;
    private static final float PLANE_SIZE = 650f;
    /** Если игрок отошёл от последнего "якоря" дальше этого — переставляем декорации. */
    private static final double REANCHOR_DISTANCE = 60;

    private final VoidQuirksPlugin plugin;
    private final Map<UUID, List<ItemDisplay>> eyes = new ConcurrentHashMap<>();
    private final Map<UUID, Location> eyeAnchor = new ConcurrentHashMap<>();
    private final Map<UUID, BlockDisplay> planes = new ConcurrentHashMap<>();
    private final Map<UUID, Location> planeAnchor = new ConcurrentHashMap<>();

    public SkyEffectsTask(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean eyesEnabled() {
        return plugin.getConfig().getBoolean("sky.eyes.enabled", false);
    }

    public boolean redEnabled() {
        return plugin.getConfig().getBoolean("sky.red.enabled", false);
    }

    public void setEyesEnabled(boolean on) {
        plugin.getConfig().set("sky.eyes.enabled", on);
        plugin.saveConfig();
        if (!on) despawnAllEyes();
    }

    public void setRedEnabled(boolean on) {
        plugin.getConfig().set("sky.red.enabled", on);
        plugin.saveConfig();
        if (!on) despawnAllPlanes();
    }

    @Override
    public void run() {
        boolean eyesOn = eyesEnabled();
        boolean redOn = redEnabled();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (eyesOn) {
                ensureEyes(player);
            } else if (eyes.containsKey(player.getUniqueId())) {
                despawnEyes(player.getUniqueId());
            }
            if (redOn) {
                ensurePlane(player);
                ambienceRed(player);
            } else if (planes.containsKey(player.getUniqueId())) {
                despawnPlane(player.getUniqueId());
            }
        }
    }

    /** Полная остановка (выгрузка плагина) — убрать вообще все декорации у всех. */
    public void shutdown() {
        despawnAllEyes();
        despawnAllPlanes();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        despawnEyes(id);
        despawnPlane(id);
    }

    // ---------- 9 глаз вместо звёзд ----------

    private void ensureEyes(Player player) {
        UUID id = player.getUniqueId();
        Location anchor = eyeAnchor.get(id);
        List<ItemDisplay> list = eyes.get(id);
        boolean valid = list != null && list.size() == TOTAL_EYE_COUNT
                && list.stream().allMatch(d -> d != null && d.isValid() && d.getWorld().equals(player.getWorld()));
        if (valid && anchor != null && anchor.getWorld().equals(player.getWorld())
                && anchor.distanceSquared(player.getLocation()) < REANCHOR_DISTANCE * REANCHOR_DISTANCE) {
            return;
        }
        despawnEyes(id);
        Location base = player.getLocation();
        List<ItemDisplay> fresh = new ArrayList<>(TOTAL_EYE_COUNT);
        for (int i = 0; i < EYE_COUNT; i++) {
            double az = Math.toRadians(i * (360.0 / EYE_COUNT) + ThreadLocalRandom.current().nextDouble(-10, 10));
            double elev = Math.toRadians(ThreadLocalRandom.current().nextDouble(35, 75));
            double horiz = EYE_RADIUS * Math.cos(elev);
            double y = EYE_RADIUS * Math.sin(elev);
            Location spot = base.clone().add(horiz * Math.cos(az), y, horiz * Math.sin(az));
            ItemDisplay display = spawnEye(spot, EYE_SCALE);
            if (display != null) fresh.add(display);
        }
        // 10-й глаз — большой, в самом зените полусферы (прямо над игроком).
        Location zenith = base.clone().add(0, EYE_RADIUS, 0);
        ItemDisplay center = spawnEye(zenith, CENTER_EYE_SCALE);
        if (center != null) fresh.add(center);
        if (!fresh.isEmpty()) {
            eyes.put(id, fresh);
            eyeAnchor.put(id, base);
        }
    }

    private ItemDisplay spawnEye(Location spot, float scale) {
        try {
            ItemStack stack = new ItemStack(Material.PAPER);
            stack.editMeta(meta -> {
                var cmd = meta.getCustomModelDataComponent();
                cmd.setStrings(List.of("void_eye"));
                meta.setCustomModelDataComponent(cmd);
            });
            return spot.getWorld().spawn(spot, ItemDisplay.class, display -> {
                display.setItemStack(stack);
                display.setBillboard(Display.Billboard.CENTER);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setShadowRadius(0f);
                display.setGlowing(true);
                display.setGlowColorOverride(Color.RED);
                display.setPersistent(false);
                display.setInvulnerable(true);
                display.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f()));
            });
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return null;
        }
    }

    private void despawnEyes(UUID id) {
        List<ItemDisplay> list = eyes.remove(id);
        eyeAnchor.remove(id);
        if (list != null) {
            list.forEach(d -> {
                if (d != null && d.isValid()) d.remove();
            });
        }
    }

    private void despawnAllEyes() {
        List<UUID> ids = new ArrayList<>(eyes.keySet());
        ids.forEach(this::despawnEyes);
    }

    // ---------- красное небо ----------

    private void ensurePlane(Player player) {
        UUID id = player.getUniqueId();
        BlockDisplay bd = planes.get(id);
        Location anchor = planeAnchor.get(id);
        boolean valid = bd != null && bd.isValid() && bd.getWorld().equals(player.getWorld());
        if (valid && anchor != null && anchor.getWorld().equals(player.getWorld())
                && anchor.distanceSquared(player.getLocation()) < REANCHOR_DISTANCE * REANCHOR_DISTANCE) {
            return;
        }
        despawnPlane(id);
        Location base = player.getLocation();
        Location spot = base.clone().add(0, PLANE_Y_OFFSET, 0);
        float thin = 0.4f;
        try {
            BlockDisplay created = spot.getWorld().spawn(spot, BlockDisplay.class, display -> {
                display.setBlock(Material.RED_STAINED_GLASS.createBlockData());
                display.setTransformation(new Transformation(
                        new Vector3f(-PLANE_SIZE / 2f, -thin / 2f, -PLANE_SIZE / 2f),
                        new AxisAngle4f(),
                        new Vector3f(PLANE_SIZE, thin, PLANE_SIZE),
                        new AxisAngle4f()));
                display.setBillboard(Display.Billboard.FIXED);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setShadowRadius(0f);
                display.setGlowing(true);
                display.setGlowColorOverride(Color.RED);
                display.setPersistent(false);
                display.setInvulnerable(true);
            });
            planes.put(id, created);
            planeAnchor.put(id, base);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // мир выгружен/чанк недоступен — пропускаем, попробуем на следующем тике
        }
    }

    private void despawnPlane(UUID id) {
        BlockDisplay bd = planes.remove(id);
        planeAnchor.remove(id);
        if (bd != null && bd.isValid()) bd.remove();
    }

    private void despawnAllPlanes() {
        List<UUID> ids = new ArrayList<>(planes.keySet());
        ids.forEach(this::despawnPlane);
    }

    /** Медленный красный пепельный «дождь» высоко над игроком + редкий далёкий гул. */
    private void ambienceRed(Player player) {
        var rnd = ThreadLocalRandom.current();
        Location p = player.getLocation();
        for (int i = 0; i < 5; i++) {
            double ox = rnd.nextDouble(-30, 30);
            double oz = rnd.nextDouble(-30, 30);
            Location at = p.clone().add(ox, 35 + rnd.nextDouble(0, 20), oz);
            player.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 0, 0), 2.2f));
        }
        if (rnd.nextInt(10) == 0) {
            player.playSound(p, Sound.ENTITY_WITHER_AMBIENT, SoundCategory.AMBIENT, 0.15f, 0.5f);
        }
    }
}
