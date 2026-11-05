package ru.voidsmp.void2fa.bridge;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared protocol primitives; deliberately independent of Bukkit and Telegram. */
public final class BridgeSecurity {
    public static final long SKEW_SECONDS = 30;
    private final byte[] secret;
    private final Map<String, Long> nonces = new LinkedHashMap<>();

    public BridgeSecurity(byte[] secret) {
        if (secret == null || secret.length != 32) throw new IllegalArgumentException("Bridge secret must contain exactly 32 bytes");
        this.secret = secret.clone();
    }

    public static byte[] decodeSecret(String value) {
        String s = value == null ? "" : value.trim();
        byte[] bytes;
        if (s.matches("[0-9a-fA-F]{64}")) bytes = HexFormat.of().parseHex(s);
        else {
            try { bytes = Base64.getDecoder().decode(s); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid bridge secret encoding"); }
        }
        if (bytes.length != 32) throw new IllegalArgumentException("Bridge secret must contain exactly 32 bytes");
        return bytes;
    }

    public String sign(String direction, String path, long timestamp, String nonce, byte[] body) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(body);
            String canonical = "V2FA1\n" + direction + "\n" + path + "\n" + timestamp + "\n"
                    + nonce + "\n" + HexFormat.of().formatHex(hash);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("HMAC unavailable", e); }
    }

    public boolean matches(String expected, String supplied) {
        if (supplied == null || !supplied.matches("[0-9a-f]{64}")) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII));
    }

    public static boolean fresh(long timestamp, long now) {
        return timestamp >= now - SKEW_SECONDS && timestamp <= now + SKEW_SECONDS;
    }

    /** Call only after authenticating the signature. Bound memory even under authenticated floods. */
    public synchronized boolean acceptNonce(String nonce, long now) {
        Iterator<Map.Entry<String, Long>> it = nonces.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue() <= now) it.remove();
        if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{22,64}") || nonces.size() >= 20000 || nonces.containsKey(nonce)) return false;
        nonces.put(nonce, now + 2 * SKEW_SECONDS + 1);
        return true;
    }
}
