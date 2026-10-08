package me.darkz70.quirks.util;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Сообщения из config.yml с &-цветами и простыми %-плейсхолдерами.
 * Если ключа нет в конфиге (старый конфиг у игрока), берётся встроенный дефолт.
 */
public final class Msg {

    private static FileConfiguration cfg;
    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        def("no-permission", "%prefix%&cНедостаточно прав.");
        def("usage", "%prefix%&f/quirk set <игрок> <причуда> [уровень] &8| &f/quirk remove <игрок> &8| &f/quirk info [игрок] &8| &f/quirk list &8| &f/quirk reload");
        def("target-offline", "%prefix%&cИгрок не найден или не в сети.");
        def("invalid-quirk", "%prefix%&cНеизвестная причуда. Доступны: инженер, кот, бедрок, топор, скалк, фермер, земноводный, паук.");
        def("invalid-level", "%prefix%&cУровень должен быть от 1 до 3.");
        def("set-ok", "%prefix%&aИгроку &f%player% &aназначена причуда &d%quirk% &a(ур. &d%level%&a).");
        def("removed", "%prefix%&aПричуда игрока &f%player% &aснята.");
        def("reload-ok", "%prefix%&aКонфиг перезагружен.");
        def("you-got-quirk", "%prefix%&dРасколотая душа отозвалась… в ней что-то дрогнуло.");
        def("you-lost-quirk", "%prefix%&7Что-то в душе истлело и исчезло…");
        def("info-header", "&8&m--------&r &5Причуды %player% &8&m--------");
        def("info-line", "&fПричуда: &d%quirk% &7| &fУровень: &d%level%");
        def("info-notify", "&fОповещения: &d%state%");
        def("info-none", "%prefix%&7У игрока нет причуды.");
        def("list-header", "&8&m-----&r &5Причуды игроков &8&m-----");
        def("list-line", "&f%player% &8— &d%quirks%");
        def("list-empty", "%prefix%&7Причуд ни у кого нет.");
        def("notify-on", "%prefix%&7Оповещения причуд &aвключены&7.");
        def("notify-off", "%prefix%&7Оповещения причуд &cвыключены&7. Включить обратно: /quirk notify");
        def("engineer-meat-denied", "&cЭльфийский желудок не может это переварить!");
        def("axe-sword-craft-denied", "&cТвои руки отталкивают мечи. Крафт невозможен.");
        def("axe-sword-to-stick", "&eМеч в твоём инвентаре рассыпался в палку…");
        def("axe-no-meat", "&2Тебя мутит… твоё тело принимает только мясо.");
        def("axe-hint-2", "&eShift+ЛКМ &fтопором — ярость");
        def("axe-hint-3", "&eShift+ПКМ &f— разрыв пространства &8| &eShift+ЛКМ &f— ярость");
        def("axe-rage-on", "&4Ярость переполняет тебя!");
        def("axe-rage-cooldown", "&cЯрость остывает: &f%time%&c.");
        def("axe-teleport-cooldown", "&cРывок ещё остывает: &f%time% с.");
        def("axe-teleport-fail", "&cНекуда телепортироваться.");
        def("bedrock-steaks", "%prefix%&7Расколотая душа отзывается: &f+%amount% стейка(-ов)!");
        def("bedrock-aoe", "%prefix%&8Ударная волна души ранила монстров поблизости!");
        def("bedrock-immortal", "%prefix%&8Душа вспыхивает ядром — ты неуязвим!");
        def("sculk-denied", "&2Плоть отторгает это…");
        def("shard-given", "%prefix%&aОсколок души &d%quirk% &aур. &d%level% &aвыдан игроку &f%player%&a.");
        def("shard-used", "%prefix%&dОсколок души вливается в тебя… душа отозвалась.");
        def("shard-weaker", "%prefix%&7Твоя душа уже носит эту причуду (или сильнее). Осколок сохранён.");
        def("soul-freed", "&d✦ &b%player% &dосвободил душу из клетки лабиринта! Она оставила осколок…");
        def("pack-link", "%prefix%&bРесурспак VoidSMP: &f%link%\n%prefix%&7Пропиши ссылку в server.properties в строку &fresource-pack=&7 и перезапусти.");
        def("lab-building", "%prefix%&5Начинаю строить лабиринт душ в мире &f%world%&5… не выключай сервер.");
        def("lab-created", "%prefix%&dЛабиринт душ построен! Площадь &f%size%&d, клеток с душами: &f%cages%&d. Телепорт: /quirk lab tp");
        def("lab-exists", "%prefix%&eЛабиринт уже построен. Телепорт: /quirk lab tp");
        def("lab-no-world", "%prefix%&cЛабиринт ещё не создан. Сначала /quirk lab create");
        def("lab-tp", "%prefix%&dТы входишь в лабиринт душ…");
        def("title-engineer-redstone", "&cРедстоуна поблизости: &f%count%");
        def("title-engineer-tnt", "&4ТНТ рядом: &c%count%");
        def("title-cat-water", "&bВоды поблизости: &f%count%");
        // v0.6.0 — крафты, Фермер, Земноводный, Паук
        def("antidote-cured", "%prefix%&2Антидот жжёт вены… чужеродное покидает твоё тело!");
        def("antidote-none", "%prefix%&7Горьковато. Но и изгонять нечего.");
        def("farmer-raw-denied", "&eСырое! Это нужно пожарить.");
        def("farmer-craft-denied", "&cТвоим рукам этот рецепт не подвластен.");
        def("spider-meat-denied", "&cЭто тело питается только мясом!");
        def("spider-webs-hint", "&eShift + ПКМ мечом &f— выстрел паутиной &8(&7нужно 9 паутины&8)");
        def("spider-webs-cooldown", "&cПаутинные железы отдыхают: &f%time%&c.");
        def("spider-webs-none", "&cНужно минимум 9 блоков паутины в инвентаре!");
        def("spider-webs-cast", "&8*фшш* &7Паутина выпущена!");
        def("amph-armor-denied", "&bНезерит тянет тебя на дно — броня соскальзывает!");

        // v1.0 — магия
        def("magic-no-mage", "&7Ты ещё не ощутил стихию. Найди книгу стихии…");
        def("magic-bar", "%emoji% %element% &8| &bМана %mana%/%max% &8| &f%spell%");
        def("magic-learned", "&dСтихия отозвалась! %emoji% &f%element% &dстала твоей. &7ПКМ — каст, Q/F — смена, Shift+F — меню.");
        def("magic-already-element", "&7Твоя душа уже слушает: &f%element%&7. Смена — только зельем переквалификации.");
        def("magic-need-four-books", "&cСвет и тьма смиряются только теми, кто держит &f4 книги&c других стихий.");
        def("magic-book-weak", "&cЭта книга слишком сильна для тебя. Нужен уровень магии %min%+.");
        def("magic-level-up", "&b✦ Уровень магии: &f%level%&b!");
        def("magic-spell-locked", "&8Заклинание &7%spell% &8откроется на %level% уровне магии.");
        def("magic-cooldown", "&7Заклинание остывает: &f%time%с&7.");
        def("magic-no-mana", "&cМаны не хватает — нужно %need%.");
        def("magic-quirks-back", "&7Отголоски чужих душ рассеялись. Ты снова собой.");
        def("magic-flight-end", "&7Крылья магии угасают… плавное снижение.");
        def("magic-menu-title", "%emoji% Заклинания: %element%");
        def("magic-menu-item", "%emoji% &f%spell% %state%");
        def("magic-usage", "&f/magic &8— меню заклинаний &8| &f/magic book <стихия> [игрок] &8| &f/magic upbook <1-5> [игрок] &8| &f/magic brew <тег> [игрок] &8| &f/magic set <игрок> <element|level|mana> <значение>");
        def("magic-bad-element", "&cНеизвестная стихия. Доступны: fire, water, wind, earth, dark, light.");
        def("magic-book-given", "&aКнига стихии &d%element% &aвыдана игроку &f%player%&a.");
        def("magic-brew-given", "&aЗелье &d%brew% &aвыдано игроку &f%player%&a.");
        def("magic-bad-brew", "&cНеизвестный тег зелья. См. таб-комплит.");
        def("magic-set-ok", "&aМагия игрока &f%player% &aобновлена.");
        def("magic-cast-fire3", "&cТвоя кожа дышит жаром — огненная аура!");
        def("magic-cast-fire4", "&6Огонь больше не тронет тебя (10 минут).");
        def("magic-cast-fire5", "&6Ты — огненный профи: жар в крови, удары вдвойне!");
        def("magic-cast-fire6", "&4Семь блейзов отвечают на зов!");
        def("magic-cast-water3", "&bЛёд стынет вокруг тебя.");
        def("magic-cast-water4", "&bВода теперь твой дом (10 минут).");
        def("magic-cast-water6", "&dГлубины делятся своей силой.");
        def("magic-cast-wind3", "&fВетер подхватывает твои шаги.");
        def("magic-cast-wind4", "&fПадение больше не страшно (5 минут).");
        def("magic-cast-wind6", "&fПорабощённая буря несёт тебя к небу!");
        def("magic-cast-earth1", "&7Каменный кулак заряжен — следующий удар сокрушит.");
        def("magic-cast-earth3", "&7Каменная аура окружила тебя.");
        def("magic-cast-earth4", "&7Ты твёрд, как сама порода (5 минут).");
        def("magic-cast-earth6", "&8Земля встаёт стеной!");
        def("magic-cast-dark2", "&5Твои руки касаются теней.");
        def("magic-cast-dark3", "&5Тьма прячет тебя от чужих глаз (10 минут).");
        def("magic-cast-dark4", "&5Тени укрывают — четверть ударов пройдёт мимо.");
        def("magic-cast-dark5", "&5Твои теневые копии дублируют удары!");
        def("magic-cast-light3", "&eАура света жжёт нежить вокруг (30 с).");
        def("magic-cast-light4", "&eСвет защищает твой разум (5 минут).");
        def("magic-cast-light5", "&eСветовой щит: поглощает до 20 урона (30 с).");
        // v1.0 — зелья
        def("potion-quirk-away", "&8Что-то внутри тебя растворилось в тишине…");
        def("potion-nothing", "&7Прошло сквозь тебя, ничего не найдя.");
        def("potion-soul-stir", "&5Что-то вибрирует из глубин… твоя душа отзывается.");
        def("potion-samogon", "&6Самогон ур. %level% &7— горит, но живёшь.");
        def("potion-deny", "&8Всё хорошее смыто.");
        def("potion-confirm", "&fВсё плохое смыто.");
        def("potion-life", "&cЖизнь пульсирует в тебе!");
        def("potion-limit", "&cТело не выдержит ещё одну дозу так скоро.");
        def("potion-watch", "&8Зелье смотрит, справишься ли ты… (60 с)");
        def("potion-noharm-strike", "&4&lТы ослаб — зелье вреда отомстило!");
        def("potion-norestore-gift", "&2&lТы выстоял без лечения — восстановление отвечает!");
        def("potion-fly", "&fВосемь секунд — и ты птица!");
        def("potion-god", "&6&lТы бог. Две минуты.");
        def("potion-god-price", "&c&lРасплата: −2 максимального здоровья на 7 дней…");
        def("potion-berserk", "&4&lЯРОСТЬ БЕЗ БОЛИ!");
        def("potion-qp-short", "&dНа десять секунд ты — всё и каждый…");
        def("potion-qp-warn", "&4Глоток №%count% за полчаса. Душа рвётся: −2 макс. здоровья на 5 минут!");
        def("potion-qp-death", "&4&lДУША ЛОПНУЛА. &c&oСердце навсегда слабее…");
        def("potion-qp-ban", "&8&oТвоя душа навсегда безмолвна для этого зелья.");
        def("potion-return", "&bЧистый лист. Всё, что наслоила магия душ, смыто.");
        def("potion-retrain", "&eСтихия забыта. Можно начать магию заново.");
        def("potion-demagic", "&5Волна притупления расходится от тебя…");
        def("potion-demagic-hit", "&5Твой уровень магии осыпался на единицу.");
        def("potion-grandsam", "&6Великий самогон вскипает в жилах!");
        def("gaid-given", "&aКнига-гайд открыта. Приятного чтения.");
        def("gaid-player-only", "&cТолько из игры.");
    }

    private Msg() {}

    private static void def(String path, String text) {
        DEFAULTS.put(path, text);
    }

    public static void init(JavaPlugin plugin) {
        cfg = plugin.getConfig();
    }

    /** Компонент из messages.<path>. repl — пары "ключ", "значение". */
    public static Component comp(String path, String... repl) {
        String raw = cfg.getString("messages." + path);
        if (raw == null) raw = DEFAULTS.getOrDefault(path, path);
        raw = raw.replace("%prefix%", cfg.getString("prefix", ""));
        for (int i = 0; i + 1 < repl.length; i += 2) {
            raw = raw.replace(repl[i], repl[i + 1]);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    /** Окраска произвольной строки &-кодами. */
    public static Component color(String input) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(input);
    }

    public static void send(CommandSender to, String path, String... repl) {
        to.sendMessage(comp(path, repl));
    }
}
