package me.darkz70.quirks.mechanic;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.magic.Element;
import me.darkz70.quirks.mechanic.customblock.CustomBlockType;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Скалк-купол: прямоугольная (по периметру, без крыши и пола) стена из
 * полупрозрачных блоков — видно, можно пройти насквозь (BlockDisplay не имеет
 * хитбокса, это НЕ настоящий блок — ломать физически нечего), сломать нельзя.
 * Внутри купола вся магия подавлена, КРОМЕ Тьмы и Света (см. MagicSystem#cast).
 *
 * Технически — никакого стороннего плагина (ExecutableBlocks и т.п.) не нужно:
 * то же самое "кастомный блок без хитбокса" даёт штатный BlockDisplay (Paper API,
 * с 1.19.4) — один НЕмасштабированный дисплей на каждый блок периметра×высоты
 * (масштабирование по Y тянет одну и ту же текстуру на всю высоту вместо
 * повтора — именно так ловился баг с гигантскими полосами вместо решётки).
 * Текстура — переодетый STRUCTURE_VOID (технический блок редактора структур:
 * у игроков нет предмета, чтобы его получить/поставить — ни один настоящий
 * блок на сервере никогда не превратится в стену купола и наоборот).
 * Это и есть "свой" кастомный блок без стороннего плагина (ExecutableBlocks
 * и т.п. физически недоступны в этом окружении — ни скачать, ни поставить
 * на чужой боевой сервер).
 */
public final class SculkDomeManager {

    private final VoidQuirksPlugin plugin;
    private final File file;
    private final YamlConfiguration cfg;
    private final Map<String, Dome> domes = new HashMap<>();

    public SculkDomeManager(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "domes.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    private static final class Dome {
        String world;
        int x1, z1, x2, z2; // нормализованы: x1<=x2, z1<=z2
        int baseY;
        int height;
        List<UUID> entities = new ArrayList<>();

        boolean contains(Location loc) {
            if (!loc.getWorld().getName().equals(world)) return false;
            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();
            return x >= x1 && x <= x2 + 1 && z >= z1 && z <= z2 + 1
                    && y >= baseY && y <= baseY + height;
        }
    }

    // ---------- персистентность ----------

    public void loadIfExists() {
        ConfigurationSection root = cfg.getConfigurationSection("domes");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) continue;
            Dome d = new Dome();
            d.world = s.getString("world", "world");
            d.x1 = s.getInt("x1");
            d.z1 = s.getInt("z1");
            d.x2 = s.getInt("x2");
            d.z2 = s.getInt("z2");
            d.baseY = s.getInt("baseY");
            d.height = s.getInt("height");
            for (String uuid : s.getStringList("entities")) {
                try {
                    d.entities.add(UUID.fromString(uuid));
                } catch (IllegalArgumentException ignored) { }
            }
            domes.put(name.toLowerCase(java.util.Locale.ROOT), d);
        }
    }

    private void persist() {
        cfg.set("domes", null);
        for (Map.Entry<String, Dome> e : domes.entrySet()) {
            Dome d = e.getValue();
            String path = "domes." + e.getKey();
            cfg.set(path + ".world", d.world);
            cfg.set(path + ".x1", d.x1);
            cfg.set(path + ".z1", d.z1);
            cfg.set(path + ".x2", d.x2);
            cfg.set(path + ".z2", d.z2);
            cfg.set(path + ".baseY", d.baseY);
            cfg.set(path + ".height", d.height);
            List<String> uuids = new ArrayList<>();
            for (UUID u : d.entities) uuids.add(u.toString());
            cfg.set(path + ".entities", uuids);
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Не смог сохранить domes.yml", e);
        }
    }

    // ---------- создание/удаление ----------

    private static final int MAX_ENTITIES = 6000; // защита от случайного лагодрома (периметр × высота)

    public void create(CommandSender sender, String rawName, int x1, int z1, int x2, int z2, int height) {
        if (!(sender instanceof Player admin)) {
            Msg.send(sender, "usage");
            return;
        }
        String name = rawName.toLowerCase(java.util.Locale.ROOT);
        if (domes.containsKey(name)) {
            admin.sendMessage(Msg.color("&cКупол с именем &f" + rawName + " &cуже существует."
                    + " Сначала /quirk dome remove " + rawName));
            return;
        }
        if (height <= 0 || height > 64) {
            admin.sendMessage(Msg.color("&cВысота должна быть от 1 до 64."));
            return;
        }
        int nx1 = Math.min(x1, x2);
        int nx2 = Math.max(x1, x2);
        int nz1 = Math.min(z1, z2);
        int nz2 = Math.max(z1, z2);
        long width = (long) (nx2 - nx1 + 1);
        long depth = (long) (nz2 - nz1 + 1);
        long perimeter = width <= 1 || depth <= 1 ? width * depth : 2 * width + 2 * depth - 4;
        long totalEntities = perimeter * height;
        if (perimeter <= 0 || totalEntities > MAX_ENTITIES) {
            admin.sendMessage(Msg.color("&cСлишком много панелей (" + totalEntities
                    + ", максимум " + MAX_ENTITIES + "). Уменьши прямоугольник или высоту."));
            return;
        }

        World world = admin.getWorld();
        int baseY = admin.getLocation().getBlockY();

        Dome dome = new Dome();
        dome.world = world.getName();
        dome.x1 = nx1; dome.z1 = nz1; dome.x2 = nx2; dome.z2 = nz2;
        dome.baseY = baseY;
        dome.height = height;

        for (int[] cell : perimeterCells(nx1, nz1, nx2, nz2)) {
            for (int dy = 0; dy < height; dy++) {
                BlockDisplay display = spawnUnit(world, cell[0], baseY + dy, cell[1], name);
                dome.entities.add(display.getUniqueId());
            }
        }

        domes.put(name, dome);
        persist();
        admin.sendMessage(Msg.color("&a✦ Купол &f" + rawName + " &aпостроен: &7" + width + "×" + depth
                + "&a, высота &7" + height + "&a, &7" + dome.entities.size() + " панелей."));
        admin.sendMessage(Msg.color("&7Внутри подавлена вся магия, кроме Тьмы и Света."));
    }

    public void remove(CommandSender sender, String rawName) {
        String name = rawName.toLowerCase(java.util.Locale.ROOT);
        Dome dome = domes.remove(name);
        if (dome == null) {
            sender.sendMessage(Msg.color("&cКупола с именем &f" + rawName + " &cнет. /quirk dome list"));
            return;
        }
        int removed = 0;
        for (UUID id : dome.entities) {
            var entity = Bukkit.getEntity(id);
            if (entity instanceof BlockDisplay bd) {
                bd.remove();
                removed++;
            }
        }
        persist();
        sender.sendMessage(Msg.color("&a✦ Купол &f" + rawName + " &aубран (" + removed + " панелей)."));
    }

    public void list(CommandSender sender) {
        if (domes.isEmpty()) {
            sender.sendMessage(Msg.color("&7Куполов нет."));
            return;
        }
        sender.sendMessage(Msg.color("&3&lКупола (" + domes.size() + "):"));
        for (Map.Entry<String, Dome> e : domes.entrySet()) {
            Dome d = e.getValue();
            long alive = d.entities.stream().filter(id -> Bukkit.getEntity(id) != null).count();
            sender.sendMessage(Msg.color("&7— &f" + e.getKey() + " &7в &f" + d.world
                    + " &7(" + d.x1 + "," + d.z1 + ")-(" + d.x2 + "," + d.z2 + ") &7высота &f" + d.height
                    + " &7жив.панелей &f" + alive + "&7/" + d.entities.size()));
        }
    }

    /** Все клетки по периметру прямоугольника [x1..x2]×[z1..z2] (стороны, без внутренности). */
    private List<int[]> perimeterCells(int x1, int z1, int x2, int z2) {
        List<int[]> cells = new ArrayList<>();
        if (x1 == x2 && z1 == z2) {
            cells.add(new int[]{x1, z1});
            return cells;
        }
        for (int x = x1; x <= x2; x++) {
            cells.add(new int[]{x, z1});
            if (z2 != z1) cells.add(new int[]{x, z2});
        }
        for (int z = z1 + 1; z <= z2 - 1; z++) {
            cells.add(new int[]{x1, z});
            if (x2 != x1) cells.add(new int[]{x2, z});
        }
        return cells;
    }

    /** Один НЕмасштабированный блок-панель (ровно как обычный блок 1×1×1) — без растяжки текстуры. */
    private BlockDisplay spawnUnit(World world, int x, int y, int z, String domeName) {
        Location loc = new Location(world, x, y, z);
        BlockDisplay display = plugin.customBlocks().place(world, loc, CustomBlockType.SCULK_WARD, 1f, 1f, 1f);
        display.getPersistentDataContainer().set(Keys.domeMarker, PersistentDataType.STRING, domeName);
        return display;
    }

    // ---------- проверка зоны для магии ----------

    /** Внутри ЛЮБОГО купола — по умолчанию вся магия подавлена, см. вызывающую сторону про Тьму/Свет. */
    public boolean insideAnyDome(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        for (Dome d : domes.values()) {
            if (d.contains(loc)) return true;
        }
        return false;
    }

    @Nullable
    public String domeNameAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        for (Map.Entry<String, Dome> e : domes.entrySet()) {
            if (e.getValue().contains(loc)) return e.getKey();
        }
        return null;
    }

    // ---------- "поглощение" уже летящей/существующей чужой магии ----------

    /** Запускается один раз из onEnable: уже выпущенные снаряды заклинаний, залетевшие
     *  в купол, гасятся (кроме Тьмы и Света) — не только новый каст блокируется, но и то,
     *  что уже летело снаружи и попало внутрь. */
    public void startWarding() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::purgeForeignMagic, 20L, 4L);
    }

    private void purgeForeignMagic() {
        if (domes.isEmpty()) return;
        for (Dome d : domes.values()) {
            World world = Bukkit.getWorld(d.world);
            if (world == null) continue;
            BoundingBox box = new BoundingBox(d.x1, d.baseY, d.z1, d.x2 + 1, d.baseY + d.height, d.z2 + 1);
            for (Entity e : world.getNearbyEntities(box)) {
                if (e instanceof BlockDisplay) continue; // наши же панели купола
                String elemId = e.getPersistentDataContainer().get(Keys.spellElem, PersistentDataType.STRING);
                if (elemId == null) continue;
                Element element = Element.byId(elemId);
                if (element == Element.DARK || element == Element.LIGHT) continue;
                world.spawnParticle(Particle.SCULK_SOUL, e.getLocation(), 10, 0.2, 0.2, 0.2, 0.01);
                world.playSound(e.getLocation(), Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.6f, 1.4f);
                e.remove();
            }
        }
    }
}
