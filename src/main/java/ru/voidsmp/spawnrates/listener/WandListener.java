package ru.voidsmp.spawnrates.listener;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import ru.voidsmp.spawnrates.VoidSpawnRatesPlugin;
import ru.voidsmp.spawnrates.zone.ZoneSelection;

/**
 * Выделение кубоида «палкой» (как в WorldEdit): ЛКМ — первая точка, ПКМ —
 * вторая. Палкой служит предмет из конфига, помеченный PersistentDataContainer,
 * поэтому обычный топор из сундука ничего не сломает.
 */
public final class WandListener implements Listener {

    private final VoidSpawnRatesPlugin plugin;

    public WandListener(VoidSpawnRatesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isWand(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(VoidSpawnRatesPlugin.PERMISSION_ADMIN)) {
            return;
        }
        // Присев с палкой, игрок хочет открыть/поставить блок, а не менять выделение.
        if (action == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        boolean first = action == Action.LEFT_CLICK_BLOCK;
        ZoneSelection selection = plugin.selections().getOrCreate(player.getUniqueId());
        ZoneSelection.Point point = new ZoneSelection.Point(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        ZoneSelection.Point other = first ? selection.second() : selection.first();
        if (other != null && !other.world.equalsIgnoreCase(point.world)) {
            // Вторая точка из другого мира — молча портить выделение нельзя.
            plugin.messages().send(player, "selection.world-mismatch",
                    ru.voidsmp.spawnrates.util.Args.of().put("world", point.world).asMap());
            return;
        }
        if (first) {
            selection.setFirst(point);
        } else {
            selection.setSecond(point);
        }
        plugin.messages().send(player, "selection.pos-set",
                ru.voidsmp.spawnrates.util.Args.of()
                        .put("index", first ? 1 : 2)
                        .put("x", point.x)
                        .put("y", point.y)
                        .put("z", point.z)
                        .put("world", point.world)
                        .asMap());
        if (selection.isComplete()) {
            plugin.messages().send(player, "selection.size",
                    ru.voidsmp.spawnrates.util.Args.of()
                            .put("x", selection.maxX() - selection.minX() + 1)
                            .put("y", selection.maxY() - selection.minY() + 1)
                            .put("z", selection.maxZ() - selection.minZ() + 1)
                            .put("chunks", selection.chunkCount())
                            .asMap());
            plugin.preview().drawBoxOnce(player, selection);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.selections().clear(event.getPlayer().getUniqueId());
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != wandMaterial()) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .has(wandKey(), PersistentDataType.BYTE);
    }

    public ItemStack createWand() {
        ItemStack item = new ItemStack(wandMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(wandKey(), PersistentDataType.BYTE, (byte) 1);
            meta.displayName(plugin.messages().parse("<gray>Палка выделения зон <dark_gray>(VoidSpawnRates)"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material wandMaterial() {
        return plugin.settings().wandMaterial;
    }

    private NamespacedKey wandKey() {
        return plugin.key("selection_wand");
    }
}
