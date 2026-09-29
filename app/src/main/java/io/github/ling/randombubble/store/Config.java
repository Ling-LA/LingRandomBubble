package io.github.ling.randombubble.store;

import io.github.ling.randombubble.core.BubbleSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable config snapshot, swapped atomically by the host bridge. */
public final class Config {
    public final boolean enabled, collect, fixed, avoidRepeat, groups, privateChats;
    public final List<BubbleSpec> selected;
    public Config(boolean enabled, boolean collect, boolean fixed, boolean avoidRepeat,
            boolean groups, boolean privateChats, List<BubbleSpec> selected) {
        this.enabled = enabled; this.collect = collect; this.fixed = fixed;
        this.avoidRepeat = avoidRepeat; this.groups = groups; this.privateChats = privateChats;
        this.selected = Collections.unmodifiableList(new ArrayList<>(selected));
    }
    public static Config off() { return new Config(false, false, false, true, true, true, Collections.emptyList()); }
}
