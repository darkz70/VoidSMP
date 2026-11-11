package me.darkz70.minekilld;

import java.io.File;
import java.io.IOException;
import me.darkz70.minekilld.command.MinekilldCommand;
import me.darkz70.minekilld.egg.DragonEggListener;
import me.darkz70.minekilld.egg.DragonEggManager;
import me.darkz70.minekilld.monolith.MonolithManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MinekilldPlugin extends JavaPlugin {

    private File dataFile;
    private DragonEggManager eggManager;
    private MonolithManager monolithManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");

        eggManager = new DragonEggManager(this);
        monolithManager = new MonolithManager(this);

        loadData();
        eggManager.reload();
        monolithManager.reload();

        Bukkit.getPluginManager().registerEvents(new DragonEggListener(eggManager), this);

        MinekilldCommand cmdHandler = new MinekilldCommand(this);
        PluginCommand cmd = getCommand("minekilld");
        if (cmd != null) {
            cmd.setExecutor(cmdHandler);
            cmd.setTabCompleter(cmdHandler);
        }

        getLogger().info("MinekilldEvents включён (Paper 26.2). Яиц в базе: "
                + eggManager.trackedEggCount() + ", монолитов: " + monolithManager.monoliths().size());
    }

    @Override
    public void onDisable() {
        if (eggManager != null) eggManager.stopTasks();
        if (monolithManager != null) monolithManager.stopTasks();
        saveData();
    }

    public void reloadAll() {
        reloadConfig();
        if (eggManager != null) eggManager.reload();
        if (monolithManager != null) monolithManager.reload();
    }

    public void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        eggManager.loadData(data);
        monolithManager.loadData(data);
    }

    public void saveData() {
        if (eggManager == null || monolithManager == null) return;
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) return;
        YamlConfiguration data = new YamlConfiguration();
        eggManager.saveData(data);
        monolithManager.saveData(data);
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Не удалось сохранить data.yml: " + e.getMessage());
        }
    }

    public DragonEggManager eggs() {
        return eggManager;
    }

    public MonolithManager monoliths() {
        return monolithManager;
    }
}
