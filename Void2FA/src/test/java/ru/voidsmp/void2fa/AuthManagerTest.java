package ru.voidsmp.void2fa;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import ru.voidsmp.void2fa.telegram.TelegramTransport;
import java.util.UUID;
import java.net.InetSocketAddress;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** State-machine tests without a Telegram token, network or running Minecraft server. */
class AuthManagerTest {
    private Void2FAPlugin plugin;
    private ConfigManager config;
    private PlayerDataManager data;
    private TelegramTransport bot;
    private Player player;
    private AuthManager auth;
    private MockedStatic<Bukkit> bukkit;
    private final UUID id = UUID.randomUUID();

    @BeforeEach void setup() {
        plugin=mock(Void2FAPlugin.class); config=mock(ConfigManager.class); data=mock(PlayerDataManager.class);
        bot=mock(TelegramTransport.class); player=mock(Player.class);
        Server server=mock(Server.class); BukkitScheduler scheduler=mock(BukkitScheduler.class);
        bukkit=mockStatic(Bukkit.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
        when(plugin.getBot()).thenReturn(bot); when(plugin.getConfigManager()).thenReturn(config);
        when(bot.isReady()).thenReturn(true); when(bot.getBotUsername()).thenReturn("test_bot");
        when(player.getUniqueId()).thenReturn(id); when(player.getName()).thenReturn("Tester");
        when(player.hasPermission(anyString())).thenReturn(false);
        when(data.isLinked(id)).thenReturn(true); when(data.isEnabled(id)).thenReturn(true); when(data.getTelegramId(id)).thenReturn(42L);
        when(config.getCodeLength()).thenReturn(6); when(config.getCodeTtlSeconds()).thenReturn(120);
        when(config.getAuthTimeoutSeconds()).thenReturn(90); when(config.getMaxAttempts()).thenReturn(2);
        when(config.getSessionMinutes()).thenReturn(0); when(config.isBlindness()).thenReturn(false);
        when(config.isAccountLockEnabled()).thenReturn(true);
        when(config.isTrustedDevicesEnabled()).thenReturn(true);
        when(config.isRememberDeviceOnApprove()).thenReturn(true);
        when(config.getTrustedDeviceMax()).thenReturn(5);
        when(config.getTrustedDeviceTtlMillis()).thenReturn(0L);
        when(data.getTrustedDevices(id)).thenReturn(java.util.List.of());
        when(config.tg(anyString(), any(String[].class))).thenReturn("message");
        when(config.msg(anyString(), any(String[].class))).thenReturn("message");
        when(config.kick(anyString(), any(String[].class))).thenReturn("kick");
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(mock(BukkitTask.class));
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        auth=new AuthManager(plugin,config,data);
    }
    @AfterEach void teardown() { bukkit.close(); }

    private String joinAndCaptureAuthId() {
        auth.onJoin(player);
        var captor=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(bot).sendAuthRequest(captor.capture(),eq(42L),anyString(),any(),any());
        return captor.getValue();
    }

    @Test void onlyCurrentLinkedOwnerCanApproveAndReplayFails() {
        String authId=joinAndCaptureAuthId();
        assertFalse(auth.approveByAuthId(authId,99L));
        assertTrue(auth.isPending(id));
        assertTrue(auth.approveByAuthId(authId,42L));
        assertFalse(auth.isPending(id));
        assertFalse(auth.approveByAuthId(authId,42L));
        verify(data).updateLastAuth(eq(id),anyString());
    }

    @Test void denyAndQuitInvalidateButton() {
        String authId=joinAndCaptureAuthId();
        assertTrue(auth.denyByAuthId(authId,42L));
        verify(player).kickPlayer("kick");
        assertFalse(auth.approveByAuthId(authId,42L));
        clearInvocations(bot);
        String second=joinAndCaptureAuthId();
        auth.onQuit(player);
        assertFalse(auth.approveByAuthId(second,42L));
    }

    @Test void resendRotatesIdAndDoesNotResetWrongCodeBudget() {
        String first=joinAndCaptureAuthId();
        auth.submitCode(player,"wrong");
        // Move beyond cooldown without sleeping.
        try {
            var pending=AuthManager.class.getDeclaredField("pending"); pending.setAccessible(true);
            var map=(java.util.Map<?,?>)pending.get(auth); Object state=map.get(id);
            var field=state.getClass().getDeclaredField("lastSentAt"); field.setAccessible(true);
            field.setLong(state,System.currentTimeMillis()-11000);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        auth.resend(player);
        var captor=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(bot,times(2)).sendAuthRequest(captor.capture(),eq(42L),anyString(),any(),any());
        String second=captor.getAllValues().get(1);
        assertNotEquals(first,second);
        assertFalse(auth.approveByAuthId(first,42L));
        auth.submitCode(player,"wrong");
        verify(player).kickPlayer("kick");
        assertFalse(auth.isPending(id));
    }

    @Test void lockedAccountKicksEvenWithBypass() {
        when(data.isLocked(id)).thenReturn(true);
        when(player.hasPermission("void2fa.bypass")).thenReturn(true);
        auth.onJoin(player);
        verify(player).kickPlayer("kick");
        verify(bot, never()).sendAuthRequest(anyString(), anyLong(), anyString(), any(), any());
        assertFalse(auth.isPending(id));
    }

    @Test void trustedDeviceSkipsTelegramButUnknownDeviceStillNeedsAuth() {
        when(player.getAddress()).thenReturn(new InetSocketAddress("192.0.2.10", 25565));
        when(data.isTrustedDevice(id, "192.0.2.10", 0L)).thenReturn(true);
        auth.onJoin(player);
        assertFalse(auth.isPending(id));
        verify(data).touchTrustedDevice(id, "192.0.2.10");
        verify(bot, never()).sendAuthRequest(anyString(), anyLong(), anyString(), any(), any());

        when(player.getAddress()).thenReturn(new InetSocketAddress("192.0.2.99", 25565));
        String authId=joinAndCaptureAuthId();
        assertTrue(auth.isPending(id));
        assertTrue(auth.approveByAuthId(authId,42L));
    }

    @Test void approveRemembersDeviceWhenEnabled() {
        when(player.getAddress()).thenReturn(new InetSocketAddress("192.0.2.10", 25565));
        String authId=joinAndCaptureAuthId();
        assertTrue(auth.approveByAuthId(authId,42L));
        verify(data).addTrustedDevice(id, "192.0.2.10", 5);
    }

    @Test void telegramLockKicksOnlinePlayerAndUnlockRestoresJoin() {
        when(data.findByTelegramId(42L)).thenReturn(id);
        when(data.getName(id)).thenReturn("Tester");
        auth.lockByTelegram(42L);
        verify(data).setLocked(id, true);
        verify(player).kickPlayer("kick");

        when(data.isLocked(id)).thenReturn(true);
        auth.onJoin(player);
        verify(player, times(2)).kickPlayer("kick");

        auth.unlockByTelegram(42L);
        verify(data).setLocked(id, false);
    }

    @Test void adminResetDoesNotSilentlyAuthorizePendingLogin() {
        String authId=joinAndCaptureAuthId();
        auth.adminReset(id);
        verify(player).kickPlayer("kick");
        assertFalse(auth.isPending(id));
        assertFalse(auth.approveByAuthId(authId,42L));
    }
}
