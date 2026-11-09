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

    @Test void authRejectionIsNotTreatedAsAnOutage() {
        // The bot answered 401/403: it is demonstrably up, only the secret is wrong.
        assertTrue(ExternalBotBridge.credentialsRejected(401));
        assertTrue(ExternalBotBridge.credentialsRejected(403));
        // 502/504 from the hosting edge and plain successes are not credential refusals.
        assertFalse(ExternalBotBridge.credentialsRejected(200));
        assertFalse(ExternalBotBridge.credentialsRejected(502));
        assertFalse(ExternalBotBridge.credentialsRejected(504));
    }

    @Test void refusalIsItsOwnExceptionSoReadinessSurvives() {
        ExternalBotBridge.RejectedException e =
                new ExternalBotBridge.RejectedException("bad-signature", "Unauthenticated bridge response");
        assertEquals("bad-signature", e.kind());
        // It must remain an IOException so existing call sites still compile and behave.
        assertInstanceOf(java.io.IOException.class, e);
    }

    @Test void clockSkewHintBlamesTheClockNotTheSecret() {
        String hint = ExternalBotBridge.hint("clock-skew");
        assertTrue(hint.contains("время"), hint);
        assertTrue(hint.contains("NTP"), hint);
        // A valid signature proves the secret is right; do not send the operator to replace it.
        assertFalse(hint.contains("bridge-secret"), hint);
    }

    @Test void wrongSecretHintPointsAtBothSidesOfTheSecret() {
        for (String kind : new String[]{"http-401", "http-403", "bad-signature"}) {
            String hint = ExternalBotBridge.hint(kind);
            assertTrue(hint.contains("BRIDGE_SHARED_SECRET"), hint);
            assertTrue(hint.contains("секрет") || hint.contains("Секрет"), hint);
        }
    }

    @Test void usernameAndProtocolMismatchesGetActionableHints() {
        assertTrue(ExternalBotBridge.hint("username").contains("bot-username"));
        assertTrue(ExternalBotBridge.hint("protocol").contains("версии")
                || ExternalBotBridge.hint("protocol").contains("Версии"));
    }
}
