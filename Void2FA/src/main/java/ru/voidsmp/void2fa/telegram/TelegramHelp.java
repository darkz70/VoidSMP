package ru.voidsmp.void2fa.telegram;

/**
 * Keeps the public Telegram command reference discoverable even when a server
 * still has an older local config.yml with a custom/old {@code tg-help} text.
 */
public final class TelegramHelp {

    /** Command token -> user-facing help line. Order is the display order. */
    private static final String[][] COMMANDS = {
            {"/link", "/link <код> — привязать аккаунт (код из игры: /2fa link)"},
            {"/status", "/status — статус привязки"},
            {"/unlink", "/unlink — отвязать аккаунт"},
            {"/lock", "/lock — заблокировать аккаунт (никто не войдёт)"},
            {"/unlock", "/unlock — разблокировать аккаунт"},
            {"/devices", "/devices — список доверенных устройств"},
            {"/untrust", "/untrust <id|all> — убрать доверенное устройство (all = все и кик)"},
            {"/kick", "/kick — завершить текущий вход, устройства сохранить"},
            {"/revoke", "/revoke — убрать все устройства, сбросить сессию и кикнуть"},
            {"/help", "/help — эта справка"},
    };

    /** Telegram bot menu: command name (no slash) -> short description shown in the chat menu. */
    private static final String[][] MENU = {
            {"start", "Приветствие и список команд"},
            {"help", "Все команды бота"},
            {"link", "Привязать аккаунт по коду из игры"},
            {"status", "Статус привязки и 2FA"},
            {"lock", "Заблокировать аккаунт"},
            {"unlock", "Разблокировать аккаунт"},
            {"kick", "Завершить текущий вход"},
            {"revoke", "Убрать все устройства и кикнуть"},
            {"devices", "Список доверенных устройств"},
            {"untrust", "Убрать устройство: id или all"},
            {"unlink", "Отвязать аккаунт"},
    };

    private TelegramHelp() {
    }

    /** Payload for Bot API {@code setMyCommands}, so the commands are visible in the Telegram menu. */
    public static java.util.List<Object> menuCommands() {
        java.util.List<Object> commands = new java.util.ArrayList<>();
        for (String[] entry : MENU) {
            commands.add(Json.obj("command", entry[0], "description", entry[1]));
        }
        return commands;
    }

    /**
     * Returns the configured help/start text, appending any missing current
     * command lines. Existing custom text is preserved; nothing is duplicated
     * when the configured text already mentions every command.
     */
    public static String withRequiredCommands(String configuredText) {
        String base = configuredText == null ? "" : configuredText.strip();
        StringBuilder missing = new StringBuilder();
        for (String[] command : COMMANDS) {
            if (!base.contains(command[0])) {
                if (missing.length() > 0) {
                    missing.append('\n');
                }
                missing.append(command[1]);
            }
        }
        if (missing.length() == 0) {
            return base;
        }
        if (base.isEmpty()) {
            return "Команды:\n" + missing;
        }
        return base + "\n\nДополнительные команды:\n" + missing;
    }
}
