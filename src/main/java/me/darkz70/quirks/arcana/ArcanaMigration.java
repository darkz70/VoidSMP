package me.darkz70.quirks.arcana;

import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Автозамена старых (с причудой) фолиантов Арканы на новые (без причуды) —
 * по просьбе владельца: у игроков не должно остаться старых предметов, только
 * новые. Затрагивает ТОЛЬКО сами предметы-фолианты, лежащие в инвентаре/эндер-сундуке
 * (не трогает уже изученный прогресс — стихии, выученные старым фолиантом, как
 * и раньше кастуются только с причудой MAGE/ADMIN).
 *
 * Срабатывает: один раз при запуске/перезагрузке плагина для всех, кто уже на
 * сервере, и при каждом входе — для тех, кто был офлайн на момент обновления.
 */
public final class ArcanaMigration implements Listener {

    private final VoidQuirksPlugin plugin;

    public ArcanaMigration(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    /** Разовый проход по всем игрокам онлайн — вызывается при включении плагина. */
    public void migrateOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            migrate(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        migrate(event.getPlayer());
    }

    /** Заменяет старые фолианты на новые в основном инвентаре и эндер-сундуке игрока. */
    public int migrate(Player player) {
        int count = migrateContainer(player.getInventory());
        count += migrateContainer(player.getEnderChest());
        if (count > 0) {
            plugin.getLogger().info("Аркана: заменено " + count + " старых фолиантов на новые (без причуды) у "
                    + player.getName() + ".");
        }
        return count;
    }

    private int migrateContainer(Inventory inv) {
        int count = 0;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            Arcana element = ArcanaItems.tomeElement(stack);
            if (element == null) continue;
            ItemStack replacement = ArcanaItems.makeFreeTome(element);
            replacement.setAmount(stack.getAmount());
            inv.setItem(i, replacement);
            count++;
        }
        return count;
    }
}
