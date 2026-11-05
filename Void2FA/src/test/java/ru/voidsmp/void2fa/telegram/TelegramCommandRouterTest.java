package ru.voidsmp.void2fa.telegram;

import org.junit.jupiter.api.*;
import ru.voidsmp.void2fa.AuthManager;
import ru.voidsmp.void2fa.ConfigManager;
import ru.voidsmp.void2fa.Void2FAPlugin;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/** Telegram command routing stays private-chat only and delegates ownership checks to AuthManager. */
class TelegramCommandRouterTest {
    private Void2FAPlugin plugin;
    private ConfigManager config;
    private AuthManager auth;
    private TelegramTransport bot;
    private TelegramCommandRouter router;

    @BeforeEach void setup() {
        plugin=mock(Void2FAPlugin.class); config=mock(ConfigManager.class);
        auth=mock(AuthManager.class); bot=mock(TelegramTransport.class);
        when(plugin.getBot()).thenReturn(bot);
        when(plugin.getConfigManager()).thenReturn(config);
        when(plugin.getAuthManager()).thenReturn(auth);
        when(bot.getBotUsername()).thenReturn("test_bot");
        when(config.tg(anyString(), any(String[].class))).thenReturn("message");
        router=new TelegramCommandRouter(plugin);
    }

    @Test void kickAndRevokeReachAuthManager() {
        router.text(42L,42L,"/kick");
        router.text(42L,42L,"/revoke");
        router.text(42L,42L,"/untrust all");
        verify(auth).kickByTelegram(42L);
        verify(auth).revokeByTelegram(42L);
        verify(auth).untrustDeviceByTelegram(42L,"all");
    }

    @Test void addressedCommandsUseBotUsername() {
        router.text(42L,42L,"/kick@test_bot");
        router.text(42L,42L,"/revoke@TEST_BOT");
        verify(auth).kickByTelegram(42L);
        verify(auth).revokeByTelegram(42L);
        verify(bot, never()).sendMessage(anyLong(), anyString());
    }

    @Test void extraArgumentsOnlyReturnUsage() {
        router.text(42L,42L,"/kick someone");
        router.text(42L,42L,"/revoke someone");
        verifyNoInteractions(auth);
        verify(bot,times(2)).sendMessage(eq(42L),anyString());
    }

    @Test void groupOrSpoofedChatCannotKickOrRevoke() {
        router.text(42L,-100L,"/kick");
        router.text(42L,43L,"/revoke");
        router.text(0L,0L,"/revoke");
        verifyNoInteractions(auth,bot);
    }

    @Test void helpListsEveryCommandEvenWithOldConfigText() {
        when(config.tg("tg-help")).thenReturn("Команды:\n/link <код> — привязать\n/status — статус");
        router.text(42L,42L,"/help");
        var captor=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessage(eq(42L),captor.capture());
        String help=captor.getValue();
        for (String command : new String[]{"/link","/status","/unlink","/lock","/unlock","/devices","/untrust","/kick","/revoke","/help"}) {
            assertTrue(help.contains(command), command);
        }
        verifyNoInteractions(auth);
    }

    @Test void startWithoutLinkTokenAlsoListsCommands() {
        when(config.tg("tg-start")).thenReturn("Привет! Привяжи аккаунт: /2fa link");
        router.text(42L,42L,"/start");
        var captor=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessage(eq(42L),captor.capture());
        assertTrue(captor.getValue().contains("/kick"));
        assertTrue(captor.getValue().contains("/revoke"));
        assertTrue(captor.getValue().contains("/help"));
        verifyNoInteractions(auth);
    }

    @Test void menuCoversEveryRoutedCommand() {
        var names=new java.util.HashSet<String>();
        for (Object entry : TelegramHelp.menuCommands()) {
            names.add(String.valueOf(((java.util.Map<?,?>)entry).get("command")));
        }
        assertTrue(names.containsAll(java.util.List.of("start","help","link","status","unlink",
                "lock","unlock","kick","revoke","devices","untrust")));
    }

    @Test void wrongBotAddressIsIgnored() {
        router.text(42L,42L,"/kick@other_bot");
        verifyNoInteractions(auth);
        verify(bot, never()).sendMessage(anyLong(), anyString());
    }
}
