package me.darkz70.quirks.arcana;

import org.bukkit.entity.Player;

/** Исполнение одного заклинания Arcana. */
@FunctionalInterface
public interface ArcanaEffect {
    void cast(ArcanaSystem system, Player player, Arcana element, int level);
}
