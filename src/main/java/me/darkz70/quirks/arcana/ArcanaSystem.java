package me.darkz70.quirks.arcana;

import java.util.ArrayList;
import java.util.List;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Ядро НОВОЙ системы магии Arcana (#99, причуда MAGE — «Маг», доступна обычным
 * игрокам): 12 стихий, независимая от старой {@link me.darkz70.quirks.magic.MagicSystem}
 * (теперь на технической причуде ADMIN_MAG — только через /gaid, для админов).
 * Старая система не трогается — ни полями PlayerData, ни ключами PDC, ни логикой;
 * поменялось только то, какая причуда какую систему магии открывает.
 */
public final class ArcanaSystem {

    private final VoidQuirksPlugin plugin;

    public ArcanaSystem(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    public VoidQuirksPlugin plugin() {
        return plugin;
    }

    public boolean hasQuirk(Player player) {
        // ADMIN — универсальный пропуск везде по плагину (как и у старой системы,
        // см. MageMenu.hasAccess/CraftListener), поэтому и здесь даёт доступ к Аркане
        // даже без явно выданной причуды MAGE.
        return plugin.quirks().levelOf(player, Quirk.MAGE) > 0
                || plugin.quirks().levelOf(player, Quirk.ADMIN) > 0;
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

    public int maxMana(int level) {
        return 30 + level * 6;
    }

    /** Список изученных стихий игрока, в фиксированном порядке enum-а. */
    public List<Arcana> learned(Player player) {
        PlayerData data = data(player);
        List<Arcana> list = new ArrayList<>();
        if (data == null) return list;
        for (Arcana a : Arcana.values()) {
            if (data.arcanaLevelOf(a.id()) > 0) list.add(a);
        }
        return list;
    }

    @Nullable
    public Arcana activeElement(Player player) {
        PlayerData data = data(player);
        if (data == null || data.arcanaActive() == null) return null;
        Arcana a = Arcana.byId(data.arcanaActive());
        return (a != null && data.arcanaLevelOf(a.id()) > 0) ? a : null;
    }

    // ---------- изучение стихии фолиантом ----------

    /** Обычный фолиант (с причудой): изучить может только MAGE/ADMIN. */
    public boolean learnElement(Player player, Arcana element) {
        if (!hasQuirk(player)) {
            Msg.send(player, "arcana-no-quirk");
            return false;
        }
        return doLearn(player, element, false);
    }

    /**
     * Фолиант БЕЗ причуды: изучить и кастовать может ЛЮБОЙ игрок, причуда
     * MAGE/ADMIN не требуется ни сейчас, ни впредь для именно этой стихии
     * (см. {@link PlayerData#arcanaIsFree(String)} и проверку в {@link #cast}).
     */
    public boolean learnElementFree(Player player, Arcana element) {
        return doLearn(player, element, true);
    }

    private boolean doLearn(Player player, Arcana element, boolean free) {
        PlayerData data = dataOrCreate(player);
        if (data.arcanaLevelOf(element.id()) > 0) {
            Msg.send(player, "arcana-already", "%element%", element.display());
            return false;
        }
        boolean hasAny = !learned(player).isEmpty();
        if (hasAny) {
            boolean anyMastered = learned(player).stream().anyMatch(a -> data.arcanaLevelOf(a.id()) >= 20);
            if (!anyMastered) {
                Msg.send(player, "arcana-need-20");
                return false;
            }
        }
        data.arcanaSetLevel(element.id(), 1);
        data.arcanaSpellIndex(element.id(), 0);
        data.arcanaSetFree(element.id(), free);
        if (data.arcanaActive() == null) data.arcanaActive(element.id());
        plugin.storage().save();
        Msg.send(player, "arcana-learned", "%element%", element.display(), "%emoji%", element.emoji());
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.02);
        return true;
    }

    /**
     * Общий доступ к UI/переключению Арканы: причуда MAGE/ADMIN, ИЛИ у игрока
     * есть хотя бы одна стихия, изученная фолиантом без причуды.
     */
    public boolean canUseArcana(Player player) {
        if (hasQuirk(player)) return true;
        PlayerData data = data(player);
        if (data == null) return false;
        for (Arcana a : Arcana.values()) {
            if (data.arcanaLevelOf(a.id()) > 0 && data.arcanaIsFree(a.id())) return true;
        }
        return false;
    }

    // ---------- переключение стихии / заклинания ----------

    public void cycleElement(Player player) {
        PlayerData data = data(player);
        if (data == null) { Msg.send(player, "arcana-no-element"); return; }
        List<Arcana> list = learned(player);
        if (list.isEmpty()) { Msg.send(player, "arcana-no-element"); return; }
        if (list.size() == 1) {
            data.arcanaActive(list.get(0).id());
            plugin.storage().save();
            Msg.send(player, "arcana-no-other");
            return;
        }
        Arcana current = activeElement(player);
        int idx = current == null ? -1 : list.indexOf(current);
        Arcana next = list.get((idx + 1) % list.size());
        data.arcanaActive(next.id());
        plugin.storage().save();
        Msg.send(player, "arcana-switched", "%element%", next.display(), "%emoji%", next.emoji());
        player.getWorld().playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
    }

    public void cycleSpell(Player player) {
        PlayerData data = data(player);
        Arcana element = activeElement(player);
        if (data == null || element == null) { Msg.send(player, "arcana-no-element"); return; }
        ArcanaSpell[] spells = ArcanaSpells.of(element);
        int level = data.arcanaLevelOf(element.id());
        int current = Math.max(0, Math.min(data.arcanaSpellIndex(element.id()), spells.length - 1));
        for (int step = 1; step <= spells.length; step++) {
            int next = (current + step) % spells.length;
            if (level >= spells[next].requiredLevel()) {
                data.arcanaSpellIndex(element.id(), next);
                plugin.storage().save();
                showBar(player, element, data, next);
                player.getWorld().playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
                return;
            }
        }
    }

    public void showBar(Player player, Arcana element, PlayerData data, int idx) {
        ArcanaSpell spell = ArcanaSpells.of(element)[idx];
        player.sendActionBar(Msg.comp("arcana-bar",
                "%emoji%", element.emoji(),
                "%mana%", String.valueOf((int) Math.floor(data.arcanaMana())),
                "%max%", String.valueOf(maxMana(data.arcanaLevelOf(element.id()))),
                "%element%", element.display(),
                "%spell%", spell.name()));
    }

    // ---------- каст ----------

    public void cast(Player player) {
        PlayerData data = data(player);
        Arcana element = activeElement(player);
        if (data == null || element == null) { Msg.send(player, "arcana-no-element"); return; }
        // причуда MAGE/ADMIN открывает каст любой изученной стихии; без причуды —
        // только той, что изучена фолиантом БЕЗ причуды (см. learnElementFree).
        if (!hasQuirk(player) && !data.arcanaIsFree(element.id())) {
            Msg.send(player, "arcana-no-quirk");
            return;
        }
        int level = data.arcanaLevelOf(element.id());
        ArcanaSpell[] spells = ArcanaSpells.of(element);
        int idx = Math.max(0, Math.min(data.arcanaSpellIndex(element.id()), spells.length - 1));
        ArcanaSpell spell = spells[idx];
        if (level < spell.requiredLevel()) {
            Msg.send(player, "arcana-spell-locked", "%spell%", spell.name(), "%level%", String.valueOf(spell.requiredLevel()));
            return;
        }
        long cdMs = (long) (spell.cooldownSeconds() * 1000);
        long remaining = ScanUtil.remaining(player.getUniqueId(), "arcana:" + spell.id(), cdMs);
        if (remaining > 0) {
            Msg.send(player, "arcana-cooldown", "%time%", String.valueOf((remaining + 999) / 1000));
            return;
        }
        if (data.arcanaMana() < spell.mana()) {
            Msg.send(player, "arcana-no-mana", "%need%", String.valueOf((int) spell.mana()));
            return;
        }
        if (!ScanUtil.tryUse(player.getUniqueId(), "arcana:" + spell.id(), cdMs)) return;
        data.arcanaMana(data.arcanaMana() - spell.mana());
        gainXp(player, data, element, level);
        plugin.storage().save();
        spell.effect().cast(this, player, element, level);
        showBar(player, element, data, idx);
    }

    // ---------- прокачка стихии (опыт за каст) ----------

    private void gainXp(Player player, PlayerData data, Arcana element, int level) {
        if (level >= 20) return;
        double gained = data.arcanaXp(element.id()) + 3 + level * 0.4;
        double need = 15 + level * 10;
        if (gained >= need) {
            int newLevel = Math.min(20, level + 1);
            data.arcanaSetLevel(element.id(), newLevel);
            data.arcanaXp(element.id(), 0.0);
            Msg.send(player, "arcana-level-up", "%element%", element.display(), "%level%", String.valueOf(newLevel));
        } else {
            data.arcanaXp(element.id(), gained);
        }
    }

    // ---------- реген маны ----------

    public void tickSecond(Player player) {
        if (!canUseArcana(player)) return;
        PlayerData data = data(player);
        Arcana element = activeElement(player);
        if (data == null || element == null) return;
        int level = data.arcanaLevelOf(element.id());
        int max = maxMana(level);
        if (data.arcanaMana() < max) {
            data.arcanaMana(Math.min(max, data.arcanaMana() + 2 + level * 0.15));
        }
    }
}
