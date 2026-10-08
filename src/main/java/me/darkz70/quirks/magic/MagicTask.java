package me.darkz70.quirks.magic;

import me.darkz70.quirks.Keys;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

/** Раз в секунду: реген маны, ауры, actionbar с маной и выбранным заклинанием. */
public final class MagicTask extends BukkitRunnable {

    private final VoidQuirksPlugin plugin;
    private int tick = 0;

    public MagicTask(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tick++;
        boolean even = (tick % 2) == 0; // каждая вторая секунда — тик «медленного» регена
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            magic().tickSecond(player, even);
            magic().tickAuras(player);
            me.darkz70.quirks.listener.PotionListener.tickWatchers(plugin, player, now);
            showBarIfNeeded(player);
        }
    }

    private MagicSystem magic() {
        return plugin.magic();
    }

    private void showBarIfNeeded(Player player) {
        PlayerData data = magic().data(player);
        Element element = magic().elementOf(player);
        if (data == null || element == null) return;
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        boolean focus = hasFocus(main) || hasFocus(off);
        if (!focus) return;
        Spell[] spells = Spell.of(element);
        int idx = Math.max(0, Math.min(data.selectedSpell(), spells.length - 1));
        player.sendActionBar(Msg.comp("magic-bar",
                "%emoji%", element.emoji(),
                "%mana%", String.valueOf((int) Math.floor(data.mana())),
                "%max%", String.valueOf(magic().maxMana(data.magicLevel())),
                "%element%", element.display(),
                "%spell%", spells[idx].name()));
    }

    private static boolean hasFocus(ItemStack item) {
        return item != null && !item.getType().isAir()
                && item.getPersistentDataContainer().has(Keys.focusKey, PersistentDataType.BYTE);
    }
}
