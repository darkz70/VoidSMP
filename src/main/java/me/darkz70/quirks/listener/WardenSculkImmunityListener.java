package me.darkz70.quirks.listener;

import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * Вардены больше не воспринимают игроков со Скалком (обычным или Главой) от 2 уровня
 * как угрозу — "тьма = дом" теперь не только ночное зрение, но и реальное перемирие
 * с главным обитателем Глубинных пещер. Ловим ОБА пути агрессии на всякий случай:
 *  - EntityTargetLivingEntityEvent — варден вообще не берёт игрока в цель (вой/рык не будет);
 *  - EntityDamageByEntityEvent — подстраховка, если цель уже была выбрана ДО получения
 *    причуды (например, игрок повысил уровень скалка уже во время погони).
 */
public final class WardenSculkImmunityListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public WardenSculkImmunityListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean isSculkSafe(Player player) {
        return plugin.quirks().sculkOf(player) >= 2;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Warden)) return;
        if (!(event.getTarget() instanceof Player player)) return;
        if (isSculkSafe(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Warden)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (isSculkSafe(player)) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Warden warden) {
                warden.setTarget(null);
            }
        }
    }
}
