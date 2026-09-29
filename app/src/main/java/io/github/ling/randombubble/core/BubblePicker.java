package io.github.ling.randombubble.core;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Session-local no-consecutive-repeat picker. Only commit after successful argument replacement. */
public final class BubblePicker {
    private final Random random;
    private String last;
    public BubblePicker() { this(new SecureRandom()); }
    public BubblePicker(Random random) { this.random = random; }
    public synchronized BubbleSpec choose(List<BubbleSpec> list, boolean fixed, boolean avoidRepeat) {
        if (list == null || list.isEmpty()) return null;
        if (fixed) return list.get(0);
        List<BubbleSpec> options = new ArrayList<>();
        for (BubbleSpec b : list) if (b != null && (!avoidRepeat || list.size() == 1 || !b.key().equals(last))) options.add(b);
        if (options.isEmpty()) return list.get(0);
        return options.get(random.nextInt(options.size()));
    }
    public synchronized void commit(BubbleSpec b) { last = b.key(); }
}
