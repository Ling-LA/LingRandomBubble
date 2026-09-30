package io.github.ling.randombubble.core;

import java.util.Locale;

/** Deny known repeat/forward paths. Positive authorization is still mandatory. */
public final class OriginGuard {
    private OriginGuard() {}
    /** True when this thread still contains the input UI. NT usually sends later, on another thread. */
    public static boolean composerStack(StackTraceElement[] stack) {
        if (stack == null) return false;
        for (StackTraceElement f : stack) {
            String c=f.getClassName();
            if (c.startsWith("com.tencent.mobileqq.aio.input.") || c.startsWith("com.tencent.input.")) return true;
        }
        return false;
    }
    public static boolean deniedStack(StackTraceElement[] stack) { return deniedReason(stack) != null; }
    /** Null when this stack may be a normal send. QFun's own send hook is not a repeat. */
    public static String deniedReason(StackTraceElement[] stack) {
        if (stack == null) return "nostack";
        for (StackTraceElement f : stack) {
            String c = f.getClassName().toLowerCase(Locale.ROOT);
            String m = f.getMethodName().toLowerCase(Locale.ROOT);
            String kind = null;
            if (c.contains("repeatmsg") || c.contains("repeater") || m.contains("repeat") || m.equals("directsend")) kind = "repeat";
            else if (c.contains("multiforward") || c.contains("forwardactivity") || m.equals("forwardmsg")) kind = "forward";
            else if (c.contains("msgfollow") || c.contains("plusone") || m.contains("plusone")) kind = "follow";
            if (kind == null) continue;
            String simple = f.getClassName();
            int dot = simple.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < simple.length()) simple = simple.substring(dot + 1);
            if (simple.length() > 40) simple = simple.substring(0, 40);
            return kind + " " + simple + "." + f.getMethodName();
        }
        return null;
    }
}
