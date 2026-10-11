package ru.voidsmp.fastboats;

import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;

/** Kept separate so diagnostics can load even when PacketEvents is absent. */
interface VelocityTransport {
    void send(Player driver, Boat boat, double x, double y, double z);
}
