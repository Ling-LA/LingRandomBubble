package io.github.ling.randombubble.hook;

import android.content.Context;
import android.os.SystemClock;
import io.github.ling.randombubble.core.*;
import io.github.ling.randombubble.store.Config;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

final class HostRuntime {
    final SendPermit permit=new SendPermit();
    final IdentityWeakSet originalObjects=new IdentityWeakSet();
    final BubblePicker picker=new BubblePicker();
    final MsgAttrAdapter adapter;
    final SendPolicy policy;
    final HostBridge bridge;
    final AccountDecoration decoration;
    final ThreadLocal<Integer> forwarding=new ThreadLocal<Integer>() { @Override protected Integer initialValue() { return 0; } };
    final String qqVersion;
    final boolean versionSupported;
    volatile boolean sendInstalled,forwardInstalled,recordInstalled,tapInstalled,menuInstalled,settingInstalled,bridgeHealthy;
    volatile int menuInjected;
    private final ArrayDeque<String> logs=new ArrayDeque<>();
    private volatile String error="无";
    private volatile String lastUnknownFrames="未出现";
    private final AtomicLong arms=new AtomicLong(),applied=new AtomicLong(),skipRepeat=new AtomicLong(),
            skipUnknownSource=new AtomicLong(),skipNoPermit=new AtomicLong(),skipType=new AtomicLong(),skipDisabled=new AtomicLong(),errors=new AtomicLong(),observations=new AtomicLong(),recordsSeen=new AtomicLong();
    private final AtomicLong messageHolds=new AtomicLong(),messageResumes=new AtomicLong(),messageCancels=new AtomicLong();
    void messageHeld(){messageHolds.incrementAndGet();}
    void messageFinished(boolean sent){if(sent)messageResumes.incrementAndGet();else messageCancels.incrementAndGet();}
    HostRuntime(Context c,ClassLoader loader) {
        adapter=new MsgAttrAdapter(loader);
        policy=new SendPolicy(permit,originalObjects,adapter,picker);
        String v;
        try { v=c.getPackageManager().getPackageInfo("com.tencent.mobileqq",0).versionName; }
        catch(Exception e) { v="unknown"; }
        qqVersion=v==null ? "unknown" : v;
        versionSupported=qqVersion.equals("9.3.50") || qqVersion.startsWith("9.3.50.");
        decoration=new AccountDecoration(c,this);
        bridge=new HostBridge(c,this);
    }
    void arm(String text) {
        if(!ready(bridge.config)) return;
        permit.arm(text,SystemClock.uptimeMillis()); arms.incrementAndGet();
    }
    void observeRecord(Object record) {
        if(record==null) return;
        recordsSeen.incrementAndGet();
        try {
            Object elements=Reflect.get(record,"elements"), attrs=Reflect.get(record,"msgAttrs");
            // QFun reuses exactly these original objects in both repeater branches.
            originalObjects.add(elements);
            if(elements instanceof List) for(Object e:(List<?>)elements) originalObjects.add(e);
            originalObjects.add(attrs);
            if(attrs instanceof Map) for(Object a:((Map<?,?>)attrs).values()) originalObjects.add(a);
            if(bridge.config.collect) {
                List<BubbleSpec> specs=adapter.extract(attrs);
                for(BubbleSpec b:specs) { bridge.harvest(b); observations.incrementAndGet(); }
            }
        } catch(Throwable e) { setError("原消息读取："+e.getClass().getSimpleName()); }
    }
    /** No sends/cancels: only returns a new attributes map after the tested policy passes. */
    synchronized HashMap<Object,Object> prepare(Object[] args,StackTraceElement[] stack) {
        Config c=bridge.config;
        boolean ready=ready(c);
        SendPolicy.Result r=policy.prepare(args,stack,SystemClock.uptimeMillis(),ready,c.groups,c.privateChats,
                forwarding.get(),c.fixed,c.avoidRepeat,c.selected);
        switch(r.reason) {
            case DISABLED: skipDisabled.incrementAndGet(); break;
            case ORIGINAL_OR_REPEAT: skipRepeat.incrementAndGet(); break;
            case UNKNOWN_SOURCE:
                skipUnknownSource.incrementAndGet();
                StringBuilder frames=new StringBuilder(); int count=0;
                if(stack!=null) for(StackTraceElement frame:stack) {
                    if(frame.getClassName().startsWith("com.tencent.")) {
                        if(count++>=8) break;
                        frames.append(frame.getClassName()).append(".").append(frame.getMethodName()).append("\n");
                    }
                }
                lastUnknownFrames=frames.length()==0?"没有可识别的腾讯类帧":frames.toString();
                break;
            case UNSUPPORTED: skipType.incrementAndGet(); break;
            case NO_PERMIT: skipNoPermit.incrementAndGet(); break;
            case APPLIED: applied.incrementAndGet(); active=r.used; break;
            case ERROR: errors.incrementAndGet(); setError("发送前跳过："+r.errorType); break;
        }
        if(r.reason!=SendPolicy.Reason.DISABLED) {
            String extra=r.errorType==null?"":" "+r.errorType;
            if(r.reason==SendPolicy.Reason.APPLIED && r.used!=null)
                extra=" "+BubblePicker.visual(r.used)+" 样式"+visuals(c.selected)+(c.fixed?" 固定":" 随机")+(isEquipped(r.used)?" 与装扮相同":" 装扮为 "+equippedLabel());
            log("发送结果 "+r.reason+extra);
        }
        return r.attributes;
    }
    private boolean ready(Config c) {
        // Receiver validation failed for this path. AccountDecoration is independent.
        return false;
    }
    private BubbleSpec active;
    private volatile java.lang.ref.WeakReference<android.app.Activity> resumed=new java.lang.ref.WeakReference<>(null);
    void resumed(android.app.Activity activity) { resumed=new java.lang.ref.WeakReference<>(activity); }
    void paused(android.app.Activity activity) {if(resumed.get()==activity)resumed=new java.lang.ref.WeakReference<>(null);}
    android.app.Activity currentActivity() {
        android.app.Activity a=resumed.get();
        return a==null || a.isFinishing() || a.isDestroyed() ? null : a;
    }
    private volatile int equippedBubble=Integer.MIN_VALUE, equippedSub=Integer.MIN_VALUE;
    /** Account bubble as QQ itself reports it; the value the server most likely shows to others. */
    void equipped(boolean sub,int value) {
        if(sub ? value==equippedSub : value==equippedBubble) return;
        if(sub) equippedSub=value; else equippedBubble=value;
        log("账号装扮 "+equippedLabel());
    }
    String equippedLabel() {
        String main=equippedBubble==Integer.MIN_VALUE?"?":String.valueOf(equippedBubble);
        String sub=equippedSub==Integer.MIN_VALUE?"?":String.valueOf(equippedSub);
        return "气泡 "+main+" / 子气泡 "+sub;
    }
    boolean isEquipped(BubbleSpec spec) {
        if(spec==null || equippedBubble==Integer.MIN_VALUE) return false;
        int main=spec.bubbleId==null?0:spec.bubbleId;
        return main==equippedBubble;
    }
    private static int visuals(List<BubbleSpec> list) {
        java.util.HashSet<String> looks=new java.util.HashSet<>();
        if(list!=null) for(BubbleSpec s:list) if(s!=null) looks.add(BubblePicker.visual(s));
        return looks.size();
    }
    void log(String line) {
        String row=android.text.format.DateFormat.format("HH:mm:ss",System.currentTimeMillis())+" "+line;
        synchronized(logs) { logs.addLast(row); while(logs.size()>80) logs.removeFirst(); }
        if(bridge!=null) bridge.writeLog(row);
    }
    void enterForward() { permit.clear(); forwarding.set(forwarding.get()+1); skipRepeat.incrementAndGet(); }
    void leaveForward() { int n=forwarding.get()-1; if(n<=0) forwarding.remove(); else forwarding.set(n); }
    void setError(String message) { error=message; }
    private String dumpLogs() {
        StringBuilder out=new StringBuilder();
        synchronized(logs) { for(String row:logs) out.append(row).append('\n'); }
        return out.length()==0?"尚无":out.toString();
    }
    String report() {
        return "Ling 随机气泡 "+io.github.ling.randombubble.BuildConfig.VERSION_NAME+"\nQQ："+qqVersion+"\n账号："+bridge.libraryMask()+"\n版本门控："+(versionSupported?"匹配目标":"版本不同，停止替换")
            +"\n发送接口："+sendInstalled+"\n转发回避接口："+forwardInstalled+"\n原消息识别接口："+recordInstalled
            +"\n收藏菜单："+menuInstalled+" 注入次数："+menuInjected+"\nQQ设置入口："+settingInstalled
            +"\n发送按钮观察："+tapInstalled+"\n配置桥接："+bridgeHealthy
            +"\nQQ 内配置："+bridge.controlsHealthy()+"；来源 "+bridge.controlSource()
            +"\n轮换计时："+bridge.timerStatus()
            +"\n逐消息延后 / 恢复 / 取消："+messageHolds.get()+" / "+messageResumes.get()+" / "+messageCancels.get()
            +"\n账号装扮切换："+decoration.status()
            +"\n\n有效发送点击："+arms.get()+"\n已替换发送参数："+applied.get()
            +"\n复读/转发/原对象跳过："+skipRepeat.get()+"\n未知来源调用栈跳过："+skipUnknownSource.get()+"\n无匹配点击跳过："+skipNoPermit.get()
            +"\n非普通文字/聊天类型跳过："+skipType.get()+"\n开关/适配条件不满足："+skipDisabled.get()
            +"\n原消息访问次数："+recordsSeen.get()+"\n属性处理异常："+errors.get()+"\n读取到气泡模板次数："+observations.get()
            +"\n最近诊断："+error+"\n\n最近未知来源类/方法（无参数）：\n"+lastUnknownFrames
            +"\n\n运行日志（不含正文）：\n"+dumpLogs()
            +"\n\n“已替换发送参数”不代表服务器已接受，更不代表对方已看到。\n仅统计计数和接口状态；不记录正文、QQ号、群号或令牌。";
    }
}
