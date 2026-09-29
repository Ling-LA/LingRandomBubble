package io.github.ling.randombubble.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** One physical Send-button tap authorizes at most ONE matching send for 2.5 seconds.
 * Only a session-salted digest is retained; never log/persist it. Clock is supplied
 * by the caller (Android SystemClock.uptimeMillis in production).
 */
public final class SendPermit {
    public static final int MAX_TEXT = 4000;
    public static final long TTL_MS = 2500;
    private final byte[] salt = new byte[32];
    private byte[] digest;
    private long created;
    private long generation;
    public SendPermit() { new SecureRandom().nextBytes(salt); }
    public synchronized void arm(String text, long now) {
        clear();
        if (text == null || text.isEmpty() || text.length() > MAX_TEXT) return;
        digest = hash(text); created = now; generation++;
    }
    public synchronized boolean consume(String text, long now) {
        byte[] expected = digest; long age = now - created;
        clear(); // Even a mismatch consumes it: do NOT authorize an unrelated later send.
        return expected != null && text != null && text.length() <= MAX_TEXT
                && age >= 0 && age <= TTL_MS && MessageDigest.isEqual(expected, hash(text));
    }
    public synchronized void clear() { digest = null; created = 0; }
    public synchronized long generation() { return generation; }
    private byte[] hash(String s) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            d.update(salt);
            return d.digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
