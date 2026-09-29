package io.github.ling.randombubble.core;

import java.util.Objects;

/** Only style metadata. No message content, peer ID, sender ID or auth material. */
public final class BubbleSpec {
    public final int attrKey;
    public final int attrType;
    public final long attrId;
    public final Integer bubbleId;
    public final Integer subBubbleId;
    public final Integer bubbleDiyTextId;
    public final Integer canConvertToText;

    public BubbleSpec(int attrKey, int attrType, long attrId, Integer bubbleId,
            Integer subBubbleId, Integer bubbleDiyTextId, Integer canConvertToText) {
        if (attrKey < 0 || attrType < 0 || attrId < 0) throw new IllegalArgumentException("Negative attribute metadata");
        checkNonNegative(bubbleId); checkNonNegative(subBubbleId);
        checkNonNegative(bubbleDiyTextId); checkNonNegative(canConvertToText);
        if ((bubbleId == null || bubbleId == 0) && (subBubbleId == null || subBubbleId == 0))
            throw new IllegalArgumentException("Default/empty bubble is not a template");
        this.attrKey = attrKey; this.attrType = attrType; this.attrId = attrId;
        this.bubbleId = bubbleId; this.subBubbleId = subBubbleId;
        this.bubbleDiyTextId = bubbleDiyTextId; this.canConvertToText = canConvertToText;
    }
    private static void checkNonNegative(Integer n) {
        if (n != null && n < 0) throw new IllegalArgumentException("Negative bubble field");
    }
    public String key() {
        return attrKey + ":" + attrType + ":" + attrId + ":" + bubbleId + ":" + subBubbleId
                + ":" + bubbleDiyTextId + ":" + canConvertToText;
    }
    public String label() { return "气泡 " + bubbleId + " / 子气泡 " + subBubbleId; }
    @Override public boolean equals(Object o) { return o instanceof BubbleSpec && key().equals(((BubbleSpec)o).key()); }
    @Override public int hashCode() { return Objects.hash(key()); }
}
