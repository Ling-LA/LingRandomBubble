package io.github.ling.randombubble.hook;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.ling.randombubble.core.BubbleSpec;
import io.github.ling.randombubble.core.Reflect;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * QQ draws only the items inside the menu object passed to setMenu.
 * A view added afterwards is not part of that row, so 0.1.2 never showed a button.
 * This inserts one real item and then takes over only that item's click.
 */
final class MenuCollector {
    static final String LABEL="收藏气泡";
    private final HostRuntime runtime;
    private final ClassLoader loader;
    private final Set<Method> viewHooks=new HashSet<>();
    private final Set<Class<?>> clickHooked=new HashSet<>();
    private final WeakHashMap<View,Object> aioByMenu=new WeakHashMap<>();
    private Class<?> layoutClass;
    MenuCollector(HostRuntime runtime,ClassLoader loader) { this.runtime=runtime; this.loader=loader; }
    boolean install() throws Throwable {
        layoutClass=Class.forName("com.tencent.qqnt.aio.menu.ui.QQCustomMenuExpandableLayout",false,loader);
        XC_MethodHook hook=new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if(!(param.thisObject instanceof View) || param.args==null || param.args.length==0 || param.args[0]==null) return;
                try { inject((View)param.thisObject,param.args[0]); }
                catch(Throwable e) { runtime.setError("消息菜单："+e.getClass().getSimpleName()); }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if(!(param.thisObject instanceof View)) return;
                View layout=(View)param.thisObject;
                bind(layout);
                layout.post(() -> bind(layout));
                layout.postDelayed(() -> bind(layout),80);
            }
        };
        boolean any=false;
        for(Class<?> type=layoutClass; type!=null && type!=Object.class; type=type.getSuperclass()) {
            Set<?> hooks=XposedBridge.hookAllMethods(type,"setMenu",hook);
            any|=hooks!=null && !hooks.isEmpty();
        }
        return any;
    }
    @SuppressWarnings("unchecked")
    private void inject(View layout,Object menu) throws ReflectiveOperationException {
        List<Object> items=findItemList(menu);
        if(items==null || items.isEmpty()) return;
        Object sample=items.get(0);
        Class<?> aioType=Class.forName("com.tencent.mobileqq.aio.msg.AIOMsgItem",false,loader);
        Object aio=findAio(sample,aioType);
        if(aio==null) { runtime.setError("消息菜单：找不到消息对象"); return; }
        aioByMenu.put(layout,aio);
        if(ensure(items,sample,aio,layout,LABEL)) runtime.menuInjected++;
        hookItemViews(sample.getClass());
        hookItemClicks(sample.getClass());
    }
    private void hookItemClicks(Class<?> itemClass) {
        if(!clickHooked.add(itemClass)) return;
        for(Class<?> type=itemClass; type!=null && type!=Object.class; type=type.getSuperclass()) {
            for(Method method:type.getDeclaredMethods()) {
                String name=method.getName().toLowerCase(java.util.Locale.ROOT);
                if(!name.contains("click") || method.getParameterTypes().length>2) continue;
                XposedBridge.hookMethod(method,new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if(param.thisObject==null || !hasText(param.thisObject,LABEL)) return;
                        Context context=findContext(param.thisObject);
                        Object aio=findAio(param.thisObject,aioTypeQuiet());
                        if(context!=null && aio!=null) collect(context,aio);
                    }
                });
            }
        }
    }
    private static Context findContext(Object item) {
        for(Class<?> type=item.getClass(); type!=null && type!=Object.class; type=type.getSuperclass()) {
            for(java.lang.reflect.Field field:type.getDeclaredFields()) {
                if(!Context.class.isAssignableFrom(field.getType())) continue;
                try { field.setAccessible(true); Object value=field.get(item); if(value instanceof Context) return (Context)value; }
                catch(Throwable ignored) { /* unreadable */ }
            }
        }
        return null;
    }
    private boolean ensure(List<Object> items,Object sample,Object aio,View layout,String text) throws ReflectiveOperationException {
        if(hasLabel(items,text)) return false;
        Object created=newItem(sample.getClass(),activityContext(layout),aio);
        if(created==null) { runtime.setError("消息菜单：无法创建"+text); return false; }
        label(created,text);
        items.add(created);
        return true;
    }
    private void bind(View layout) {
        Object aio=aioByMenu.get(menuRoot(layout));
        if(aio==null) aio=aioByMenu.get(layout);
        bindText(layout,layout,aio);
    }
    private void bindText(View root,View layout,Object aio) {
        if(root instanceof TextView) {
            String text=((TextView)root).getText()==null?"":((TextView)root).getText().toString();
            if(LABEL.equals(text)) {
                View target=clickableRow(root);
                target.setOnClickListener(v -> onItem(layout,aio));
                target.setOnTouchListener((v,event) -> {
                    if(event.getActionMasked()!=MotionEvent.ACTION_UP) return false;
                    onItem(layout,aio);
                    return true;
                });
            }
        }
        if(root instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++) bindText(group.getChildAt(i),layout,aio);
        }
    }
    private void onItem(View layout,Object aio) {
        if(aio!=null) collect(layout,aio);
        else Toast.makeText(layout.getContext().getApplicationContext(),"没有读到这条消息",Toast.LENGTH_SHORT).show();
        try { XposedHelpers.callMethod(menuRoot(layout),"dismiss"); } catch(Throwable ignored) { /* menu can stay open */ }
    }
    private static View clickableRow(View text) {
        View current=text;
        while(current.getParent() instanceof View) {
            View parent=(View)current.getParent();
            if(parent.getClass().getName().contains("QQCustomMenu")) break;
            if(countLabels(parent)>1) break;
            current=parent;
        }
        current.setClickable(true);
        return current;
    }
    private static int countLabels(View view) {
        int count=0;
        if(view instanceof TextView) {
            String text=((TextView)view).getText()==null?"":((TextView)view).getText().toString();
            if(LABEL.equals(text)) count++;
        }
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) count+=countLabels(group.getChildAt(i));
        }
        return count;
    }
    private static View menuRoot(View view) {
        View current=view;
        while(current.getParent() instanceof View) {
            View parent=(View)current.getParent();
            if(parent.getClass().getName().contains("QQCustomMenu")) return parent;
            current=parent;
        }
        return view;
    }
    private void hookItemViews(Class<?> itemClass) {
        for(Class<?> type=layoutClass; type!=null && type!=Object.class; type=type.getSuperclass()) {
            for(Method method:type.getDeclaredMethods()) {
                if(viewHooks.contains(method) || !View.class.isAssignableFrom(method.getReturnType())) continue;
                boolean takesItem=false;
                for(Class<?> p:method.getParameterTypes()) {
                    if(p.isPrimitive() || p.isArray() || p==String.class) continue;
                    String name=p.getName();
                    if(name.startsWith("java.") || name.startsWith("android.")) continue;
                    if(p.isAssignableFrom(itemClass)) takesItem=true;
                }
                if(!takesItem) continue;
                XposedBridge.hookMethod(method,new XC_MethodHook(XC_MethodHook.PRIORITY_LOWEST) {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        Object item=null;
                        if(param.args!=null) for(Object arg:param.args) if(itemClass.isInstance(arg)) item=arg;
                        if(item==null || !hasText(item,LABEL) || !(param.getResult() instanceof View)) return;
                        View view=(View)param.getResult();
                        Object aio=findAio(item,aioTypeQuiet());
                        retitle(view,LABEL);
                        if(aio!=null) arm(view,outerLayout(view),aio);
                    }
                });
                viewHooks.add(method);
            }
        }
    }
    private Class<?> aioTypeQuiet() {
        try { return Class.forName("com.tencent.mobileqq.aio.msg.AIOMsgItem",false,loader); }
        catch(ClassNotFoundException e) { return null; }
    }
    private void collect(View layout,Object aio) { collect(layout.getContext(),aio); try { XposedHelpers.callMethod(menuRoot(layout),"dismiss"); } catch(Throwable ignored) {} }
    private void collect(Context context,Object aio) {
        String message;
        try {
            Object record=XposedHelpers.callMethod(aio,"getMsgRecord");
            runtime.observeRecord(record);
            List<BubbleSpec> specs=runtime.adapter.extract(Reflect.get(record,"msgAttrs"));
            if(specs.isEmpty()) message="这条消息没有可收藏的气泡";
            else {
                String status=runtime.bridge.favorite(specs.get(0));
                if("added".equals(status)) message="已收藏。请到设置勾选，并手动开启发送";
                else if("selected".equals(status)) message="这个气泡已在库中；发送开关保持原样";
                else message="收藏没有写入："+status;
            }
        } catch(Throwable e) {
            runtime.setError("收藏气泡："+e.getClass().getSimpleName());
            message="收藏失败，这条消息没有被改动";
        }
        Toast.makeText(context.getApplicationContext(),message,Toast.LENGTH_SHORT).show();
    }
    private void arm(View root,View layout,Object aio) {
        root.setClickable(true);
        root.setOnClickListener(v -> collect(layout,aio));
        if(root instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++) arm(group.getChildAt(i),layout,aio);
        }
    }
    private static View outerLayout(View view) {
        View current=view;
        while(current.getParent() instanceof View) {
            View parent=(View)current.getParent();
            if(parent.getClass().getName().contains("QQCustomMenu")) return parent;
            current=parent;
        }
        return view;
    }
    private static Context activityContext(View layout) {
        Context context=layout.getContext();
        while(context instanceof ContextWrapper && !(context instanceof Activity)) {
            Context base=((ContextWrapper)context).getBaseContext();
            if(base==null || base==context) break;
            context=base;
        }
        return context;
    }
    private static void retitle(View view,String text) {
        if(view instanceof TextView) ((TextView)view).setText(text);
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) retitle(group.getChildAt(i),text);
        }
    }
    @SuppressWarnings("unchecked")
    private static List<Object> findItemList(Object menu) throws IllegalAccessException {
        if(menu instanceof List) return (List<Object>)menu;
        for(Class<?> c=menu.getClass(); c!=null && c!=Object.class; c=c.getSuperclass()) {
            for(Field field:c.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value=field.get(menu);
                if(value instanceof List && !((List<?>)value).isEmpty()) return (List<Object>)value;
            }
        }
        return null;
    }
    private static boolean hasLabel(List<?> items,String text) {
        for(Object item:items) if(hasText(item,text)) return true;
        return false;
    }
    private static boolean hasText(Object item,String expected) {
        if(item==null) return false;
        for(Class<?> c=item.getClass(); c!=null && c!=Object.class; c=c.getSuperclass()) {
            for(Field field:c.getDeclaredFields()) {
                if(field.getType()!=String.class || Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    if(expected.equals(field.get(item))) return true;
                } catch(Throwable ignored) { /* unreadable field */ }
            }
        }
        return false;
    }
    private static Object findAio(Object item,Class<?> aioType) {
        if(item==null || aioType==null) return null;
        for(Class<?> c=item.getClass(); c!=null && c!=Object.class; c=c.getSuperclass()) {
            for(Field field:c.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    Object value=field.get(item);
                    if(aioType.isInstance(value)) return value;
                } catch(Throwable ignored) { /* unreadable field */ }
            }
        }
        return null;
    }
    private static Object newItem(Class<?> type,Context context,Object aio) {
        for(Constructor<?> ctor:type.getDeclaredConstructors()) {
            Class<?>[] params=ctor.getParameterTypes();
            if(params.length!=2) continue;
            Object[] args=argsFor(params,context,aio);
            if(args==null) continue;
            try { ctor.setAccessible(true); return ctor.newInstance(args); }
            catch(Throwable ignored) { /* try the other constructor */ }
        }
        return null;
    }
    private static Object[] argsFor(Class<?>[] params,Context context,Object aio) {
        Object[] args=new Object[2];
        boolean contextUsed=false,aioUsed=false;
        for(int i=0;i<2;i++) {
            if(!contextUsed && params[i].isAssignableFrom(context.getClass())) { args[i]=context; contextUsed=true; }
            else if(!aioUsed && params[i].isAssignableFrom(aio.getClass())) { args[i]=aio; aioUsed=true; }
            else return null;
        }
        return contextUsed && aioUsed ? args : null;
    }
    private static void label(Object item,String text) throws IllegalAccessException {
        for(Class<?> c=item.getClass(); c!=null && c!=Object.class; c=c.getSuperclass()) {
            for(Field field:c.getDeclaredFields()) {
                if(field.getType()!=String.class || Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object current=field.get(item);
                if(current==null || ((String)current).length()<=16) field.set(item,text);
            }
        }
    }
}
