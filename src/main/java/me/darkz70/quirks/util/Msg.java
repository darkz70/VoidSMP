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
        def("you-got-quirk", "%prefix%&dТвоя расколотая душа отозвалась! Причуда: &f%quirk% &dур. &f%level%&d.");
        def("you-lost-quirk", "%prefix%&7Твоя причуда угасла…");
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
        def("axe-hint-2", "&eНажми &fCtrl &eс топором в руке — режим ярости");
        def("axe-hint-3", "&eShift+ПКМ &f— разрыв пространства &8| &eCtrl &f— ярость");
        def("axe-rage-on", "&4Ярость переполняет тебя!");
        def("axe-rage-cooldown", "&cЯрость остывает: &f%time%&c.");
        def("axe-teleport-cooldown", "&cРывок ещё остывает: &f%time% с.");
        def("axe-teleport-fail", "&cНекуда телепортироваться.");
        def("bedrock-steaks", "%prefix%&7Расколотая душа отзывается: &f+%amount% стейка(-ов)!");
        def("bedrock-aoe", "%prefix%&8Ударная волна души ранила монстров поблизости!");
        def("bedrock-immortal", "%prefix%&8Ядро бедрока вспыхивает — ты неуязвим!");
        def("sculk-denied", "&2Плоть отторгает это…");
        def("shard-given", "%prefix%&aОсколок души &d%quirk% &aур. &d%level% &aвыдан игроку &f%player%&a.");
        def("shard-used", "%prefix%&dОсколок души вливается в тебя… Причуда: &f%quirk% &dур. &f%level%&d.");
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
        def("antidote-cured", "%prefix%&2Антидот жжёт вены… Скалк покидает твоё тело!");
        def("antidote-none", "%prefix%&7Горьковато. Но лечить нечего — скалка в тебе нет.");
        def("farmer-raw-denied", "&eСырое! Это нужно пожарить.");
        def("farmer-craft-denied", "&cТолько Фермер знает рецепт супер-удобрения.");
        def("spider-meat-denied", "&cПаук питается только мясом!");
        def("spider-webs-hint", "&eShift + ПКМ мечом &f— выстрел паутиной &8(&7нужно 9 паутины&8)");
        def("spider-webs-cooldown", "&cПаутинные железы отдыхают: &f%time%&c.");
        def("spider-webs-none", "&cНужно минимум 9 блоков паутины в инвентаре!");
        def("spider-webs-cast", "&8*фшш* &7Паутина выпущена!");
        def("amph-armor-denied", "&bНезерит тянет тебя на дно — броня соскальзывает!");
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
