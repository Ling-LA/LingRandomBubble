package io.github.ling.randombubble.core;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** One visual style per send. Same bubble id counts as a repeat even if the saved metadata differs. */
public final class BubblePicker {
    public static final long HOLD_MS=400;
    private final Random random;
    private String lastVisual;
    private BubbleSpec held;
    private long heldAt=Long.MIN_VALUE;
    public BubblePicker() { this(new SecureRandom()); }
    public BubblePicker(Random random) { this.random = random; }
    public static String visual(BubbleSpec b) { return b.bubbleId + ":" + b.subBubbleId; }
    public synchronized BubbleSpec choose(List<BubbleSpec> list, boolean fixed, boolean avoidRepeat) {
        if (list == null || list.isEmpty()) return null;
        if (fixed) return list.get(0);
        boolean another = false;
        for (BubbleSpec b : list) if (b != null && !visual(b).equals(lastVisual)) { another = true; break; }
        Map<String,BubbleSpec> unique = new LinkedHashMap<>();
        for (BubbleSpec b : list) {
            if (b == null) continue;
            String look = visual(b);
            if (avoidRepeat && another && look.equals(lastVisual)) continue;
            unique.putIfAbsent(look, b);
        }
        if (unique.isEmpty()) return list.get(0);
        List<BubbleSpec> options = new ArrayList<>(unique.values());
        return options.get(random.nextInt(options.size()));
    }
    public synchronized void commit(BubbleSpec b) { if (b != null) lastVisual = visual(b); }
    /** Getter and sendMsg within one message must share one style. */
    public synchronized BubbleSpec held(long now, long windowMs) {
        if (held != null && now >= heldAt && now - heldAt <= windowMs) return held;
        return null;
    }
    public synchronized void hold(BubbleSpec b, long now) {
        if (b == null) return;
        held = b;
        heldAt = now;
        lastVisual = visual(b);
    }
}
