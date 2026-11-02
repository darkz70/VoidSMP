package me.darkz70.quirks.listener;

import me.darkz70.quirks.mechanic.SculkWood;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Обслуживает «настоящие новые блоки» скалк-древесины (бревно/листва/доски),
 * реализованные через редкие состояния NOTE_BLOCK (см. {@link SculkWood}):
 * ставит их с нужным instrument-состоянием при размещении предмета, не даёт
 * игрокам случайно сменить ноту/инструмент ПКМ, и выдаёт правильный предмет
 * при ломании (вместо ванильного дропа нотного блока).
 */
public final class SculkWoodListener implements Listener {

    public SculkWoodListener(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        SculkWood.Type type = SculkWood.typeOfItem(event.getItemInHand());
        if (type == null) return;
        SculkWood.place(event.getBlockPlaced(), type);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        SculkWood.Type type = SculkWood.typeOf(block);
        if (type == null) return;
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), SculkWood.makeItem(type));
    }

    /** Блокируем ПКМ по нашим блокам — иначе ванильный нотный блок сменит ноту/вид. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (SculkWood.typeOf(block) != null) {
            event.setCancelled(true);
        }
    }
}
