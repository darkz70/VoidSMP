package ru.voidsmp.void2fa;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Config defaults and message fallback for older local config.yml files. */
class ConfigManagerTest {
    private Void2FAPlugin plugin;
    private YamlConfiguration config;

    @BeforeEach void setup() {
        plugin = mock(Void2FAPlugin.class);
        config = new YamlConfiguration();
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(in);
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        when(plugin.getConfig()).thenReturn(config);
        doAnswer(inv -> null).when(plugin).reloadConfig();
    }

    @Test void newSettingsDefaultToEnabled() {
        ConfigManager manager = new ConfigManager(plugin);
        assertTrue(manager.isTrustedDevicesEnabled());
        assertTrue(manager.isRememberDeviceOnApprove());
        assertEquals(5, manager.getTrustedDeviceMax());
        assertEquals(0L, manager.getTrustedDeviceTtlMillis());
        assertTrue(manager.isAccountLockEnabled());
    }

    @Test void missingMessagesFallBackToBundledDefaults() {
        ConfigManager manager = new ConfigManager(plugin);
        assertFalse(manager.msg("locked-kick").isEmpty());
        assertFalse(manager.tg("tg-account-locked", "player", "Tester").isEmpty());
        assertFalse(manager.tg("tg-device-trusted").isEmpty());
        assertFalse(manager.tg("tg-trusted-device-login").isEmpty());
        assertFalse(manager.tg("tg-approved-trusted").isEmpty());
        assertFalse(manager.tg("tg-kicked", "player", "Tester").isEmpty());
        assertFalse(manager.tg("tg-revoked", "player", "Tester", "count", "2").isEmpty());
        assertFalse(manager.kick("owner-kick").isEmpty());
        assertFalse(manager.kick("revoke-kick").isEmpty());
    }

    @Test void localOverridesStillWin() {
        config.set("settings.trusted-devices.enabled", false);
        config.set("settings.trusted-devices.ttl-days", 7);
        config.set("settings.account-lock.enabled", false);
        config.set("messages.locked-kick", "custom");
        ConfigManager manager = new ConfigManager(plugin);
        assertFalse(manager.isTrustedDevicesEnabled());
        assertEquals(7 * 86_400_000L, manager.getTrustedDeviceTtlMillis());
        assertFalse(manager.isAccountLockEnabled());
        assertTrue(manager.kick("locked-kick").endsWith("custom"));
    }

    @Test void bridgeTimeoutsDefaultToValuesThatSurviveASlowBot() {
        ConfigManager manager = new ConfigManager(plugin);
        assertEquals(30, manager.getBridgeTimeoutSeconds());
        assertEquals(10, manager.getBridgeConnectTimeoutSeconds());
        // The old hard-coded 15s readiness window was shorter than the 20s request timeout,
        // so a slow but healthy bot was treated as permanently offline.
        assertTrue(manager.getBridgeReadyGraceSeconds() > manager.getBridgeTimeoutSeconds(),
                "readiness grace must outlast one request timeout");
        assertEquals(40, manager.getBridgeReadyGraceSeconds());
    }

    @Test void bridgeGraceTracksCustomTimeoutAndExplicitOverride() {
        config.set("telegram.bridge.timeout-seconds", 60);
        ConfigManager manager = new ConfigManager(plugin);
        assertEquals(60, manager.getBridgeTimeoutSeconds());
        assertEquals(70, manager.getBridgeReadyGraceSeconds());

        config.set("telegram.bridge.ready-grace-seconds", 120);
        manager.reload();
        assertEquals(120, manager.getBridgeReadyGraceSeconds());
    }

    @Test void bridgeTimeoutsAreClampedToSaneRange() {
        config.set("telegram.bridge.timeout-seconds", 1);
        config.set("telegram.bridge.connect-timeout-seconds", 999);
        config.set("telegram.bridge.ready-grace-seconds", 5000);
        ConfigManager manager = new ConfigManager(plugin);
        assertEquals(5, manager.getBridgeTimeoutSeconds());
        assertEquals(60, manager.getBridgeConnectTimeoutSeconds());
        assertEquals(600, manager.getBridgeReadyGraceSeconds());
    }

    @Test void activePollingIsFasterThanIdleToKeepLoginLatencyLow() {
        ConfigManager manager = new ConfigManager(plugin);
        assertEquals(1000, manager.getBridgePollIntervalMs());
        assertEquals(250, manager.getBridgeActivePollIntervalMs());
        assertTrue(manager.getBridgeActivePollIntervalMs() < manager.getBridgePollIntervalMs(),
                "a login must be polled faster than an idle server");
    }

    @Test void activePollIntervalNeverSlowerThanIdle() {
        config.set("telegram.bridge.poll-interval-ms", 300);
        config.set("telegram.bridge.active-poll-interval-ms", 2000);
        ConfigManager manager = new ConfigManager(plugin);
        assertEquals(300, manager.getBridgeActivePollIntervalMs());
    }

    @Test void pollIntervalsAreClamped() {
        config.set("telegram.bridge.poll-interval-ms", 1);
        config.set("telegram.bridge.active-poll-interval-ms", 99999);
        ConfigManager manager = new ConfigManager(plugin);
        assertEquals(200, manager.getBridgePollIntervalMs());
        // Clamped to 5000, then pulled down to the idle interval.
        assertEquals(200, manager.getBridgeActivePollIntervalMs());
    }
}
