package me.darkz70.quirks.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Причуда «Глава скалка» (drug-dealing причуда, со 1-го уровня):
 *  - Shift+ПКМ по игроку СО скалком → меню изменения: −4 HP на 5 мин, снять скалк (3 подтв.),
 *    разрешить/запретить предложения, инфо, раз в 24 ч — повысить уровень скалка.
 *  - Shift+ПКМ по игроку БЕЗ скалка → меню: предложить принять скалк (жертве приходит
 *    кликабельное приглашение в чат) + переключатель предложений + инфо.
 *  Остальные способности/паузы — как у обычной причуды Скалк.
 */
public final class SculkLeaderListener implements Listener {


    private final VoidQuirksPlugin plugin;
    private final Map<UUID, UUID> invites = new HashMap<>();     // приглашённый -> глава
    private final Map<UUID, Long> levelUpCd = new HashMap<>();   // глава -> время след. апгрейда
    private final Map<UUID, Boolean> offersAllowed = new HashMap<>(); // глава -> могу ли посылать предложения
    private final Map<UUID, Integer> blickr = new HashMap<>();   // подтверждение снятия (3 клика)

    private record LeaderHolder(UUID leader, UUID target) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 27, Component.empty());
        }
    }

    public SculkLeaderListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isLeader(Player player) {
        return plugin.quirks().levelOf(player, Quirk.SCULK_LEADER) >= 1;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;
        Player leader = event.getPlayer();
        if (!isLeader(leader) || !leader.isSneaking()) return;
        event.setCancelled(true);
        boolean targetSculk = plugin.quirks().sculkOf(target) > 0;
        openMenu(leader, target, targetSculk);
    }

    private void openMenu(Player leader, Player target, boolean targetSculk) {
        Inventory inv = Bukkit.createInventory(new LeaderHolder(leader.getUniqueId(), target.getUniqueId()),
                27, Msg.color(targetSculk ? "&5Изменение: " + target.getName()
                        : "&5Предложение скалка: " + target.getName()));
        if (targetSculk) {
            inv.setItem(1, item(Material.CRIMSON_FUNGUS, "&cДисциплинарно: −4 HP на 5 мин",
                    "&7Сольные поражения отступают — порадуемся. Клик — применить сразу."));
            long cd = levelUpCd.getOrDefault(leader.getUniqueId(), 0L);
            boolean ready = cd < System.currentTimeMillis();
            inv.setItem(3, item(ready ? Material.EXPERIENCE_BOTTLE : Material.GRAY_DYE,
                    "&bПовысить уровень на 1",
                    ready ? "&7Раз в 24 часа. Клик — повысить уровень Скалка."
                            : "&7Перезарядка: раз в 24 часа."));
            inv.setItem(4, item(Material.PAPER, "&fИнфо",
                    "&7Глава скалка с 1 уровня. Хочешь — управляй,",
                    "&7хочешь — предлагай. Награды/ургедью по 24 часа.",
                    "&dСкалк светится тьмой: стелс, уклон, эхо удары."));
        } else {
            inv.setItem(2, item(offersAllowed.getOrDefault(leader.getUniqueId(), true)
                            ? Material.SCULK_CATALYST : Material.GRAY_DYE,
                    "&6Предложить стать Скалком",
                    offersAllowed.getOrDefault(leader.getUniqueId(), true)
                            ? "&7Клик — шлём приглашение прямо в чат игроку."
                            : "&cСейчас предложения у тебя выключены (переключи ниже)."));
            inv.setItem(4, item(Material.PAPER, "&fИнфо",
                    "&7Глава скалка с 1 уровня. Предлагает Скалка новичкам,",
                    "&7перенасиедует старых, галины строки роду души." + "\n"
                            .replace("\n",""), "&dВсе пассивы — как у Скалка."));
        }
        // общие кнопки
        boolean allowed = offersAllowed.getOrDefault(leader.getUniqueId(), true);
        inv.setItem(6, item(allowed ? Material.LANTERN : Material.REDSTONE_TORCH,
                "&eПредложения: " + (allowed ? "&aВКЛ" : "&cВЫКЛ"),
                "&7Клик — переключить возможность присылки офферов."));
        if (targetSculk) {
            inv.setItem(8, item(Material.ENDER_EYE, "&4&lУбрать СДУШУ Скалка",
                    "&4Осторожно: нужно ТРИ подтверждения кликом.",
                    "&7Клики: " + blickr.getOrDefault(leader.getUniqueId(), 0) + "/3"));
        }
        leader.openInventory(inv);
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Msg.color(line));
        stack.editMeta(meta -> {
            meta.displayName(Msg.color(name));
            meta.lore(lines);
        });
        return stack;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof LeaderHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player leader)) return;
        if (!leader.getUniqueId().equals(holder.leader())) return;
        Player target = Bukkit.getPlayer(holder.target());
        if (target == null) {
            leader.closeInventory();
            return;
        }
        boolean targetSculk = plugin.quirks().sculkOf(target) > 0;
        switch (event.getRawSlot()) {
            case 1 -> punish(leader, target);
            case 2 -> offer(leader, target);
            case 3 -> upgrade24(leader, target);
            case 6 -> {
                boolean current = offersAllowed.getOrDefault(leader.getUniqueId(), true);
                offersAllowed.put(leader.getUniqueId(), !current);
                Msg.send(leader, "leader-offers", "%state%", !current ? "&aВКЛ" : "&cВЫКЛ");
                openMenu(leader, target, targetSculk);
            }
            case 8 -> {
                if (targetSculk) strip(leader, target);
            }
            default -> { }
        }
    }

    /** −4 HP на 5 минут. */
    private void punish(Player leader, Player target) {
        var inst = target.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        AttributeModifier mod = new AttributeModifier(me.darkz70.quirks.Keys.qpHp, -4.0,
                AttributeModifier.Operation.ADD_NUMBER);
        inst.getModifiers().removeIf(m -> m.getKey().equals(me.darkz70.quirks.Keys.qpHp));
        inst.addModifier(mod);
        new BukkitRunnable() {
            @Override
            public void run() {
                var again = target.getAttribute(Attribute.MAX_HEALTH);
                if (again != null) again.getModifiers().removeIf(m -> m.getKey().equals(me.darkz70.quirks.Keys.qpHp));
            }
        }.runTaskLater(plugin, 100L * 60L); // 5 минут
        Msg.send(target, "leader-punish");
        Msg.send(leader, "leader-punish-done", "%player%", target.getName());
        leader.closeInventory();
    }

    /** Раз в 24 часа: +1 уровень Скалка. */
    private void upgrade24(Player leader, Player target) {
        long next = levelUpCd.getOrDefault(leader.getUniqueId(), 0L);
        if (next >= System.currentTimeMillis()) {
            Msg.send(leader, "leader-upgrade-cd");
            return;
        }
        var data = plugin.storage().get(target.getUniqueId());
        if (data == null) return;
        Quirk which = data.levelOf(Quirk.SCULK) > 0 ? Quirk.SCULK : Quirk.SCULK_LEADER;
        int lvl = data.levelOf(which);
        if (lvl >= 3) {
            Msg.send(leader, "leader-upgrade-max");
            return;
        }
        data.put(which, lvl + 1);
        plugin.storage().save();
        levelUpCd.put(leader.getUniqueId(), System.currentTimeMillis() + 86_400_000L);
        Msg.send(target, "leader-upgrade", "%level%", String.valueOf(lvl + 1));
        Msg.send(leader, "leader-upgrade-done", "%player%", target.getName());
        leader.closeInventory();
    }

    /** Три подтверждения — скалк снят безвозвратно. */
    private void strip(Player leader, Player target) {
        int count = blickr.getOrDefault(leader.getUniqueId(), 0) + 1;
        if (count < 3) {
            blickr.put(leader.getUniqueId(), count);
            Msg.send(leader, "leader-strip-confirm", "%count%", String.valueOf(count));
            openMenu(leader, target, true);
            return;
        }
        blickr.remove(leader.getUniqueId());
        var data = plugin.storage().get(target.getUniqueId());
        if (data != null) {
            data.remove(Quirk.SCULK);
            data.remove(Quirk.SCULK_LEADER);
            plugin.storage().save();
        }
        Msg.send(target, "leader-stripped");
        Msg.send(leader, "leader-stripped-done", "%player%", target.getName());
        leader.closeInventory();
    }

    /** Оффер скалка: тому отправляется кликабельное приглашение в чат. */
    private void offer(Player leader, Player target) {
        if (!offersAllowed.getOrDefault(leader.getUniqueId(), true)) {
            Msg.send(leader, "leader-offers-off");
            return;
        }
        invites.put(target.getUniqueId(), leader.getUniqueId());
        Msg.send(leader, "leader-offer-sent", "%player%", target.getName());
        Component msg = Msg.color("&5✦ Глава скалка &d" + leader.getName()
                        + " &5предлагает принять причуду &dСкалк&5.  ")
                .append(Msg.color("&a&l[ПРИНЯТЬ]")
                        .clickEvent(ClickEvent.runCommand("/quirk acceptSculk"))
                        .hoverEvent(HoverEvent.showText(Msg.color("&7Нажми — получишь Скалк 1 ур."))));
        target.sendMessage(msg);
        leader.closeInventory();
    }

    /** Вызывается из QuirkCommand (/quirk acceptSculk): принятие приглашения. */
    public void acceptInvite(Player player) {
        UUID leaderId = invites.remove(player.getUniqueId());
        if (leaderId == null) return;
        var data = plugin.storage().get(player.getUniqueId());
        if (data == null) {
            data = new me.darkz70.quirks.PlayerData();
            plugin.storage().put(player.getUniqueId(), data);
        }
        data.put(Quirk.SCULK, 1);
        plugin.storage().save();
        Msg.send(player, "leader-invite-accepted");
        Player leader = Bukkit.getPlayer(leaderId);
        if (leader != null && leader.isOnline()) {
            Msg.send(leader, "leader-invite-notify", "%player%", player.getName());
        }
    }
}
