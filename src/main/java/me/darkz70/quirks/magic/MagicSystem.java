package me.darkz70.quirks.magic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;

import me.darkz70.quirks.util.Anims;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/** Ядро магии Skyfall-ветки VoidSMP: мана, касты, ауры, баффы, матрицы урона. */
public final class MagicSystem {

    private final VoidQuirksPlugin plugin;

    // активные состояния (uuid → конец действия, ms)
    private final Map<UUID, Long> invulnUntil = new HashMap<>();
    private final Map<UUID, Long> rootedUntil = new HashMap<>();
    private final Map<UUID, Long> fireAuraUntil = new HashMap<>();
    private final Map<UUID, Long> fireMasterUntil = new HashMap<>();
    private final Map<UUID, Long> waterAuraUntil = new HashMap<>();
    private final Map<UUID, Long> fireProtUntil = new HashMap<>();
    private final Map<UUID, Long> waterProtUntil = new HashMap<>();
    private final Map<UUID, Long> windProtUntil = new HashMap<>();
    private final Map<UUID, Long> lightProtUntil = new HashMap<>();
    private final Map<UUID, Long> darkStealthUntil = new HashMap<>();
    private final Map<UUID, Long> lightAuraUntil = new HashMap<>();
    private final Map<UUID, Long> darkTouchUntil = new HashMap<>();
    private final Map<UUID, Long> earthFistUntil = new HashMap<>();
    private final Map<UUID, Long> windDashUntil = new HashMap<>();
    private final Map<UUID, Long> echoUntil = new HashMap<>();
    private final Map<UUID, Long> leapUntil = new HashMap<>();
    private final Map<UUID, Long> flightUntil = new HashMap<>();
    private final Map<UUID, Long> darkEvadeUntil = new HashMap<>();
    private final Map<UUID, Long> noCastUntil = new HashMap<>();      // запрет каста (великий архимаг)
    private final Map<UUID, Long> halfManaUntil = new HashMap<>();    // мана ×0.5 (архимаг/светлая магия)
    private final Map<UUID, Long> freeCastUntil = new HashMap<>();    // мана 0 (великий архимаг)
    private final Map<UUID, Long> darkBoostUntil = new HashMap<>();   // тьма x2 урон, цена 1 сердце за каст
    private final Map<UUID, Long> lightBoostUntil = new HashMap<>();  // свет: лечение x2 + мана ×0.5
    private final Map<UUID, Long> necroUntil = new HashMap<>();       // некромант: удары по носителю жгут врагов
    private final Map<UUID, Long> noPotionHealUntil = new HashMap<>();// иссушение: нельзя пить зелья
    private final Map<UUID, Long> noFallUntil = new HashMap<>();      // ангел/невесомость: нет урона от падения
    private final Map<UUID, double[]> shieldPools = new HashMap<>();
    private final Map<UUID, Long> shieldUntil = new HashMap<>();
    private final List<long[]> blazesExpire = new ArrayList<>(); // [uuidMost, uuidLeast, expiry]

    // матрицы урона
    private final Map<String, Double> elemVsQuirk = new HashMap<>();
    private final Map<String, Double> quirkVsElem = new HashMap<>();

    // стены земли
    private final List<EarthWall> walls = new ArrayList<>();

    public MagicSystem(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        reloadMults();
    }

    // ---------- конфиг ----------

    public void reloadMults() {
        elemVsQuirk.clear();
        quirkVsElem.clear();
        defaultsMults();
        loadMatrix(plugin.getConfig().getConfigurationSection("magic.elem-vs-quirk"), elemVsQuirk);
        loadMatrix(plugin.getConfig().getConfigurationSection("magic.quirk-vs-elem"), quirkVsElem);
    }

    private static void loadMatrix(@Nullable ConfigurationSection section, Map<String, Double> target) {
        if (section == null) return;
        for (String outerKey : section.getKeys(false)) {
            ConfigurationSection inner = section.getConfigurationSection(outerKey);
            if (inner == null) continue;
            for (String innerKey : inner.getKeys(false)) {
                target.put(outerKey.toLowerCase(Locale.ROOT) + "." + innerKey.toLowerCase(Locale.ROOT),
                        inner.getDouble(innerKey));
            }
        }
    }

    private void defaultsMults() {
        // стихия заклинания × причуда жертвы
        put(elemVsQuirk, "fire", 1.2, 1.0, 0.5, 1.0, 0.6, 1.0, 1.2, 1.3);
        put(elemVsQuirk, "water", 0.9, 0.8, 0.7, 1.0, 1.0, 1.0, 1.5, 1.1);
        put(elemVsQuirk, "wind", 0.8, 1.1, 0.6, 1.0, 1.0, 1.0, 1.0, 0.9);
        put(elemVsQuirk, "earth", 1.0, 1.0, 0.4, 1.0, 1.2, 1.2, 0.9, 1.1);
        put(elemVsQuirk, "dark", 1.0, 1.2, 0.6, 1.0, 0.5, 1.0, 1.0, 0.9);
        put(elemVsQuirk, "light", 1.0, 0.9, 0.5, 1.0, 1.6, 1.0, 1.0, 1.3);
        // причуда кастера × стихия
        put(quirkVsElem, "axe", 1.3, 0.9, 1.0, 1.1, 1.1, 0.8);
        put(quirkVsElem, "cat", 1.0, 0.7, 1.2, 0.9, 1.3, 1.0);
        put(quirkVsElem, "bedrock", 0.8, 0.9, 0.7, 1.4, 1.0, 1.1);
        put(quirkVsElem, "sculk", 0.7, 0.8, 0.9, 1.1, 1.5, 0.6);
        put(quirkVsElem, "farmer", 0.9, 1.3, 1.1, 1.3, 0.8, 1.1);
        put(quirkVsElem, "engineer", 1.2, 1.0, 1.1, 1.1, 0.9, 1.0);
        put(quirkVsElem, "amphibian", 0.6, 1.5, 1.1, 0.9, 0.9, 1.0);
        put(quirkVsElem, "spider", 1.1, 0.8, 1.0, 1.1, 1.3, 0.8);
    }

    private static void put(Map<String, Double> map, String e, double axe, double cat, double bedrock,
            double farmer, double sculk, double engineeOrAmphib, double amphOrQuirk, double spider) {
        map.put(e + ".axe", axe);
        map.put(e + ".cat", cat);
        map.put(e + ".bedrock", bedrock);
        map.put(e + ".farmer", farmer);
        map.put(e + ".sculk", sculk);
        map.put(e + ".engineer", engineeOrAmphib);
        map.put(e + ".amphibian", amphOrQuirk);
        map.put(e + ".spider", spider);
    }

    private static void put(Map<String, Double> map, String q, double fire, double water, double wind,
            double earth, double dark, double light) {
        map.put(q + ".fire", fire);
        map.put(q + ".water", water);
        map.put(q + ".wind", wind);
        map.put(q + ".earth", earth);
        map.put(q + ".dark", dark);
        map.put(q + ".light", light);
    }

    private double mult(String prefix, String key) {
        return (prefix.equals("e") ? elemVsQuirk : quirkVsElem).getOrDefault(key, 1.0);
    }

    // ---------- мана ----------

    public int maxMana(int level) {
        return 20 + level * 10;
    }

    public double manaPerTick() {
        return plugin.getConfig().getDouble("magic.mana-per-tick", 5);
    }

    public int fastRegenLevel() {
        return plugin.getConfig().getInt("magic.fast-regen-level", 20);
    }

    @Nullable
    public Element elementOf(Player player) {
        PlayerData data = data(player);
        return data == null ? null : Element.byId(data.magicElement());
    }

    @Nullable
    public PlayerData data(Player player) {
        return plugin.storage().get(player.getUniqueId());
    }

    public PlayerData dataOrCreate(Player player) {
        PlayerData data = plugin.storage().get(player.getUniqueId());
        if (data == null) {
            data = new PlayerData();
            plugin.storage().put(player.getUniqueId(), data);
        }
        return data;
    }

    public boolean spendMana(PlayerData data, int amount) {
        if (data.mana() < amount) return false;
        data.mana(data.mana() - amount);
        return true;
    }

    // ---------- изучение и книги ----------

    /** Изучить стихию книгой стихии. Возвращает true, если книгу можно потратить. */
    public boolean learnElement(Player player, Element element) {
        PlayerData data = dataOrCreate(player);
        if (data.magicElement() != null) {
            Msg.send(player, "magic-already-element", "%element%", elementOf(player).display());
            return false;
        }
        if (element.requiresFourBooks()) {
            int books = countOtherBooks(player);
            if (books < 4) {
                Msg.send(player, "magic-need-four-books");
                return false;
            }
            consumeOtherBooks(player, 4);
        }
        data.magicElement(element.id());
        data.magicLevel(1);
        data.selectedSpell(0);
        grantUpgradeBookRecipes(player);
        data.mana(Math.min(data.mana(), maxMana(1)));
        plugin.storage().save();
        Msg.send(player, "magic-learned", "%element%", element.display(), "%emoji%", element.emoji());
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.02);
        return true;
    }

    private int countOtherBooks(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String mark = item.getPersistentDataContainer().get(Keys.brewMark, PersistentDataType.STRING);
            if (mark != null && mark.startsWith("elem-book-")) count += item.getAmount();
        }
        return count;
    }

    private void consumeOtherBooks(Player player, int need) {
        ItemStack[] contents = player.getInventory().getContents();
        outer:
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            String mark = item.getPersistentDataContainer().get(Keys.brewMark, PersistentDataType.STRING);
            if (mark == null || !mark.startsWith("elem-book-")) continue;
            int take = Math.min(item.getAmount(), need);
            item.setAmount(item.getAmount() - take);
            need -= take;
            if (need <= 0) break outer;
        }
    }

    /** Использовать книгу прокачки. Возвращает true, если книгу можно потратить. */
    public boolean useUpgradeBook(Player player, int tier) {
        PlayerData data = dataOrCreate(player);
        Element element = elementOf(player);
        if (element == null) {
            Msg.send(player, "magic-no-mage");
            return false;
        }
        int min = switch (tier) {
            case 1 -> 1;
            case 2 -> 10;
            case 3 -> 20;
            case 4 -> 30;
            default -> 40;
        };
        int max = switch (tier) {
            case 1 -> 9;
            case 2 -> 19;
            case 3 -> 29;
            case 4 -> 39;
            default -> Integer.MAX_VALUE;
        };
        if (data.magicLevel() < min) {
            Msg.send(player, "magic-book-weak", "%min%", String.valueOf(min));
            return false;
        }
        if (data.magicLevel() > max) {
            Msg.send(player, "magic-book-capped", "%max%", String.valueOf(max));
            return false;
        }
        data.magicLevel(data.magicLevel() + 1);
        plugin.storage().save();
        Msg.send(player, "magic-level-up", "%level%", String.valueOf(data.magicLevel()));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        return true;
    }

    /** Полное обнуление магии (зелья переквалификации/возврата/уменьшения). */
    public void resetMagic(Player player) {
        PlayerData data = data(player);
        if (data == null) return;
        boolean had = data.magicElement() != null;
        data.magicElement(null);
        data.magicLevel(1);
        data.mana(0);
        data.selectedSpell(0);
        plugin.storage().save();
        revokeUpgradeBookRecipes(player);
    }

    /** Книги прокачки видны в книге рецептов только магам (ур. 1+). */
    public void grantUpgradeBookRecipes(Player player) {
        for (int tier = 1; tier <= 5; tier++) {
            player.discoverRecipe(new org.bukkit.NamespacedKey(plugin, "up_book_" + tier));
        }
    }

    public void revokeUpgradeBookRecipes(Player player) {
        for (int tier = 1; tier <= 5; tier++) {
            player.undiscoverRecipe(new org.bukkit.NamespacedKey(plugin, "up_book_" + tier));
        }
    }

    /** Полное восстановление маны. */
    public void refillMana(Player player) {
        PlayerData data = data(player);
        if (data == null || data.magicElement() == null) return;
        data.mana(maxMana(data.magicLevel()));
        plugin.storage().save();
    }

    /** Восстановление amount маны. */
    public void addMana(Player player, double amount) {
        PlayerData data = data(player);
        if (data == null || data.magicElement() == null) return;
        data.mana(Math.min(maxMana(data.magicLevel()), data.mana() + amount));
        plugin.storage().save();
    }

    // ---------- временные причуды (зелье причуды / божества) ----------

    /** Выдать все 8 причуд 3 уровня на millis, запомнив прошлый набор. */
    public void tempAllQuirks(Player player, long millis) {
        PlayerData data = dataOrCreate(player);
        if (data.tmpQuirks() == null) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Quirk, Integer> entry : data.entries()) {
                if (sb.length() > 0) sb.append(';');
                sb.append(entry.getKey().id()).append(':').append(entry.getValue());
            }
            data.tmpQuirks(sb.toString());
        }
        for (Quirk quirk : Quirk.values()) {
            if (quirk == Quirk.ADMIN) continue;   // техпричуда не входит в «все причуды»
            if (!data.has(quirk) || data.levelOf(quirk) < 3) {
                plugin.quirks().assign(player, quirk, 3);
            }
        }
        // удаление opacity-поддержки: assign мог перезаписать tmpQuirks через storage — восстановим
        PlayerData ref = plugin.storage().get(player.getUniqueId());
        if (ref != null && ref.tmpQuirks() == null) ref.tmpQuirks(data.tmpQuirks());
        data.tmpUntil(Math.max(data.tmpUntil(), System.currentTimeMillis() + millis));
        if (plugin.storage().get(player.getUniqueId()) != null) {
            plugin.storage().get(player.getUniqueId()).tmpUntil(data.tmpUntil());
        }
        plugin.storage().save();
    }

    /** Таская MagicTask: вернуть сохранённый набор причуд, когда время вышло. */
    public void restoreTmpQuirks(Player player) {
        PlayerData data = plugin.storage().get(player.getUniqueId());
        if (data == null || data.tmpQuirks() == null) return;
        if (System.currentTimeMillis() < data.tmpUntil()) return;
        String snapshot = data.tmpQuirks();
        List<Quirk> current = new ArrayList<>();
        for (Map.Entry<Quirk, Integer> e : data.entries()) current.add(e.getKey());
        Map<Quirk, Integer> wanted = new java.util.EnumMap<>(Quirk.class);
        for (String pair : snapshot.split(";")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split(":");
            Quirk quirk = Quirk.byName(kv[0]);
            if (quirk == null) continue;
            int lvl = kv.length > 1 ? Integer.parseInt(kv[1]) : 1;
            wanted.put(quirk, lvl);
        }
        for (Quirk quirk : current) {
            if (quirk == Quirk.ADMIN) continue;
            if (!wanted.containsKey(quirk)) plugin.quirks().remove(player, quirk);
        }
        for (Map.Entry<Quirk, Integer> e : wanted.entrySet()) {
            if (e.getKey() == Quirk.ADMIN) continue;
            if (data(player) == null || plugin.quirks().levelOf(player, e.getKey()) != e.getValue()) {
                plugin.quirks().assign(player, e.getKey(), e.getValue());
            }
        }
        PlayerData ref = plugin.storage().get(player.getUniqueId());
        if (ref == null) return;
        ref.tmpQuirks(null);
        ref.tmpUntil(0);
        plugin.storage().save();
        Msg.send(player, "magic-quirks-back");
    }

    // ---------- состояния для слушателей ----------

    public boolean isInvuln(UUID id) { return active(invulnUntil, id); }
    public boolean isRooted(UUID id) { return active(rootedUntil, id); }
    public boolean fireAuraActive(UUID id) { return active(fireAuraUntil, id); }
    public boolean fireMasterActive(UUID id) { return active(fireMasterUntil, id); }
    public boolean waterAuraActive(UUID id) { return active(waterAuraUntil, id); }
    public boolean fireProtActive(UUID id) { return active(fireProtUntil, id); }
    public boolean waterProtActive(UUID id) { return active(waterProtUntil, id); }
    public boolean windProtActive(UUID id) { return active(windProtUntil, id); }
    public boolean lightProtActive(UUID id) { return active(lightProtUntil, id); }
    public boolean darkStealthActive(UUID id) { return active(darkStealthUntil, id); }
    public boolean lightAuraActive(UUID id) { return active(lightAuraUntil, id); }
    public boolean darkTouchActive(UUID id) { return active(darkTouchUntil, id); }
    public boolean windDashActive(UUID id) { return active(windDashUntil, id); }
    public boolean echoActive(UUID id) { return active(echoUntil, id); }
    public boolean darkEvadeActive(UUID id) { return active(darkEvadeUntil, id); }
    public boolean flightActive(UUID id) { return active(flightUntil, id); }
    public boolean leapActive(UUID id) { return active(leapUntil, id); }
    public boolean earthFistActive(UUID id) { return active(earthFistUntil, id); }
    public boolean noCastActive(UUID id) { return active(noCastUntil, id); }
    public boolean halfManaActive(UUID id) { return active(halfManaUntil, id); }
    public boolean freeCastActive(UUID id) { return active(freeCastUntil, id); }
    public boolean darkBoostActive(UUID id) { return active(darkBoostUntil, id); }
    public boolean lightBoostActive(UUID id) { return active(lightBoostUntil, id); }
    public boolean necroActive(UUID id) { return active(necroUntil, id); }
    public boolean noPotionHealActive(UUID id) { return active(noPotionHealUntil, id); }
    public boolean noFallActive(UUID id) { return active(noFallUntil, id); }

    /** Поставить флаг зелья на millis. */
    public void stampPotionFlag(UUID id, org.bukkit.NamespacedKey ignored, long millis) { /* совместимость */ }
    public void stampNoCast(Player p, long millis) { stamp(noCastUntil, p, millis); }
    public void stampHalfMana(Player p, long millis) { stamp(halfManaUntil, p, millis); }
    public void stampFreeCast(Player p, long millis) { stamp(freeCastUntil, p, millis); }
    public void stampDarkBoost(Player p, long millis) { stamp(darkBoostUntil, p, millis); }
    public void stampLightBoost(Player p, long millis) { stamp(lightBoostUntil, p, millis); }
    public void stampNecro(Player p, long millis) { stamp(necroUntil, p, millis); }
    public void stampNoPotionHeal(Player p, long millis) { stamp(noPotionHealUntil, p, millis); }
    public void stampNoFall(Player p, long millis) { stamp(noFallUntil, p, millis); }

    public void earthFistConsume(UUID id) { earthFistUntil.remove(id); }

    private boolean active(Map<UUID, Long> map, UUID id) {
        Long until = map.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    public double shieldLeft(UUID id) {
        double[] pool = shieldPools.get(id);
        if (pool == null) return 0;
        Long until = shieldUntil.get(id);
        if (until == null || until < System.currentTimeMillis()) return 0;
        return pool[0];
    }

    /** Часть урона, поглощённая световым щитом. */
    public double absorbShield(UUID id, double damage) {
        double left = shieldLeft(id);
        if (left <= 0) return 0;
        double absorbed = Math.min(left, damage);
        shieldPools.get(id)[0] = left - absorbed;
        return absorbed;
    }

    public Map<UUID, Long> flightMap() { return flightUntil; }
    public List<EarthWall> walls() { return walls; }
    public Map<UUID, Long> leapMap() { return leapUntil; }
    public Map<UUID, Long> lightAuraMap() { return lightAuraUntil; }

    // ---------- каст ----------

    public void cast(Player player) {
        PlayerData data = data(player);
        Element element = elementOf(player);
        if (data == null || element == null) {
            Msg.send(player, "magic-no-mage");
            return;
        }
        Spell[] spells = Spell.of(element);
        int idx = Math.max(0, Math.min(data.selectedSpell(), spells.length - 1));
        Spell spell = spells[idx];
        if (data.magicLevel() < spell.requiredLevel()) {
            Msg.send(player, "magic-spell-locked", "%level%", String.valueOf(spell.requiredLevel()),
                    "%spell%", spell.name());
            return;
        }
        long now = System.currentTimeMillis();
        long cdEnd = data.cooldown("spell-cd." + element.id() + "." + idx);
        if (cdEnd > now) {
            long left = (cdEnd - now + 999) / 1000;
            Msg.send(player, "magic-cooldown", "%time%", String.valueOf(left));
            return;
        }
        if (noCastActive(player.getUniqueId())) {
            Msg.send(player, "magic-no-cast", "%time%",
                    ((noCastUntil.getOrDefault(player.getUniqueId(), 0L) - now) / 1000 + 1) + " с.");
            return;
        }
        int manaCost = spell.mana();
        if (freeCastActive(player.getUniqueId())) manaCost = 0;
        else if (halfManaActive(player.getUniqueId())
                || (element == Element.LIGHT && lightBoostActive(player.getUniqueId()))) {
            manaCost = manaCost / 2;
        }
        if (!spendMana(data, manaCost)) {
            Msg.send(player, "magic-no-mana", "%need%", String.valueOf(manaCost));
            return;
        }
        if (element == Element.DARK && darkBoostActive(player.getUniqueId()) && spell.mana() > 0) {
            player.damage(Math.max(0.0, Math.min(2.0, player.getHealth() - 0.5))); // тёмная магия: −1 сердце за каст
        }
        data.setCooldown("spell-cd." + element.id() + "." + idx, now + spell.cooldownSeconds() * 1000);
        plugin.storage().save();
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.2f);
        switch (element) {
            case FIRE -> castFire(player, idx);
            case WATER -> castWater(player, idx);
            case WIND -> castWind(player, idx);
            case EARTH -> castEarth(player, idx);
            case DARK -> castDark(player, idx);
            case LIGHT -> castLight(player, idx);
        }
    }

    // ---------- снаряды и урон ----------

    /** Итоговый урон с матрицами стихий/причуд (без нанесения). */
    public double computeDamage(Player caster, LivingEntity victim, double base, Element element) {
        double mult = 1.0;
        if (victim instanceof Player vp) {
            for (Quirk quirk : Quirk.values()) {
                if (plugin.quirks().levelOf(vp, quirk) > 0) {
                    mult *= mult("e", element.id() + "." + quirk.id());
                }
            }
        }
        PlayerData data = data(caster);
        if (data != null) {
            for (Map.Entry<Quirk, Integer> entry : data.entries()) {
                mult *= mult("q", entry.getKey().id() + "." + element.id());
            }
        }
        double dmg = Math.max(0, base * mult);
        // Огненный профи усиливает и рукопашку, и снаряды
        if (element == Element.FIRE && fireMasterActive(caster.getUniqueId())) dmg *= 2;
        // Тёмная магия: заклинания тьмы x2
        if (element == Element.DARK && darkBoostActive(caster.getUniqueId())) dmg *= 2;
        return dmg;
    }

    /** Вычислить и нанести урон заклинания. */
    public double applySpellDamage(Player caster, LivingEntity victim, double base, Element element) {
        double dmg = computeDamage(caster, victim, base, element);
        victim.damage(dmg, caster);
        return dmg;
    }

    /** Сферический взрыв урона вокруг точки. */
    public void burst(Player caster, Location center, double radius, double base, Element element,
            double knockback, @Nullable java.util.function.Consumer<LivingEntity> extra) {
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity victim) || entity == caster) continue;
            if (victim.getLocation().distanceSquared(center) > radius * radius) continue;
            double dmg = applySpellDamage(caster, victim, base, element);
            applySpellSide(caster, victim, element);
            if (knockback > 0) {
                Vector dir = victim.getLocation().toVector().subtract(center.toVector()).normalize()
                        .multiply(knockback).setY(0.35);
                victim.setVelocity(victim.getVelocity().add(dir));
            }
            if (extra != null) extra.accept(victim);
        }
    }

    /** Стихийные дот/дрёфты от урона заклинаний (из таблицы «побочки»). */
    public void applySpellSide(@Nullable Player caster, LivingEntity victim, Element element) {
        if (element == null) return;
        int slow = 0;
        double drain = 0;
        switch (element) {
            case FIRE -> victim.setFireTicks(Math.max(victim.getFireTicks(), 60));      // поджог 3с
            case WATER -> slow = 40;                                                     // замедление 2с
            case WIND -> victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 8, 0, false, false));
            case EARTH -> slow = 60;                                                     // оглушение ~3с
            case DARK -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
                drain = 2;
            }
            case LIGHT -> victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false));
        }
        if (slow > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slow, 1, false, false));
        }
        if (drain > 0 && caster != null && caster.getHealth() > 0) {
            caster.setHealth(Math.min(maxHealth(caster), caster.getHealth() + drain));
        }
    }

    /** Магический снаряд-снежок с тегами урона. */
    public Snowball shoot(Player caster, Element element, Material view, double baseDamage, double speed) {
        Snowball ball = caster.launchProjectile(Snowball.class);
        ball.setItem(new ItemStack(view));
        ball.setVelocity(ball.getVelocity().multiply(speed));
        ball.getPersistentDataContainer().set(Keys.spellElem, PersistentDataType.STRING, element.id());
        ball.getPersistentDataContainer().set(Keys.spellDmg, PersistentDataType.DOUBLE, baseDamage);
        caster.getWorld().playSound((caster).getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.9f);
        return ball;
    }

    // ---------- огонь ----------

    private void castFire(Player player, int idx) {
        Anims.tornado(plugin, player.getLocation(), Particle.FLAME, 12, 0.8, 1.6);
        Location eye = player.getEyeLocation();
        World world = player.getWorld();
        switch (idx) {
            case 0 -> { // Искорки: веер из 3 файерболов по 2 урона
                for (int i = -1; i <= 1; i++) {
                    SmallFireball fb = player.launchProjectile(SmallFireball.class);
                    Vector dir = eye.getDirection().clone().rotateAroundY(Math.toRadians(i * 9));
                    fb.setVelocity(dir.multiply(1.4));
                    fb.setIsIncendiary(false);
                    fb.getPersistentDataContainer().set(Keys.spellElem, PersistentDataType.STRING, Element.FIRE.id());
                    fb.getPersistentDataContainer().set(Keys.spellDmg, PersistentDataType.DOUBLE, 2.0);
                }
                world.spawnParticle(Particle.FLAME, eye, 20, 0.3, 0.3, 0.3, 0.02);
            }
            case 1 -> { // Большой бум: 8 урона + взрыв при попадании
                Snowball boom = shoot(player, Element.FIRE, Material.FIRE_CHARGE, 8, 1.3);
                boom.getPersistentDataContainer().set(Keys.spellExtra, PersistentDataType.INTEGER, 1);
            }
            case 2 -> { // Огненная аура 30с
                stamp(fireAuraUntil, player, 30_000);
                world.spawnParticle(Particle.FLAME, player.getLocation(), 60, 0.6, 0.8, 0.6, 0.03);
                Msg.send(player, "magic-cast-fire3");
            }
            case 3 -> { // Огненный резист 10 мин
                stamp(fireProtUntil, player, 600_000);
                Msg.send(player, "magic-cast-fire4");
            }
            case 4 -> { // Огненный профи 60с
                stamp(fireMasterUntil, player, 60_000);
                stamp(fireProtUntil, player, 60_000);
                Msg.send(player, "magic-cast-fire5");
            }
            case 5 -> { // Огненный победитель: 7 блейзов на 60с без дропа
                for (int i = 0; i < 7; i++) {
                    Location at = player.getLocation().add(rand(3), 1.5, rand(3));
                    Blaze blaze = world.spawn(at, Blaze.class);
                    blaze.getPersistentDataContainer().set(Keys.noLoot, PersistentDataType.BYTE, (byte) 1);
                    blaze.setTarget(nearestMonster(player, 24));
                    blazesExpire.add(new long[]{blaze.getUniqueId().getMostSignificantBits(),
                        blaze.getUniqueId().getLeastSignificantBits(), System.currentTimeMillis() + 60_000});
                }
                world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1f, 0.8f);
                Msg.send(player, "magic-cast-fire6");
            }
            case 6 -> { // Огненный бог: неуявзвимость 8с + кольцо взрыва 6 урона
                stamp(invulnUntil, player, 8_000);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 1));
                burst(player, player.getLocation(), 5, 6, Element.FIRE, 0.8, v -> v.setFireTicks(100));
                world.createExplosion(player.getLocation(), 2.2f, false, false, player);
            }
        }
        fx(player, Particle.FLAME);
    }

    // ---------- вода ----------

    private void castWater(Player player, int idx) {
        Anims.ring(player.getLocation(), Particle.SPLASH, 1.4, 22, 0.01);
        World world = player.getWorld();
        switch (idx) {
            case 0 -> shoot(player, Element.WATER, Material.HEART_OF_THE_SEA, 4, 1.3); // Водяной шар
            case 1 -> { // Волна: 5 урона + отброс
                burst(player, player.getLocation(), 4, 5, Element.WATER, 1.1, null);
                world.spawnParticle(Particle.SPLASH, player.getLocation(), 120, 1.6, 0.6, 1.6, 0.01);
            }
            case 2 -> { stamp(waterAuraUntil, player, 30_000); Msg.send(player, "magic-cast-water3"); }
            case 3 -> { // Водный резист 10 мин
                stamp(waterProtUntil, player, 600_000);
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 10 * 60 * 20, 0, false, false));
                Msg.send(player, "magic-cast-water4");
            }
            case 4 -> { // Ледяные шипы: два кольца
                spikes(player, 4, 3.0, 8, Particle.SNOWFLAKE);
                spikes(player, 4, 5.0, 12, Particle.SNOWFLAKE);
            }
            case 5 -> { // Целитель глубин
                player.setHealth(Math.min(maxHealth(player), player.getHealth() + 8));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 45 * 20, 1));
                world.spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 8, 0.4, 0.5, 0.4, 0);
                Msg.send(player, "magic-cast-water6");
            }
            case 6 -> { // Водный бог
                stamp(invulnUntil, player, 5_000);
                player.setHealth(Math.min(maxHealth(player), player.getHealth() + 10));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 15 * 20, 0, false, false));
            }
        }
        fx(player, Particle.SPLASH);
    }

    // ---------- ветер ----------

    private void castWind(Player player, int idx) {
        Anims.ring(player.getLocation(), Particle.CLOUD, 1.2, 20, 0.02);
        World world = player.getWorld();
        switch (idx) {
            case 0 -> shoot(player, Element.WIND, Material.FEATHER, 2, 1.6); // Порыв (усил. отброс при попадании)
            case 1 -> { // Воздушный клинок: рывок вперёд
                Vector dir = player.getLocation().getDirection().normalize().multiply(1.8).setY(Math.max(0.3,
                        player.getLocation().getDirection().getY() * 0.6));
                player.setVelocity(player.getVelocity().add(dir));
                stamp(windDashUntil, player, 2_000);
                world.spawnParticle(Particle.CLOUD, player.getLocation(), 25, 0.3, 0.3, 0.3, 0.05);
            }
            case 2 -> { // Аура ветра 20с
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 400, 0));
                Msg.send(player, "magic-cast-wind3");
            }
            case 3 -> { stamp(windProtUntil, player, 300_000); Msg.send(player, "magic-cast-wind4"); }
            case 4 -> { // Вихрь: корни+урон
                burst(player, player.getLocation(), 6, 3, Element.WIND, 0.3,
                        v -> rootedUntil.put(v.getUniqueId(), System.currentTimeMillis() + 4_000));
                world.spawnParticle(Particle.SWEEP_ATTACK, player.getLocation(), 30, 2.2, 0.4, 2.2, 0);
            }
            case 5 -> { // Прыжок бури
                player.setVelocity(player.getVelocity().add(player.getLocation().getDirection().multiply(0.9))
                        .add(new Vector(0, 1.6, 0)));
                stamp(leapUntil, player, 6_000);
                Msg.send(player, "magic-cast-wind6");
            }
            case 6 -> { // Ветряной бог: полёт 20с + неуязвимость 4с
                stamp(invulnUntil, player, 4_000);
                flight(player, 20);
            }
        }
        fx(player, Particle.CLOUD);
    }

    // ---------- земля ----------

    private void castEarth(Player player, int idx) {
        Anims.ring(player.getLocation(), Particle.CRIT, 2.2, 32, 0.04);
        World world = player.getWorld();
        switch (idx) {
            case 0 -> { stamp(earthFistUntil, player, 15_000); Msg.send(player, "magic-cast-earth1"); }
            case 1 -> shoot(player, Element.EARTH, Material.COBBLED_DEEPSLATE, 5, 1.15); // Каменный шар
            case 2 -> { // Аура камня 30с
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 600, 0));
                Msg.send(player, "magic-cast-earth3");
            }
            case 3 -> { // Резист земли 5 мин
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 5 * 60 * 20, 1));
                Msg.send(player, "magic-cast-earth4");
            }
            case 4 -> { // Каменные шипы
                spikes(player, 6, 3.0, 8, Particle.CRIT);
                spikes(player, 6, 5.5, 12, Particle.CRIT);
            }
            case 5 -> buildWall(player); // Стена земли
            case 6 -> { // Земляной бог
                stamp(invulnUntil, player, 6_000);
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60 * 20, 4, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 30 * 20, 1));
            }
        }
        fx(player, Particle.WAX_OFF);
    }

    // ---------- тьма ----------

    private void castDark(Player player, int idx) {
        Anims.ring(player.getLocation(), Particle.SMOKE, 1.4, 26, 0.01);
        Anims.burst(player.getLocation().add(0, 1, 0), Particle.SCULK_SOUL, 8);
        World world = player.getWorld();
        switch (idx) {
            case 0 -> shoot(player, Element.DARK, Material.SCULK_SENSOR, 3, 1.3); // Тёмный шёпот
            case 1 -> { stamp(darkTouchUntil, player, 20_000); Msg.send(player, "magic-cast-dark2"); }
            case 2 -> { stamp(darkStealthUntil, player, 600_000); Msg.send(player, "magic-cast-dark3"); }
            case 3 -> { // Теневая защита: 25% уворота 60с
                stamp(darkEvadeUntil, player, 60_000);
                Msg.send(player, "magic-cast-dark4");
            }
            case 4 -> { // Теневые копии: эхо-урон 60с
                stamp(echoUntil, player, 60_000);
                Msg.send(player, "magic-cast-dark5");
            }
            case 5 -> { // Пожирание
                List<LivingEntity> victims = new ArrayList<>();
                burst(player, player.getLocation(), 5, 5, Element.DARK, 0.4, victims::add);
                double heal = victims.size() * 2.5;
                player.setHealth(Math.min(maxHealth(player), player.getHealth() + heal));
                world.spawnParticle(Particle.SCULK_SOUL, player.getLocation(), 40, 1.4, 0.8, 1.4, 0.02);
            }
            case 6 -> { // Тёмный бог: копии+стелс+неуязв+сила на 90с
                stamp(echoUntil, player, 90_000);
                stamp(darkStealthUntil, player, 90_000);
                stamp(invulnUntil, player, 6_000);
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 90 * 20, 1));
            }
        }
        fx(player, Particle.SCULK_SOUL);
    }

    // ---------- свет ----------

    private void castLight(Player player, int idx) {
        Anims.ring(player.getLocation(), Particle.END_ROD, 1.0, 18, 0.01);
        World world = player.getWorld();
        switch (idx) {
            case 0 -> shoot(player, Element.LIGHT, Material.GLOW_BERRIES, 4, 1.3); // Светящийся шар
            case 1 -> { // Целительный свет
                heal(player, 6);
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 0));
                for (Entity entity : world.getNearbyEntities(player.getLocation(), 5, 5, 5)) {
                    if (entity instanceof Player ally && entity != player) {
                        ally.setHealth(Math.min(maxHealth(ally), ally.getHealth() + 4));
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 300, 0));
                    }
                }
                world.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.4, 0), 24, 1.8, 0.8, 1.8, 0.02);
            }
            case 2 -> { // Аура света 30с: жжёт нежить рядом
                stamp(lightAuraUntil, player, 30_000);
                Msg.send(player, "magic-cast-light3");
            }
            case 3 -> { // Световой резист 5 мин
                stamp(lightProtUntil, player, 300_000);
                Msg.send(player, "magic-cast-light4");
            }
            case 4 -> { // Световой щит: пул 20 урона на 30с
                shieldPools.put(player.getUniqueId(), new double[]{20.0});
                shieldUntil.put(player.getUniqueId(), System.currentTimeMillis() + 30_000);
                Msg.send(player, "magic-cast-light5");
            }
            case 5 -> { // Кара нежити
                for (Entity entity : world.getNearbyEntities(player.getLocation(), 8, 8, 8)) {
                    if (entity instanceof LivingEntity victim && isUndead(victim)) {
                        victim.damage(computeDamage(player, victim, 15, Element.LIGHT), player);
                        victim.setFireTicks(120);
                    }
                }
                world.spawnParticle(Particle.END_ROD, player.getLocation(), 60, 3.0, 1.2, 3.0, 0.02);
            }
            case 6 -> { // Светлый бог
                stamp(invulnUntil, player, 8_000);
                heal(player, 10);
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60 * 20, 1, false, false));
                for (Entity entity : world.getNearbyEntities(player.getLocation(), 10, 10, 10)) {
                    if (entity instanceof Player ally) {
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 2));
                    }
                }
            }
        }
        fx(player, Particle.END_ROD);
    }

    // ---------- утилиты ----------

    /** Лечение игрока с бонусом светлой магии (x2). */
    public void heal(Player player, double amount) {
        if (lightBoostActive(player.getUniqueId())) amount *= 2;
        player.setHealth(Math.min(maxHealth(player), player.getHealth() + amount));
    }

    private void stamp(Map<UUID, Long> map, Player player, long millis) {
        map.put(player.getUniqueId(), System.currentTimeMillis() + millis);
    }

    /** Неуязвимость на millis (зелья берсерка/потупления). */
    public void grantInvuln(Player player, long millis) {
        stamp(invulnUntil, player, millis);
    }

    /** Пригвоздить игрока на millis (бастион/корни). */
    public void stampRoot(Player player, long millis) {
        stamp(rootedUntil, player, millis);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                (int) Math.max(1, millis / 50), 254, false, false));
    }

    /** Призванный страж: живёт millis, потом удаляется (без дропа). */
    public void summonGuardian(LivingEntity entity, long millis) {
        blazesExpire.add(new long[]{entity.getUniqueId().getMostSignificantBits(),
            entity.getUniqueId().getLeastSignificantBits(), System.currentTimeMillis() + millis});
    }

    public void flight(Player player, int seconds) {
        player.setAllowFlight(true);
        player.setFlying(true);
        flightUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    /** Завершить полёт мягко (замедленное падение). */
    public void endFlight(Player player) {
        flightUntil.remove(player.getUniqueId());
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }
        player.setAllowFlight(false);
        player.setFlying(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false));
    }

    // сколько осталось полёту безопасно снять при выключении/выходе
    public void endFlightIfActive(Player player) {
        if (flightActive(player.getUniqueId())) endFlight(player);
    }

    /** Кольцо шипов на радиусе: урон живым по кругу + частицы. */
    private void spikes(Player player, double base, double radius, int points, Particle particle) {
        World world = player.getWorld();
        Location center = player.getLocation();
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            Location at = new Location(world, x, center.getY(), z);
            for (double dy = 0; dy <= 2.0; dy += 0.35) {
                world.spawnParticle(particle, at.clone().add(0, dy, 0), 3, 0.08, 0.08, 0.08, 0.01);
            }
            Element element = elementOf(player);
            for (Entity entity : world.getNearbyEntities(at, 1.1, 1.6, 1.1)) {
                if (entity instanceof LivingEntity victim && entity != player) {
                    applySpellDamage(player, victim, base, element != null ? element : Element.EARTH);
                    applySpellSide(player, victim, element);
                }
            }
        }
        world.playSound(center, Sound.BLOCK_POINTED_DRIPSTONE_FALL, 1.2f, 0.7f);
    }

    /** Стена земли 5×3 на 15 секунд. */
    private void buildWall(Player player) {
        Location base = player.getLocation();
        Vector dir = base.getDirection().setY(0).normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        Location anchor = base.clone().add(dir.clone().multiply(3));
        List<EarthWall.WallBlock> blocks = new ArrayList<>();
        for (int w = -2; w <= 2; w++) {
            for (int h = 0; h < 3; h++) {
                Location at = anchor.clone().add(side.clone().multiply(w)).add(0, h, 0);
                Block block = at.getBlock();
                if (!block.getType().isAir() && block.getType() != Material.SHORT_GRASS
                        && block.getType() != Material.TALL_GRASS && block.getType() != Material.SNOW) {
                    continue;
                }
                blocks.add(new EarthWall.WallBlock(block.getLocation(), block.getType()));
                block.setType(Material.COBBLED_DEEPSLATE);
            }
        }
        if (!blocks.isEmpty()) {
            walls.add(new EarthWall(blocks, System.currentTimeMillis() + 15_000));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1.2f, 0.6f);
        Msg.send(player, "magic-cast-earth6");
    }

    @Nullable
    private Monster nearestMonster(Player player, double radius) {
        Monster best = null;
        double bestDist = radius * radius;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Monster monster) {
                double dist = monster.getLocation().distanceSquared(player.getLocation());
                if (dist < bestDist) { bestDist = dist; best = monster; }
            }
        }
        return best;
    }

    /** Нежить (зомби- и скелето-семейства, фантомы, визеры, зоглины). */
    public static boolean isUndead(LivingEntity entity) {
        return entity instanceof org.bukkit.entity.Zombie
                || entity instanceof org.bukkit.entity.AbstractSkeleton
                || entity instanceof org.bukkit.entity.Phantom
                || entity instanceof org.bukkit.entity.Wither
                || entity instanceof org.bukkit.entity.Zoglin
                || entity instanceof org.bukkit.entity.ZombieHorse
                || entity instanceof org.bukkit.entity.SkeletonHorse;
    }

    private static double rand(double spread) {
        return (Math.random() - 0.5) * spread * 2;
    }

    private void fx(Player player, Particle particle) {
        player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1.2, 0), 18, 0.5, 0.7, 0.5, 0.03);
    }

    // ---------- периодика (вызывается из MagicTask) ----------

    /** 1 сек: мана-реген, прозрачные элементы ауры. fast=true — каждый тик (ур. 20+). */
    public void tickSecond(Player player, boolean fast) {
        PlayerData data = data(player);
        if (data == null || data.magicElement() == null) return;
        int level = data.magicLevel();
        double max = maxMana(level);
        boolean regenNow = level >= fastRegenLevel() || fast;
        if (regenNow && data.mana() < max) {
            data.mana(Math.min(max, data.mana() + manaPerTick()));
        }
    }

    /** Ауры и свет: жжение нежити возле ауры света, пламя возле огненной ауры. */
    public void tickAuras(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (lightAuraActive(id)) {
            for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 4, 4, 4)) {
                if (entity instanceof LivingEntity victim && isUndead(victim)) {
                    victim.damage(2.0, player);
                }
            }
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 6, 1.6, 0.8, 1.6, 0.01);
        }
        if (fireAuraActive(id)) {
            player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 0.6, 0), 10, 0.5, 0.6, 0.5, 0.01);
        }
        if (waterAuraActive(id)) {
            player.getWorld().spawnParticle(Particle.DRIPPING_DRIPSTONE_WATER, player.getLocation().add(0, 0.6, 0),
                    6, 0.5, 0.6, 0.5, 0.01);
        }
        if (darkStealthActive(id) || darkEvadeActive(id) || echoActive(id)) {
            player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0, 0.8, 0), 3,
                    0.4, 0.5, 0.4, 0.005);
        }
        // истечение стен
        walls.removeIf(wall -> {
            if (wall.expiresAt() > now) return false;
            wall.restore();
            return true;
        });
        // истечение блейзов
        blazesExpire.removeIf(rec -> {
            if (rec[2] > now) return false;
            killBlaze(rec);
            return true;
        });
        // блок движения корнями
        if (isRooted(id)) {
            player.setVelocity(player.getVelocity().setX(0).setZ(0));
        }
        // мягкое окончание полёта
        if (flightUntil.containsKey(id) && !flightActive(id)) {
            endFlight(player);
            Msg.send(player, "magic-flight-end");
        }
        // возврат причуд
        restoreTmpQuirks(player);
    }

    private void killBlaze(long[] rec) {
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(new UUID(rec[0], rec[1]));
            if (entity != null) entity.remove();
        }
    }

    /** Стена земли. */
    public record EarthWall(java.util.List<WallBlock> blocks, long expiresAt) {

        public record WallBlock(org.bukkit.Location location, org.bukkit.Material original) {}

        public void restore() {
            for (WallBlock block : blocks) {
                if (block.location().getBlock().getType() == Material.COBBLED_DEEPSLATE) {
                    block.location().getBlock().setType(block.original());
                }
            }
        }
    }

    // ---------- снаряды: урон вычисляется в MagicListener ----------

    /** Урон из тегов снаряда. Возвращает 0, если это не наш снаряд. */
    public double projectileDamage(Projectile projectile) {
        Double dmg = projectile.getPersistentDataContainer().get(Keys.spellDmg, PersistentDataType.DOUBLE);
        return dmg == null ? 0 : dmg;
    }

    @Nullable
    public Element projectileElement(Projectile projectile) {
        String id = projectile.getPersistentDataContainer().get(Keys.spellElem, PersistentDataType.STRING);
        return Element.byId(id);
    }

    // ============ зелья-стороны (счётчик зелья причуды и max-HP модификаторы) ============

    /** Добавить временный модификатор -2 maxHP (на millis). */
    public void addTempMaxHp(Player player, org.bukkit.NamespacedKey key, double amount) {
        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        AttributeModifier old = inst.getModifier(key);
        double sum = amount;
        if (old != null) {
            sum += old.getAmount();
            inst.removeModifier(key);
        }
        inst.addModifier(new AttributeModifier(key, sum, AttributeModifier.Operation.ADD_NUMBER));
        if (player.getHealth() > inst.getValue()) {
            player.setHealth(Math.max(1, inst.getValue()));
        }
    }

    /** Добавить перманентный −1 сердце (суммируется). */
    public void addPermHeartLoss(Player player) {
        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        AttributeModifier old = inst.getModifier(Keys.qpPerm);
        double sum = -2.0;
        if (old != null) {
            sum += old.getAmount();
            inst.removeModifier(Keys.qpPerm);
        }
        inst.addModifier(new AttributeModifier(Keys.qpPerm, sum, AttributeModifier.Operation.ADD_NUMBER));
        if (player.getHealth() > inst.getValue()) {
            player.setHealth(Math.max(1, inst.getValue()));
        }
    }

    public void removeModifier(Player player, org.bukkit.NamespacedKey key) {
        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        if (inst != null && inst.getModifier(key) != null) inst.removeModifier(key);
    }

    /** player.getAttribute crafty getter для лечения. */
    public static double maxHealth(Player player) {
        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        return inst == null ? 20.0 : inst.getValue();
    }

    /** CraftListener hook placeholder (используется CraftListener для пометок зелий). */
    public static String craftHookProbe(ItemStack item) {
        String mark = item.getPersistentDataContainer().get(Keys.brewMark, PersistentDataType.STRING);
        return mark == null ? "" : mark;
    }
}
