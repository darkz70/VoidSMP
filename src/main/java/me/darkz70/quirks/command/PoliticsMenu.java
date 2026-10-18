package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * /gaid → «Политика»: партии, родословная царей, итоги выборов.
 * Правки — через чат (кнопка призывает ввести текст, ввод ловится в AsyncChatEvent).
 */
public final class PoliticsMenu implements Listener {

    private final VoidQuirksPlugin plugin;

    private record Holder(String page, int idx) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 27, Component.empty());
        }
    }

    // состояние ввода: tsar-name / tsar-bio / party-name / party-desc:<имя> / election-line
    private final Map<UUID, String> input = new HashMap<>();

    public PoliticsMenu(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ---------- корневая страница ----------

    public void openRoot(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("root", 0), 27,
                Msg.color("&6Политика зайцекролов"));
        inv.setItem(11, btn(Material.WHITE_BANNER, "&eПартии", "&7Список партий, описание, статус"));
        inv.setItem(13, btn(Material.BEACON, "&cЦари", "&7Текущий царь, его био и прошлые родословные"));
        inv.setItem(15, btn(Material.PAPER, "&bВыборы", "&7Прошлые итоги: кто сколько и сколько это %"));
        inv.setItem(22, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    // ---------- страница партий ----------

    private void openParties(Player player) {
        Map<String, String> parties = plugin.politics().parties();
        Inventory inv = Bukkit.createInventory(new Holder("parties", 0), 54,
                Msg.color("&eПартии"));
        int slot = 0;
        for (var e : parties.entrySet()) {
            if (slot >= 45) break;
            String[] parts = e.getValue().split("\\|");
            boolean active = parts.length > 1 && parts[1].equals("1");
            String desc = parts[0];
            inv.setItem(slot++, btn(active ? Material.LANTERN : Material.GRAY_DYE,
                    (active ? "&a" : "&7") + e.getKey(),
                    "&f" + desc,
                    active ? "&8активна · клик — к настройке" : "&8распущена · клик — к настройке"));
        }
        inv.setItem(45, btn(Material.EMERALD, "&a+ Новая партия"));
        inv.setItem(53, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    private void openPartySettings(Player player, String name, int ordinal) {
        Map<String, String> parties = plugin.politics().parties();
        String[] parts = parties.getOrDefault(name, "|1").split("\\|");
        boolean active = parts.length > 1 && parts[1].equals("1");
        Inventory inv = Bukkit.createInventory(new Holder("party", ordinal), 27,
                Msg.color("&eПартия: " + name));
        inv.setItem(10, btn(Material.WRITABLE_BOOK, "&fОписание",
                "&7Текущее: &f" + parts[0], "&7Клик — ввести новое в чате"));
        inv.setItem(13, btn(active ? Material.LANTERN : Material.GRAY_DYE,
                "-- " + (active ? "&aактивная" : "&cраспущена"),
                "&7Клик — переключить статус существования"));
        inv.setItem(16, btn(Material.LAVA_BUCKET, "&cУдалить партию"));
        inv.setItem(22, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    // ---------- страница царей ----------

    private void openTsars(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("tsars", 0), 54,
                Msg.color("&cРодословная царей"));
        inv.setItem(4, btn(Material.DRAGON_HEAD, "&6&l" + plugin.politics().currentTsar(),
                "&f" + plugin.politics().currentTsarBio(),
                "&8текущий царь"));
        inv.setItem(45, btn(Material.NAME_TAG, "&fПоменять имя царя", "&7Клик — ввести в чате"));
        inv.setItem(46, btn(Material.WRITABLE_BOOK, "&fПоменять биографию", "&7Клик — ввести в чате"));
        inv.setItem(47, btn(Material.HOPPER, "&8Отставка текущего → в родословную"));
        inv.setItem(53, btn(Material.ARROW, "&7Назад"));
        // история
        List<Map<String, Object>> past = plugin.politics().pastTsars();
        int slot = 18;
        for (Map<String, Object> row : past) {
            if (slot >= 45) break;
            inv.setItem(slot++, btn(Material.PLAYER_HEAD,
                    "&7" + String.valueOf(row.getOrDefault("name", "—")),
                    "&f" + String.valueOf(row.getOrDefault("bio", "")),
                    "&8" + String.valueOf(row.getOrDefault("note", ""))));
        }
        player.openInventory(inv);
    }

    // ---------- страница выборов ----------

    private void openElections(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("elections", 0), 54,
                Msg.color("&bИтоги выборов"));
        List<Map<String, Object>> elections = plugin.politics().elections();
        int slot = 0;
        for (Map<String, Object> entry : elections) {
            if (slot >= 45) break;
            long time = ((Number) entry.getOrDefault("time", 0)).longValue();
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
                    .ofPattern("dd.MM.yyyy HH:mm");
            String when = time > 0 ? java.time.LocalDateTime
                    .ofInstant(java.time.Instant.ofEpochMilli(time), java.time.ZoneId.systemDefault())
                    .format(fmt) : "—";
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.color("&8" + when));
            Object partiesObj = entry.get("parties");
            if (partiesObj instanceof List<?> rows) {
                for (Object o : rows) {
                    if (o instanceof Map<?, ?> row) {
                        lore.add(Msg.color("&d" + row.getOrDefault("party", "?")
                                + "&7: депутатов &f" + row.getOrDefault("deputies", "0")
                                + "&7, голосов &f" + row.getOrDefault("percent", "0") + "%"));
                    }
                }
            }
            inv.setItem(slot++, btnMulti(Material.BOOK, "&bВыборы #" + (slot + 1), lore));
        }
        inv.setItem(45, btn(Material.EMERALD, "&a+ Записать итог выборов",
                "&7Клик — заполняй построчно в чате: &fПартия;Процент;Депутатов",
                "&7Писать к-ставкой готово → фиксит."));
        inv.setItem(53, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    // ---------- клики ----------

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot == 53 && "root".equals(holder.page())) return;
        if ((slot == 53 || slot == 22) && slot >= 22) {
            if ("root".equals(holder.page())) openRootBack(player);
            else openRoot(player);
            return;
        }
        switch (holder.page()) {
            case "root" -> {
                if (slot == 11) openParties(player);
                else if (slot == 13) openTsars(player);
                else if (slot == 15) openElections(player);
            }
            case "parties" -> {
                if (slot == 45) {
                    beginInput(player, "party-name");
                    return;
                }
                String name = partyAt(slot);
                if (name != null) openPartySettings(player, name, slot);
            }
            case "party" -> {
                String name = partyAt(holder.idx());
                if (name == null) return;
                if (slot == 10) beginInput(player, "party-desc:" + name);
                else if (slot == 13) {
                    Map<String, String> parties = plugin.politics().parties();
                    String[] parts = parties.getOrDefault(name, "|1").split("\\|");
                    String desc = parts[0];
                    boolean active = parts.length > 1 && parts[1].equals("1");
                    plugin.politics().setParty(name, desc, !active);
                    Msg.send(player, "politics-saved");
                    openPartySettings(player, name, holder.idx());
                } else if (slot == 16) {
                    plugin.politics().removeParty(name);
                    Msg.send(player, "politics-removed", "%name%", name);
                    openParties(player);
                }
            }
            case "tsars" -> {
                if (slot == 45) beginInput(player, "tsar-name");
                else if (slot == 46) beginInput(player, "tsar-bio");
                else if (slot == 47) {
                    plugin.politics().retireTsar("ушёл в родословную");
                    Msg.send(player, "politics-saved");
                    openTsars(player);
                }
            }
            case "elections" -> {
                if (slot == 45) beginInput(player, "election-line");
            }
            default -> { }
        }
    }

    private void openRootBack(Player player) {
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            // вернуться в /gaid корень
            player.performCommand("gaid");
        });
    }

    @org.jetbrains.annotations.Nullable
    private String partyAt(int slot) {
        Map<String, String> parties = plugin.politics().parties();
        int i = 0;
        for (String name : parties.keySet()) {
            if (i++ == slot && slot < 45) return name;
        }
        return null;
    }

    // ---------- ввод через чат ----------

    private void beginInput(Player player, String key) {
        input.put(player.getUniqueId(), key);
        player.closeInventory();
        String hint = switch (key.split(":")[0]) {
            case "party-name" -> "&aВведи в чате название партии:";
            case "party-desc" -> "&aВведи в чате описание партии (одна строка):";
            case "tsar-name" -> "&aВведи в чате имя царя:";
            case "tsar-bio" -> "&aВведи в чате биографию царя (одна строка):";
            case "election-line" -> "&aПиши итоги построчно: &fПартия;Процент;Депутатов&a. Когда закончишь — введи &fготово&a.";
            default -> "&aВведи текст:";
        };
        player.sendMessage(Msg.color(hint));
    }

    private final Map<UUID, List<Map<String, Object>>> electionBuffer = new HashMap<>();

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String key = input.remove(player.getUniqueId());
        if (key == null) return;
        event.setCancelled(true);
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.message());
        String step = key.split(":")[0];
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (step) {
                case "party-name" -> {
                    plugin.politics().setParty(text, "", true);
                    Msg.send(player, "politics-saved");
                }
                case "party-desc" -> {
                    String name = key.substring("party-desc:".length());
                    Map<String, String> parties = plugin.politics().parties();
                    String active = parties.getOrDefault(name, "|1").split("\\|")[1];
                    plugin.politics().setParty(name, text, "1".equals(active));
                    Msg.send(player, "politics-saved");
                }
                case "tsar-name" -> {
                    plugin.politics().setTsar(text, plugin.politics().currentTsarBio());
                    Msg.send(player, "politics-saved");
                }
                case "tsar-bio" -> {
                    plugin.politics().setTsar(plugin.politics().currentTsar(), text);
                    Msg.send(player, "politics-saved");
                }
                case "election-line" -> {
                    if (text.equalsIgnoreCase("готово")) {
                        List<Map<String, Object>> rows = electionBuffer
                                .getOrDefault(player.getUniqueId(), new ArrayList<>());
                        if (!rows.isEmpty()) {
                            plugin.politics().recordElection(new ArrayList<>(), rows);
                            electionBuffer.remove(player.getUniqueId());
                            Msg.send(player, "politics-saved");
                        }
                    } else {
                        String[] cells = text.split(";");
                        Map<String, Object> row = new LinkedHashMapX().toMap(cells);
                        electionBuffer.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(row);
                        player.sendMessage(Msg.color("&7+ " + text + " (продолжай, потом 'готово')"));
                        input.put(player.getUniqueId(), "election-line");
                    }
                }
                default -> { }
            }
        });
    }

    /** Мелкая разборка "Партия;Процент;Депутатов". */
    private static final class LinkedHashMapX {
        Map<String, Object> toMap(String[] cells) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("party", cells.length > 0 ? cells[0].trim() : "?");
            m.put("percent", cells.length > 1 ? cells[1].trim() : "0");
            m.put("deputies", cells.length > 2 ? cells[2].trim() : "0");
            return m;
        }
    }

    // ---------- утилити ----------

    private ItemStack btn(Material mat, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Msg.color(l));
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> {
            meta.displayName(Msg.color(name));
            meta.lore(lines);
        });
        return stack;
    }

    private ItemStack btnMulti(Material mat, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> {
            meta.displayName(Msg.color(name));
            meta.lore(lore);
        });
        return stack;
    }
}
