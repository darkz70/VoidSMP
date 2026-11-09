package ru.voidsmp.void2fa;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import ru.voidsmp.void2fa.telegram.TelegramBot;
import ru.voidsmp.void2fa.telegram.TelegramTransport;
import ru.voidsmp.void2fa.bridge.ExternalBotBridge;
import ru.voidsmp.void2fa.telegram.TelegramUpdateHandler;

/**
 * Void2FA — two-factor authentication for Paper 26.2 via a Telegram bot.
 * <p>
 * Supports the original embedded polling transport and an external bot through
 * an authenticated bridge. Only one transport may own a Telegram token.
 */
public final class Void2FAPlugin extends JavaPlugin {

    private ConfigManager config;
    private PlayerDataManager data;
    private AuthManager auth;
    private TelegramTransport bot;
    private TelegramUpdateHandler handler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new ConfigManager(this);
        data = new PlayerDataManager(this);
        auth = new AuthManager(this, config, data);
        handler = new TelegramUpdateHandler(this);

        getServer().getPluginManager().registerEvents(new AuthListener(this, auth, config), this);

        AuthCommand authCommand = new AuthCommand(this, auth, config);
        registerCommand("2fa", authCommand, authCommand);
        registerCommand("login", authCommand, authCommand);

        AdminCommand adminCommand = new AdminCommand(this, auth, config);
        registerCommand("2faadmin", adminCommand, adminCommand);

        startBot();

        getLogger().info("Void2FA 1.3.4 включён. Режим: " + config.getMode() + ".");
        if ("external".equals(config.getTransport())) {
            getLogger().info("External: запусти отдельный бот-сервис по BOT_DEPLOY.md; токен в плагин не требуется.");
        } else if (config.getBotToken().isEmpty()) {
            getLogger().info("Embedded: токен бота не задан — 2FA пока не активна.");
            getLogger().info("Укажи токен только в локальном config.yml или выбери external по BOT_DEPLOY.md.");
        }
    }

    @Override
    public void onDisable() {
        stopBot();
        if (auth != null) {
            auth.shutdown();
        }
        if (data != null) {
            data.save();
        }
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("Void2FA выключен.");
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Команда '" + name + "' не найдена в plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    /** (Re)creates the Telegram bot from the current config. */
    public void startBot() {
        stopBot();
        if ("external".equals(config.getTransport())) {
            if (!config.getBotToken().isEmpty()) {
                getLogger().severe("В external очисти telegram.bot-token: токен принадлежит отдельному бот-сервису.");
                return;
            }
            try {
                ExternalBotBridge bridge = new ExternalBotBridge(this,
                        ExternalBotBridge.loadSecret(this, config.getBridgeSecretFile()));
                bridge.start();
                bot = bridge;
            } catch (Exception e) {
                // Do not print paths, configuration values or credential-bearing exceptions.
                getLogger().severe("Не удалось запустить bridge (" + e.getClass().getSimpleName() + "). Проверь telegram.bridge.url и секрет. См. BOT_DEPLOY.md.");
            }
            return;
        }
        String token = config.getBotToken();
        if (token == null || token.isEmpty()) {
            bot = null;
            return;
        }
        TelegramBot embedded = new TelegramBot(this, token);
        embedded.setUpdateListener(handler);
        bot = embedded;
        embedded.start();
    }

    public void stopBot() {
        if (bot != null) {
            bot.stop();
            bot = null;
        }
    }

    public void reloadEverything() {
        // Old Telegram references must never authenticate a player after a transport change.
        if (auth != null) auth.shutdown();
        stopBot();
        config.reload();
        startBot();
    }

    /** Called from the polling thread once the bot successfully connected. */
    public void onBotReady() {
        getLogger().info("2FA активна. Привязка аккаунта в игре: /2fa link");
    }

    public ConfigManager getConfigManager() {
        return config;
    }

    public PlayerDataManager getDataManager() {
        return data;
    }

    public AuthManager getAuthManager() {
        return auth;
    }

    public TelegramTransport getBot() {
        return bot;
    }
}
