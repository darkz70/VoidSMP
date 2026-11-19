package me.darkz70.quirks.mechanic;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * Реестр реально поставленных блоков-реликвий («Лавовое ядро» / «Призма»):
 * MAGMA_BLOCK и PRISMARINE — настоящие ванильные материалы, на сервере их и без
 * нас полно (нижний мир, базальтовые дельты, храмы океана), поэтому купольный
 * эффект должен срабатывать СТРОГО на конкретных координатах, куда реликвию
 * действительно поставили игроком/админом — отсюда персистентный реестр,
 * переживающий рестарт сервера (как domes.yml у SculkDomeManager).
 */
public final class RelicBlocks {

    public enum Type { LAVA_CORE, PRISM }

    private final VoidQuirksPlugin plugin;
    private final File file;
    private final YamlConfiguration cfg;
    private final Map<String, Type> blocks = new HashMap<>();

    public RelicBlocks(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "relic-blocks.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    private static String key(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public void loadIfExists() {
        ConfigurationSection root = cfg.getConfigurationSection("blocks");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            String raw = root.getString(k + ".type");
            if (raw == null) continue;
            try {
                // ключи YAML не допускают точки — храним с '|' вместо ';'
                blocks.put(k.replace('|', ';'), Type.valueOf(raw));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void persist() {
        cfg.set("blocks", null);
        for (Map.Entry<String, Type> e : blocks.entrySet()) {
            String safeKey = e.getKey().replace(';', '|');
            cfg.set("blocks." + safeKey + ".type", e.getValue().name());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Не смог сохранить relic-blocks.yml", e);
        }
    }

    public void register(Location loc, Type type) {
        blocks.put(key(loc), type);
        persist();
    }

    public void unregister(Location loc) {
        if (blocks.remove(key(loc)) != null) persist();
    }

    @Nullable
    public Type typeAt(Location loc) {
        return blocks.get(key(loc));
    }

    public boolean isRelic(Location loc) {
        return blocks.containsKey(key(loc));
    }

    /** Все координаты данного типа (мира могут быть не загружены — вызывающий сам фильтрует). */
    public Map<Location, Type> all() {
        Map<Location, Type> out = new HashMap<>();
        for (Map.Entry<String, Type> e : blocks.entrySet()) {
            String[] parts = e.getKey().split(";");
            if (parts.length != 4) continue;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) continue;
            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                out.put(new Location(world, x, y, z), e.getValue());
            } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    /** На всякий случай: если реликвия пропала из мира (разрушена сторонним способом/WorldEdit) — забываем её. */
    public void pruneMissing() {
        boolean changed = false;
        for (var it = blocks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Type> e = it.next();
            String[] parts = e.getKey().split(";");
            if (parts.length != 4) { it.remove(); changed = true; continue; }
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) continue; // мир просто не загружен — не трогаем
            int x, y, z;
            try {
                x = Integer.parseInt(parts[1]);
                y = Integer.parseInt(parts[2]);
                z = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ex) { it.remove(); changed = true; continue; }
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block block = world.getBlockAt(x, y, z);
            Type expected = e.getValue();
            boolean ok = (expected == Type.LAVA_CORE && block.getType() == org.bukkit.Material.MAGMA_BLOCK)
                    || (expected == Type.PRISM && block.getType() == org.bukkit.Material.PRISMARINE);
            if (!ok) { it.remove(); changed = true; }
        }
        if (changed) persist();
    }
}
