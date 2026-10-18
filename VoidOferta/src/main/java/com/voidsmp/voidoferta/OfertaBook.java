package com.voidsmp.voidoferta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Собирает подписанную книгу с текстом оферты.
 *
 * <p>Одна страница ванильной книги вмещает совсем немного текста,
 * поэтому длинный текст оферты автоматически режется на страницы
 * по абзацам и словам. В конце добавляется отдельная страница
 * подтверждения с кликабельной кнопкой «ПРИНЯТЬ ОФЕРТУ»
 * (выполняет команду {@code /oferta accept}).
 */
public final class OfertaBook {

    private final VoidOfertaPlugin plugin;
    private String rawText = "";

    public OfertaBook(VoidOfertaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText == null ? "" : rawText;
    }

    /** Создаёт книгу под конкретного игрока (страница подтверждения зависит от того, принял ли он оферту). */
    public ItemStack createBook(boolean alreadyAccepted, String acceptedAt) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();

        meta.title(Component.text(plugin.getConfig().getString("book-title", "Публичная оферта № VDS-VOID-01")));
        meta.author(Component.text(plugin.getConfig().getString("book-author", "VDS Corp")));
        meta.setGeneration(BookMeta.Generation.ORIGINAL);

        List<Component> pages = new ArrayList<>();
        pages.add(coverPage());
        for (String pageText : paginate(rawText, pageSize())) {
            pages.add(Component.text(pageText));
        }
        // Добиваем книгу ровно до 42 страниц: перед финальной идут
        // приложения к оферте, а страница 42 — цитаты Стэтхэма.
        // (Если текст в oferta.txt сильно вырастет, приложений просто
        // не будет, а финальная страница уедет дальше 42-й.)
        int appendix = 1;
        while (pages.size() < 41) {
            pages.add(appendixPage(appendix++));
        }
        pages.add(stathamPage(alreadyAccepted, acceptedAt));

        // У ванильной книги жёсткий лимит в 100 страниц
        if (pages.size() > 100) {
            pages = new ArrayList<>(pages.subList(0, 100));
        }

        meta.pages(pages);
        item.setItemMeta(meta);
        return item;
    }

    private int pageSize() {
        return Math.max(80, Math.min(250, plugin.getConfig().getInt("page-size", 210)));
    }

    private Component coverPage() {
        return Component.empty()
                .append(Component.text("ОФИЦИАЛЬНАЯ\nПУБЛИЧНАЯ ОФЕРТА\n", NamedTextColor.DARK_PURPLE)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text("№ VDS-VOID-01\n\n", NamedTextColor.DARK_PURPLE))
                .append(Component.text("Эмитент: ", NamedTextColor.BLACK).decorate(TextDecoration.BOLD))
                .append(Component.text("VDS Corp\nпод влиянием OZO™\n\n", NamedTextColor.BLACK))
                .append(Component.text("Предмет: ", NamedTextColor.BLACK).decorate(TextDecoration.BOLD))
                .append(Component.text("участие в сервере\nVOID SMP\n\n", NamedTextColor.BLACK))
                .append(Component.text("Листайте страницы.\nНа последней —\nкнопка подтверждения.\n\n", NamedTextColor.DARK_GRAY))
                .append(Component.text("Чтение обязательно.\nВыхода нет.", NamedTextColor.DARK_RED)
                        .decorate(TextDecoration.ITALIC));
    }

    /** Приложения к оферте — ими книга добивается до 41-й страницы перед финалом. */
    private Component appendixPage(int number) {
        String[] texts = {
            "О КУРИЦЕ\n\nКурица, охраняющая сундук с душами на спавне, не является сотрудником VDS Corp. Она — независимый подрядчик. Переговоры с курицей запрещены.",
            "О ПЕЧЕНЬЕ\n\nВиртуальное печенье из п. 6.2 существует ровно до тех пор, пока вы в него верите. После этого оно становится активом OZO™.",
            "О САЖЕНЦАХ\n\nСаженец, посаженный при мысли о крипере, считается отчётностью. Три саженца подряд — уже бухгалтерия VDS Corp.",
            "О ТИШИНЕ\n\nТри секунды тишины из п. 4.2 должны быть качественными. Тишина с хрустом чипсов к зачёту Советом директоров OZO™ не принимается.",
            "О КОТЕ\n\nКот, решающий споры гляделками, всегда прав. Если кот моргнул — вы проиграли. Если кот не моргнул — вы проиграли уважительно.",
            "О СУНДУКЕ\n\nСундук на спавне не открывается не потому, что заперт. Он открывается только тем, кто принял оферту всей душой. Душа уже внутри. Замкнутый круг.",
        };
        String text = texts[(number - 1) % texts.length];
        return Component.empty()
                .append(Component.text("ПРИЛОЖЕНИЕ №" + number + "\n\n", NamedTextColor.DARK_PURPLE)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text(text, NamedTextColor.BLACK));
    }

    /**
     * Страница 42 — финальная. Жизненные цитаты Стэтхэма
     * и, прямо под ними, кнопка подтверждения оферты.
     */
    private Component stathamPage(boolean alreadyAccepted, String acceptedAt) {
        Component quotes = Component.empty()
                .append(Component.text("42. ЦИТАТЫ СТЭТХЭМА\n\n", NamedTextColor.DARK_PURPLE)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text("«Сниму квартиру. Порядок на районе гарантирую».", NamedTextColor.BLACK))
                .append(Component.text("\n\n«Взял нож — режь, взял дошик — ешь».", NamedTextColor.BLACK))
                .append(Component.text("\n\n«В жизни всегда есть две дороги: одна — первая, а другая — вторая».", NamedTextColor.BLACK))
                .append(Component.text("\n\n«Я запрещаю вам срать».", NamedTextColor.BLACK));

        if (alreadyAccepted) {
            String when = (acceptedAt == null || acceptedAt.isBlank()) ? "" : "\n" + acceptedAt.replace("T", " ").replaceAll("\\..*$", "") + " UTC";
            return Component.empty()
                    .append(quotes)
                    .append(Component.text("\n\n✔ Оферта принята." + when, NamedTextColor.DARK_GREEN)
                            .decorate(TextDecoration.BOLD));
        }

        Component button = Component.text("\n[ ✔ ПРИНЯТЬ ОФЕРТУ ]")
                .color(NamedTextColor.DARK_GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/oferta accept"))
                .hoverEvent(HoverEvent.showText(Component.text("Стэтхэм одобряет. И OZO™ тоже.")));

        return Component.empty()
                .append(quotes)
                .append(button)
                .append(Component.text("\nСтэтхэм уже нажал бы.", NamedTextColor.DARK_GRAY)
                        .decorate(TextDecoration.ITALIC));
    }

    /**
     * Режет текст на страницы: не разрывает слова, абзацы разделяет
     * пустой строкой, слишком длинные абзацы режет по словам.
     */
    static List<String> paginate(String text, int pageSize) {
        List<String> pages = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        String normalized = text.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            pages.add("");
            return pages;
        }

        for (String paragraph : normalized.split("\n\\s*\n")) {
            String p = paragraph.replace('\n', ' ').replaceAll("\\s+", " ").trim();
            if (p.isEmpty()) {
                continue;
            }
            // Длинный абзац режем на куски по словам, каждый кусок — как отдельный блок
            for (String chunk : splitByWords(p, pageSize)) {
                if (current.length() == 0) {
                    current.append(chunk);
                } else if (current.length() + 2 + chunk.length() <= pageSize) {
                    current.append("\n\n").append(chunk);
                } else {
                    pages.add(current.toString());
                    current = new StringBuilder(chunk);
                }
            }
        }
        if (current.length() > 0) {
            pages.add(current.toString());
        }
        if (pages.isEmpty()) {
            pages.add("");
        }
        return pages;
    }

    private static List<String> splitByWords(String text, int maxLen) {
        List<String> chunks = new ArrayList<>();
        if (text.length() <= maxLen) {
            chunks.add(text);
            return chunks;
        }
        StringBuilder chunk = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.length() > maxLen) { // патологически длинное слово — режем жёстко
                if (chunk.length() > 0) {
                    chunks.add(chunk.toString());
                    chunk = new StringBuilder();
                }
                for (int i = 0; i < word.length(); i += maxLen) {
                    chunks.add(word.substring(i, Math.min(word.length(), i + maxLen)));
                }
                continue;
            }
            if (chunk.length() == 0) {
                chunk.append(word);
            } else if (chunk.length() + 1 + word.length() <= maxLen) {
                chunk.append(' ').append(word);
            } else {
                chunks.add(chunk.toString());
                chunk = new StringBuilder(word);
            }
        }
        if (chunk.length() > 0) {
            chunks.add(chunk.toString());
        }
        return chunks;
    }
}
