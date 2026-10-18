package me.darkz70.quirks.command;

import java.util.List;
import me.darkz70.quirks.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Пять команд-отвлекалочек: при вводе пишут всякую безобидную ерунду
 * (никакой важной информации!). Существуют, чтобы отводить взгляд.
 */
public final class DecoyCommand implements CommandExecutor {

    private static final String[][] LINES = {
        {"&7Служба голосов душ отключена по техпричинам.", "&8Попробуйте хорошую погоду."},
        {"&8Резонанс пустоты: &fноминал&8. Проверка: OK.", "&7Всё по расписанию, всё по-старому."},
        {"&eСклейка эфира: &fуспешно обнаружена и сразу же забыта.", "&7Продолжайте прежнюю дорогу."},
        {"&dУже четвертое утро без специальных сообщений.", "&fОтличные новости!"},
        {"&7Шум из подвала признан стандартным.", "&8Никто ни о чём не говорит."},
    };

    private final int which;

    public DecoyCommand(int which) {
        this.which = Math.max(0, Math.min(LINES.length - 1, which));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        for (String line : LINES[which]) {
            sender.sendMessage(Msg.color(line));
        }
        return true;
    }
}
