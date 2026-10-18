package com.voidsmp.voidoferta;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Интеграция с плагином авторизации (по умолчанию — SimpleLogin).
 *
 * <p>Единого публичного API у SimpleLogin нет, поэтому интеграция
 * не зависит от конкретной сборки плагина:
 * <ul>
 *   <li>ждём, пока игрок введёт пароль (команда /login или /register —
 *       список настраивается в config.yml);</li>
 *   <li>если у плагина авторизации случайно найдётся публичный метод
 *       вида {@code isLoggedIn/isAuthenticated(Player|UUID|String)} —
 *       дополнительно опрашиваем его рефлексией раз в секунду;</li>
 *   <li>если метода нет, считаем вход успешным, когда игрок после
 *       ввода пароля смог сдвинуться с места (SimpleLogin не даёт
 *       ходить неавторизованным — значит, раз ходит, пароль принят).</li>
 * </ul>
 * Пока игрок не авторизовался, VoidOferta его вообще не трогает:
 * не открывает книгу и не блокирует команды — иначе наш собственный
 * запрет команд не дал бы ему даже ввести /login.
 */
public final class AuthHook {

    public enum Probe { LOGGED_IN, NOT_LOGGED_IN, UNKNOWN }

    private final Plugin authPlugin;
    private final Method probeMethod;
    private final Logger logger;

    private AuthHook(Plugin authPlugin, Method probeMethod, Logger logger) {
        this.authPlugin = authPlugin;
        this.probeMethod = probeMethod;
        this.logger = logger;
    }

    /** Ищет включённый плагин авторизации по имени из конфига. */
    public static AuthHook detect(VoidOfertaPlugin plugin) {
        if (!plugin.getConfig().getBoolean("auth.enabled", true)) {
            plugin.getLogger().info("Интеграция с авторизацией отключена в config.yml — оферта показывается сразу при входе.");
            return new AuthHook(null, null, plugin.getLogger());
        }
        String wanted = plugin.getConfig().getString("auth.plugin-name", "SimpleLogin");
        Plugin found = null;
        for (Plugin candidate : plugin.getServer().getPluginManager().getPlugins()) {
            if (candidate.getName().equalsIgnoreCase(wanted) && candidate.isEnabled()) {
                found = candidate;
                break;
            }
        }
        if (found == null) {
            plugin.getLogger().info("Плагин авторизации «" + wanted + "» не найден — оферта показывается сразу при входе.");
            return new AuthHook(null, null, plugin.getLogger());
        }
        Method probe = findProbeMethod(found);
        plugin.getLogger().info("Найден плагин авторизации «" + found.getName() + "» — оферта будет показываться после входа по паролю."
                + (probe == null ? "" : " (найден метод проверки: " + probe.getName() + ")"));
        return new AuthHook(found, probe, plugin.getLogger());
    }

    public boolean isPresent() {
        return authPlugin != null;
    }

    /** Спрашивает у плагина авторизации, вошёл ли игрок. UNKNOWN — если спросить не у кого. */
    public Probe probe(Player player) {
        if (authPlugin == null || probeMethod == null) {
            return Probe.UNKNOWN;
        }
        try {
            Object arg = switch (probeMethod.getParameterTypes()[0].getSimpleName()) {
                case "UUID" -> player.getUniqueId();
                case "String" -> player.getName();
                default -> player;
            };
            Object result = probeMethod.invoke(authPlugin, arg);
            return Boolean.TRUE.equals(result) ? Probe.LOGGED_IN : Probe.NOT_LOGGED_IN;
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.fine("Не удалось опросить " + authPlugin.getName() + ": " + e.getMessage());
            return Probe.UNKNOWN;
        }
    }

    /**
     * Ищет публичный метод проверки входа: boolean, один аргумент
     * (Player / UUID / String / OfflinePlayer) и имя про логин/авторизацию.
     */
    private static Method findProbeMethod(Plugin authPlugin) {
        for (Method method : authPlugin.getClass().getMethods()) {
            if (!Modifier.isPublic(method.getModifiers())
                    || method.getReturnType() != boolean.class
                    || method.getParameterCount() != 1) {
                continue;
            }
            String name = method.getName().toLowerCase();
            if (!(name.contains("loggedin") || name.contains("authenticated")
                    || name.contains("authorized") || name.contains("authorised"))) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[0];
            if (param.isAssignableFrom(Player.class) || param == UUID.class || param == String.class) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }
}
