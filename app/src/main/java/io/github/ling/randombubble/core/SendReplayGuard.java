package io.github.ling.randombubble.core;

/** One deferred physical send gesture, bound to a monotonic edit revision and deadline. */
public final class SendReplayGuard {
    private final long revision;
    private final long createdAt;
    private final long deadline;
    private boolean consumed;
    public SendReplayGuard(long revision,long now,long timeout) {
        this.revision=revision;
        this.createdAt=now;
        long expires;
        try {expires=Math.addExact(now,Math.max(0L,timeout));}
        catch(ArithmeticException overflow) {expires=Long.MAX_VALUE;}
        this.deadline=expires;
    }
    /** Every attempt consumes the gesture, including rejection; it cannot become valid again. */
    public synchronized boolean consume(long currentRevision,long now,boolean contextValid) {
        if(consumed)return false;
        consumed=true;
        return contextValid && currentRevision==revision && now>=createdAt && now<deadline;
    }
}
