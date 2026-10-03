package me.darkz70.quirks.listener;

import java.util.List;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Nullable;

/**
 * Крафтовая механика v0.6.0:
 * зелье вреда + зелье исцеления -> «Нейтрализованное зелье»;
 * нейтрализованное + смола + сота -> «Антидот от скалка» (снимает Скалк любого уровня);
 * 6 костной муки + семена -> «Супер-удобрение» (крафтит только Фермер, работает как 6 костной муки).
 */
public final class CraftListener implements Listener {

    private static final String NEUTRAL = "neutral";
    private static final String ANTIDOTE = "antidote";
    private static final String FERTILIZER = "fertilizer";

    /** Ключ рецепта супер-удобрения (видимость книги рецептов — только у Фермера). */
    public static NamespacedKey fertilizerKey;

    /** Выдать/забрать видимость рецепта удобрения у игрока. */
    public static void syncFertilizerRecipe(Player player, boolean farmer) {
        if (fertilizerKey == null) return;
        if (farmer) {
            player.discoverRecipe(fertilizerKey);
        } else {
            player.undiscoverRecipe(fertilizerKey);
        }
    }

    private final VoidQuirksPlugin plugin;

    public CraftListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    public static void registerRecipes(VoidQuirksPlugin plugin) {
        NamespacedKey neutralKey = new NamespacedKey(plugin, "neutral_potion");
        ShapelessRecipe neutral = new ShapelessRecipe(neutralKey, makeNeutral());
        neutral.addIngredient(new RecipeChoice.ExactChoice(basePotion(PotionType.HARMING)));
        neutral.addIngredient(new RecipeChoice.ExactChoice(basePotion(PotionType.HEALING)));
        add(neutralKey, neutral);

        NamespacedKey antidoteKey = new NamespacedKey(plugin, "sculk_antidote");
        ShapelessRecipe antidote = new ShapelessRecipe(antidoteKey, makeAntidote());
        antidote.addIngredient(new RecipeChoice.ExactChoice(makeNeutral()));
        antidote.addIngredient(Material.RESIN_CLUMP);
        antidote.addIngredient(Material.HONEYCOMB);
        add(antidoteKey, antidote);

        fertilizerKey = new NamespacedKey(plugin, "super_fertilizer");
        ShapelessRecipe fert = new ShapelessRecipe(fertilizerKey, makeFertilizer());
        for (int i = 0; i < 6; i++) {
            fert.addIngredient(Material.BONE_MEAL);
        }
        fert.addIngredient(Material.WHEAT_SEEDS);
        add(fertilizerKey, fert);
    }

    private static void add(NamespacedKey key, ShapelessRecipe recipe) {
        if (Bukkit.getRecipe(key) != null) Bukkit.removeRecipe(key);
        Bukkit.addRecipe(recipe);
    }

    private static ItemStack basePotion(PotionType type) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }

    /** Зелье «без эффекта» — нейтрализация вреда и исцеления. */
    public static ItemStack makeNeutral() {
        ItemStack item = basePotion(PotionType.WATER);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.displayName(Msg.color("&7Нейтрализованное зелье"));
        meta.lore(List.of(
                Msg.color("&7Тихое, безвредное, почти бесполезное."),
                Msg.color("&8Зелье вреда + зелье исцеления")));
        meta.getPersistentDataContainer().set(Keys.brewMark, PersistentDataType.STRING, NEUTRAL);
        item.setItemMeta(meta);
        return item;
    }

    /** Антидот от скалка — выпил, и причуда Скалка снимается полностью. */
    public static ItemStack makeAntidote() {
        ItemStack item = basePotion(PotionType.AWKWARD);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.displayName(Msg.color("&2Антидот от скалка"));
        meta.lore(List.of(
                Msg.color("&7Выпей, чтобы изгнать Скалк любого уровня."),
                Msg.color("&8Нейтрализованное зелье + смола + сота")));
        meta.setColor(Color.fromRGB(0x3B, 0xD3, 0x7A));
        meta.getPersistentDataContainer().set(Keys.brewMark, PersistentDataType.STRING, ANTIDOTE);
        item.setItemMeta(meta);
        return item;
    }

    /** Супер-удобрение Фермера — как 6 костной муки за раз. */
    public static ItemStack makeFertilizer() {
        ItemStack item = new ItemStack(Material.BONE_MEAL);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&6Супер-удобрение"));
            meta.lore(List.of(
                    Msg.color("&7Работает как 6 костной муки за один клик."),
                    Msg.color("&8Только Фермер знает рецепт: 6 костной муки + семена")));
            meta.getPersistentDataContainer().set(Keys.brewMark, PersistentDataType.STRING, FERTILIZER);
        });
        return item;
    }

    private static boolean tagged(@Nullable ItemStack item, String tag) {
        if (item == null || !item.hasItemMeta()) return false;
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.brewMark, PersistentDataType.STRING);
        return tag.equals(value);
    }

    /** Не-фермеру результат удобрения даже не рисуем в выдаче стола. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        ItemStack result = event.getInventory().getResult();
        if (tagged(result, FERTILIZER) && plugin.quirks().levelOf(player, Quirk.FARMER) == 0) {
            event.getInventory().setResult(null);
        }
    }

    /** Супер-удобрение крафтит только Фермер. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getInventory().getResult();
        if (tagged(result, FERTILIZER) && plugin.quirks().levelOf(player, Quirk.FARMER) == 0) {
            event.setCancelled(true);
            Msg.send(player, "farmer-craft-denied");
        }
    }

    /** Антидот: снимает Скалк любого уровня. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!tagged(item, ANTIDOTE)) return;
        Player player = event.getPlayer();
        if (plugin.quirks().levelOf(player, Quirk.SCULK) > 0) {
            plugin.quirks().remove(player, Quirk.SCULK);
            player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.8f, 1.4f);
            plugin.notify(player, "antidote-cured");
        } else {
            plugin.notify(player, "antidote-none");
        }
    }

    /** Супер-удобрение: ПКМ по грядке — 6 применений костной муки. Использовать могут все. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!tagged(item, FERTILIZER)) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Player player = event.getPlayer();
        if (!me.darkz70.quirks.util.ScanUtil.tryUse(player.getUniqueId(), "fertilizer", 200)) return;

        // глушим ванильное применение костной муки (предмет основан на ней)
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);

        for (int i = 0; i < 6; i++) {
            clicked.applyBoneMeal(event.getBlockFace());
        }
        item.setAmount(item.getAmount() - 1);
        player.swingMainHand();
        player.getWorld().playSound(clicked.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.2f);
    }
}
