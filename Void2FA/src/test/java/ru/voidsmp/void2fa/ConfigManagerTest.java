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
        assertFalse(manager.msg("device-trusted", "id", "ABCD1234", "ip", "192.0.2.1").isEmpty());
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
}
