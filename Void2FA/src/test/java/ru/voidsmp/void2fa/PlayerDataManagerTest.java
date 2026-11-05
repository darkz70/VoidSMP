package ru.voidsmp.void2fa;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Persistence tests for account locks and trusted devices (real YAML, temp folder). */
class PlayerDataManagerTest {
    @TempDir File folder;
    private Void2FAPlugin plugin;
    private PlayerDataManager data;
    private final UUID id = UUID.randomUUID();

    @BeforeEach void setup() {
        plugin = mock(Void2FAPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        data = new PlayerDataManager(plugin);
    }

    @Test void lockSurvivesReloadAndUnlinkClearsIt() {
        assertFalse(data.isLocked(id));
        data.setLocked(id, true);
        data.load();
        assertTrue(data.isLocked(id));
        data.link(id, "Tester", 42L);
        assertTrue(data.isLocked(id));
        data.unlink(id);
        data.load();
        assertFalse(data.isLocked(id));
        assertFalse(data.isLinked(id));
    }

    @Test void trustedDeviceDeduplicatesRefreshesAndEvictsLeastRecentlyUsed() {
        PlayerDataManager.TrustedDevice a = data.addTrustedDevice(id, "192.0.2.1", 2);
        PlayerDataManager.TrustedDevice b = data.addTrustedDevice(id, "192.0.2.2", 2);
        assertNotNull(a); assertNotNull(b);
        assertEquals(2, data.getTrustedDevices(id).size());

        PlayerDataManager.TrustedDevice again = data.addTrustedDevice(id, "192.0.2.1", 2);
        assertNotNull(again);
        assertEquals(a.id, again.id);
        assertEquals(2, data.getTrustedDevices(id).size());

        data.touchTrustedDevice(id, "192.0.2.1");
        PlayerDataManager.TrustedDevice c = data.addTrustedDevice(id, "192.0.2.3", 2);
        assertNotNull(c);
        List<PlayerDataManager.TrustedDevice> devices = data.getTrustedDevices(id);
        assertEquals(2, devices.size());
        assertTrue(devices.stream().anyMatch(d -> d.ip.equals("192.0.2.1")));
        assertTrue(devices.stream().anyMatch(d -> d.ip.equals("192.0.2.3")));
        assertFalse(devices.stream().anyMatch(d -> d.ip.equals("192.0.2.2")));
        assertNull(data.addTrustedDevice(id, "?", 2));
        assertNull(data.addTrustedDevice(id, null, 2));
    }

    @Test void invalidateSessionSurvivesReloadAndKeepsDevicesAndLock() {
        data.link(id, "Tester", 42L);
        data.updateLastAuth(id, "192.0.2.10");
        data.addTrustedDevice(id, "192.0.2.10", 5);
        data.setLocked(id, true);

        data.invalidateSession(id);
        data.load();

        assertEquals(0L, data.getLastAuth(id));
        assertEquals("", data.getLastIp(id));
        assertEquals(1, data.getTrustedDevices(id).size());
        assertTrue(data.isLocked(id));
        assertTrue(data.isLinked(id));
    }

    @Test void trustedDeviceTtlAndRemoval() {
        PlayerDataManager.TrustedDevice device = data.addTrustedDevice(id, "192.0.2.10", 5);
        assertNotNull(device);
        assertTrue(data.isTrustedDevice(id, "192.0.2.10", 0L));
        assertTrue(data.isTrustedDevice(id, "192.0.2.10", 60_000L));
        assertFalse(data.isTrustedDevice(id, "192.0.2.99", 0L));
        assertEquals(1, data.removeTrustedDevice(id, device.id.toLowerCase(java.util.Locale.ROOT)));
        assertFalse(data.isTrustedDevice(id, "192.0.2.10", 0L));

        data.addTrustedDevice(id, "192.0.2.11", 5);
        data.addTrustedDevice(id, "192.0.2.12", 5);
        assertEquals(2, data.removeTrustedDevice(id, "all"));
        assertTrue(data.getTrustedDevices(id).isEmpty());
    }
}
