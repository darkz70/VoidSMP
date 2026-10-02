package me.darkz70.quirks.mechanic;

import java.util.List;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/** «Осколок души» — предмет, ПКМ по которому добавляет причуду в набор игрока. */
public final class SoulShards {

    private SoulShards() {}

    public static ItemStack makeShard(Quirk quirk, int level) {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&b✦ Осколок души — &f" + quirk.display() + " &dур. " + level));
            meta.lore(List.of(
                    Msg.color("&7Обломок души, похищенной демоном азарта."),
                    Msg.color("&eПКМ&7 — впустить причуду &d" + quirk.display() + " ур. " + level + "&7 в свой набор"),
                    Msg.color("&8VoidSMP")
            ));
            meta.getPersistentDataContainer().set(Keys.shardQuirk, PersistentDataType.STRING, quirk.id());
            meta.getPersistentDataContainer().set(Keys.shardLevel, PersistentDataType.INTEGER, level);
        });
        return item;
    }

    public static boolean isShard(@Nullable ItemStack item) {
        return item != null && item.getType() == Material.ECHO_SHARD && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(Keys.shardQuirk);
    }

    @Nullable
    public static Quirk quirkOf(@Nullable ItemStack item) {
        if (!isShard(item)) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(Keys.shardQuirk, PersistentDataType.STRING);
        return Quirk.byName(id);
    }

    public static int levelOf(@Nullable ItemStack item) {
        if (!isShard(item)) return 0;
        Integer level = item.getItemMeta().getPersistentDataContainer().get(Keys.shardLevel, PersistentDataType.INTEGER);
        return level == null ? 1 : Math.max(1, Math.min(3, level));
    }
}
