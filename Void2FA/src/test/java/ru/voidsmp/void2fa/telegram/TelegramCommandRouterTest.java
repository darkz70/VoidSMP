package ru.voidsmp.void2fa.telegram;

import org.junit.jupiter.api.*;
import ru.voidsmp.void2fa.AuthManager;
import ru.voidsmp.void2fa.ConfigManager;
import ru.voidsmp.void2fa.Void2FAPlugin;

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

    @Test void wrongBotAddressIsIgnored() {
        router.text(42L,42L,"/kick@other_bot");
        verifyNoInteractions(auth);
        verify(bot, never()).sendMessage(anyLong(), anyString());
    }
}
