package io.github.ling.randombubble.core;

/** Explicit flags used by QQ mall responses. Unknown/missing values fail closed. */
public final class ServerFlags {
    private ServerFlags() {}
    public static boolean value(Object input) {
        if(input instanceof Boolean)return (Boolean)input;
        if(input instanceof Number){double n=((Number)input).doubleValue();if(n==0)return false;if(n==1)return true;}
        if(input instanceof String){if(input.equals("0") || input.equals("false"))return false;if(input.equals("1") || input.equals("true"))return true;}
        throw new IllegalArgumentException("unknown server flag");
    }
}
