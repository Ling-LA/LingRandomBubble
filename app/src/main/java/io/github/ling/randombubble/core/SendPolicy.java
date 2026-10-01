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
        public final BubbleSpec used;
        private Result(Reason r,HashMap<Object,Object> a,String e,BubbleSpec used) { reason=r; attributes=a; errorType=e; this.used=used; }
    }
    private final SendPermit permit;
    private final IdentityWeakSet originals;
    private final MsgAttrAdapter adapter;
    private final BubblePicker picker;
    public SendPolicy(SendPermit p,IdentityWeakSet o,MsgAttrAdapter a,BubblePicker b) {
        permit=p; originals=o; adapter=a; picker=b;
    }
    private Result skip(Reason r) { return skip(r,null); }
    private Result skip(Reason r,String detail) { permit.clear(); return new Result(r,null,detail,null); }
    public synchronized Result prepare(Object[] args,StackTraceElement[] stack,long now,boolean ready,
            boolean groups,boolean privateChats,int forwardDepth,boolean fixed,boolean avoidRepeat,List<BubbleSpec> pool) {
        if(!ready || pool==null || pool.isEmpty()) return skip(Reason.DISABLED);
        if(args==null || args.length!=5) return skip(Reason.UNSUPPORTED);
        try {
            boolean reused=originals.contains(args[2]) || originals.contains(args[3]);
            if(args[2] instanceof List) for(Object e:(List<?>)args[2]) reused|=originals.contains(e);
            if(args[3] instanceof java.util.Map) for(Object a:((java.util.Map<?,?>)args[3]).values()) reused|=originals.contains(a);
            if(forwardDepth>0) return skip(Reason.ORIGINAL_OR_REPEAT,"forward");
            String deny=OriginGuard.deniedReason(stack);
            if(deny!=null) return skip(Reason.ORIGINAL_OR_REPEAT,"stack "+deny);
            if(reused) return skip(Reason.ORIGINAL_OR_REPEAT,"elements");
            // QQ NT posts sendMsg off the input thread, so the composer frame is usually absent.
            // The one-time Send-button permit is the authorization; denied stacks still block repeats.
            if(args[1]==null) return skip(Reason.UNSUPPORTED);
            int type;
            try { type=Reflect.intValue(Reflect.get(args[1],"chatType")); }
            catch(ReflectiveOperationException | IllegalArgumentException e) { return skip(Reason.UNSUPPORTED); }
            if(type==1 && !privateChats) return skip(Reason.UNSUPPORTED);
            if(type==2 && !groups) return skip(Reason.UNSUPPORTED);
            if(type!=1 && type!=2) return skip(Reason.UNSUPPORTED);
            String text=MsgAttrAdapter.plainText(args[2]);
            if(text==null) return skip(Reason.UNSUPPORTED);
            // Async NT sends may lose the composer stack; only an exact one-use tap
            // permit authorizes them. A missing deny-list match is not authorization.
            if(!permit.consume(text,now)) return skip(Reason.NO_PERMIT);
            BubbleSpec b=picker.choose(pool,fixed,avoidRepeat);
            if(b==null) return skip(Reason.DISABLED);
            long messageId=args[0] instanceof Number ? ((Number)args[0]).longValue() : -1L;
            HashMap<Object,Object> map=adapter.withBubble(args[3],b,messageId);
            picker.commit(b);
            return new Result(Reason.APPLIED,map,null,b);
        } catch(Throwable e) {
            permit.clear(); return new Result(Reason.ERROR,null,e.getClass().getSimpleName(),null);
        }
    }
}
