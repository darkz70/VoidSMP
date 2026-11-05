package ru.voidsmp.void2fa;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import ru.voidsmp.void2fa.telegram.TelegramBot;
import ru.voidsmp.void2fa.telegram.TelegramUpdateHandler;

/**
 * Void2FA — two-factor authentication for Paper 26.2 via a Telegram bot.
 * <p>
 * The plugin contains the whole bot backend (long polling). The server owner
 * only has to create a bot with @BotFather and paste its token into
 * {@code config.yml}; until then the plugin stays idle and blocks nobody.
 */
public final class Void2FAPlugin extends JavaPlugin {

    private ConfigManager config;
    private PlayerDataManager data;
    private AuthManager auth;
    private TelegramBot bot;
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

        getLogger().info("Void2FA 1.0.0 включён. Режим: " + config.getMode() + ".");
        if (config.getBotToken().isEmpty()) {
            getLogger().info("Токен бота не задан — 2FA пока не активна.");
            getLogger().info("Создай бота у @BotFather, укажи telegram.bot-token в config.yml и выполни /2faadmin reload.");
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
        String token = config.getBotToken();
        if (token == null || token.isEmpty()) {
            bot = null;
            return;
        }
        bot = new TelegramBot(this, token);
        bot.setUpdateListener(handler);
        bot.start();
    }

    public void stopBot() {
        if (bot != null) {
            bot.stop();
            bot = null;
        }
    }

    public void reloadEverything() {
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

    public TelegramBot getBot() {
        return bot;
    }
}
