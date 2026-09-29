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
        test("QFun script send blocked",()->ok(OriginGuard.deniedStack(frame("me.yxp.qfun.plugin.api.PluginMethod","sendMsg"))));
        test("forward stack blocked",()->ok(OriginGuard.deniedStack(frame("com.tencent.qqnt.kernel.SomeClass","forwardMsg"))));
        test("follow/repeater stack blocked",()->ok(OriginGuard.deniedStack(frame("com.tencent.mobileqq.aio.msgfollow.Follow","run"))));
        test("native composer stack allowed by deny check",()->ok(!OriginGuard.deniedStack(NORMAL)));
        test("unknown async source rejected even with matching click",()->{Env e=new Env();e.permit.arm("hi",1000);eq(e.run(args("hi",null),frame("com.tencent.qqnt.AsyncWorker","run"),0).reason,SendPolicy.Reason.UNKNOWN_SOURCE);});
        test("unknown source consumes stale pending permit",()->{Env e=new Env();e.permit.arm("hi",1000);e.run(args("hi",null),frame("obfuscated.a","run"),0);ok(!e.permit.consume("hi",1002));});
        test("missing stack fails closed",()->ok(OriginGuard.deniedStack(null)));
        test("normal fresh plain text produces replacement",()->{Env e=new Env();Object[] a=args("hi",null);e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.APPLIED);ok(a[3]==null);});
        test("QFun direct send denied even with matching pending tap",()->{Env e=new Env();e.permit.arm("hi",1000);eq(e.run(args("hi",null),frame("me.yxp.qfun.hook.chat.RepeatMsg","directSend"),0).reason,SendPolicy.Reason.ORIGINAL_OR_REPEAT);});
        test("forward nesting denied even with matching pending tap",()->{Env e=new Env();e.permit.arm("hi",1000);eq(e.run(args("hi",null),NORMAL,1).reason,SendPolicy.Reason.ORIGINAL_OR_REPEAT);});
        test("original elements list blocks obfuscated repeater",()->{Env e=new Env();Object[]a=args("hi",null);e.originals.add(a[2]);e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.ORIGINAL_OR_REPEAT);});
        test("original attribute map blocks repeater",()->{Env e=new Env();Object[]a=args("hi",map(attribute()));e.originals.add(a[3]);e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.ORIGINAL_OR_REPEAT);});
        test("copied list with original elements still blocked",()->{Env e=new Env();Object[]a=args("hi",null);Object el=((List<?>)a[2]).get(0);e.originals.add(el);a[2]=new ArrayList<>((List<?>)a[2]);e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.ORIGINAL_OR_REPEAT);});
        test("script/background send without tap remains unchanged",()->{Env e=new Env();eq(e.run(args("hi",null),NORMAL,0).reason,SendPolicy.Reason.NO_PERMIT);});
        test("altered text preprocessing safely skipped",()->{Env e=new Env();e.permit.arm("hi",1000);eq(e.run(args("modified hi",null),NORMAL,0).reason,SendPolicy.Reason.NO_PERMIT);});
        test("disabled policy clears pending permit",()->{Env e=new Env();e.permit.arm("hi",1000);eq(e.policy.prepare(args("hi",null),NORMAL,1001,false,true,true,0,false,true,Arrays.asList(A)).reason,SendPolicy.Reason.DISABLED);ok(!e.permit.consume("hi",1002));});
        test("unsupported chat type skipped",()->{Env e=new Env();Object[]a=args("hi",null);((Contact)a[1]).chatType=100;e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.UNSUPPORTED);});
        test("mentions skipped",()->{Env e=new Env();Object[]a=args("hi",null);((Element)((List<?>)a[2]).get(0)).textElement.atType=2;e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.UNSUPPORTED);});
        test("mixed/photo/voice/reply elements skipped",()->{for(int type:new int[]{2,3,4,5,6,7}){Env e=new Env();Object[]a=args("hi",null);((Element)((List<?>)a[2]).get(0)).elementType=type;e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.UNSUPPORTED);}});
        test("failed reflection preserves original args",()->{Env e=new Env();Object[]a=args("hi",null);a[1]=new Object();e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.ERROR);ok(a[3]==null);});
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
        test("conflicting VAS key rejects without mutation",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());Map<Integer,MsgAttributeInfo>m=map(attribute());m.put(99,attribute());try{a.withBubble(m,A);throw new AssertionError();}catch(IllegalArgumentException expected){}eq(m.size(),2);eq(m.get(17).vasMsgInfo.bubbleInfo.bubbleId,7);});
        test("attribute type mismatch rejects",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());MsgAttributeInfo x=attribute();x.attrType=88;try{a.withBubble(map(x),A);throw new AssertionError();}catch(IllegalArgumentException expected){}});
        test("extract contains metadata only",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());List<BubbleSpec>b=a.extract(map(attribute()));eq(b.size(),1);eq(b.get(0).bubbleId,7);eq(b.get(0).subBubbleId,8);});
        test("default/malformed templates ignored",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());MsgAttributeInfo x=attribute();x.vasMsgInfo.bubbleInfo.bubbleId=0;x.vasMsgInfo.bubbleInfo.subBubbleId=0;eq(a.extract(map(x)).size(),0);eq(a.extract("bad").size(),0);});
        test("negative bubble IDs rejected",()->{try{new BubbleSpec(1,1,0,-1,2,null,0);throw new AssertionError();}catch(IllegalArgumentException expected){}});
        test("nullable fields preserved",()->{MsgAttrAdapter a=new MsgAttrAdapter(CoreSelfTest.class.getClassLoader());MsgAttributeInfo x=(MsgAttributeInfo)a.withBubble(null,A).get(17);ok(x.vasMsgInfo.bubbleInfo.bubbleDiyTextId==null);});
        test("fixed picker picks first selected",()->eq(new BubblePicker(new Random(1)).choose(Arrays.asList(A,B),true,true),A));
        test("random picker never immediately repeats with two entries",()->{BubblePicker p=new BubblePicker(new Random(1));BubbleSpec last=null;for(int i=0;i<500;i++){BubbleSpec n=p.choose(Arrays.asList(A,B),false,true);ok(!n.equals(last));p.commit(n);last=n;}});
        test("one-item random pool valid",()->{BubblePicker p=new BubblePicker();p.commit(A);eq(p.choose(Arrays.asList(A),false,true),A);});
        test("policy never changes message ID/contact/elements/callback",()->{Env e=new Env();Object[]a=args("hi",null);Object[]copy=a.clone();e.permit.arm("hi",1000);eq(e.run(a,NORMAL,0).reason,SendPolicy.Reason.APPLIED);for(int i=0;i<5;i++)ok(a[i]==copy[i]);});
        test("second send after same click stays unmodified",()->{Env e=new Env();e.permit.arm("hi",1000);eq(e.run(args("hi",null),NORMAL,0).reason,SendPolicy.Reason.APPLIED);eq(e.run(args("hi",null),NORMAL,0).reason,SendPolicy.Reason.NO_PERMIT);});
        System.out.println("\nRESULT: "+passed+" test groups passed. These are policy/fixture tests, NOT Android builds or QQ device tests.");
    }
}
