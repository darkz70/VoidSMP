package me.darkz70.quirks.util;

import org.bukkit.command.CommandSender;

/**
 * Единая проверка «админ ли это» для команд/меню VoidQuirks.
 *
 * <p>Баг: на некоторых серверах плагин прав (LuckPerms и т.п.) не простукивает
 * ванильный {@code default: op} из plugin.yml вниз до игрока, из-за чего даже
 * настоящие операторы сервера получают «Недостаточно прав.» на /gaid, /ivent
 * и т.д., хотя они OP. Остальной код (ZayceMobMenu, XyzPossessionListener,
 * MagicDeathListener) уже даёт OP бесплатный пропуск — эта утилита приводит
 * все проверки к единому виду: OP ИЛИ явная permission-нода quirks.admin.</p>
 */
public final class Perms {

    private Perms() {
    }

    /** OP или явно выданная нода quirks.admin — именно так уже работают часть меню в этом плагине. */
    public static boolean isAdmin(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("quirks.admin");
    }
}
