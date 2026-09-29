package io.github.ling.randombubble.hook;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** Legacy Xposed API 82 entry for the user's NPatch setup. No QFun classes are hooked. */
public final class HookEntry implements IXposedHookLoadPackage {
    private static boolean started;
    private final Set<Method> installed=new HashSet<>();
    private HostRuntime runtime;
    private UiTapGate taps;
    private ClassLoader loader;
    private int attempts;
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) throws Throwable {
        if(!"com.tencent.mobileqq".equals(p.packageName) || !"com.tencent.mobileqq".equals(p.processName)) return;
        XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try { start((Application)param.thisObject,(Context)param.args[0]); }
                catch(Throwable e) { XposedBridge.log("[LingBubble] attach failed: "+e.getClass().getSimpleName()); }
            }
        });
    }
    private synchronized void start(Application app,Context context) {
        if(started) return; started=true;
        loader=context.getClassLoader(); runtime=new HostRuntime(context.getApplicationContext()==null?context:context.getApplicationContext(),loader);
        taps=new UiTapGate(runtime);
        XposedBridge.log("[LingBubble] initialize QQ="+runtime.qqVersion+" targetMatched="+runtime.versionSupported);
        installTapObserver(); attemptHooks(); runtime.bridge.start();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) { attemptHooks(); }
            @Override public void onActivityPaused(Activity a) { runtime.permit.clear(); taps.reset(); }
            @Override public void onActivityCreated(Activity a,Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a,Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
        Handler h=new Handler(Looper.getMainLooper());
        for(long delay:new long[]{1000,3000,8000,15000}) h.postDelayed(this::attemptHooks,delay);
    }
    private void installTapObserver() {
        try {
            XposedHelpers.findAndHookMethod(Activity.class,"dispatchTouchEvent",MotionEvent.class,new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    taps.event((Activity)p.thisObject,(MotionEvent)p.args[0]);
                }
            });
            runtime.tapInstalled=true;
        } catch(Throwable e) { runtime.setError("发送按钮接口："+e.getClass().getSimpleName()); }
    }
    private synchronized void attemptHooks() {
        if(runtime==null || (runtime.sendInstalled && runtime.forwardInstalled && runtime.recordInstalled) || ++attempts>16) return;
        try {
            Class<?> service=Class.forName("com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService$CppProxy",false,loader);
            for(Method m:service.getDeclaredMethods()) {
                Class<?>[] t=m.getParameterTypes();
                if(m.getName().equals("sendMsg") && t.length==5 && t[0]==long.class
                        && t[1].getSimpleName().equals("Contact")
                        && t[2]==java.util.ArrayList.class && t[3]==java.util.HashMap.class && !installed.contains(m)) {
                    XposedBridge.hookMethod(m,new XC_MethodHook(XC_MethodHook.PRIORITY_LOWEST) {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            // Run last among before-hooks, so changes from earlier text preprocessors are checked.
                            HashMap<Object,Object> replacement=runtime.prepare(p.args,Thread.currentThread().getStackTrace());
                            if(replacement!=null) p.args[3]=replacement;
                        }
                    });
                    installed.add(m); runtime.sendInstalled=true;
                }
                if(m.getName().equals("forwardMsg") && t.length==5 && !installed.contains(m)) {
                    XposedBridge.hookMethod(m,new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                        @Override protected void beforeHookedMethod(MethodHookParam p) { runtime.enterForward(); }
                        @Override protected void afterHookedMethod(MethodHookParam p) { runtime.leaveForward(); }
                    });
                    installed.add(m); runtime.forwardInstalled=true;
                }
            }
        } catch(Throwable e) { runtime.setError("消息接口："+e.getClass().getSimpleName()); }
        try {
            Class<?> item=Class.forName("com.tencent.mobileqq.aio.msg.AIOMsgItem",false,loader);
            Method m=item.getMethod("getMsgRecord");
            if(!installed.contains(m)) {
                XposedBridge.hookMethod(m,new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if(!p.hasThrowable()) runtime.observeRecord(p.getResult());
                    }
                });
                installed.add(m);
            }
            runtime.recordInstalled=true;
        } catch(Throwable e) { runtime.setError("原消息接口："+e.getClass().getSimpleName()); }
    }
}
