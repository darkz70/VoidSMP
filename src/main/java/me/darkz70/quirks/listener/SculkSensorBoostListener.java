package me.darkz70.quirks.listener;

import me.darkz70.quirks.mechanic.SculkWood;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Скалк-древесина (бревно/листва/доски) усиливает дальность вибраций рядом с собой —
 * обычные скалк-сенсоры (и всё остальное, что слушает игровые события вибраций —
 * стражи, аллаи) замечают события чуть дальше обычного, если рядом есть наш блок.
 * Используется официальный публичный API ({@link GenericGameEvent#setRadius(int)}),
 * без каких-либо NMS-хаков — это тот же канал, которым вибрации и так пользуются.
 */
public final class SculkSensorBoostListener implements Listener {

    private static final double CHECK_RADIUS = 16.0; // где ищем скалк-древесину рядом с событием
    private static final int BOOST = 8;               // на сколько блоков расширяем радиус
    private static final int MAX_RADIUS = 24;          // потолок, чтобы не было абсурда

    public SculkSensorBoostListener(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameEvent(GenericGameEvent event) {
        if (event.getRadius() >= MAX_RADIUS) return;
        if (!SculkWood.hasNodeNear(event.getLocation(), CHECK_RADIUS)) return;
        event.setRadius(Math.min(MAX_RADIUS, event.getRadius() + BOOST));
    }
}
