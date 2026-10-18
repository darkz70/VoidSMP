package com.voidsmp.voidoferta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * VoidOferta — официальная публичная оферта VDS Corp № VDS-VOID-01 для VOID SMP.
 *
 * <ul>
 *   <li>При входе игрок, ещё не принявший оферту, автоматически получает книгу с её текстом.</li>
 *   <li>Подтверждение — кнопкой на последней странице книги или командой /oferta accept. Нужно 1 раз.</li>
 *   <li>/oferta в любой момент открывает книгу повторно (уже без обязательного подтверждения).</li>
 *   <li>Текст оферты редактируется без перекомпиляции: plugins/VoidOferta/oferta.txt + /oferta reload.</li>
 * </ul>
 */
public final class VoidOfertaPlugin extends JavaPlugin {

    private AcceptedStorage storage;
    private OfertaBook book;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveOfertaTextIfAbsent();

        storage = new AcceptedStorage(new File(getDataFolder(), "accepted-players.yml"), getLogger());
        book = new OfertaBook(this);
        reloadOfertaText();

        PluginCommand command = getCommand("oferta");
        if (command != null) {
            OfertaCommand executor = new OfertaCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Команда /oferta не найдена в plugin.yml!");
        }

        getServer().getPluginManager().registerEvents(new OfertaListener(this), this);
        getLogger().info("VoidOferta включена. Оферту VDS-VOID-01 уже приняло игроков: " + storage.size());
    }

    /** Открывает книгу оферты игроку (с учётом того, принял ли он её уже). */
    public void openOferta(Player player) {
        boolean accepted = storage.hasAccepted(player.getUniqueId());
        player.openBook(book.createBook(accepted, accepted ? storage.acceptedAt(player.getUniqueId()) : null));
    }

    /** Напоминание для тех, кто ещё не подтвердил оферту (кликабельное). */
    public void sendNotAcceptedMessage(Player player) {
        Component openBook = Component.text("[ Открыть оферту ]")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/oferta"));
        player.sendMessage(Component.empty()
                .append(Component.text("⚠ ", NamedTextColor.RED))
                .append(Component.text("Для игры на VOID SMP нужно прочитать и принять публичную оферту VDS Corp № VDS-VOID-01. ", NamedTextColor.YELLOW))
                .append(openBook)
                .append(Component.text(" — подтверждение требуется 1 раз, на последней странице книги.", NamedTextColor.YELLOW)));
    }

    /** Перечитывает config.yml, oferta.txt и список принявших (для /oferta reload). */
    public void reloadOferta() {
        reloadConfig();
        reloadOfertaText();
        storage.load();
    }

    public AcceptedStorage storage() {
        return storage;
    }

    private void reloadOfertaText() {
        File file = new File(getDataFolder(), "oferta.txt");
        try {
            book.setRawText(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            getLogger().warning("Не удалось прочитать oferta.txt, использую текст из jar: " + e.getMessage());
            book.setRawText(readBundledText());
        }
    }

    private void saveOfertaTextIfAbsent() {
        File file = new File(getDataFolder(), "oferta.txt");
        if (file.exists()) {
            return;
        }
        // saveResource не перезаписывает существующий файл и создаёт папку плагина
        saveResource("oferta.txt", false);
    }

    private String readBundledText() {
        try (InputStream in = getResource("oferta.txt")) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().severe("Не удалось прочитать oferta.txt ни из папки плагина, ни из jar!");
            return "";
        }
    }
}
