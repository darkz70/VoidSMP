package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * /gaid → «Политика»: партии, цари (нынешний + родословная с правкой/удалением),
 * выборы (GUI-редактор: выбор партии из списка, кнопки ±0,1/1/10/25/50 %,
 * депутаты ±, число голосовавших вводом в чат).
 * Иконки идут с нулевого слота подряд, стрелка «назад» — последним слотом.
 */
public final class PoliticsMenu implements Listener {

    private final VoidQuirksPlugin plugin;

    private record Holder(String page, int idx, String extra) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 27, Component.empty());
        }
    }

    private final Map<UUID, String> input = new HashMap<>();

    public PoliticsMenu(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ---------- корень ----------

    public void openRoot(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("root", 0, ""), 27,
                Msg.color("&6Политика зайцекролов"));
        inv.setItem(11, btn(Material.WHITE_BANNER, "&eПартии", "&7Список, описание, статус"));
        inv.setItem(13, btn(Material.BEACON, "&cЦари", "&7Текущий и родословная: правка и удаление"));
        inv.setItem(15, btn(Material.PAPER, "&bВыборы", "&7Итоги: партии, проценты, депутаты, голосовавшие"));
        inv.setItem(22, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    // ---------- партии ----------

    private void openParties(Player player) {
        Map<String, String> parties = plugin.politics().parties();
        Inventory inv = Bukkit.createInventory(new Holder("parties", 0, ""), 54,
                Msg.color("&eПартии"));
        int slot = 0;
        for (var e : parties.entrySet()) {
            if (slot >= 45) break;
            String[] parts = e.getValue().split("\\|");
            boolean active = parts.length > 1 && parts[1].equals("1");
            inv.setItem(slot++, btn(active ? Material.LANTERN : Material.GRAY_DYE,
                    (active ? "&a" : "&7") + e.getKey(),
                    "&f" + parts[0],
                    active ? "&8существует · клик — настройка" : "&8распущена · клик — настройка"));
        }
        inv.setItem(45, btn(Material.EMERALD, "&a+ Новая партия"));
        inv.setItem(53, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    private void openPartySettings(Player player, String name, int ordinal) {
        String[] parts = plugin.politics().parties().getOrDefault(name, "|1").split("\\|");
        boolean active = parts.length > 1 && parts[1].equals("1");
        Inventory inv = Bukkit.createInventory(new Holder("party", ordinal, name), 27,
                Msg.color("&eПартия: " + name));
        inv.setItem(10, btn(Material.WRITABLE_BOOK, "&fОписание",
                "&7Текущее: &f" + parts[0], "&7Клик — ввести новое в чате"));
        inv.setItem(13, btn(active ? Material.LANTERN : Material.GRAY_DYE,
                "-- " + (active ? "&aсуществует" : "&cраспущена"),
                "&7Клик — переключить статус существования"));
        inv.setItem(16, btn(Material.LAVA_BUCKET, "&cУдалить партию"));
        inv.setItem(22, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    // ---------- цари ----------

    /** Родословная: с нулевого слота — текущий, дальше подряд прошлые до стрелки. */
    private void openTsars(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("tsars", 0, ""), 54,
                Msg.color("&cРодословная царей"));
        inv.setItem(0, btn(Material.DRAGON_HEAD, "&6&l" + plugin.politics().currentTsar(),
                "&f" + plugin.politics().currentTsarBio(),
                "&8текущий царь · клик — управление"));
        List<Map<String, Object>> past = plugin.politics().pastTsars();
        int slot = 1;
        for (Map<String, Object> row : past) {
            if (slot >= 53) break;
            inv.setItem(slot++, btn(Material.PLAYER_HEAD,
                    "&7" + String.valueOf(row.get("name")),
                    "&f" + textOf(row.get("bio")),
                    "&8" + textOf(row.get("note")) + " · клик — правка/удаление"));
        }
        inv.setItem(53, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    private void openTsarEdit(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("tsar_edit", 0, ""), 27,
                Msg.color("&6Царь: " + plugin.politics().currentTsar()));
        inv.setItem(4, btn(Material.DRAGON_HEAD, "&6&l" + plugin.politics().currentTsar(),
                "&f" + plugin.politics().currentTsarBio()));
        inv.setItem(10, btn(Material.NAME_TAG, "&fПоменять имя", "&7Клик — ввести в чате"));
        inv.setItem(11, btn(Material.WRITABLE_BOOK, "&fПоменять биографию", "&7Клик — ввести в чате"));
        inv.setItem(13, btn(Material.HOPPER, "&8Отставка → в родословную"));
        inv.setItem(15, btn(Material.LAVA_BUCKET, "&cУдалить царя", "&7Без занесения в родословную"));
        inv.setItem(22, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    private void openPastTsarEdit(Player player, int idx) {
        List<Map<String, Object>> past = plugin.politics().pastTsars();
        if (idx < 0 || idx >= past.size()) { openTsars(player); return; }
        Map<String, Object> row = past.get(idx);
        Inventory inv = Bukkit.createInventory(new Holder("past_edit", idx, ""), 27,
                Msg.color("&7Прошлый царь: " + String.valueOf(row.get("name"))));
        inv.setItem(4, btn(Material.PLAYER_HEAD, "&7&l" + String.valueOf(row.get("name")),
                "&f" + textOf(row.get("bio")), "&8" + textOf(row.get("note"))));
        inv.setItem(10, btn(Material.NAME_TAG, "&fПоменять имя", "&7Клик — ввести в чате"));
        inv.setItem(11, btn(Material.WRITABLE_BOOK, "&fПоменять биографию", "&7Клик — ввести в чате"));
        inv.setItem(12, btn(Material.PAPER, "&fПоменять примечание", "&7Клик — ввести в чате"));
        inv.setItem(15, btn(Material.LAVA_BUCKET, "&cУдалить из родословной"));
        inv.setItem(22, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    // ---------- выборы ----------

    private void openElections(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("elections", 0, ""), 54,
                Msg.color("&bИтоги выборов"));
        List<Map<String, Object>> elections = plugin.politics().elections();
        int slot = 0;
        for (Map<String, Object> entry : elections) {
            if (slot >= 45) break;
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.color("&8" + whenOf(entry)));
            lore.add(Msg.color("&7голосовавших: &f" + numOf(entry.get("voters"))));
            for (Component c : rowsLore(entry)) lore.add(c);
            inv.setItem(slot++, btnMulti(Material.BOOK, "&bВыборы #" + (slot + 1), lore));
        }
        inv.setItem(45, btn(Material.EMERALD, "&a+ Новые выборы", "&7Создать; правка — клик по записи"));
        inv.setItem(53, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    private Map<String, Object> entryAt(int idx) {
        List<Map<String, Object>> elections = plugin.politics().elections();
        if (idx < 0 || idx >= elections.size()) return null;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", elections.get(idx).get("time"));
        entry.put("voters", elections.get(idx).get("voters"));
        List<Map<String, Object>> rows = new ArrayList<>();
        Object p = elections.get(idx).get("parties");
        if (p instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("party", String.valueOf(m.get("party")));
                    row.put("percent", String.valueOf(m.get("percent")));
                    row.put("deputies", String.valueOf(m.get("deputies")));
                    rows.add(row);
                }
            }
        }
        entry.put("parties", rows);
        return entry;
    }

    @SuppressWarnings("unchecked")
    private void saveEntry(int idx, Map<String, Object> entry) {
        plugin.politics().saveElection(idx, entry);
    }

    /** Страница выборов: строки партий + управление. */
    private void openElection(Player player, int idx) {
        Map<String, Object> entry = entryAt(idx);
        if (entry == null) { openElections(player); return; }
        Inventory inv = Bukkit.createInventory(new Holder("election", idx, ""), 54,
                Msg.color("&bВыборы: " + whenOf(entry)));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) entry.get("parties");
        int slot = 0;
        for (Map<String, Object> row : rows) {
            if (slot >= 45) break;
            inv.setItem(slot++, btn(Material.WHITE_BANNER, "&d" + row.get("party"),
                    "&7голосов: &f" + row.get("percent") + "%",
                    "&7депутатов: &f" + row.get("deputies"),
                    "&8клик — настройка строки"));
        }
        inv.setItem(46, btn(Material.EMERALD, "&a+ Партия (выбрать из списка)"));
        inv.setItem(48, btn(Material.SUNFLOWER, "&eГолосовавших: &f" + numOf(entry.get("voters")),
                "&7Клик — ввести число в чате"));
        inv.setItem(50, btn(Material.LAVA_BUCKET, "&cУдалить эти выборы"));
        inv.setItem(53, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    /** Пикер партии для добавления в выборы. */
    private void openElectionPicker(Player player, int idx) {
        Map<String, Object> entry = entryAt(idx);
        if (entry == null) { openElections(player); return; }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) entry.get("parties");
        Inventory inv = Bukkit.createInventory(new Holder("pick", idx, ""), 54,
                Msg.color("&aКакую партию взять?"));
        int slot = 0;
        for (String name : plugin.politics().parties().keySet()) {
            if (slot >= 53) break;
            boolean already = rows.stream().anyMatch(r -> name.equals(r.get("party")));
            inv.setItem(slot++, btn(already ? Material.GRAY_DYE : Material.WHITE_BANNER,
                    (already ? "&7" : "&a") + name,
                    already ? "&8уже в итогах" : "&7клик — добавить строку"));
        }
        inv.setItem(53, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    private static final double[] P_MINUS = {-50, -25, -10, -1, -0.1};
    private static final double[] P_PLUS = {0.1, 1, 10, 25, 50};

    /** Редактор строки партии внутри выборов: кнопки процентов и депутатов. */
    private void openElectionRow(Player player, int idx, String party) {
        Map<String, Object> entry = entryAt(idx);
        if (entry == null) { openElections(player); return; }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) entry.get("parties");
        Map<String, Object> row = rows.stream()
                .filter(r -> party.equals(String.valueOf(r.get("party"))))
                .findFirst().orElse(null);
        if (row == null) { openElection(player, idx); return; }
        Inventory inv = Bukkit.createInventory(new Holder("row", idx, party), 27,
                Msg.color("&d" + party + " &8(выборы)"));
        inv.setItem(4, btn(Material.WHITE_BANNER, "&d&l" + party,
                "&7голосов: &f" + row.get("percent") + "%",
                "&7депутатов: &f" + row.get("deputies")));
        int slot = 9;
        for (double d : P_MINUS) {
            inv.setItem(slot++, btn(Material.PAPER, "&c" + trim(d) + "%"));
        }
        slot = 15;
        for (double d : P_PLUS) {
            inv.setItem(slot++, btn(Material.ENCHANTED_BOOK, "&a+" + trim(d) + "%"));
        }
        inv.setItem(21, btn(Material.RED_DYE, "&c−1 депутат"));
        inv.setItem(22, btn(Material.LIME_DYE, "&a+1 депутат"));
        inv.setItem(23, btn(Material.LANTERN, "&a+5 депутатов"));
        inv.setItem(24, btn(Material.LAVA_BUCKET, "&cУдалить строку"));
        inv.setItem(26, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    // ---------- клики ----------

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        switch (holder.page()) {
            case "root" -> {
                switch (slot) {
                    case 11 -> openParties(player);
                    case 13 -> openTsars(player);
                    case 15 -> openElections(player);
                    case 22 -> backToGaid(player);
                    default -> { }
                }
            }
            case "parties" -> {
                if (slot == 45) { beginInput(player, "party-name"); return; }
                if (slot == 53) { openRoot(player); return; }
                if (slot >= 0 && slot < 45) {
                    String name = partyAt(slot);
                    if (name != null) openPartySettings(player, name, slot);
                }
            }
            case "party" -> {
                String name = holder.extra();
                if (slot == 10) beginInput(player, "party-desc:" + name);
                else if (slot == 13) {
                    String[] parts = plugin.politics().parties().getOrDefault(name, "|1").split("\\|");
                    plugin.politics().setParty(name, parts[0], !(parts.length > 1 && parts[1].equals("1")));
                    openPartySettings(player, name, holder.idx());
                } else if (slot == 16) {
                    plugin.politics().removeParty(name);
                    Msg.send(player, "politics-removed", "%name%", name);
                    openParties(player);
                } else if (slot == 22) openParties(player);
            }
            case "tsars" -> {
                if (slot == 53) { openRoot(player); return; }
                if (slot == 0) openTsarEdit(player);
                else if (slot > 0 && slot < 53) openPastTsarEdit(player, slot - 1);
            }
            case "tsar_edit" -> {
                switch (slot) {
                    case 10 -> beginInput(player, "tsar-name");
                    case 11 -> beginInput(player, "tsar-bio");
                    case 13 -> {
                        plugin.politics().retireTsar("ушёл в родословную");
                        plugin.politics().clearTsar();
                        Msg.send(player, "politics-saved");
                        openTsars(player);
                    }
                    case 15 -> {
                        plugin.politics().clearTsar();
                        Msg.send(player, "politics-removed", "%name%", "царь");
                        openTsars(player);
                    }
                    case 22 -> openTsars(player);
                    default -> { }
                }
            }
            case "past_edit" -> {
                int idx = holder.idx();
                switch (slot) {
                    case 10 -> beginInput(player, "past-name:" + idx);
                    case 11 -> beginInput(player, "past-bio:" + idx);
                    case 12 -> beginInput(player, "past-note:" + idx);
                    case 15 -> {
                        plugin.politics().removePastTsar(idx);
                        Msg.send(player, "politics-removed", "%name%", "запись");
                        openTsars(player);
                    }
                    case 22 -> openTsars(player);
                    default -> { }
                }
            }
            case "elections" -> {
                if (slot == 45) {
                    int idx = plugin.politics().createElection(0L);
                    openElection(player, idx);
                    return;
                }
                if (slot == 53) { openRoot(player); return; }
                if (slot >= 0 && slot < 45 && slot < plugin.politics().elections().size()) {
                    openElection(player, slot);
                }
            }
            case "election" -> {
                int idx = holder.idx();
                if (slot == 53) { openElections(player); return; }
                if (slot == 46) { openElectionPicker(player, idx); return; }
                if (slot == 48) { beginInput(player, "voters:" + idx); return; }
                if (slot == 50) {
                    plugin.politics().removeElection(idx);
                    Msg.send(player, "politics-removed", "%name%", "выборы");
                    openElections(player);
                    return;
                }
                Map<String, Object> entry = entryAt(idx);
                if (entry != null && slot >= 0 && slot < 45) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) entry.get("parties");
                    if (slot < rows.size()) openElectionRow(player, idx, String.valueOf(rows.get(slot).get("party")));
                }
            }
            case "pick" -> {
                if (slot == 53) { openElection(player, holder.idx()); return; }
                String name = partyAt(slot);
                if (name == null) return;
                Map<String, Object> entry = entryAt(holder.idx());
                if (entry == null) return;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rows = (List<Map<String, Object>>) entry.get("parties");
                if (rows.stream().noneMatch(r -> name.equals(String.valueOf(r.get("party"))))) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("party", name);
                    row.put("percent", "0");
                    row.put("deputies", "0");
                    rows.add(row);
                    saveEntry(holder.idx(), entry);
                }
                openElection(player, holder.idx());
            }
            case "row" -> {
                int idx = holder.idx();
                String party = holder.extra();
                if (slot == 26) { openElection(player, idx); return; }
                Map<String, Object> entry = entryAt(idx);
                if (entry == null) return;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rows = (List<Map<String, Object>>) entry.get("parties");
                Map<String, Object> row = rows.stream()
                        .filter(r -> party.equals(String.valueOf(r.get("party"))))
                        .findFirst().orElse(null);
                if (row == null) { openElection(player, idx); return; }
                double percentAdd = 0;
                boolean deputiesChanged = false;
                int depAdd = 0;
                if (slot >= 9 && slot < 9 + P_MINUS.length) percentAdd = P_MINUS[slot - 9];
                else if (slot >= 15 && slot < 15 + P_PLUS.length) percentAdd = P_PLUS[slot - 15];
                else if (slot == 21) { depAdd = -1; deputiesChanged = true; }
                else if (slot == 22) { depAdd = 1; deputiesChanged = true; }
                else if (slot == 23) { depAdd = 5; deputiesChanged = true; }
                else if (slot == 24) {
                    rows.remove(row);
                    saveEntry(idx, entry);
                    openElection(player, idx);
                    return;
                } else { return; }
                if (percentAdd != 0) {
                    double p = clamp0(percentOf(String.valueOf(row.get("percent"))) + percentAdd, 100);
                    row.put("percent", fmt(p));
                    saveEntry(idx, entry);
                }
                if (deputiesChanged) {
                    long d = numOf(row.get("deputies")) + depAdd;
                    if (d < 0) d = 0;
                    row.put("deputies", String.valueOf(d));
                    saveEntry(idx, entry);
                }
                openElectionRow(player, idx, party);
            }
            default -> { }
        }
    }

    private void backToGaid(Player player) {
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("gaid"));
    }

    private String partyAt(int slot) {
        int i = 0;
        for (String name : plugin.politics().parties().keySet()) {
            if (i++ == slot) return name;
        }
        return null;
    }

    // ---------- ввод через чат ----------

    private void beginInput(Player player, String key) {
        input.put(player.getUniqueId(), key);
        player.closeInventory();
        String step = key.split(":")[0];
        player.sendMessage(Msg.color(switch (step) {
            case "party-name" -> "&aВведи в чате название партии:";
            case "party-desc" -> "&aВведи в чате описание партии (одна строка):";
            case "tsar-name", "past-name" -> "&aВведи в чате имя:";
            case "tsar-bio", "past-bio" -> "&aВведи в чате биографию (одна строка):";
            case "past-note" -> "&aВведи в чате примечание (одна строка):";
            case "voters" -> "&aВведи в чате число голосовавших:";
            default -> "&aВведи текст:";
        }));
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String key = input.remove(player.getUniqueId());
        if (key == null) return;
        event.setCancelled(true);
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.message());
        String step = key.contains(":") ? key.substring(0, key.indexOf(':')) : key;
        String arg = key.contains(":") ? key.substring(key.indexOf(':') + 1) : "";
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (step) {
                case "party-name" -> {
                    plugin.politics().setParty(text, "", true);
                    Msg.send(player, "politics-saved");
                    openParties(player);
                }
                case "party-desc" -> {
                    String active = plugin.politics().parties().getOrDefault(arg, "|1").split("\\|")[1];
                    plugin.politics().setParty(arg, text, "1".equals(active));
                    Msg.send(player, "politics-saved");
                    openPartySettings(player, arg, 0);
                }
                case "tsar-name" -> {
                    plugin.politics().setTsar(text, plugin.politics().currentTsarBio());
                    Msg.send(player, "politics-saved");
                    openTsarEdit(player);
                }
                case "tsar-bio" -> {
                    plugin.politics().setTsar(plugin.politics().currentTsar(), text);
                    Msg.send(player, "politics-saved");
                    openTsarEdit(player);
                }
                case "past-name", "past-bio", "past-note" -> {
                    int idx = parseIdx(arg);
                    List<Map<String, Object>> past = plugin.politics().pastTsars();
                    if (idx >= 0 && idx < past.size()) {
                        Map<String, Object> row = past.get(idx);
                        String name = String.valueOf(row.get("name"));
                        String bio = textOf(row.get("bio"));
                        String note = textOf(row.get("note"));
                        if (step.equals("past-name")) name = text;
                        else if (step.equals("past-bio")) bio = text;
                        else note = text;
                        plugin.politics().updatePastTsar(idx, name, bio, note);
                        Msg.send(player, "politics-saved");
                    }
                    openPastTsarEdit(player, idx);
                }
                case "voters" -> {
                    int idx = parseIdx(arg);
                    Map<String, Object> entry = entryAt(idx);
                    if (entry != null) {
                        long v;
                        try { v = Long.parseLong(text.replaceAll("[^0-9]", "")); }
                        catch (NumberFormatException ex) { v = 0; }
                        entry.put("voters", v);
                        saveEntry(idx, entry);
                        Msg.send(player, "politics-saved");
                        openElection(player, idx);
                    }
                }
                default -> { }
            }
        });
    }

    // ---------- утилиты ----------

    private static int parseIdx(String arg) {
        try { return Integer.parseInt(arg); } catch (NumberFormatException ex) { return -1; }
    }

    private static double percentOf(String raw) {
        try { return Double.parseDouble(raw.replace(',', '.')); } catch (NumberFormatException ex) { return 0; }
    }

    private static double clamp0(double v, double max) {
        if (v < 0) return 0;
        return Math.min(v, max);
    }

    private static String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static String trim(double v) {
        return fmt(v);
    }

    private static long numOf(Object raw) {
        if (raw instanceof Number n) return n.longValue();
        if (raw != null) {
            try { return Long.parseLong(String.valueOf(raw).replaceAll("[^0-9-]", "")); }
            catch (NumberFormatException ignored) { }
        }
        return 0;
    }

    private static String textOf(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    private static String whenOf(Map<String, Object> entry) {
        long time = numOf(entry.get("time"));
        if (time <= 0) return "—";
        return java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(time),
                        java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    private static List<Component> rowsLore(Map<String, Object> entry) {
        List<Component> lore = new ArrayList<>();
        Object p = entry.get("parties");
        if (p instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> row) {
                    lore.add(Msg.color("&d" + row.get("party")
                            + "&7: депутатов &f" + row.get("deputies")
                            + "&7, голосов &f" + row.get("percent") + "%"));
                }
            }
        }
        return lore;
    }

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
