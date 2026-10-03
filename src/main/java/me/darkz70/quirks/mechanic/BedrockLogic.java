package me.darkz70.quirks.mechanic;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/** Логика причуды «Бедрок»: запечатанные слоты, стейки и ударная волна. */
public final class BedrockLogic {

    /** Центральный слот основного инвентаря (ряд 2, колонка 5). */
    public static final int BEDROCK_SLOT = 22;

    private BedrockLogic() {}

    public static void init(VoidQuirksPlugin plugin) {
        // пока нечего инициализировать — метод для симметрии/будущего
    }

    /** Слоты, занятые барьерами (без слота бедрока). */
    public static Set<Integer> barrierSlots(int level) {
        Set<Integer> slots = new HashSet<>();
        switch (level) {
            case 1 -> {
                for (int i = 9; i <= 35; i++) {
                    if (i != BEDROCK_SLOT) slots.add(i);
                }
            }
            case 2 -> {
                for (int i = 9; i <= 21; i++) slots.add(i);
            }
            default -> { }
        }
        return slots;
    }

    /** Все запечатанные слоты (барьеры + бедрок в центре). */
    public static Set<Integer> lockedSlots(int level) {
        Set<Integer> slots = barrierSlots(level);
        slots.add(BEDROCK_SLOT);
        return slots;
    }

    public static boolean isLocked(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(Keys.lockedItem);
    }

    public static ItemStack barrierItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&cЗапечатанная ячейка души"));
            meta.getPersistentDataContainer().set(Keys.lockedItem, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public static ItemStack bedrockItem() {
        ItemStack item = new ItemStack(Material.BEDROCK);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&8Каменное сердце души"));
            meta.getPersistentDataContainer().set(Keys.lockedItem, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    /** Полностью применить раскладку (и подчистить «убежавшие» запечатанные предметы). */
    public static void applyLayout(VoidQuirksPlugin plugin, Player player, int level) {
        purge(player, lockedSlots(level));
        ensure(player, level);
    }

    /** Убрать ВСЕ запечатанные предметы с игрока (при снятии причуды). */
    public static void clearLayout(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isLocked(inv.getItem(i))) inv.setItem(i, null);
        }
        if (isLocked(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    /** Удаляет запечатанные предметы из всех мест, кроме своих слотов. */
    private static void purge(Player player, Set<Integer> allowedSlots) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isLocked(item) && !allowedSlots.contains(i)) {
                inv.setItem(i, null);
            }
        }
        if (isLocked(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        // и из открытого контейнера (верхний инвентарь)
        Inventory top = player.getOpenInventory().getTopInventory();
        for (int i = 0; i < top.getSize(); i++) {
            if (isLocked(top.getItem(i))) {
                top.setItem(i, null);
            }
        }
    }

    /** Гарантирует, что запечатанные слоты на месте; чужие вещи вытесняются, не удаляются. */
    private static void ensure(Player player, int level) {
        PlayerInventory inv = player.getInventory();
        Set<Integer> locked = lockedSlots(level);
        for (int slot : locked) {
            Material want = slot == BEDROCK_SLOT ? Material.BEDROCK : Material.BARRIER;
            ItemStack current = inv.getItem(slot);
            if (isLocked(current) && current.getType() == want) continue;
            if (current != null && !isLocked(current)) {
                displace(player, inv, current, locked);
            }
            inv.setItem(slot, want == Material.BEDROCK ? bedrockItem() : barrierItem());
        }
    }

    /** Перекладывает предмет в свободный слот вне запечатанных; нет места — выкидывает под ноги. */
    private static void displace(Player player, PlayerInventory inv, ItemStack item, Set<Integer> locked) {
        for (int i = 0; i <= 35; i++) {
            if (locked.contains(i)) continue;
            ItemStack at = inv.getItem(i);
            if (at == null || at.getType().isAir()) {
                inv.setItem(i, item);
                return;
            }
        }
        player.getWorld().dropItemNaturally(player.getLocation(), item);
    }

    /**
     * Бафф голода: стейки (все уровни) + удар по монстрам (2 уровень).
     * Вызывается при смене сытости и периодически из EffectsTask.
     */
    public static void hungerCheck(VoidQuirksPlugin plugin, Player player, PlayerData data, int level) {
        if (player.isDead() || !player.isOnline()) return;
        double drums = plugin.getConfig().getDouble("bedrock.hunger-threshold-drumsticks." + level, 3.0);
        int thresholdPoints = (int) Math.round(drums * 2.0);
        if (player.getFoodLevel() > thresholdPoints) return;

        long now = System.currentTimeMillis();

        long steakCd = plugin.getConfig().getLong("bedrock.steak-cooldown-minutes", 30) * 60_000L;
        if (now >= data.cooldown("steak")) {
            int amount = plugin.getConfig().getInt("bedrock.steak-amount." + level, 4);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, Math.max(1, amount)));
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            data.setCooldown("steak", now + steakCd);
            plugin.storage().save();
            plugin.notify(player, "bedrock-steaks", "%amount%", String.valueOf(amount));
        }

        if (level == 2) {
            long aoeCd = plugin.getConfig().getLong("bedrock.aoe-cooldown-minutes", 10) * 60_000L;
            if (now >= data.cooldown("aoe")) {
                double radius = plugin.getConfig().getDouble("bedrock.aoe-radius", 10.0);
                double damage = plugin.getConfig().getDouble("bedrock.aoe-damage", 8.0);
                boolean hit = false;
                for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                    if (entity instanceof Zombie || entity instanceof Skeleton
                            || entity instanceof Spider || entity instanceof Creeper) {
                        ((LivingEntity) entity).damage(damage, player);
                        hit = true;
                    }
                }
                if (hit) {
                    data.setCooldown("aoe", now + aoeCd);
                    plugin.storage().save();
                    plugin.notify(player, "bedrock-aoe");
                }
            }
        }
    }
}
