package ru.voidsmp.fastboats;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.PEVersion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PacketVelocityTransportTest {
    private PacketEventsAPI<?> api;
    private PlayerManager manager;
    private Player driver;
    private Boat boat;

    @BeforeEach void setup() {
        api = mock(PacketEventsAPI.class);
        manager = mock(PlayerManager.class);
        driver = mock(Player.class);
        boat = mock(Boat.class);
        ServerManager server = mock(ServerManager.class);
        when(api.isInitialized()).thenReturn(true);
        when(api.getVersion()).thenReturn(new PEVersion(2, 13, 0));
        when(api.getServerManager()).thenReturn(server);
        when(server.getVersion()).thenReturn(ServerVersion.V_26_2);
        when(api.getPlayerManager()).thenReturn(manager);
        when(manager.getUser(driver)).thenReturn(mock(User.class));
        when(manager.getChannel(driver)).thenReturn(new Object());
        when(manager.getClientVersion(driver)).thenReturn(ClientVersion.V_26_2);
        when(boat.getEntityId()).thenReturn(1234);
        PacketEvents.setAPI(api);
    }

    @AfterEach void cleanup() { PacketEvents.setAPI(null); }

    @Test void sendsBoatMotionDirectlyToDriverWithoutChangingServerVelocity() {
        new PacketVelocityTransport().send(driver, boat, 0.5, -0.01, 0.25);
        var packet = ArgumentCaptor.forClass(WrapperPlayServerEntityVelocity.class);
        verify(manager).sendPacket(eq(driver), packet.capture());
        assertEquals(1234, packet.getValue().getEntityId());
        assertEquals(0.5, packet.getValue().getVelocity().x);
        assertEquals(-0.01, packet.getValue().getVelocity().y);
        assertEquals(0.25, packet.getValue().getVelocity().z);
        verify(boat, never()).setVelocity(any());
        verify(driver, never()).setVelocity(any());
    }

    @Test void missingOrUninitializedApiFailsClearly() {
        PacketEvents.setAPI(null);
        assertThrows(IllegalStateException.class, PacketVelocityTransport::new);
        PacketEvents.setAPI(api);
        when(api.isInitialized()).thenReturn(false);
        assertThrows(IllegalStateException.class, PacketVelocityTransport::new);
    }

    @Test void unsupportedDependencyFailsClearly() {
        when(api.getVersion()).thenReturn(new PEVersion(2, 12, 0));
        assertThrows(IllegalStateException.class, PacketVelocityTransport::new);
    }

    @Test void rejectsInvalidVectorAndDisconnectedDriver() {
        var transport = new PacketVelocityTransport();
        assertThrows(IllegalArgumentException.class, () -> transport.send(driver, boat, Double.NaN, 0, 0));
        when(manager.getUser(driver)).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> transport.send(driver, boat, 1, 0, 0));
    }

    @Test void doesNotOverflowOldClientsShortVelocityFormat() {
        when(manager.getClientVersion(driver)).thenReturn(ClientVersion.V_1_21_7);
        assertThrows(IllegalStateException.class, () -> new PacketVelocityTransport().send(driver, boat, 10, 0, 0));
    }
}
