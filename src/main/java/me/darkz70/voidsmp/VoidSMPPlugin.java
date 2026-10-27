package me.darkz70.voidsmp;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.loot.LootTable;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * VoidSMP — Paper 26.2.
 * <ul>
 *   <li>Обычным хранилищам (Vault) ставит свою лут-таблицу: 2 предмета за открытие.</li>
 *   <li>Жуткие (синие / ominous) хранилища не меняет.</li>
 *   <li>Раз в неделю сбрасывает все хранилища (включая жуткие).</li>
 *   <li>Ванильную механику ключей не трогает.</li>
 * </ul>
 */
public final class VoidSMPPlugin extends JavaPlugin {

    private LootConfig config;
    private VaultManager vaults;
    private ResetScheduler scheduler;

    @Override
    public void onEnable() {
        Path dataDir = getDataFolder().toPath();
        try {
            LootConfig.ensureDefaultConfig(dataDir);
            config = LootConfig.load(dataDir);
        } catch (Exception e) {
            getLogger().warning("Не удалось загрузить config.yml (" + e.getMessage() + "), использую встроенный");
            try {
                config = LootConfig.loadDefault();
            } catch (Exception e2) {
                getLogger().severe("Встроенный config.yml тоже не читается: " + e2);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
        validateConfig();

        vaults = new VaultManager(this);
        vaults.onEnable();

        scheduler = new ResetScheduler(this, vaults, config);
        scheduler.onEnable();

        getServer().getPluginManager().registerEvents(vaults, this);
        registerCommands();

        LootTable table = Bukkit.getLootTable(VaultManager.LOOT_TABLE_KEY);
        getLogger().info("LootTable=" + (table != null ? "LOADED" : "MISSING")
            + " rolls=" + config.rolls
            + " entries=" + countConcreteEntries()
            + " | сброс: " + scheduler.scheduleDescription());
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.onDisable();
        }
        if (vaults != null) {
            vaults.onDisable();
        }
    }

    // ------------------------------------------------------------------
    // валидация конфига (сервер уже запущен → Bukkit API доступен)
    // ------------------------------------------------------------------

    private void validateConfig() {
        int ok = 0;
        int bad = 0;
        for (LootConfig.Entry entry : config.entries) {
            if (entry.isGroup()) {
                for (String material : entry.group()) {
                    if (Material.matchMaterial(material) != null) {
                        ok++;
                    } else {
                        bad++;
                        getLogger().warning("config.yml: неизвестный предмет в group: " + material);
                    }
                }
            } else {
                if (Material.matchMaterial(entry.material()) != null) {
                    ok++;
                } else {
                    bad++;
                    getLogger().warning("config.yml: неизвестный предмет: " + entry.material());
                }
            }
        }
        try {
            ZoneId.of(config.resetTimezone);
        } catch (Exception e) {
            getLogger().warning("config.yml: некорректный reset.timezone \"" + config.resetTimezone
                + "\" — будет использован Europe/Warsaw");
        }
        getLogger().info("Конфигурация лута: предметов " + ok + (bad > 0 ? (" (" + bad + " некорректных)") : ""));
    }

    private int countConcreteEntries() {
        int count = 0;
        for (LootConfig.Entry entry : config.entries) {
            count += entry.isGroup() ? entry.group().size() : 1;
        }
        return count;
    }

    // ------------------------------------------------------------------
    // команды: /voidsmp status | reset | reload
    // ------------------------------------------------------------------

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                Commands.literal("voidsmp")
                    .requires(source -> source.getSender().hasPermission("voidsmp.admin"))
                    .then(Commands.literal("status").executes(ctx -> {
                        sendStatus(ctx.getSource().getSender());
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(Commands.literal("reset").executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        int cleared = scheduler.doReset("вручную");
                        sender.sendMessage(Component.text("VoidSMP: сброс выполнен, с ограничений снято: " + cleared + "."));
                        sender.sendMessage(Component.text("Следующий сброс: " + scheduler.formatNext()));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(Commands.literal("reload").executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        reloadEverything(sender);
                        return Command.SINGLE_SUCCESS;
                    }))
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        sender.sendMessage(Component.text("VoidSMP — команды:"));
                        sender.sendMessage(Component.text("/voidsmp status — состояние лута и времени сброса"));
                        sender.sendMessage(Component.text("/voidsmp reset — принудительный сброс хранилищ"));
                        sender.sendMessage(Component.text("/voidsmp reload — перечитать config.yml и датапак"));
                        return Command.SINGLE_SUCCESS;
                    })
                    .build(),
                "VoidSMP: лут и еженедельный сброс хранилищ",
                List.of()
            );
        });
    }

    private void sendStatus(CommandSender sender) {
        LootTable table = Bukkit.getLootTable(VaultManager.LOOT_TABLE_KEY);
        sender.sendMessage(Component.text("VoidSMP status:"));
        sender.sendMessage(Component.text("LootTable=" + (table != null ? "LOADED" : "MISSING")
            + " rolls=" + config.rolls
            + " entries=" + countConcreteEntries()));
        sender.sendMessage(Component.text("Tracked=" + vaults.trackedCount()));
        sender.sendMessage(Component.text("NextReset=" + scheduler.formatNext()));
    }

    private void reloadEverything(CommandSender sender) {
        Path dataDir = getDataFolder().toPath();
        LootConfig newConfig;
        try {
            newConfig = LootConfig.load(dataDir);
        } catch (Exception e) {
            sender.sendMessage(Component.text("VoidSMP: ошибка чтения config.yml: " + e.getMessage()));
            return;
        }
        this.config = newConfig;
        validateConfig();

        try {
            LootPackGenerator.writePack(dataDir, newConfig);
        } catch (Exception e) {
            sender.sendMessage(Component.text("VoidSMP: не удалось перезаписать датапак: " + e.getMessage()));
            return;
        }

        scheduler.setConfig(newConfig);
        Bukkit.getDatapackManager().refreshPacks();
        Bukkit.reloadData();

        LootTable table = Bukkit.getLootTable(VaultManager.LOOT_TABLE_KEY);
        sender.sendMessage(Component.text("VoidSMP перезагружен. LootTable="
            + (table != null ? "LOADED" : "MISSING")
            + ", entries=" + countConcreteEntries()
            + ", сброс: " + scheduler.scheduleDescription()));
    }
}
