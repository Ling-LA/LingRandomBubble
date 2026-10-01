import io.github.ling.randombubble.core.*;
import com.tencent.qqnt.kernel.nativeinterface.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real tests of production pure-Java policy; fixtures only stand in for QQ model classes. */
public final class CoreSelfTest {
    static int passed=0;
    interface Checked { void run() throws Exception; }
    static void test(String name,Checked run) throws Exception { run.run(); passed++; System.out.println("PASS "+name); }
    static void ok(boolean b) { if(!b) throw new AssertionError(); }
    static void eq(Object a,Object b) { if(!Objects.equals(a,b)) throw new AssertionError(a+" != "+b); }
    static final BubbleSpec A=new BubbleSpec(17,17,0L,101,201,null,0);
    static final BubbleSpec B=new BubbleSpec(17,17,0L,102,202,0,1);
    static final StackTraceElement[] NORMAL={new StackTraceElement("com.tencent.mobileqq.aio.input.Send", "send","Send.java",1)};
    static StackTraceElement[] frame(String c,String m) { return new StackTraceElement[]{new StackTraceElement(c,m,"test",1)}; }
    public static final class Contact { public int chatType=2; }
    public static final class Text { public int atType; public String content; Text(String s){content=s;} }
    public static final class Element { public int elementType=1; public Text textElement; Element(String s){textElement=new Text(s);} }
    public static final class BadModel { public final int immutable=1; }
    static Object[] args(String text,Map<?,?> attrs) {
        return new Object[]{1L,new Contact(),new ArrayList<>(Arrays.asList(new Element(text))),attrs,null};
    }
    static final class Env {
        SendPermit permit=new SendPermit(); IdentityWeakSet originals=new IdentityWeakSet();
        MsgAttrAdapter adapter=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());
        SendPolicy policy=new SendPolicy(permit,originals,adapter,new BubblePicker(new Random(1)));
        SendPolicy.Result run(Object[] args,StackTraceElement[] stack,int depth) {
            return policy.prepare(args,stack,1001,true,true,true,depth,false,true,Arrays.asList(A,B));
        }
    }
    static MsgAttributeInfo attribute() {
        MsgAttributeInfo a=new MsgAttributeInfo();a.attrType=17;a.attrId=99L;a.groupHonor=new Object();a.unrelatedAttribute=new Object();
        a.vasMsgInfo=new VASMsgElement();a.vasMsgInfo.vasFont=new Object();a.vasMsgInfo.avatarPendantInfo=new Object();
        a.vasMsgInfo.bubbleInfo=new VASMsgBubble();a.vasMsgInfo.bubbleInfo.bubbleId=7;
        a.vasMsgInfo.bubbleInfo.subBubbleId=8;a.vasMsgInfo.bubbleInfo.canConvertToText=0;
        a.vasMsgInfo.bubbleInfo.futureUnrelatedField=new Object(); return a;
    }
    static Map<Integer,MsgAttributeInfo> map(MsgAttributeInfo a) { Map<Integer,MsgAttributeInfo> m=new HashMap<>();m.put(17,a);return m; }
    public static void main(String[] argv) throws Exception {
        test("decoration intervals include both preset boundaries",()->{DecorationSettings.interval(60);DecorationSettings.interval(1800);DecorationSettings.interval(86400);});
        test("decoration intervals reject sub-minute and above-day values",()->{for(int seconds:new int[]{-1,0,59,86401,Integer.MAX_VALUE}){try{DecorationSettings.interval(seconds);throw new AssertionError();}catch(IllegalArgumentException expected){}}});
        test("decoration IDs deduplicate before rotation validation",()->{int[] ids=DecorationSettings.ids("101,101，102 102");eq(ids.length,2);eq(ids[0],101);eq(ids[1],102);DecorationSettings.rotationPool(ids.length,false,true);});
        test("manual decoration may use one style and stopped defaults may be empty",()->{DecorationSettings.rotationPool(1,false,false);DecorationSettings.rotationPool(0,false,false);});
        test("timer and per-message modes each require different styles",()->{for(boolean[] modes:new boolean[][]{{true,false},{false,true},{true,true}}){for(int count:new int[]{0,1}){try{DecorationSettings.rotationPool(count,modes[0],modes[1]);throw new AssertionError();}catch(IllegalArgumentException expected){}}DecorationSettings.rotationPool(2,modes[0],modes[1]);}});
        test("decoration ID parser rejects empty and invalid numeric IDs",()->{for(String raw:new String[]{"", "0", "-1", "1000000000", "101,broken"}){try{DecorationSettings.ids(raw);throw new AssertionError();}catch(IllegalArgumentException expected){}}});
        test("high-frequency timer keeps its final millisecond then becomes due",()->{eq(RotationTiming.remaining(160999L,101000L,60000L),1L);eq(RotationTiming.remaining(161000L,101000L,60000L),0L);});
        test("future restored timestamp cannot freeze rotation",()->{eq(RotationTiming.remaining(100000L,160000L,60000L),0L);eq(RotationTiming.remaining(159999L,160000L,60000L),0L);});
        test("restart with an old successful rotation is immediately due",()->{eq(RotationTiming.remaining(180000L,100000L,60000L),0L);eq(RotationTiming.remaining(180000L,0L,60000L),0L);});
        test("monotonic timer continues counting down across successive polls",()->{long last=650000L;long[] times={700000L,701000L,709999L,710000L,711000L};long[] expected={10000L,9000L,1L,0L,0L};for(int i=0;i<times.length;i++)eq(RotationTiming.remaining(times[i],last,60000L),expected[i]);});
        test("rotation retry delay retains the configured interval and caps short-interval backoff",()->{for(long interval:new long[]{60000L,180000L,300000L}){long previous=0;for(int failures=0;failures<100;failures++){long delay=RotationTiming.retryDelay(failures,interval);ok(delay>=interval && delay<=300000L && delay>=previous);previous=delay;}eq(previous,300000L);}});
        test("rotation retry delay has a floor even without a configured interval",()->{eq(RotationTiming.retryDelay(-1,0L),5000L);eq(RotationTiming.retryDelay(0,0L),5000L);eq(RotationTiming.retryDelay(100,0L),300000L);});
        test("low-frequency retry cannot bypass the user's long interval",()->{for(int failures:new int[]{0,1,5,100})ok(RotationTiming.retryDelay(failures,1800000L)>=1800000L);});
        test("deferred send gesture resumes at most once",()->{SendReplayGuard guard=new SendReplayGuard(1L,1000L,45000L);ok(guard.consume(1L,1001L,true));ok(!guard.consume(1L,1002L,true));});
        test("concurrent callbacks cannot replay one deferred send twice",()->{SendReplayGuard guard=new SendReplayGuard(1L,1000L,45000L);AtomicInteger wins=new AtomicInteger();ExecutorService workers=Executors.newFixedThreadPool(8);try{List<Future<?>> results=new ArrayList<>();for(int i=0;i<32;i++)results.add(workers.submit(()->{if(guard.consume(1L,1001L,true))wins.incrementAndGet();}));for(Future<?> result:results)result.get();eq(wins.get(),1);}finally{workers.shutdown();}});
        test("deferred send expires at deadline and cannot recover",()->{SendReplayGuard before=new SendReplayGuard(1L,1000L,45000L);ok(before.consume(1L,45999L,true));SendReplayGuard at=new SendReplayGuard(1L,1000L,45000L);ok(!at.consume(1L,46000L,true));ok(!at.consume(1L,45999L,true));SendReplayGuard after=new SendReplayGuard(1L,1000L,45000L);ok(!after.consume(1L,46001L,true));});
        test("deferred send rejects a backward clock permanently",()->{SendReplayGuard guard=new SendReplayGuard(1L,1000L,45000L);ok(!guard.consume(1L,999L,true));ok(!guard.consume(1L,1001L,true));});
        test("editing then restoring text still cancels deferred send",()->{SendReplayGuard guard=new SendReplayGuard(5L,1000L,45000L);ok(!guard.consume(7L,1001L,true));ok(!guard.consume(5L,1002L,true));});
        test("changed send context consumes the gesture without retry",()->{SendReplayGuard guard=new SendReplayGuard(1L,1000L,45000L);ok(!guard.consume(1L,1001L,false));ok(!guard.consume(1L,1002L,true));});
        test("equivalent conversation wrappers resolve to one stable binding",()->{Object fragment=new Object(),root=new Object(),first=new Object(),second=new Object();ConversationMatch<Object> match=new ConversationMatch<>();match.add(first,fragment,root,2,"fixture-peer","");match.add(second,fragment,root,2,new String("fixture-peer"),new String(""));ok(match.unique()==first);});
        test("many equivalent conversation observations do not create ambiguity",()->{Object fragment=new Object(),root=new Object(),first=new Object();ConversationMatch<Object> match=new ConversationMatch<>();match.add(first,fragment,root,2,"fixture-peer","");for(int i=0;i<32;i++)match.add(new Object(),fragment,root,2,"fixture-peer","");ok(match.unique()==first);});
        test("value-equal distinct fragments or roots cannot authorize a binding",()->{Object fragment=new String("fragment"),root=new String("root");ok(!ConversationMatch.same(fragment,root,2,"fixture-peer","",new String("fragment"),root,2,"fixture-peer",""));ok(!ConversationMatch.same(fragment,root,2,"fixture-peer","",fragment,new String("root"),2,"fixture-peer",""));});
        test("every contact tuple field participates in conversation identity",()->{Object fragment=new Object(),root=new Object();ok(!ConversationMatch.same(fragment,root,2,"fixture-peer","",fragment,root,1,"fixture-peer",""));ok(!ConversationMatch.same(fragment,root,2,"fixture-peer","",fragment,root,2,"other-fixture-peer",""));ok(!ConversationMatch.same(fragment,root,2,"fixture-peer","",fragment,root,2,"fixture-peer","other-fixture-guild"));});
        test("conflicting conversations cannot be resolved by selecting the newest wrapper",()->{Object fragment=new Object(),root=new Object();ConversationMatch<Object> match=new ConversationMatch<>();match.add(new Object(),fragment,root,2,"old-fixture-peer","");match.add(new Object(),fragment,root,2,"new-fixture-peer","");match.add(new Object(),fragment,root,2,"new-fixture-peer","");ok(match.unique()==null);});
        test("conversation ambiguity persists after another equivalent old observation",()->{Object fragment=new Object(),root=new Object();ConversationMatch<Object> match=new ConversationMatch<>();match.add(new Object(),fragment,root,2,"fixture-peer","");match.add(new Object(),new Object(),root,2,"fixture-peer","");match.add(new Object(),fragment,root,2,"fixture-peer","");ok(match.unique()==null);});
        test("cleared wrapper references do not change stable conversation identity",()->{Object fragment=new Object(),root=new Object();java.lang.ref.WeakReference<Object> wrapper=new java.lang.ref.WeakReference<>(new Object());wrapper.clear();ok(wrapper.get()==null);ConversationMatch<Object> replacement=new ConversationMatch<>();Object fresh=new Object();replacement.add(fresh,fragment,root,2,"fixture-peer","");ok(replacement.unique()==fresh);ok(ConversationMatch.same(fragment,root,2,"fixture-peer","",fragment,root,2,"fixture-peer",""));});
        test("missing real conversation objects fail closed even when both references cleared",()->{Object fragment=new Object(),root=new Object();ok(!ConversationMatch.same(null,root,2,"fixture-peer","",null,root,2,"fixture-peer",""));ok(!ConversationMatch.same(fragment,null,2,"fixture-peer","",fragment,null,2,"fixture-peer",""));ok(!ConversationMatch.same(fragment,root,2,null,"",fragment,root,2,null,""));ok(!ConversationMatch.same(fragment,root,2,"fixture-peer",null,fragment,root,2,"fixture-peer",null));ConversationMatch<Object> match=new ConversationMatch<>();match.add(new Object(),null,root,2,"fixture-peer","");match.add(new Object(),fragment,root,2,"fixture-peer","");ok(match.unique()==null);});
        test("session change consumes deferred send even if the previous tuple returns",()->{Object fragment=new Object(),root=new Object();SendReplayGuard guard=new SendReplayGuard(1L,1000L,45000L);boolean changed=ConversationMatch.same(fragment,root,2,"old-fixture-peer","",fragment,root,2,"new-fixture-peer","");ok(!guard.consume(1L,1001L,changed));boolean restored=ConversationMatch.same(fragment,root,2,"old-fixture-peer","",fragment,root,2,"old-fixture-peer","");ok(!guard.consume(1L,1002L,restored));});
        test("complete live conversation owner chain is consistent",()->{Object fragment=new Object(),pie=new Object(),context=new Object(),param=new Object();ok(ActiveConversationChain.consistent(fragment,fragment,pie,pie,context,context,context,param,param));});
        test("context belonging to another fragment cannot authorize current conversation",()->{Object fragment=new Object(),pie=new Object(),context=new Object(),param=new Object();ok(!ActiveConversationChain.consistent(fragment,new Object(),pie,pie,context,context,context,param,param));});
        test("manager getter and manager field must identify the same live pie",()->{Object fragment=new Object(),pie=new Object(),context=new Object(),param=new Object();ok(!ActiveConversationChain.consistent(fragment,fragment,pie,new Object(),context,context,context,param,param));});
        test("pie getter and stored context must identify the same live context",()->{Object fragment=new Object(),pie=new Object(),context=new Object(),param=new Object();ok(!ActiveConversationChain.consistent(fragment,fragment,pie,pie,context,new Object(),context,param,param));});
        test("fragment weak current context cannot point to an older wrapper",()->{Object fragment=new Object(),pie=new Object(),context=new Object(),param=new Object();ok(!ActiveConversationChain.consistent(fragment,fragment,pie,pie,context,context,new Object(),param,param));});
        test("pie and current context must share the identical session param",()->{Object fragment=new Object(),pie=new Object(),context=new Object(),param=new Object();ok(!ActiveConversationChain.consistent(fragment,fragment,pie,pie,context,context,context,param,new Object()));});
        test("every missing live chain link fails closed",()->{Object fragment=new Object(),pie=new Object(),context=new Object(),param=new Object();Object[] complete={fragment,fragment,pie,pie,context,context,context,param,param};for(int i=0;i<complete.length;i++){Object[] link=complete.clone();link[i]=null;ok(!ActiveConversationChain.consistent(link[0],link[1],link[2],link[3],link[4],link[5],link[6],link[7],link[8]));}});
        test("value-equal owner links cannot substitute for actual live identities",()->{Object fragment=new String("fragment"),pie=new String("pie"),context=new String("context"),param=new String("param");ok(!ActiveConversationChain.consistent(fragment,new String("fragment"),pie,pie,context,context,context,param,param));ok(!ActiveConversationChain.consistent(fragment,fragment,pie,new String("pie"),context,context,context,param,param));ok(!ActiveConversationChain.consistent(fragment,fragment,pie,pie,context,new String("context"),context,param,param));ok(!ActiveConversationChain.consistent(fragment,fragment,pie,pie,context,context,context,param,new String("param")));});
        test("stale observation metadata cannot replace the active chain param",()->{Object fragment=new Object(),pie=new Object(),liveContext=new Object(),liveParam=new Object(),oldObservedContext=new Object(),oldObservedParam=new Object();ok(ActiveConversationChain.consistent(fragment,fragment,pie,pie,liveContext,liveContext,liveContext,liveParam,liveParam));ok(!ActiveConversationChain.consistent(fragment,fragment,pie,pie,liveContext,oldObservedContext,liveContext,liveParam,oldObservedParam));});
        test("pending send accepts the same current pie context and param",()->{Object pie=new Object(),context=new Object(),param=new Object();ok(ActiveConversationChain.sameCurrent(pie,context,param,pie,context,param));});
        test("pending current identity rejects each changed or cleared owner link",()->{Object pie=new Object(),context=new Object(),param=new Object();ok(!ActiveConversationChain.sameCurrent(pie,context,param,new Object(),context,param));ok(!ActiveConversationChain.sameCurrent(pie,context,param,pie,new Object(),param));ok(!ActiveConversationChain.sameCurrent(pie,context,param,pie,context,new Object()));Object[] complete={pie,context,param,pie,context,param};for(int i=0;i<complete.length;i++){Object[] link=complete.clone();link[i]=null;ok(!ActiveConversationChain.sameCurrent(link[0],link[1],link[2],link[3],link[4],link[5]));}});
        test("observed active owner switch cancels deferred send even if old chain returns",()->{Object pie=new Object(),context=new Object(),param=new Object();SendReplayGuard guard=new SendReplayGuard(1L,1000L,45000L);ok(!guard.consume(1L,1001L,ActiveConversationChain.sameCurrent(pie,context,param,new Object(),new Object(),new Object())));ok(!guard.consume(1L,1002L,ActiveConversationChain.sameCurrent(pie,context,param,pie,context,param)));});
        test("permit requires a physical arm",()->ok(!new SendPermit().consume("hi",10)));
        test("permit matches exact text once",()->{SendPermit p=new SendPermit();p.arm("你好",10);ok(p.consume("你好",11));ok(!p.consume("你好",12));});
        test("permit rejects mismatched text and clears",()->{SendPermit p=new SendPermit();p.arm("A",10);ok(!p.consume("B",11));ok(!p.consume("A",12));});
        test("permit expires",()->{SendPermit p=new SendPermit();p.arm("A",10);ok(!p.consume("A",2511));});
        test("permit boundary accepted",()->{SendPermit p=new SendPermit();p.arm("A",10);ok(p.consume("A",2510));});
        test("permit rejects backward clock",()->{SendPermit p=new SendPermit();p.arm("A",10);ok(!p.consume("A",9));});
        test("permit clear prevents stale use",()->{SendPermit p=new SendPermit();p.arm("A",10);p.clear();ok(!p.consume("A",11));});
        test("blank/null/oversized input never arms",()->{SendPermit p=new SendPermit();p.arm("",1);ok(!p.consume("",2));p.arm(null,1);ok(!p.consume("",2));p.arm(new String(new char[4001]),1);ok(!p.consume("x",2));});
        test("two concurrent sends cannot consume same permit",()->{
            SendPermit p=new SendPermit();p.arm("A",1000);AtomicInteger wins=new AtomicInteger();ExecutorService ex=Executors.newFixedThreadPool(8);
            List<Future<?>> fs=new ArrayList<>();for(int i=0;i<32;i++)fs.add(ex.submit(()->{if(p.consume("A",1001))wins.incrementAndGet();}));
            for(Future<?> f:fs)f.get();ex.shutdown();eq(wins.get(),1);
        });
        test("identity set rejects equal-but-different lists",()->{IdentityWeakSet s=new IdentityWeakSet();List<String>a=new ArrayList<>(),b=new ArrayList<>();s.add(a);ok(s.contains(a));ok(!s.contains(b));});
        test("identity set null safe",()->{IdentityWeakSet s=new IdentityWeakSet();s.add(null);ok(!s.contains(null));eq(s.size(),0);});
        test("QFun directSend call stack blocked",()->ok(OriginGuard.deniedStack(frame("me.yxp.qfun.hook.chat.RepeatMsg","directSend"))));
        test("QFun send hook is not a repeat",()->ok(!OriginGuard.deniedStack(frame("me.yxp.qfun.hook.msg.OnSendMsg","beforeHookedMethod"))));
        test("QFun script send is not a repeat",()->ok(!OriginGuard.deniedStack(frame("me.yxp.qfun.plugin.api.PluginMethod","sendMsg"))));
        test("normal send with a tap applies when QFun observer is on the stack",()->{Env e=new Env();e.permit.arm("hi",1000);eq(e.run(args("hi",null),new StackTraceElement[]{new StackTraceElement("me.yxp.qfun.hook.msg.OnSendMsg","beforeHookedMethod","x",1),new StackTraceElement("com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService$CppProxy","sendMsg","x",1)},0).reason,SendPolicy.Reason.APPLIED);});
        test("forward stack blocked",()->ok(OriginGuard.deniedStack(frame("com.tencent.qqnt.kernel.SomeClass","forwardMsg"))));
        test("follow/repeater stack blocked",()->ok(OriginGuard.deniedStack(frame("com.tencent.mobileqq.aio.msgfollow.Follow","run"))));
        test("native composer stack allowed by deny check",()->ok(!OriginGuard.deniedStack(NORMAL)));
        test("kernel async send with exact tap is applied",()->{Env e=new Env();e.permit.arm("hi",1000);eq(e.run(args("hi",null),frame("com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService$CppProxy","sendMsg"),0).reason,SendPolicy.Reason.APPLIED);});
        test("async send without a tap is skipped",()->{Env e=new Env();eq(e.run(args("hi",null),frame("com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService$CppProxy","sendMsg"),0).reason,SendPolicy.Reason.NO_PERMIT);});
        test("missing stack fails closed",()->ok(OriginGuard.deniedStack(null)));
        test("normal fresh plain text produces replacement",()->{Env e=new Env();Object[] a=args("hi",null);e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.APPLIED);ok(a[3]==null);});
        test("QFun direct send denied even with matching pending tap",()->{Env e=new Env();e.permit.arm("hi",1000);eq(e.run(args("hi",null),frame("me.yxp.qfun.hook.chat.RepeatMsg","directSend"),0).reason,SendPolicy.Reason.ORIGINAL_OR_REPEAT);});
        test("forward nesting denied even with matching pending tap",()->{Env e=new Env();e.permit.arm("hi",1000);eq(e.run(args("hi",null),NORMAL,1).reason,SendPolicy.Reason.ORIGINAL_OR_REPEAT);});
        test("original elements list blocks obfuscated repeater",()->{Env e=new Env();Object[]a=args("hi",null);e.originals.add(a[2]);e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.ORIGINAL_OR_REPEAT);});
        test("reused original attribute map blocks repeat",()->{Env e=new Env();Object[]a=args("hi",map(attribute()));e.originals.add(a[3]);e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.ORIGINAL_OR_REPEAT);});
        test("copied list with original elements still blocked",()->{Env e=new Env();Object[]a=args("hi",null);Object el=((List<?>)a[2]).get(0);e.originals.add(el);a[2]=new ArrayList<>((List<?>)a[2]);e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.ORIGINAL_OR_REPEAT);});
        test("ordinary send without a tap is skipped",()->{Env e=new Env();eq(e.run(args("hi",null),NORMAL,0).reason,SendPolicy.Reason.NO_PERMIT);});
        test("another script changing text consumes and rejects permit",()->{Env e=new Env();e.permit.arm("hi",1000);eq(e.run(args("modified hi",null),NORMAL,0).reason,SendPolicy.Reason.NO_PERMIT);eq(e.run(args("hi",null),NORMAL,0).reason,SendPolicy.Reason.NO_PERMIT);});
        test("disabled policy clears pending permit",()->{Env e=new Env();e.permit.arm("hi",1000);eq(e.policy.prepare(args("hi",null),NORMAL,1001,false,true,true,0,false,true,Arrays.asList(A)).reason,SendPolicy.Reason.DISABLED);ok(!e.permit.consume("hi",1002));});
        test("unsupported chat type skipped",()->{Env e=new Env();Object[]a=args("hi",null);((Contact)a[1]).chatType=100;e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.UNSUPPORTED);});
        test("mentions skipped",()->{Env e=new Env();Object[]a=args("hi",null);((Element)((List<?>)a[2]).get(0)).textElement.atType=2;e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.UNSUPPORTED);});
        test("mixed/photo/voice/reply elements skipped",()->{for(int type:new int[]{2,3,4,5,6,7}){Env e=new Env();Object[]a=args("hi",null);((Element)((List<?>)a[2]).get(0)).elementType=type;e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.UNSUPPORTED);}});
        test("missing chat type fails closed and preserves args",()->{Env e=new Env();Object[]a=args("hi",null);a[1]=new Object();e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.UNSUPPORTED);ok(a[3]==null);});
        test("null argument array safe",()->{Env e=new Env();eq(e.run(null,NORMAL,0).reason,SendPolicy.Reason.UNSUPPORTED);});
        test("empty bubble pool safe",()->{Env e=new Env();eq(e.policy.prepare(args("hi",null),NORMAL,1001,true,true,true,0,false,true,Collections.emptyList()).reason,SendPolicy.Reason.DISABLED);});
        test("copy-on-write retains original bubble and attributes",()->{
            MsgAttributeInfo old=attribute();Map<Integer,MsgAttributeInfo> source=map(old);MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());
            Map<Object,Object> result=a.withBubble(source,A);MsgAttributeInfo n=(MsgAttributeInfo)result.get(17);
            ok((Object)result!=(Object)source);ok(n!=old);ok(n.vasMsgInfo!=old.vasMsgInfo);ok(n.vasMsgInfo.bubbleInfo!=old.vasMsgInfo.bubbleInfo);
            eq(old.vasMsgInfo.bubbleInfo.bubbleId,7);eq(n.vasMsgInfo.bubbleInfo.bubbleId,101);eq(old.attrId,99L);eq(n.attrId,99L);
            ok(n.groupHonor==old.groupHonor);ok(n.unrelatedAttribute==old.unrelatedAttribute);ok(n.vasMsgInfo.vasFont==old.vasMsgInfo.vasFont);
            ok(n.vasMsgInfo.avatarPendantInfo==old.vasMsgInfo.avatarPendantInfo);ok(n.vasMsgInfo.bubbleInfo.futureUnrelatedField==old.vasMsgInfo.bubbleInfo.futureUnrelatedField);
        });
        test("other map entries preserve identity",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());Map<Integer,MsgAttributeInfo> m=map(attribute());MsgAttributeInfo other=new MsgAttributeInfo();m.put(99,other);ok(a.withBubble(m,A).get(99)==other);});
        test("null map uses learned attribute metadata",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());MsgAttributeInfo n=(MsgAttributeInfo)a.withBubble(null,A).get(17);eq(n.attrId,0L);eq(n.attrType,17);});
        test("new attribute uses outgoing message id",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());MsgAttributeInfo n=(MsgAttributeInfo)a.withBubble(null,A,555L).get(17);eq(n.attrId,555L);eq(n.vasMsgInfo.bubbleInfo.bubbleId,101);});
        test("conflicting VAS key rejects without mutation",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());Map<Integer,MsgAttributeInfo>m=map(attribute());m.put(99,attribute());try{a.withBubble(m,A);throw new AssertionError();}catch(IllegalArgumentException expected){}eq(m.size(),2);eq(m.get(17).vasMsgInfo.bubbleInfo.bubbleId,7);});
        test("existing bubble keeps its attribute type",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());MsgAttributeInfo x=attribute();x.attrType=88;MsgAttributeInfo n=(MsgAttributeInfo)a.withBubble(map(x),A).get(17);eq(n.attrType,88);eq(n.vasMsgInfo.bubbleInfo.bubbleId,101);});
        test("extract contains metadata only",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());List<BubbleSpec>b=a.extract(map(attribute()));eq(b.size(),1);eq(b.get(0).bubbleId,7);eq(b.get(0).subBubbleId,8);});
        test("default/malformed templates ignored",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());MsgAttributeInfo x=attribute();x.vasMsgInfo.bubbleInfo.bubbleId=0;x.vasMsgInfo.bubbleInfo.subBubbleId=0;eq(a.extract(map(x)).size(),0);eq(a.extract("bad").size(),0);});
        test("negative bubble IDs rejected",()->{try{new BubbleSpec(1,1,0,-1,2,null,0);throw new AssertionError();}catch(IllegalArgumentException expected){}});
        test("nullable fields preserved",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());MsgAttributeInfo x=(MsgAttributeInfo)a.withBubble(null,A).get(17);ok(x.vasMsgInfo.bubbleInfo.bubbleDiyTextId==null);});
        test("fixed picker picks first selected",()->eq(new BubblePicker(new Random(1)).choose(Arrays.asList(A,B),true,true),A));
        test("hold reuses one style inside the window",()->{BubblePicker p=new BubblePicker(new Random(1));BubbleSpec first=p.choose(Arrays.asList(A,B),false,true);p.hold(first,1000);eq(p.held(1200,400),first);ok(p.held(2000,400)==null);});
        test("random picker never immediately repeats with two entries",()->{BubblePicker p=new BubblePicker(new Random(1));BubbleSpec last=null;for(int i=0;i<500;i++){BubbleSpec n=p.choose(Arrays.asList(A,B),false,true);ok(!n.equals(last));p.commit(n);last=n;}});
        test("one-item random pool valid",()->{BubblePicker p=new BubblePicker();p.commit(A);eq(p.choose(Arrays.asList(A),false,true),A);});
        test("policy never changes message ID/contact/elements/callback",()->{Env e=new Env();Object[]a=args("hi",null);Object[]copy=a.clone();e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.APPLIED);for(int i=0;i<5;i++)ok(a[i]==copy[i]);});
        test("every new send needs its own tap",()->{Env e=new Env();e.permit.arm("hi",1000);eq(e.run(args("hi",null),NORMAL,0).reason,SendPolicy.Reason.APPLIED);eq(e.run(args("hi",null),NORMAL,0).reason,SendPolicy.Reason.NO_PERMIT);e.permit.arm("yo",1000);eq(e.run(args("yo",null),NORMAL,0).reason,SendPolicy.Reason.APPLIED);});
        test("copied map with original attribute blocks repeat",()->{Env e=new Env();MsgAttributeInfo original=attribute();e.originals.add(original);e.permit.arm("hi",1000);eq(e.run(args("hi",map(original)),NORMAL,0).reason,SendPolicy.Reason.ORIGINAL_OR_REPEAT);});
        test("expired permit cannot change attributes",()->{Env e=new Env();e.permit.arm("hi",0);eq(e.policy.prepare(args("hi",null),NORMAL,2501,true,true,true,0,false,true,Arrays.asList(A)).reason,SendPolicy.Reason.NO_PERMIT);});
        test("two quick sends choose independently",()->{Env e=new Env();e.permit.arm("hi",1000);BubbleSpec first=e.run(args("hi",null),NORMAL,0).used;e.permit.arm("yo",1000);BubbleSpec second=e.run(args("yo",null),NORMAL,0).used;ok(first!=null && second!=null && !first.equals(second));});
        test("existing account bubble slot is restyled",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());MsgAttributeInfo own=attribute();Map<Integer,MsgAttributeInfo> m=new HashMap<>();m.put(0,own);Map<Object,Object> result=a.withBubble(m,A);eq(result.size(),1);eq(((MsgAttributeInfo)result.get(0)).vasMsgInfo.bubbleInfo.bubbleId,101);eq(own.vasMsgInfo.bubbleInfo.bubbleId,7);});
        System.out.println("\nRESULT: "+passed+" test groups passed. These are policy/fixture tests, NOT Android builds or QQ device tests.");
    }
}
