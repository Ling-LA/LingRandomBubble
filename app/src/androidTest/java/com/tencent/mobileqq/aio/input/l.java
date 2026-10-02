package com.tencent.mobileqq.aio.input;

/** Test APK only: QQ's confirmed immutable ReplyData shape, with synthetic values. */
public final class l {
    private final String a,b;
    private final long c,d;
    public l(String nickname,String quoted,long sequence,long messageId) {a=nickname;b=quoted;c=sequence;d=messageId;}
    public String c() {return a;}
    public String d() {return b;}
    public long b() {return c;}
    public long a() {return d;}
    @Override public String toString() {throw new AssertionError("Reply classification must not stringify reply data");}
}
