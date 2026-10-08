package me.darkz70.quirks.command;

import java.util.List;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** /gaid — выдать себе книгу-гайд по VoidSMP (механики плагина). Только для админов. */
public final class GaidCommand implements TabExecutor {

    private final VoidQuirksPlugin plugin;

    public GaidCommand(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("quirks.admin")) {
            Msg.send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "gaid-player-only");
            return true;
        }
        player.openBook(buildBook());
        Msg.send(player, "gaid-given");
        return true;
    }

    private ItemStack buildBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.title(Component.text("Гайд VoidSMP"));
        meta.author(Component.text("Архивариус Расколотого мира"));
        meta.pages(pages());
        book.setItemMeta(meta);
        return book;
    }

    private List<Component> pages() {
        return List.of(
            page("§l§5VoidSMP: Причуды§r\n\n"
                + "Души мира расколоты. Осколки душ дают причуды: Инженер, Кот, Бедрок, Топор, Скалк, Фермер, Земноводный, Паук.\n\n"
                + "У игрока может быть НЕСКОЛЬКО причуд (ур. 1–3). О причуде никто не должен говорить вслух — только «душа отозвалась»."),
            page("§l§5Бинды причуд§r\n\n"
                + "Топор 2+: §lShift+ЛКМ§r топором — ярость.\n"
                + "Топор 3: §lShift+ПКМ§r — разрыв пространства.\n"
                + "Паук 3: §lShift+ПКМ§r мечом — выстрел паутиной (9 сетей).\n"
                + "Удобрение: ПКМ по грядке = 6 костной муки."),
            page("§l§5Магия§r\n\n"
                + "Изучи стихию книгой (крафт: книга + 4 аметиста + катализатор). Стихии: огонь, вода, ветер, земля.\n"
                + "Свет и тьма требуют 4 книги других стихий в инвентаре.\n"
                + "Стихия одна. Смена — зелье переквалификации."),
            page("§l§5Фокус и каст§r\n\n"
                + "Кристалл фокуса: 4 аметиста + 4 алмаза + стекло.\n"
                + "Кристалл + любой не-блок в верстаке = фокус-предмет.\n\n"
                + "§lПКМ§r — каст выбранного.\n"
                + "§lQ/F§r — следующее заклинание.\n"
                + "§lShift+F§r — меню заклинаний."),
            page("§l§5Мана и уровни§r\n\n"
                + "Мана = 20 + уровень ×10. Реген: 5 маны/2 сек (с 20 ур. — каждую секунду).\n\n"
                + "Книги прокачки (+1 ур., ПКМ): медная (1+), железная (10+), золотая (20+), обсидиановая (30+), алмазная (40+).\n"
                + "Заклинания открываются на 1/10/10/20/20/30/41 ур."),
            page("§l§5Зелья: основа§r\n\n"
                + "Все рецепты зелий скрыты из книги — узнавай в игре.\n\n"
                + "Нейтралка (база всего) варится верстаком из двух противоположностей: вреда и исцеления.\n"
                + "Грибная настойка и зелье знаний — через зельеварочную стойку (вода + гриб / книга)."),
            page("§l§5Дерево зелий§r\n\n"
                + "Из нейтралки растёт всё: заражение, самогон (до 16 ур.), отрицание (5шт), подтверждение (6шт), отключение причуд (нейтралка + символ причуды), жизнь, ускорение, «никакого вреда», «никакого восстановления», полёт…"),
            page("§l§5Запретные зелья§r\n\n"
                + "Зелье причуды = отключение + подтверждение: 10 сек всех 8 причуд 3 ур.\n"
                + "Больше 2 за полчаса — минус здоровье. Шестая — смерть и навсегда минус сердце. Вторая такая смерть — бан.\n\n"
                + "Зелье божества: 2 мин всевластия, потом −2 макс.HP на 7 дней."),
            page("§l§5Возврат§r\n\n"
                + "Зелье возврата (нейтралка + 8 зелий божества) снимает ВСЮ магию, причуды, эффекты и наши модификаторы здоровья/опыта.\n\n"
                + "Зелье уменьшения магий бьёт −1 уровень магии всем в 5 блоках."),
            page("§l§5Лабиринт душ§r\n\n"
                + "В мире построен лабиринт (~286 клеток). В клетках томятся души.\n"
                + "Освободи душу — получи осколок. Осколки дают причуды.\n\n"
                + "Телепорт: /quirk lab tp"),
            page("§l§5Команды§r\n\n"
                + "/quirk info — свои причуды\n"
                + "/quirk notify — тумблер оповещений\n"
                + "/magic — меню заклинаний\n\n"
                + "Админ: /quirk set|remove|list|reload, /quirk shard, /magic book|upbook|set|brew, /gaid"),
            page("§l§5Философия сервера§r\n\n"
                + "VoidSMP — сервер историй. Каждая механика — повод для контента: войны, конфликты, проекты.\n\n"
                + "Прячь свои причуды. Магия видна всем — зелья никому не видны. Торгуй тайнами.")
        );
    }

    private static Component page(String legacy) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(legacy.replace('§', '&'));
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        return List.of();
    }
}
