package ru.voidsmp.void2fa.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A dead bot endpoint retries every few seconds. The console must stay readable and
 * the message must point at the real cause instead of blaming the shared secret.
 */
class BridgeDiagnosticsTest {

    @Test void firstFailureIsLoggedAndRepeatsAreThrottled() {
        long now = 1_000_000L;
        assertTrue(ExternalBotBridge.shouldWarn("HttpTimeoutException", now, 0, ""));
        // Same failure a second later: suppressed.
        assertFalse(ExternalBotBridge.shouldWarn("HttpTimeoutException", now + 1000, now, "HttpTimeoutException"));
        assertFalse(ExternalBotBridge.shouldWarn("HttpTimeoutException", now + 299_000, now, "HttpTimeoutException"));
        // After five minutes the operator gets one fresh line.
        assertTrue(ExternalBotBridge.shouldWarn("HttpTimeoutException", now + 300_000, now, "HttpTimeoutException"));
    }

    @Test void differentFailureKindIsNeverHidden() {
        long now = 1_000_000L;
        assertTrue(ExternalBotBridge.shouldWarn("ConnectException", now, now, "HttpTimeoutException"));
        assertTrue(ExternalBotBridge.shouldWarn("IOException", now, now, "ConnectException"));
    }

    @Test void timeoutHintSendsOperatorToHealthAndNotToTheSecret() {
        for (String kind : new String[]{"HttpTimeoutException", "HttpConnectTimeoutException"}) {
            String hint = ExternalBotBridge.hint(kind);
            assertTrue(hint.contains("/health"), hint);
            assertTrue(hint.contains("telegram_ready"), hint);
            // A wrong secret produces HTTP 401, not a timeout: do not send the user in circles.
            assertFalse(hint.contains("общий секрет"), hint);
        }
    }

    @Test void otherFailuresGetTheirOwnHint() {
        assertTrue(ExternalBotBridge.hint("UnknownHostException").contains("bridge.url"));
        assertTrue(ExternalBotBridge.hint("ConnectException").contains("Bothost"));
        assertTrue(ExternalBotBridge.hint("SSLHandshakeException").contains("время"));
        assertTrue(ExternalBotBridge.hint("IOException").contains("401"));
    }
}
