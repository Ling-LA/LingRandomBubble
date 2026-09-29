package io.github.ling.randombubble.core;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** One visual style per send. Same bubble id counts as a repeat even if the saved metadata differs. */
public final class BubblePicker {
    private final Random random;
    private String lastVisual;
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
}
