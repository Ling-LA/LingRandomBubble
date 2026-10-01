package io.github.ling.randombubble.hook;

import android.app.Application;
import android.content.Context;
import dalvik.system.BaseDexClassLoader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Adds this module to the same QQ settings group QFun uses, titled 模块. */
final class SettingInject {
    static final String TITLE="Ling 随机气泡";
    private final HostRuntime runtime;
    private final ClassLoader loader;
    private final Set<Class<?>> hookedProviders=new HashSet<>();
    private Class<?> itemClass;
    private boolean hooked;
    private boolean watchingLoads;
    private XC_MethodHook listHook;
    private Class<?> providerBase;
    private boolean providerBaseMissing;
    private int callbackLogs;
    private boolean groupHooked;
    private final io.github.ling.randombubble.core.IdentityWeakSet ownGroups=new io.github.ling.randombubble.core.IdentityWeakSet();
    private java.lang.ref.WeakReference<Context> lastContext=new java.lang.ref.WeakReference<>(null);
    private java.lang.ref.WeakReference<List<?>> lastSettingsList=new java.lang.ref.WeakReference<>(null);
    SettingInject(HostRuntime runtime,ClassLoader loader) { this.runtime=runtime; this.loader=loader; }
    boolean install() {
        if(!watchingLoads) watchLoads();
        if(hooked) return true;
        int count=attach(findProviders());
        hooked=true;
        runtime.log(count>0?"QQ设置钩子 "+count+" 个":"设置类上没有可用列表方法");
        return count>0;
    }
    private void watchLoads() {
        watchingLoads=true;
        XC_MethodHook watch=new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if(!(param.getResult() instanceof Class)) return;
                Class<?> type=(Class<?>)param.getResult();
                String name=type.getName();
                if(!name.startsWith("com.tencent.mobileqq.setting.")) return;
                if(hookedProviders.contains(type)) return;
                if(concreteProvider(type) || looksLikeItem(type)) considerLoaded(type);
            }
        };
        try { XposedBridge.hookAllMethods(ClassLoader.class,"loadClass",watch); }
        catch(Throwable e) { runtime.log("监听设置类加载失败 "+e.getClass().getSimpleName()); }
    }
    private void considerLoaded(Class<?> type) {
        if(hookedProviders.contains(type)) return;
        if(looksLikeItem(type) && itemClass==null) itemClass=type;
        runtime.log("加载设置类 "+type.getSimpleName());
        List<Class<?>> one=new ArrayList<>();
        one.add(type);
        int added=attach(one);
        if(added>0) runtime.log("QQ设置钩子 +"+added);
    }
    private int attach(List<Class<?>> providers) {
        XC_MethodHook hook=listHook();
        int count=0;
        for(Class<?> provider:providers) {
            if(provider==null || !hookedProviders.add(provider)) continue;
            for(Class<?> type=provider; type!=null && type!=Object.class; type=type.getSuperclass()) {
                for(Method method:type.getDeclaredMethods()) {
                    if(Modifier.isAbstract(method.getModifiers())) continue;
                    if(!List.class.isAssignableFrom(method.getReturnType()) || method.getParameterTypes().length>3) continue;
                    boolean hasContext=false;
                    for(Class<?> param:method.getParameterTypes()) if(Context.class.isAssignableFrom(param)) hasContext=true;
                    if(!hasContext) continue;
                    try { XposedBridge.hookMethod(method,hook); count++; }
                    catch(Throwable e) { hookFailure(method,e); }
                }
            }
        }
        return count;
    }
    private XC_MethodHook listHook() {
        if(listHook!=null) return listHook;
        // Xposed runs after callbacks in reverse priority order: inspect the final list
        // after ordinary settings contributors before deciding to create our own group.
        listHook=new XC_MethodHook(Integer.MAX_VALUE) {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if(!(param.getResult() instanceof List) || param.args==null) return;
                Context context=null;
                for(Object arg:param.args) if(arg instanceof Context) context=(Context)arg;
                if(context==null) return;
                if(callbackLogs<6) {
                    callbackLogs++;
                    runtime.log("设置回调 "+param.method.getDeclaringClass().getSimpleName()+"."+param.method.getName());
                }
                lastContext=new java.lang.ref.WeakReference<>(context);
                List<?> result=(List<?>)param.getResult();
                lastSettingsList=new java.lang.ref.WeakReference<>(result);
                try {
                    if(!result.isEmpty() && result.get(0)!=null) hookGroup(result.get(0).getClass());
                    result=deduplicate(result);
                    param.setResult(result);
                    lastSettingsList=new java.lang.ref.WeakReference<>(result);
                    insert(context,result);
                    result=deduplicate(result);
                    param.setResult(result);
                    lastSettingsList=new java.lang.ref.WeakReference<>(result);
                } catch(Throwable e) { runtime.log("设置入口失败 "+e.getClass().getSimpleName()); }
            }
        };
        return listHook;
    }
    /** Other modules may add the 模块 group after us; join it while it is being constructed. */
    private void hookGroup(Class<?> type) {
        if(groupHooked) return;
        XC_MethodHook hook=new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Object[] args=param.args;
                if(args==null || args.length<2 || !(args[0] instanceof List) || !(args[1] instanceof CharSequence)) return;
                if(!"模块".contentEquals((CharSequence)args[1])) return;
                try { args[0]=joinGroupItems((List<?>)args[0]); }
                catch(Throwable e) { runtime.log("加入模块分组失败 "+e.getClass().getSimpleName()); }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if(!param.hasThrowable() && containsTitle(param.thisObject,TITLE)) {
                    // The constructor may run before another module inserts its group in the list.
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if(containsTitle(lastSettingsList.get(),TITLE)) runtime.settingInstalled=true;
                    });
                }
            }
        };
        try { XposedBridge.hookAllConstructors(type,hook); groupHooked=true; runtime.log("已监听设置分组 "+type.getSimpleName()); }
        catch(Throwable e) { runtime.log("监听设置分组失败 "+e.getClass().getSimpleName()); }
    }
    private List<?> joinGroupItems(List<?> items) throws ReflectiveOperationException {
        if(containsTitle(items,TITLE)) return items;
        // Another contributor can construct a second 模块 group after our provider
        // callback already inserted a standalone group. One entry per complete page.
        if(containsTitle(lastSettingsList.get(),TITLE)) return items;
        Object sample=items.isEmpty()?null:items.get(0);
        Context context=sample==null?null:findContext(sample);
        if(context==null) context=lastContext.get();
        if(context==null) { runtime.log("模块分组没有可用上下文"); return items; }
        Object item=null;
        if(sample!=null) item=makeItem(sample.getClass(),context,TITLE);
        if(item==null && itemClass!=null) item=makeItem(itemClass,context,TITLE);
        if(item==null) { runtime.log("无法为模块分组创建条目"); return items; }
        if(!bindClick(item,context)) return items;
        List<Object> joined=new ArrayList<>(items);
        joined.add(item);
        return joined;
    }
    private static Context findContext(Object item) {
        for(Class<?> type=item.getClass(); type!=null && type!=Object.class; type=type.getSuperclass()) {
            for(Field field:type.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers()) || !Context.class.isAssignableFrom(field.getType())) continue;
                try { field.setAccessible(true); Object value=field.get(item); if(value instanceof Context) return (Context)value; }
                catch(Throwable ignored) { /* unreadable */ }
            }
        }
        return null;
    }
    @SuppressWarnings("unchecked")
    private void insert(Context context,List<?> result) throws ReflectiveOperationException {
        if(containsTitle(result,TITLE)) { runtime.settingInstalled=true; return; }
        boolean settingsPage=false;
        for(String mark:new String[]{"模块","功能","隐私","通用","消息通知","账号与安全","QFun"}) settingsPage|=containsTitle(result,mark);
        if(!settingsPage) {
            if(callbackLogs<6) runtime.log("设置列表未识别 "+titles(result));
            return;
        }
        Object moduleGroup=findGroup(result,"模块");
        Object sampleItem=moduleGroup==null?null:firstItem(moduleGroup);
        if(sampleItem==null) {
            for(Object group:result) {
                sampleItem=firstItem(group);
                if(sampleItem!=null) break;
            }
        }
        if(sampleItem==null) { runtime.log("设置列表里没有可复制的条目"); return; }
        Object item=makeItem(sampleItem.getClass(),context,TITLE);
        if(item==null && itemClass!=null) item=makeItem(itemClass,context,TITLE);
        if(item==null) { runtime.log("无法创建 QQ 设置条目"); return; }
        if(!bindClick(item,context)) return;
        if(moduleGroup!=null) {
            List<Object> items=itemList(moduleGroup);
            if(items!=null) {
                try { items.add(item); runtime.settingInstalled=true; runtime.log("已加入 QQ 设置的模块分组"); return; }
                catch(UnsupportedOperationException ignored) { /* rebuild the group below */ }
            }
        }
        Object template=moduleGroup!=null?moduleGroup:(result.isEmpty()?null:result.get(0));
        if(template==null) return;
        List<Object> items=new ArrayList<>();
        if(moduleGroup!=null) {
            List<Object> current=itemList(moduleGroup);
            if(current!=null) items.addAll(current);
        }
        items.add(item);
        Object group=newGroup(template,items,"模块");
        if(group==null) { runtime.log("无法创建模块分组"); return; }
        if(moduleGroup==null || ownGroups.contains(moduleGroup)) ownGroups.add(group);
        if(moduleGroup!=null) {
            int index=result.indexOf(moduleGroup);
            if(index>=0) ((List<Object>)result).set(index,group);
            else ((List<Object>)result).add(Math.min(1,result.size()),group);
        } else ((List<Object>)result).add(Math.min(1,result.size()),group);
        runtime.settingInstalled=true;
        runtime.log("已添加 QQ 设置模块入口");
    }
    /** Deduplicate the complete result, not just a constructor's temporary item list. */
    private List<?> deduplicate(List<?> groups) throws ReflectiveOperationException {
        Object retainedGroup=null;
        int retainedIndex=-1, bestScore=Integer.MAX_VALUE, count=0;
        for(Object group:groups) {
            List<Object> items=itemList(group);
            if(items==null) continue;
            int score=containsTitle(group,"QFun")?0:(ownGroups.contains(group)?2:1);
            for(int i=0;i<items.size();i++) if(containsTitle(items.get(i),TITLE)) {
                count++;
                if(score<bestScore) {bestScore=score;retainedGroup=group;retainedIndex=i;}
            }
        }
        if(count<=1) return groups;
        List<Object> result=new ArrayList<>(groups);
        for(int g=result.size()-1;g>=0;g--) {
            Object group=result.get(g);
            List<Object> items=itemList(group);
            if(items==null) continue;
            List<Object> kept=new ArrayList<>(items.size());
            for(int i=0;i<items.size();i++) {
                Object item=items.get(i);
                if(!containsTitle(item,TITLE) || (group==retainedGroup && i==retainedIndex)) kept.add(item);
            }
            if(kept.size()==items.size()) continue;
            // A singleton Ling group is also recognized if a cached result predates our tracking.
            boolean ours=ownGroups.contains(group) || (items.size()==1 && containsTitle(group,"模块") && containsTitle(items.get(0),TITLE));
            if(kept.isEmpty() && ours) result.remove(g);
            else replaceItems(group,items,kept);
        }
        runtime.settingInstalled=containsTitle(result,TITLE);
        runtime.log("QQ 设置入口去重，保留已有模块分组中的一条");
        return result;
    }
    /** Preserve other modules' group instance, header and items, including immutable lists. */
    private static void replaceItems(Object group,List<Object> previous,List<Object> replacement) throws ReflectiveOperationException {
        for(Class<?> type=group.getClass();type!=null && type!=Object.class;type=type.getSuperclass()) {
            for(Field field:type.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                if(field.get(group)==previous) {field.set(group,replacement);return;}
            }
        }
        throw new NoSuchFieldException("设置分组条目列表");
    }
    private boolean bindClick(Object item,Context context) throws ReflectiveOperationException {
        Class<?> function=Class.forName("kotlin.jvm.functions.Function0",false,loader);
        Class<?> unitClass=Class.forName("kotlin.Unit",false,loader);
        Object unit=unitClass.getField("INSTANCE").get(null);
        Object proxy=Proxy.newProxyInstance(loader,new Class<?>[]{function},(InvocationHandler)(p,method,args) -> {
            String name=method.getName();
            if("invoke".equals(name)) { open(context); return unit; }
            if("toString".equals(name)) return "LingBubbleSettings";
            if("hashCode".equals(name)) return System.identityHashCode(p);
            if("equals".equals(name)) return args!=null && args.length>0 && p==args[0];
            return unit;
        });
        for(Class<?> type=item.getClass(); type!=null && type!=Object.class; type=type.getSuperclass()) {
            for(Method method:type.getDeclaredMethods()) {
                Class<?>[] params=method.getParameterTypes();
                if(params.length==1 && function.isAssignableFrom(params[0]) && method.getReturnType()==void.class) {
                    method.setAccessible(true);
                    method.invoke(item,proxy);
                    return true;
                }
            }
        }
        runtime.log("设置条目没有点击回调");
        return false;
    }
    /** All QQ entries open the in-process panel, including embedded-only LSPatch installs. */
    private void open(Context context) {
        android.app.Activity activity=activityOf(context);
        if(activity==null) activity=runtime.currentActivity();
        if(activity==null) { runtime.log("没有可用的界面打开设置面板"); return; }
        runtime.log("打开模块设置面板");
        SettingsPanel.show(activity,runtime);
    }
    private static android.app.Activity activityOf(Context context) {
        for(Context c=context; c!=null; c=c instanceof android.content.ContextWrapper?((android.content.ContextWrapper)c).getBaseContext():null) {
            if(c instanceof android.app.Activity) return (android.app.Activity)c;
        }
        return null;
    }
    private static Object makeItem(Class<?> type,Context context,String title) {
        int icon=context.getResources().getIdentifier("qui_setting","drawable",context.getPackageName());
        Object[][] attempts={
                {context,Integer.valueOf(10),title,Integer.valueOf(icon),null},
                {context,Integer.valueOf(10),title,Integer.valueOf(icon)}
        };
        for(Object[] args:attempts) {
            Constructor<?> ctor=match(type,args);
            if(ctor==null) continue;
            try { ctor.setAccessible(true); return ctor.newInstance(args); }
            catch(Throwable ignored) { /* next signature */ }
        }
        return null;
    }
    private static Object newGroup(Object sample,List<Object> items,String title) {
        for(Constructor<?> ctor:sample.getClass().getDeclaredConstructors()) {
            Class<?>[] params=ctor.getParameterTypes();
            if(params.length<4 || !List.class.isAssignableFrom(params[0]) || !titleParameter(params[1]) || !titleParameter(params[2]) || params[3]!=int.class) continue;
            Object[] args=new Object[params.length];
            args[0]=items; args[1]=title; args[2]=""; args[3]=Integer.valueOf(0);
            boolean supported=true;
            for(int i=4;i<params.length;i++) if(params[i].isPrimitive()) {
                if(params[i]==int.class) args[i]=Integer.valueOf(0);
                else { supported=false; break; }
            }
            if(!supported) continue;
            try { ctor.setAccessible(true); return ctor.newInstance(args); }
            catch(Throwable ignored) { /* next constructor */ }
        }
        return null;
    }
    private static Constructor<?> match(Class<?> type,Object[] args) {
        for(Constructor<?> ctor:type.getDeclaredConstructors()) {
            Class<?>[] params=ctor.getParameterTypes();
            if(params.length!=args.length) continue;
            boolean ok=true;
            for(int i=0;i<params.length;i++) {
                if(args[i]==null) { if(params[i].isPrimitive()) ok=false; }
                else if(!params[i].isAssignableFrom(args[i].getClass()) && !(params[i]==int.class && args[i] instanceof Integer)) ok=false;
            }
            if(ok) return ctor;
        }
        return null;
    }
    private Class<?> providerBase() {
        if(providerBase!=null || providerBaseMissing) return providerBase;
        try { providerBase=Class.forName("com.tencent.mobileqq.setting.processor.SettingConfigProvider",false,loader); }
        catch(ClassNotFoundException e) { providerBaseMissing=true; }
        return providerBase;
    }
    private boolean concreteProvider(Class<?> type) {
        if(type==null || type.isInterface() || Modifier.isAbstract(type.getModifiers())) return false;
        Class<?> base=providerBase();
        return base!=null && type!=base && base.isAssignableFrom(type);
    }
    private void hookFailure(Method method,Throwable e) {
        String msg=e.getMessage()==null?"":e.getMessage().replace('\n',' ');
        if(msg.length()>80) msg=msg.substring(0,80);
        runtime.log("设置方法钩子失败 "+method.getDeclaringClass().getSimpleName()+"."+method.getName()+" "+e.getClass().getSimpleName()+" "+msg);
    }
    private List<Class<?>> findProviders() {
        List<Class<?>> found=new ArrayList<>();
        String[] known={
                "com.tencent.mobileqq.setting.main.NewSettingConfigProvider",
                "com.tencent.mobileqq.setting.main.MainSettingConfigProvider",
                "com.tencent.mobileqq.setting.main.b"
        };
        for(String name:known) rememberProvider(found,name);
        try {
            Class<?> type=Class.forName("com.tencent.mobileqq.setting.processor.i",false,loader);
            if(looksLikeItem(type)) itemClass=type;
        } catch(Throwable ignored) { /* optional version-specific simple item */ }
        List<String> names=settingClassNames();
        int examined=0;
        for(String name:names) {
            String lower=name.toLowerCase(Locale.ROOT);
            if(!(lower.contains(".setting.main.") || lower.contains("settingconfig") || lower.contains("itemprocessor") || lower.contains("simpleitem"))) continue;
            examined++;
            try {
                Class<?> type=Class.forName(name,false,loader);
                if(concreteProvider(type) && !found.contains(type)) found.add(type);
                if(itemClass==null && looksLikeItem(type)) itemClass=type;
            } catch(Throwable ignored) { /* skip unloadable class */ }
        }
        StringBuilder ids=new StringBuilder();
        for(Class<?> type:found) { if(ids.length()>0) ids.append(' '); ids.append(type.getSimpleName()); }
        runtime.log("设置候选 "+examined+" 个，具体设置类 "+found.size()+" 个 "+ids+(itemClass==null?"":"，条目类已找到"));
        return found;
    }
    private void rememberProvider(List<Class<?>> found,String name) {
        try {
            Class<?> type=Class.forName(name,false,loader);
            if(concreteProvider(type) && !found.contains(type)) found.add(type);
        } catch(ClassNotFoundException ignored) { /* optional */ }
    }
    private List<String> settingClassNames() {
        LinkedHashSet<String> names=new LinkedHashSet<>();
        names.addAll(scanApk("Lcom/tencent/mobileqq/setting/"));
        names.addAll(dexEntries("com.tencent.mobileqq.setting."));
        if(names.isEmpty()) runtime.log("没有从安装包或已加载 dex 读到设置类");
        return new ArrayList<>(names);
    }
    private static boolean looksLikeItem(Class<?> type) {
        for(Constructor<?> ctor:type.getDeclaredConstructors()) {
            Class<?>[] params=ctor.getParameterTypes();
            if(params.length>=4 && Context.class.isAssignableFrom(params[0]) && params[1]==int.class && titleParameter(params[2]) && params[3]==int.class) return true;
        }
        return false;
    }
    private static boolean titleParameter(Class<?> type) { return type==String.class || type==CharSequence.class; }
    private List<String> dexEntries(String prefix) {
        List<String> names=new ArrayList<>();
        for(ClassLoader current=loader; current!=null; current=current.getParent()) {
            if(!(current instanceof BaseDexClassLoader)) continue;
            try {
                Field pathField=BaseDexClassLoader.class.getDeclaredField("pathList");
                pathField.setAccessible(true);
                Object path=pathField.get(current);
                Field elementsField=path.getClass().getDeclaredField("dexElements");
                elementsField.setAccessible(true);
                Object[] elements=(Object[])elementsField.get(path);
                for(Object element:elements) {
                    Field dexField=element.getClass().getDeclaredField("dexFile");
                    dexField.setAccessible(true);
                    Object dex=dexField.get(element);
                    if(dex==null) continue;
                    Method entries=dex.getClass().getMethod("entries");
                    @SuppressWarnings("unchecked") Enumeration<String> values=(Enumeration<String>)entries.invoke(dex);
                    while(values.hasMoreElements()) {
                        String name=values.nextElement();
                        if(name.startsWith(prefix)) names.add(name);
                    }
                }
            } catch(Throwable ignored) { /* this loader has no dex list */ }
        }
        return names;
    }
    private List<String> scanApk(String descriptorPrefix) {
        HashSet<String> names=new HashSet<>();
        try {
            Application app=(Application)Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            ArrayList<String> apks=new ArrayList<>();
            if(app!=null) {
                apks.add(app.getPackageCodePath());
                apks.add(app.getApplicationInfo().publicSourceDir);
                apks.add(app.getApplicationInfo().sourceDir);
                String[] splits=app.getApplicationInfo().splitSourceDirs;
                if(splits!=null) for(String split:splits) apks.add(split);
            }
            byte[] needle=descriptorPrefix.getBytes(StandardCharsets.UTF_8);
            for(String apk:apks) {
                if(apk==null) continue;
                try(ZipFile zip=new ZipFile(new File(apk))) {
                    Enumeration<? extends ZipEntry> entries=zip.entries();
                    while(entries.hasMoreElements()) {
                        ZipEntry entry=entries.nextElement();
                        if(!entry.getName().endsWith(".dex") || entry.getSize()>80L*1024L*1024L) continue;
                        byte[] data=read(zip.getInputStream(entry));
                        int from=0;
                        while(from<data.length) {
                            int at=indexOf(data,needle,from);
                            if(at<0) break;
                            int end=at;
                            while(end<data.length && data[end]!=';') end++;
                            if(end<data.length && end>at+1) names.add(new String(data,at+1,end-at-1,StandardCharsets.UTF_8).replace('/','.'));
                            from=at+needle.length;
                        }
                    }
                }
            }
        } catch(Throwable e) { runtime.log("扫描 QQ 安装包失败 "+e.getClass().getSimpleName()); }
        return new ArrayList<>(names);
    }
    private static byte[] read(InputStream in) throws java.io.IOException {
        try(InputStream source=in; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n;
            while((n=source.read(buffer))>=0) out.write(buffer,0,n);
            return out.toByteArray();
        }
    }
    private static int indexOf(byte[] data,byte[] needle,int from) {
        outer: for(int i=from;i<=data.length-needle.length;i++) {
            for(int j=0;j<needle.length;j++) if(data[i+j]!=needle[j]) continue outer;
            return i;
        }
        return -1;
    }
    private static String titles(Object value) {
        StringBuilder out=new StringBuilder();
        collectTitles(value,out,Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()),0);
        return out.length()==0?"无标题":out.toString();
    }
    private static void collectTitles(Object value,StringBuilder out,Set<Object> seen,int depth) {
        if(value==null || depth>4 || out.length()>48 || !seen.add(value)) return;
        if(value instanceof CharSequence) {
            String text=value.toString();
            if(text.length()>0 && text.length()<=12) { if(out.length()>0) out.append(' '); out.append(text); }
            return;
        }
        if(value instanceof List) { for(Object child:(List<?>)value) collectTitles(child,out,seen,depth+1); return; }
        String name=value.getClass().getName();
        if(name.startsWith("java.") || name.startsWith("android.") || name.startsWith("kotlin.")) return;
        for(Class<?> type=value.getClass(); type!=null && type!=Object.class; type=type.getSuperclass()) {
            for(Field field:type.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers())) continue;
                try { field.setAccessible(true); collectTitles(field.get(value),out,seen,depth+1); }
                catch(Throwable ignored) { /* unreadable */ }
            }
        }
    }
    private static boolean containsTitle(Object value,String title) {
        return containsTitle(value,title,Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()),0);
    }
    private static boolean containsTitle(Object value,String title,Set<Object> seen,int depth) {
        if(value==null || depth>5 || !seen.add(value)) return false;
        if(value instanceof CharSequence) {
            String text=value.toString();
            return text.length()<=24 && title.equals(text);
        }
        if(value instanceof List) { for(Object child:(List<?>)value) if(containsTitle(child,title,seen,depth+1)) return true; return false; }
        String name=value.getClass().getName();
        if(name.startsWith("java.") || name.startsWith("android.") || name.startsWith("kotlin.")) return false;
        for(Class<?> type=value.getClass(); type!=null && type!=Object.class; type=type.getSuperclass()) {
            for(Field field:type.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    if(containsTitle(field.get(value),title,seen,depth+1)) return true;
                } catch(Throwable ignored) { /* unreadable */ }
            }
        }
        return false;
    }
    private static Object findGroup(List<?> groups,String title) {
        for(Object group:groups) if(containsTitle(group,title)) return group;
        return null;
    }
    @SuppressWarnings("unchecked")
    private static List<Object> itemList(Object group) throws IllegalAccessException {
        if(group==null) return null;
        for(Class<?> type=group.getClass(); type!=null && type!=Object.class; type=type.getSuperclass()) {
            for(Field field:type.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value=field.get(group);
                if(value instanceof List && !((List<?>)value).isEmpty()) return (List<Object>)value;
            }
        }
        return null;
    }
    private static Object firstItem(Object group) {
        try { List<Object> items=itemList(group); return items==null||items.isEmpty()?null:items.get(0); }
        catch(Throwable e) { return null; }
    }
}
