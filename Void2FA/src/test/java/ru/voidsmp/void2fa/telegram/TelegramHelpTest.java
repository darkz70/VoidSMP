package ru.voidsmp.void2fa.telegram;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/** Help must stay discoverable even with an older/custom local config text. */
class TelegramHelpTest {

    @Test void emptyTextBecomesFullCommandList() {
        String help = TelegramHelp.withRequiredCommands("");
        assertTrue(help.startsWith("Команды:"));
        for (String command : new String[]{"/link", "/status", "/unlink", "/lock", "/unlock",
                "/devices", "/untrust", "/kick", "/revoke", "/help"}) {
            assertTrue(help.contains(command), command);
        }
    }

    @Test void oldHelpGetsMissingSecurityCommands() {
        String old = "Команды:\n/link <код> — привязать\n/status — статус\n/help — помощь";
        String help = TelegramHelp.withRequiredCommands(old);
        assertTrue(help.startsWith(old));
        assertTrue(help.contains("/kick"));
        assertTrue(help.contains("/revoke"));
        assertTrue(help.contains("/untrust"));
        assertTrue(help.contains("/lock"));
        assertTrue(help.contains("/unlock"));
        assertTrue(help.contains("/devices"));
    }

    @Test void completeHelpIsNotDuplicated() {
        String full = "Команды:\n/link <код>\n/status\n/unlink\n/lock\n/unlock\n/devices\n/untrust <id|all>\n/kick\n/revoke\n/help";
        assertEquals(full, TelegramHelp.withRequiredCommands(full));
    }

    @Test void nullIsTreatedAsEmpty() {
        assertTrue(TelegramHelp.withRequiredCommands(null).contains("/revoke"));
    }
}
