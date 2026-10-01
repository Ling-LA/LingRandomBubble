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
import io.github.ling.randombubble.core.BubbleSpec;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** Legacy Xposed API 82 entry for the user's NPatch setup. No QFun classes are hooked. */
public final class HookEntry implements IXposedHookLoadPackage {
    private static boolean started;
    private boolean startRetryPosted;
    private int startAttempts;
    private final Set<Method> installed=new HashSet<>();
    private HostRuntime runtime;
    private UiTapGate taps;
    private MenuCollector menu;
    private SettingInject settings;
    private ClassLoader loader;
    private int attempts;
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) throws Throwable {
        if(!"com.tencent.mobileqq".equals(p.packageName)) return;
        if(p.processName!=null && !p.packageName.equals(p.processName)) return;
        loader=p.classLoader;
        // onCreate is public. Application.attach is hidden and can be blocked, which previously
        // left the module installed but never started inside NPatch.
        try {
            XposedHelpers.findAndHookMethod(Application.class,"onCreate",new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if(param.thisObject instanceof Application) start((Application)param.thisObject);
                }
            });
        } catch(Throwable e) { XposedBridge.log("[LingBubble] onCreate hook failed: "+e); }
        try {
            XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if(param.thisObject instanceof Application) start((Application)param.thisObject);
                }
            });
        } catch(Throwable e) { XposedBridge.log("[LingBubble] attach hook failed: "+e); }
        Application current=currentApplication();
        if(current!=null) start(current);
    }
    private static Application currentApplication() {
        try {
            Class<?> thread=Class.forName("android.app.ActivityThread");
            Object app=thread.getMethod("currentApplication").invoke(null);
            return app instanceof Application ? (Application)app : null;
        } catch(Throwable e) { return null; }
    }
    private synchronized void start(Application app) {
        if(started || app==null || !"com.tencent.mobileqq".equals(app.getPackageName())) return;
        startRetryPosted=false;
        Context context=app.getApplicationContext()==null ? app : app.getApplicationContext();
        ClassLoader candidate=context.getClassLoader();
        try { Class.forName("com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService$CppProxy",false,candidate); }
        catch(Throwable e) {
            if(startAttempts++<20) {
                if(!startRetryPosted) {
                    startRetryPosted=true;
                    new Handler(Looper.getMainLooper()).postDelayed(() -> start(app),1000);
                }
            } else XposedBridge.log("[LingBubble] QQ kernel class never appeared");
            return;
        }
        started=true;
        try {
            loader=candidate; runtime=new HostRuntime(context,loader);
            taps=new UiTapGate(runtime); menu=new MenuCollector(runtime,loader); settings=new SettingInject(runtime,loader);
            try { settings.install(); }
            catch(Throwable e) { runtime.log("设置入口初始化失败 "+e.getClass().getSimpleName()); }
            installOwnBubble();
            try {runtime.decoration.install(loader);}catch(Throwable e){runtime.log("商城接口初始化失败 "+e.getClass().getSimpleName());}
            XposedBridge.log("[LingBubble] initialize QQ="+runtime.qqVersion+" targetMatched="+runtime.versionSupported);
            installTapObserver();
            runtime.bridge.start();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { attemptHooks(); runtime.bridge.loadAccount(); }
                catch(Throwable ignored) { XposedBridge.log("[LingBubble] delayed init failed"); }
            },3000);
        } catch(Throwable e) {
            XposedBridge.log("[LingBubble] init aborted: "+e.getClass().getSimpleName());
            return;
        }
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) { if(runtime!=null) runtime.resumed(a); attemptHooks(); }
            @Override public void onActivityPaused(Activity a) { if(runtime!=null){ runtime.permit.clear(); taps.reset(); runtime.paused(a); } }
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
            Class<?> aio=Class.forName("com.tencent.aio.runtime.AIOContextImpl",false,loader);
            XposedBridge.hookAllConstructors(aio,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable())taps.observeAio(p.thisObject);}
            });
            XposedHelpers.findAndHookMethod(android.view.View.class,"performClick",new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    try {if(taps.deferClick((android.view.View)p.thisObject))p.setResult(Boolean.TRUE);}
                    catch(Throwable e){runtime.setError("逐消息发送观察："+e.getClass().getSimpleName());}
                }
            });
        }catch(Throwable e){runtime.setError("逐消息发送接口："+e.getClass().getSimpleName());}
        try {
            XposedHelpers.findAndHookMethod(Activity.class,"dispatchTouchEvent",MotionEvent.class,new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    taps.event((Activity)p.thisObject,(MotionEvent)p.args[0]);
                }
            });
            runtime.tapInstalled=true;
        } catch(Throwable e) { runtime.setError("发送按钮接口："+e.getClass().getSimpleName()); }
        // QQ/skin overrides can bypass Activity's base dispatch method. Observe
        // only the exact send_btn view as it receives the same physical gesture.
        try {
            XposedHelpers.findAndHookMethod(android.view.View.class,"dispatchTouchEvent",MotionEvent.class,new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    taps.sendViewEvent((android.view.View)p.thisObject,(MotionEvent)p.args[0]);
                }
            });
            runtime.tapInstalled=true;
        } catch(Throwable e) { runtime.setError("发送控件触摸接口："+e.getClass().getSimpleName()); }
    }
    private void installOwnBubble() {
        try {
            Class<?> svip=Class.forName("com.tencent.mobileqq.app.SVIPHandler",false,loader);
            XposedBridge.hookAllConstructors(svip,new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { runtime.decoration.observe(p.thisObject); }
            });
            int hooked=0;
            for(Method method:svip.getDeclaredMethods()) {
                String name=method.getName().toLowerCase(java.util.Locale.ROOT);
                if(method.getReturnType()!=int.class || !name.contains("bubbleid")) continue;
                boolean sub=name.contains("sub");
                XposedBridge.hookMethod(method,new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        // Observe the real account decoration; never fake a global getter.
                        runtime.decoration.observe(param.thisObject);
                        if(!param.hasThrowable() && param.getResult() instanceof Integer)
                            runtime.equipped(sub,(Integer)param.getResult());
                    }
                });
                hooked++;
            }
            runtime.log(hooked==0?"没有找到自己的气泡接口":"已挂上自己的气泡接口 "+hooked);
        } catch(Throwable e) { runtime.log("自己的气泡接口失败 "+e.getClass().getSimpleName()); }
    }
    private void installMenu() {
        if(runtime.menuInstalled) return;
        try { runtime.menuInstalled=menu.install(); }
        catch(Throwable e) { runtime.setError("消息菜单："+e.getClass().getSimpleName()); }
    }
    private void installSettings() {
        if(runtime.settingInstalled && settings!=null) return;
        try { settings.install(); }
        catch(Throwable e) { runtime.setError("QQ设置入口："+e.getClass().getSimpleName()); }
    }
    private synchronized void attemptHooks() {
        if(runtime==null || ++attempts>24) return;
        installMenu();
        installSettings();
        if(runtime.sendInstalled && runtime.forwardInstalled && runtime.recordInstalled && runtime.menuInstalled) attempts=100;
        try {
            Class<?> service=Class.forName("com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService$CppProxy",false,loader);
            for(Method m:service.getDeclaredMethods()) {
                Class<?>[] t=m.getParameterTypes();
                boolean mapArg=t.length>3 && java.util.Map.class.isAssignableFrom(t[3]);
                boolean listArg=t.length>2 && java.util.List.class.isAssignableFrom(t[2]);
                if(m.getName().equals("sendMsg") && t.length==5 && t[0]==long.class
                        && t[1].getSimpleName().equals("Contact")
                        && listArg && mapArg && !installed.contains(m)) {
                    final int mapIndex=3;
                    XposedBridge.hookMethod(m,new XC_MethodHook(XC_MethodHook.PRIORITY_LOWEST) {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            HashMap<Object,Object> replacement=runtime.prepare(p.args,Thread.currentThread().getStackTrace());
                            if(replacement!=null) p.args[mapIndex]=replacement;
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
