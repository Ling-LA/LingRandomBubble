package io.github.ling.randombubble.core;

import java.util.Locale;

/** Deny known repeat/forward paths. Positive authorization is still mandatory. */
public final class OriginGuard {
    private OriginGuard() {}
    /** Strict allowlist: absence of a recognizable QQ input frame is a safe skip.
     * The target's async dispatch may lose this frame; do not broaden blindly.
     */
    public static boolean composerStack(StackTraceElement[] stack) {
        if (stack == null) return false;
        for (StackTraceElement f : stack) {
            String c=f.getClassName();
            if (c.startsWith("com.tencent.mobileqq.aio.input.") || c.startsWith("com.tencent.input.")) return true;
        }
        return false;
    }
    public static boolean deniedStack(StackTraceElement[] stack) {
        if (stack == null) return true;
        for (StackTraceElement f : stack) {
            String c = f.getClassName().toLowerCase(Locale.ROOT);
            String m = f.getMethodName().toLowerCase(Locale.ROOT);
            if (c.startsWith("me.yxp.qfun.") || c.startsWith("io.github.qauxv.")
                    || c.contains("repeatmsg") || c.contains("repeater")
                    || c.contains("multiforward") || c.contains("forwardactivity")
                    || c.contains("msgfollow") || m.equals("forwardmsg")) return true;
        }
        return false;
    }
}
