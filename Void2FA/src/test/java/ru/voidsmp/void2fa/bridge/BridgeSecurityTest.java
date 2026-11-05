package ru.voidsmp.void2fa.bridge;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class BridgeSecurityTest {
    private final byte[] secret = HexFormat.of().parseHex("11".repeat(32));
    private final BridgeSecurity security = new BridgeSecurity(secret);

    @Test void secretLengthAndEncodingAreValidated() {
        assertArrayEquals(secret, BridgeSecurity.decodeSecret("11".repeat(32)));
        assertThrows(IllegalArgumentException.class, () -> BridgeSecurity.decodeSecret("11".repeat(31)));
        assertThrows(IllegalArgumentException.class, () -> BridgeSecurity.decodeSecret("not a secret!"));
        assertThrows(IllegalArgumentException.class, () -> new BridgeSecurity(new byte[16]));
    }

    @Test void signatureBindsAllWireFields() {
        byte[] body = "{\"cursor\":1}".getBytes(StandardCharsets.UTF_8);
        String sig = security.sign("request", "/v1/poll", 1000, "n".repeat(32), body);
        assertEquals(sig, security.sign("request", "/v1/poll", 1000, "n".repeat(32), body));
        assertNotEquals(sig, security.sign("response", "/v1/poll", 1000, "n".repeat(32), body));
        assertNotEquals(sig, security.sign("request", "/v1/command", 1000, "n".repeat(32), body));
        assertNotEquals(sig, security.sign("request", "/v1/poll", 1001, "n".repeat(32), body));
        assertNotEquals(sig, security.sign("request", "/v1/poll", 1000, "x".repeat(32), body));
        assertNotEquals(sig, security.sign("request", "/v1/poll", 1000, "n".repeat(32), "{}".getBytes()));
        assertTrue(security.matches(sig, sig));
        assertFalse(security.matches(sig, null));
        assertFalse(security.matches(sig, sig.toUpperCase()));
    }

    @Test void timestampAndNonceReplayWindow() {
        assertFalse(BridgeSecurity.fresh(969, 1000));
        assertTrue(BridgeSecurity.fresh(970, 1000));
        assertTrue(BridgeSecurity.fresh(1030, 1000));
        assertFalse(BridgeSecurity.fresh(1031, 1000));
        String nonce = "abcdefghijklmnopqrstuv";
        assertTrue(security.acceptNonce(nonce, 1000));
        assertFalse(security.acceptNonce(nonce, 1000));
        assertFalse(security.acceptNonce(nonce, 1060)); // still inside the 61-second replay window
        assertTrue(security.acceptNonce(nonce, 1061)); // expired entry was pruned
        assertFalse(security.acceptNonce(null, 1000));
    }
}
