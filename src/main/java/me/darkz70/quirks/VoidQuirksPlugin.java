package me.darkz70.quirks;

import me.darkz70.quirks.command.QuirkCommand;
import me.darkz70.quirks.listener.AxeListener;
import me.darkz70.quirks.listener.BedrockListener;
import me.darkz70.quirks.listener.CatListener;
import me.darkz70.quirks.listener.EngineerListener;
import me.darkz70.quirks.listener.SculkListener;
import me.darkz70.quirks.listener.SessionListener;
import me.darkz70.quirks.mechanic.BedrockLogic;
import me.darkz70.quirks.task.EffectsTask;
import me.darkz70.quirks.util.MaterialLists;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoidQuirksPlugin extends JavaPlugin {

    private QuirkStorage storage;
    private QuirkManager quirkManager;
    private me.darkz70.quirks.labyrinth.LabyrinthManager labyrinth;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);
        MaterialLists.load(getConfig(), getLogger());
        Msg.init(this);
        BedrockLogic.init(this);

        storage = new QuirkStorage(this);
        storage.load();
        quirkManager = new QuirkManager(this, storage);
        labyrinth = new me.darkz70.quirks.labyrinth.LabyrinthManager(this);
        labyrinth.loadIfExists();

        Bukkit.getPluginManager().registerEvents(new EngineerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BedrockListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AxeListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SculkListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SessionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new me.darkz70.quirks.listener.SoulShardListener(this), this);

        QuirkCommand command = new QuirkCommand(this);
        PluginCommand quirk = getCommand("quirk");
        if (quirk != null) {
            quirk.setExecutor(command);
            quirk.setTabCompleter(command);
        }

        int interval = Math.max(20, getConfig().getInt("tasks.effects-interval-ticks", 40));
        new EffectsTask(this).runTaskTimer(this, interval, interval);

        long autosave = Math.max(1, getConfig().getLong("tasks.autosave-minutes", 5)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, storage::save, autosave, autosave);

        getLogger().info("VoidQuirks включён. Причуд в базе: " + storage.all().size());
    }

    @Override
    public void onDisable() {
        if (storage != null) storage.save();
    }

    /** Перечитать config.yml (команда /quirk reload). */
    public void reloadPluginConfig() {
        reloadConfig();
        MaterialLists.load(getConfig(), getLogger());
        Msg.init(this);
    }

    public QuirkStorage storage() {
        return storage;
    }

    public QuirkManager quirks() {
        return quirkManager;
    }

    public me.darkz70.quirks.labyrinth.LabyrinthManager labyrinth() {
        return labyrinth;
    }

    /** Отправить оповещение с учётом личного тумблера /quirk notify. */
    public void notify(org.bukkit.entity.Player player, String path, String... repl) {
        PlayerData data = storage.get(player.getUniqueId());
        if (data != null && !data.notifications()) return;
        Msg.send(player, path, repl);
    }

    /** То же самое, но в action bar. */
    public void notifyBar(org.bukkit.entity.Player player, String path, String... repl) {
        PlayerData data = storage.get(player.getUniqueId());
        if (data != null && !data.notifications()) return;
        player.sendActionBar(Msg.comp(path, repl));
    }
}
