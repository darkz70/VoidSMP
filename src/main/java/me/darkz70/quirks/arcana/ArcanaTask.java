package me.darkz70.quirks.arcana;

import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/** Раз в секунду: реген маны Арканы + actionbar, только для носителей причуды MAGE. */
public final class ArcanaTask extends BukkitRunnable {

    private final VoidQuirksPlugin plugin;

    public ArcanaTask(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        ArcanaSystem arcana = plugin.arcana();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!arcana.hasQuirk(player)) continue;
            arcana.tickSecond(player);
            showBarIfNeeded(player, arcana);
        }
    }

    private void showBarIfNeeded(Player player, ArcanaSystem arcana) {
        PlayerData data = arcana.data(player);
        Arcana element = arcana.activeElement(player);
        if (data == null || element == null) return;
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (!ArcanaItems.isFocusStaff(main) && !ArcanaItems.isFocusStaff(off)) return;
        int count = ArcanaSpells.of(element).length;
        int idx = Math.max(0, Math.min(data.arcanaSpellIndex(element.id()), count - 1));
        arcana.showBar(player, element, data, idx);
    }
}
