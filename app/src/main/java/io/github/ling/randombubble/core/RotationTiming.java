package io.github.ling.randombubble.core;

/** Monotonic timers while running; bounded wall-clock restoration across restarts. */
public final class RotationTiming {
    private RotationTiming() {}
    public static long remaining(long now,long last,long interval) {
        if(last<=0 || now<last) return 0; // A backward clock must never freeze rotation.
        return Math.max(0,interval-Math.min(interval,now-last));
    }
    public static long retryDelay(int failures,long interval) {
        return Math.max(interval,Math.min(300000L,5000L << Math.min(6,Math.max(0,failures-1))));
    }
}
