package me.darkz70.quirks.command;

import me.darkz70.quirks.util.Msg;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/** Тематические книги гайда (открываются читалкой из /gaid). */
public final class GaidBooks {

    private GaidBooks() { }

    private static ItemStack book(String title, String... pages) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(title);
        meta.setAuthor("Хранитель недр");
        for (String page : pages) {
            meta.addPages(Msg.color(page));
        }
        book.setItemMeta(meta);
        return book;
    }

    private static void open(Player player, ItemStack book) {
        player.openBook(book);
    }

    // ---------- 1. Книга причуд ----------

    public static void openQuirks(Player player) {
        open(player, book("Книга причуд",
                "&5&lКНИГА ПРИЧУД&r\n\n"
                        + "&dВОИТЕЛЬ&r топор-ярость: Shift+ЛКМ, веер. Разрыв: Shift+ПКМ.\n\n"
                        + "&dПАУЧИХА&r паутина Shift+ПКМ, удавы по уровням.\n\n"
                        + "&dБЕССМЕРТНАЯ КОРОЛЕВА&r прячет 5 сердец…",
                "&dСКАЛК&r — граница тьмы; только антидот-сосуды снимают её (не отключение).\n"
                        + "&dФЕРМЕР&r — удобрение двойной костной муки.\n"
                        + "&dМУРЛЫКАТЕЛЬНИЦА&r — кот в руке: ночурон и пуш-guard.\n",
                "&dАМФИБИЯ&r — все дары воды.\n"
                        + "&dИНЖЕНЕР&r — радиусы ×5, лавовое копьё. Шифтская проверка для дыр.\n\n"
                        + "&cДУШИ&r — осколки в лабиринтах, съедение 4 частей даёт причуду."));
    }

    // ---------- 2. Книга магии ----------

    public static void openMagic(Player player) {
        open(player, book("Книга магии",
                "&5&lКНИГА МАГИИ&r\n\n"
                        + "Стихии: огненная, водяная, ветряная, земляная, тёмная, светлая.\n\n"
                        + "Фолиант (книга + 4 аметиста + символ стихии) — ПКМ даёт стихию.",
                "Мана: 50+макс ур, реген 1/10с.\n\n"
                        + "Каст: ПКМ кристаллом.\n"
                        + "Смена заклинания: Q или Shift+F (меню) — F цикл.\n\n"
                        + "Апгрейд: книга прокачки до 9/19/29/39/∞."));
    }

    // ---------- 3. Книга зелий ----------

    public static void openBrewing(Player player) {
        open(player, book("Книга зелий",
                "&5&lТАЙНЫ ВАРВАРА&r\n\n"
                        + "Нейтралка: вред + исцел + вред + исцел + редстоун + порох.\n"
                        + "Основа: 2 нейтралки + исцел + вред.\n\n"
                        + "Отключение: нейтралка + топор + мотыга + паутина + скалк + любая рыба…",
                "…+ блок изумр., блок редст., трезубец. Снимает всё, кроме скалка — её снимает антидот.\n\n"
                        + "Все прочие зелья рисуются секретными раскладками. Стрелы: варёное + 8 обычных = 32."));
    }

    // ---------- 4. Книга команд ----------

    public static void openCommands(Player player) {
        open(player, book("Книга команд",
                "&5&lКОМАНДЫ&r\n\n"
                        + "/quirk — твои причуды.\n"
                        + "/quirk list | info | give | heal | item\n"
                        + "/quirk remove_tmp — снять временные.\n"
                        + "/quirk remove_admin — снять причуду Админ.",
                "/magic — мана/ур./кулдауны.\n"
                        + "/magic learn | clear | info | pick\n\n"
                        + "/gaid — это меню.\n\n"
                        + "Shift+Q под /quirks.admin — админ-панель по игрокам."));
    }
}
