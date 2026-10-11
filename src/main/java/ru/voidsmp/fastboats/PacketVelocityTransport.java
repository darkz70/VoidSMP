package ru.voidsmp.fastboats;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.PEVersion;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;

final class PacketVelocityTransport implements VelocityTransport {
    PacketVelocityTransport() {
        var api = PacketEvents.getAPI();
        if (api == null || !api.isInitialized()) {
            throw new IllegalStateException("PacketEvents ещё не инициализирован");
        }
        if (api.getVersion().compareTo(new PEVersion(2, 13, 0)) < 0) {
            throw new IllegalStateException("Для Paper 26.2 требуется PacketEvents 2.13.0 или новее");
        }
    }

    @Override
    public void send(Player driver, Boat boat, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Неконечный вектор скорости");
        }
        var manager = PacketEvents.getAPI().getPlayerManager();
        if (manager.getUser(driver) == null || manager.getChannel(driver) == null) {
            throw new IllegalStateException("PacketEvents не видит подключение водителя");
        }
        if (manager.getClientVersion(driver).isOlderThan(ClientVersion.V_1_21_9)) {
            throw new IllegalStateException("Старый протокол клиента не поддерживает новый формат скорости");
        }
        // Address the BOAT entity, not the player. Do not mutate NMS deltaMovement:
        // the server boat tick can reset it before the tracker transmits it.
        // Only the controlling client simulates this boat; viewers receive normal position updates.
        manager.sendPacket(driver, new WrapperPlayServerEntityVelocity(
                boat.getEntityId(), new Vector3d(x, y, z)));
    }
}
