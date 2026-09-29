package io.github.ling.randombubble.hook;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import dalvik.system.BaseDexClassLoader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
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
    private Class<?> itemClass;
    private boolean hooked;
    SettingInject(HostRuntime runtime,ClassLoader loader) { this.runtime=runtime; this.loader=loader; }
    boolean install() {
        if(hooked) return true;
        List<Class<?>> providers=findProviders();
        XC_MethodHook hook=new XC_MethodHook(XC_MethodHook.PRIORITY_LOWEST) {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if(!(param.getResult() instanceof List) || param.args==null) return;
                Context context=null;
                for(Object arg:param.args) if(arg instanceof Context) context=(Context)arg;
                if(context==null) return;
                try { insert(context,(List<?>)param.getResult()); }
                catch(Throwable e) { runtime.setError("QQ设置入口："+e.getClass().getSimpleName()); runtime.log("设置入口失败 "+e.getClass().getSimpleName()); }
            }
        };
        int count=0;
        for(Class<?> provider:providers) {
            for(Class<?> type=provider; type!=null && type!=Object.class; type=type.getSuperclass()) {
                for(Method method:type.getDeclaredMethods()) {
                    if(!List.class.isAssignableFrom(method.getReturnType()) || method.getParameterTypes().length>3) continue;
                    boolean hasContext=false;
                    for(Class<?> param:method.getParameterTypes()) if(Context.class.isAssignableFrom(param)) hasContext=true;
                    if(!hasContext) continue;
                    XposedBridge.hookMethod(method,hook);
                    count++;
                }
            }
        }
        hooked=count>0;
        if(hooked) runtime.log("QQ设置钩子 "+count+" 个");
        else runtime.log("没有找到 QQ 设置配置类");
        return hooked;
    }
    @SuppressWarnings("unchecked")
    private void insert(Context context,List<?> result) throws ReflectiveOperationException {
        if(containsTitle(result,TITLE)) return;
        boolean settingsPage=containsTitle(result,"模块") || containsTitle(result,"QFun") || containsTitle(result,"消息通知") || containsTitle(result,"账号与安全");
        if(!settingsPage) return;
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
        bindClick(item,context);
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
        if(moduleGroup!=null) {
            int index=result.indexOf(moduleGroup);
            if(index>=0) ((List<Object>)result).set(index,group);
            else ((List<Object>)result).add(Math.min(1,result.size()),group);
        } else ((List<Object>)result).add(Math.min(1,result.size()),group);
        runtime.settingInstalled=true;
        runtime.log("已添加 QQ 设置模块入口");
    }
    private void bindClick(Object item,Context context) throws ReflectiveOperationException {
        Class<?> function=Class.forName("kotlin.jvm.functions.Function0",false,loader);
        Class<?> unitClass=Class.forName("kotlin.Unit",false,loader);
        Object unit=unitClass.getField("INSTANCE").get(null);
        Object proxy=Proxy.newProxyInstance(loader,new Class<?>[]{function},(InvocationHandler)(p,method,args) -> {
            String name=method.getName();
            if("invoke".equals(name)) { openSettings(context); return unit; }
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
                    return;
                }
            }
        }
        runtime.log("设置条目没有点击回调");
    }
    private static void openSettings(Context context) {
        Intent intent=new Intent();
        intent.setComponent(new ComponentName("io.github.ling.randombubble","io.github.ling.randombubble.ui.MainActivity"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
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
            if(params.length<4 || !List.class.isAssignableFrom(params[0]) || params[1]!=String.class || params[2]!=String.class || params[3]!=int.class) continue;
            Object[] args=new Object[params.length];
            args[0]=items; args[1]=title; args[2]=""; args[3]=Integer.valueOf(0);
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
    private List<Class<?>> findProviders() {
        List<Class<?>> found=new ArrayList<>();
        String[] known={
                "com.tencent.mobileqq.setting.main.NewSettingConfigProvider",
                "com.tencent.mobileqq.setting.main.MainSettingConfigProvider"
        };
        for(String name:known) addClass(found,name);
        Class<?> base=null;
        try { base=Class.forName("com.tencent.mobileqq.setting.processor.SettingConfigProvider",false,loader); found.add(base); }
        catch(ClassNotFoundException ignored) { /* older layout may use another base */ }
        for(String name:dexNames("com.tencent.mobileqq.setting.")) {
            boolean interesting=name.contains("Setting") || name.contains("Item") || name.contains("Processor");
            if(!interesting) continue;
            try {
                Class<?> type=Class.forName(name,false,loader);
                if(base!=null && base.isAssignableFrom(type) && !found.contains(type)) found.add(type);
                if(itemClass==null && looksLikeItem(type)) itemClass=type;
            } catch(Throwable ignored) { /* skip unloadable class */ }
        }
        runtime.log("设置类 "+found.size()+" 个"+(itemClass==null?"":" 条目类已找到"));
        return found;
    }
    private static boolean looksLikeItem(Class<?> type) {
        for(Constructor<?> ctor:type.getDeclaredConstructors()) {
            Class<?>[] params=ctor.getParameterTypes();
            if(params.length>=4 && Context.class.isAssignableFrom(params[0]) && params[1]==int.class && params[2]==String.class && params[3]==int.class) return true;
        }
        return false;
    }
    private void addClass(List<Class<?>> found,String name) {
        try { found.add(Class.forName(name,false,loader)); } catch(ClassNotFoundException ignored) { /* optional */ }
    }
    private List<String> dexNames(String prefix) {
        List<String> fromApk=scanApk("Lcom/tencent/mobileqq/setting/");
        if(!fromApk.isEmpty()) return fromApk;
        runtime.log("安装包里没有读到设置类，改查已加载的 dex");
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
    private static boolean containsTitle(Object value,String title) {
        return containsTitle(value,title,Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()),0);
    }
    private static boolean containsTitle(Object value,String title,Set<Object> seen,int depth) {
        if(value==null || depth>5 || !seen.add(value)) return false;
        if(value instanceof String) return title.equals(value);
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
