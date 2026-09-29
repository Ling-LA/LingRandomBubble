package io.github.ling.randombubble.hook;

import android.content.Context;
import android.os.SystemClock;
import io.github.ling.randombubble.core.*;
import io.github.ling.randombubble.store.Config;
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
    final ThreadLocal<Integer> forwarding=new ThreadLocal<Integer>() { @Override protected Integer initialValue() { return 0; } };
    final String qqVersion;
    final boolean versionSupported;
    volatile boolean sendInstalled,forwardInstalled,recordInstalled,tapInstalled,bridgeHealthy;
    private volatile String error="无";
    private volatile String lastUnknownFrames="未出现";
    private final AtomicLong arms=new AtomicLong(),applied=new AtomicLong(),skipRepeat=new AtomicLong(),
            skipUnknownSource=new AtomicLong(),skipNoPermit=new AtomicLong(),skipType=new AtomicLong(),skipDisabled=new AtomicLong(),errors=new AtomicLong(),observations=new AtomicLong(),recordsSeen=new AtomicLong();
    HostRuntime(Context c,ClassLoader loader) {
        adapter=new MsgAttrAdapter(loader);
        policy=new SendPolicy(permit,originalObjects,adapter,picker);
        String v;
        try { v=c.getPackageManager().getPackageInfo("com.tencent.mobileqq",0).versionName; }
        catch(Exception e) { v="unknown"; }
        qqVersion=v==null ? "unknown" : v;
        versionSupported=qqVersion.equals("9.3.50") || qqVersion.startsWith("9.3.50.");
        bridge=new HostBridge(c,this);
    }
    void arm(String text) {
        if(!versionSupported || !bridge.config.enabled || !sendInstalled || !forwardInstalled || !recordInstalled) return;
        permit.arm(text,SystemClock.uptimeMillis()); arms.incrementAndGet();
    }
    void observeRecord(Object record) {
        if(record==null || (!bridge.config.enabled && !bridge.config.collect)) return;
        recordsSeen.incrementAndGet();
        try {
            Object elements=Reflect.get(record,"elements"), attrs=Reflect.get(record,"msgAttrs");
            // QFun reuses exactly these original objects in both repeater branches.
            originalObjects.add(elements);
            if(attrs instanceof Map && !((Map<?,?>)attrs).isEmpty()) originalObjects.add(attrs);
            if(elements instanceof List) for(Object e:(List<?>)elements) originalObjects.add(e);
            if(bridge.config.collect) {
                List<BubbleSpec> specs=adapter.extract(attrs);
                for(BubbleSpec b:specs) { bridge.offer(b); observations.incrementAndGet(); }
            }
        } catch(Throwable e) { setError("原消息读取："+e.getClass().getSimpleName()); }
    }
    /** No sends/cancels: only returns a new attributes map after the tested policy passes. */
    synchronized HashMap<Object,Object> prepare(Object[] args,StackTraceElement[] stack) {
        Config c=bridge.config;
        boolean ready=versionSupported && sendInstalled && forwardInstalled && recordInstalled && tapInstalled
                && bridgeHealthy && c.enabled;
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
            case APPLIED: applied.incrementAndGet(); break;
            case ERROR: errors.incrementAndGet(); setError("发送前跳过："+r.errorType); break;
        }
        return r.attributes;
    }
    void enterForward() { permit.clear(); forwarding.set(forwarding.get()+1); skipRepeat.incrementAndGet(); }
    void leaveForward() { int n=forwarding.get()-1; if(n<=0) forwarding.remove(); else forwarding.set(n); }
    void setError(String message) { error=message; }
    String report() {
        return "Ling 随机气泡 0.1.0-experimental\nQQ："+qqVersion+"\n版本门控："+(versionSupported?"匹配目标":"不匹配，禁止修改")
            +"\n发送接口："+sendInstalled+"\n转发回避接口："+forwardInstalled+"\n原消息识别接口："+recordInstalled
            +"\n发送按钮观察："+tapInstalled+"\n配置桥接："+bridgeHealthy
            +"\n\n有效发送点击："+arms.get()+"\n已替换发送参数："+applied.get()
            +"\n复读/转发/原对象跳过："+skipRepeat.get()+"\n未知来源调用栈跳过："+skipUnknownSource.get()+"\n无匹配点击跳过："+skipNoPermit.get()
            +"\n非普通文字/聊天类型跳过："+skipType.get()+"\n开关/适配条件不满足："+skipDisabled.get()
            +"\n原消息访问次数："+recordsSeen.get()+"\n属性处理异常："+errors.get()+"\n读取到气泡模板次数："+observations.get()
            +"\n最近诊断："+error+"\n\n最近未知来源类/方法（无参数）：\n"+lastUnknownFrames
            +"\n\n“已替换发送参数”不代表服务器已接受，更不代表对方已看到。\n仅统计计数和接口状态；不记录正文、QQ号、群号或令牌。";
    }
}
