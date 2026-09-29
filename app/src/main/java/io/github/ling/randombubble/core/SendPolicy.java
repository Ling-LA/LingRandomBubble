package io.github.ling.randombubble.core;

import java.util.HashMap;
import java.util.List;

/** Testable decision engine used verbatim by the Android hook; no network/Android dependencies. */
public final class SendPolicy {
    public enum Reason { DISABLED, ORIGINAL_OR_REPEAT, UNKNOWN_SOURCE, UNSUPPORTED, NO_PERMIT, APPLIED, ERROR }
    public static final class Result {
        public final Reason reason;
        public final HashMap<Object,Object> attributes;
        public final String errorType;
        private Result(Reason r,HashMap<Object,Object> a,String e) { reason=r; attributes=a; errorType=e; }
    }
    private final SendPermit permit;
    private final IdentityWeakSet originals;
    private final MsgAttrAdapter adapter;
    private final BubblePicker picker;
    public SendPolicy(SendPermit p,IdentityWeakSet o,MsgAttrAdapter a,BubblePicker b) {
        permit=p; originals=o; adapter=a; picker=b;
    }
    private Result skip(Reason r) { permit.clear(); return new Result(r,null,null); }
    public synchronized Result prepare(Object[] args,StackTraceElement[] stack,long now,boolean ready,
            boolean groups,boolean privateChats,int forwardDepth,boolean fixed,boolean avoidRepeat,List<BubbleSpec> pool) {
        if(!ready || pool==null || pool.isEmpty()) return skip(Reason.DISABLED);
        if(args==null || args.length!=5) return skip(Reason.UNSUPPORTED);
        try {
            boolean reused=originals.contains(args[2]) || originals.contains(args[3]);
            if(args[2] instanceof List) for(Object e:(List<?>)args[2]) reused|=originals.contains(e);
            if(forwardDepth>0 || reused || OriginGuard.deniedStack(stack)) return skip(Reason.ORIGINAL_OR_REPEAT);
            // QQ NT posts sendMsg off the input thread, so the composer frame is usually absent.
            // The one-time Send-button permit is the authorization; denied stacks still block repeats.
            if(args[1]==null) return skip(Reason.UNSUPPORTED);
            int type=Reflect.intValue(Reflect.get(args[1],"chatType"));
            if(!((type==1 && privateChats)||(type==2 && groups))) return skip(Reason.UNSUPPORTED);
            String text=MsgAttrAdapter.plainText(args[2]);
            if(text==null) return skip(Reason.UNSUPPORTED);
            if(!permit.consume(text,now)) return skip(Reason.NO_PERMIT);
            BubbleSpec b=picker.choose(pool,fixed,avoidRepeat);
            if(b==null) return skip(Reason.DISABLED);
            HashMap<Object,Object> map=adapter.withBubble(args[3],b);
            picker.commit(b);
            return new Result(Reason.APPLIED,map,null);
        } catch(Throwable e) {
            permit.clear(); return new Result(Reason.ERROR,null,e.getClass().getSimpleName());
        }
    }
}
