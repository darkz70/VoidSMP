package me.darkz70.quirks;

import me.darkz70.quirks.command.GaidCommand;
import me.darkz70.quirks.command.MagicCommand;
import me.darkz70.quirks.command.QuirkCommand;
import me.darkz70.quirks.listener.AmphibianListener;
import me.darkz70.quirks.magic.MagicListener;
import me.darkz70.quirks.magic.MagicSystem;
import me.darkz70.quirks.magic.MagicTask;
import me.darkz70.quirks.mechanic.BrewTree;
import me.darkz70.quirks.listener.PotionListener;
import me.darkz70.quirks.listener.AxeListener;
import me.darkz70.quirks.listener.BedrockListener;
import me.darkz70.quirks.listener.CatListener;
import me.darkz70.quirks.listener.CraftListener;
import me.darkz70.quirks.listener.EngineerListener;
import me.darkz70.quirks.listener.FarmerListener;
import me.darkz70.quirks.listener.SculkListener;
import me.darkz70.quirks.listener.SessionListener;
import me.darkz70.quirks.listener.SpiderListener;
import me.darkz70.quirks.mechanic.BedrockLogic;
import me.darkz70.quirks.task.EffectsTask;
import me.darkz70.quirks.task.WebPhysicsTask;
import me.darkz70.quirks.util.MaterialLists;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoidQuirksPlugin extends JavaPlugin {

    private QuirkStorage storage;
    private QuirkManager quirkManager;
    private MagicSystem magic;
    private me.darkz70.quirks.command.GaidMenus gaidMenus;
    private me.darkz70.quirks.command.AdminMenu adminMenu;
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
        magic = new MagicSystem(this);
        labyrinth = new me.darkz70.quirks.labyrinth.LabyrinthManager(this);
        labyrinth.loadIfExists();

        gaidMenus = new me.darkz70.quirks.command.GaidMenus(this);
        adminMenu = new me.darkz70.quirks.command.AdminMenu(this);
        Bukkit.getPluginManager().registerEvents(gaidMenus, this);
        Bukkit.getPluginManager().registerEvents(adminMenu, this);

        Bukkit.getPluginManager().registerEvents(new EngineerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BedrockListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AxeListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SculkListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SessionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new me.darkz70.quirks.listener.SoulShardListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CraftListener(this), this);
        Bukkit.getPluginManager().registerEvents(new FarmerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AmphibianListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SpiderListener(this), this);
        CraftListener.registerRecipes(this);
        BrewTree.registerBrewing(this);

        MagicListener magicListener = new MagicListener(this);
        Bukkit.getPluginManager().registerEvents(magicListener, this);
        Bukkit.getPluginManager().registerEvents(new PotionListener(this), this);
        zayceTask = new me.darkz70.quirks.task.ZayceKrolTask(this);
        Bukkit.getPluginManager().registerEvents(new me.darkz70.quirks.listener.ZayceKrolListener(this), this);
        politics = new me.darkz70.quirks.mechanic.PoliticsStore(this);
        gaipolitics = new me.darkz70.quirks.command.PoliticsMenu(this);
        toggleMenu = new me.darkz70.quirks.command.ToggleMenu(this);
        Bukkit.getPluginManager().registerEvents(toggleMenu, this);
        sculkLeader = new me.darkz70.quirks.listener.SculkLeaderListener(this);
        Bukkit.getPluginManager().registerEvents(sculkLeader, this);
        sitLie = new me.darkz70.quirks.listener.SitLieListener(this);
        Bukkit.getPluginManager().registerEvents(sitLie, this);
        me.darkz70.quirks.command.DecoyCommand[] decoys = {
            new me.darkz70.quirks.command.DecoyCommand(0), new me.darkz70.quirks.command.DecoyCommand(1),
            new me.darkz70.quirks.command.DecoyCommand(2), new me.darkz70.quirks.command.DecoyCommand(3),
            new me.darkz70.quirks.command.DecoyCommand(4)};
        String[] decoyNames = {"voidticker", "soulweather", "effigylog", "hermitgram", "stardustindex"};
        for (int i = 0; i < decoyNames.length; i++) {
            org.bukkit.command.PluginCommand dc = getCommand(decoyNames[i]);
            if (dc != null) dc.setExecutor(decoys[i]);
        }
        var sitCmd = getCommand("sit"); if (sitCmd != null) sitCmd.setExecutor(new me.darkz70.quirks.command.SitLieCommand(this, true));
        var lieCmd = getCommand("lie"); if (lieCmd != null) lieCmd.setExecutor(new me.darkz70.quirks.command.SitLieCommand(this, false));

        QuirkCommand command = new QuirkCommand(this);
        PluginCommand quirk = getCommand("quirk");
        if (quirk != null) {
            quirk.setExecutor(command);
            quirk.setTabCompleter(command);
        }

        MagicCommand magicCommand = new MagicCommand(this, magicListener);
        PluginCommand magicCmd = getCommand("magic");
        if (magicCmd != null) {
            magicCmd.setExecutor(magicCommand);
            magicCmd.setTabCompleter(magicCommand);
        }

        GaidCommand gaidCommand = new GaidCommand(this);
        PluginCommand gaid = getCommand("gaid");
        if (gaid != null) {
            gaid.setExecutor(gaidCommand);
        }

        int interval = Math.max(20, getConfig().getInt("tasks.effects-interval-ticks", 40));
        new EffectsTask(this).runTaskTimer(this, interval, interval);
        new WebPhysicsTask(this).runTaskTimer(this, 1L, 1L);
        new MagicTask(this).runTaskTimer(this, 20L, 20L);
        zayceTask.runTaskTimer(this, 200L, 200L);

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
        if (magic != null) magic.reloadMults();
    }

    public me.darkz70.quirks.command.GaidMenus gaidMenus() {
        return gaidMenus;
    }

    private me.darkz70.quirks.task.ZayceKrolTask zayceTask;
    private me.darkz70.quirks.command.ToggleMenu toggleMenu;
    private me.darkz70.quirks.listener.SculkLeaderListener sculkLeader;
    private me.darkz70.quirks.listener.SitLieListener sitLie;
    private me.darkz70.quirks.mechanic.PoliticsStore politics;
    private me.darkz70.quirks.command.PoliticsMenu gaipolitics;

    public me.darkz70.quirks.mechanic.PoliticsStore politics() {
        return politics;
    }

    public me.darkz70.quirks.command.PoliticsMenu gaipolitics() {
        return gaipolitics;
    }

    public me.darkz70.quirks.command.ToggleMenu toggleMenu() {
        return toggleMenu;
    }

    public me.darkz70.quirks.listener.SculkLeaderListener sculkLeader() {
        return sculkLeader;
    }

    public me.darkz70.quirks.listener.SitLieListener sitLie() {
        return sitLie;
    }

    public me.darkz70.quirks.task.ZayceKrolTask zayceTask() {
        return zayceTask;
    }

    public MagicSystem magic() {
        return magic;
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
