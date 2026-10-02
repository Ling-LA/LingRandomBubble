package io.github.ling.randombubble.hook;

import android.os.Looper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Observes the current QQ 9.3.50 photo queue without editing or sending native media. */
final class MediaQueueObserver {
    private static final String SELECTION_KEY="com.tencent.mobileqq.aio.event.InputEvent.GetSelectMediaInfo";
    private final Object lock=new Object();
    private final ThreadLocal<Probe> querying=new ThreadLocal<>();
    private final List<WeakReference<Snapshot>> snapshots=new ArrayList<>();
    private volatile boolean installed;
    private boolean attempted;
    private ClassLoader installedLoader;
    private Class<?> contextClass,vmClass,repositoryClass,resultClass,routeContract,proxyClass,messengerClass;
    private Method contextRoute,routeQuery,vmContext,messengerContext,requestName;
    private Field requestField,vmRepository,frameworkContext,proxyMessenger,messengerOwner,queryHandlers;
    private Object request;

    synchronized boolean install(ClassLoader loader) {
        if(attempted)return installed && installedLoader==loader;
        attempted=true;
        List<XC_MethodHook.Unhook> added=new ArrayList<>();
        try {
            contextClass=XposedHelpers.findClass("com.tencent.aio.runtime.AIOContextImpl",loader);
            vmClass=XposedHelpers.findClass("com.tencent.mobileqq.aio.panel.photo.PhotoPanelVM",loader);
            repositoryClass=XposedHelpers.findClass("com.tencent.mobileqq.album.media.AIOMediaRepository",loader);
            resultClass=XposedHelpers.findClass("com.tencent.mobileqq.aio.input.edit.b$i",loader);
            Class<?> runtimeApi=XposedHelpers.findClass("com.tencent.mvi.api.runtime.b",loader);
            Class<?> routeRoot=XposedHelpers.findClass("com.tencent.mvi.base.route.j",loader);
            routeContract=XposedHelpers.findClass("com.tencent.mvi.base.route.e",loader);
            Class<?> routeResult=XposedHelpers.findClass("com.tencent.mvi.base.route.k",loader);
            Class<?> intent=XposedHelpers.findClass("com.tencent.mvi.base.route.MsgIntent",loader);
            Class<?> selection=XposedHelpers.findClass("com.tencent.mobileqq.aio.event.InputEvent$GetSelectMediaInfo",loader);
            Class<?> framework=XposedHelpers.findClass("com.tencent.mvi.mvvm.framework.FrameworkVM",loader);
            Class<?> pair=XposedHelpers.findClass("kotlin.Pair",loader);
            proxyClass=XposedHelpers.findClass("com.tencent.aio.runtime.b",loader);
            messengerClass=XposedHelpers.findClass("com.tencent.mvi.base.route.VMMessenger",loader);
            Class<?> action=XposedHelpers.findClass("com.tencent.mvi.base.route.b",loader);
            Class<?> aioRuntime=XposedHelpers.findClass("com.tencent.aio.api.runtime.a",loader);
            Class<?> registerTask=XposedHelpers.findClass("com.tencent.mvi.base.route.VMMessenger$registerR$2",loader);
            Class<?> unregisterTask=XposedHelpers.findClass("com.tencent.mvi.base.route.VMMessenger$unregisterR$1",loader);

            contextRoute=runtimeApi.getDeclaredMethod("e");
            routeQuery=routeContract.getDeclaredMethod("k",intent);
            vmContext=framework.getDeclaredMethod("getMContext");
            requirePublicInstance(contextRoute,routeRoot,false);
            requirePublicInstance(routeQuery,routeResult,false);
            requirePublicInstance(vmContext,runtimeApi,true);
            frameworkContext=framework.getDeclaredField("mContext");
            vmRepository=vmClass.getDeclaredField("o");
            requireField(frameworkContext,runtimeApi);
            requireField(vmRepository,repositoryClass);
            frameworkContext.setAccessible(true);vmRepository.setAccessible(true);
            requestField=selection.getDeclaredField("d");
            int requestModifiers=requestField.getModifiers();
            if(requestField.getType()!=selection || !Modifier.isPublic(requestModifiers)
                || !Modifier.isStatic(requestModifiers) || !Modifier.isFinal(requestModifiers))throw new IllegalArgumentException("Unknown media query singleton");
            request=requestField.get(null);
            if(request==null || request.getClass()!=selection)throw new IllegalArgumentException("Missing media query singleton");
            requestName=selection.getDeclaredMethod("eventName");
            requirePublicInstance(requestName,String.class,false);
            if(!SELECTION_KEY.equals(requestName.invoke(request)))throw new IllegalArgumentException("Unknown media query name");

            proxyMessenger=proxyClass.getDeclaredField("a");
            messengerOwner=messengerClass.getDeclaredField("h");
            queryHandlers=messengerClass.getDeclaredField("d");
            requireFinalField(proxyMessenger,routeRoot);requireFinalField(messengerOwner,runtimeApi);
            requireFinalField(queryHandlers,ConcurrentHashMap.class);
            proxyMessenger.setAccessible(true);messengerOwner.setAccessible(true);queryHandlers.setAccessible(true);
            messengerContext=messengerClass.getDeclaredMethod("s");
            requirePublicInstance(messengerContext,runtimeApi,true);
            Method dispatcher=messengerClass.getDeclaredMethod("l",String.class,intent);
            requirePublicInstance(dispatcher,routeResult,false);
            Method register=messengerClass.getDeclaredMethod("j",String.class,action);
            Method unregister=messengerClass.getDeclaredMethod("a",String.class);
            requirePublicInstance(register,void.class,false);requirePublicInstance(unregister,void.class,false);
            Method create=vmClass.getDeclaredMethod("onCreate",aioRuntime);
            requirePublicInstance(create,void.class,false);
            Field registerOwner=taskField(registerTask,"this$0",messengerClass);
            Field registerKey=taskField(registerTask,"$msgType",String.class);
            Field unregisterOwner=taskField(unregisterTask,"this$0",messengerClass);
            Field unregisterKey=taskField(unregisterTask,"$msgType",String.class);

            Method handler=vmClass.getDeclaredMethod("S",vmClass,intent);
            int handlerModifiers=handler.getModifiers();
            if(handler.getReturnType()!=routeResult || !Modifier.isPublic(handlerModifiers)
                || !Modifier.isStatic(handlerModifiers) || !Modifier.isFinal(handlerModifiers))throw new IllegalArgumentException("Unknown media query handler");
            Method notify=repositoryClass.getDeclaredMethod("i",pair);
            int notifyModifiers=notify.getModifiers();
            if(notify.getReturnType()!=void.class || !Modifier.isPrivate(notifyModifiers)
                || !Modifier.isFinal(notifyModifiers) || Modifier.isStatic(notifyModifiers))throw new IllegalArgumentException("Unknown media change notifier");

            added.add(XposedBridge.hookMethod(handler,new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {beforeQuery(param);}
                @Override protected void afterHookedMethod(MethodHookParam param) {afterQuery(param);}
            }));
            added.add(XposedBridge.hookMethod(notify,new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {changed(param.thisObject);}
            }));
            added.add(XposedBridge.hookMethod(dispatcher,new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {beforeDispatch(param);}
                @Override protected void afterHookedMethod(MethodHookParam param) {afterDispatch(param);}
            }));
            XC_MethodHook registryHook=new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if(param.args!=null && param.args.length>0 && SELECTION_KEY.equals(param.args[0]))registryChanged(param.thisObject);
                }
            };
            added.add(XposedBridge.hookMethod(register,registryHook));
            added.add(XposedBridge.hookMethod(unregister,registryHook));
            // Observe the actual queued map write too: j/a can post these tasks from another thread.
            added.add(XposedBridge.hookMethod(taskInvoke(registerTask),taskHook(registerOwner,registerKey)));
            added.add(XposedBridge.hookMethod(taskInvoke(unregisterTask),taskHook(unregisterOwner,unregisterKey)));
            added.add(XposedBridge.hookMethod(create,new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if(param.args!=null && param.args.length==1)photoCreated(param.args[0]);
                }
            }));
            installedLoader=loader;installed=true;return true;
        } catch(Throwable unknown) {
            // Partial observers never authorize a capture, and are removed before returning.
            for(XC_MethodHook.Unhook hook:added)try {hook.unhook();}catch(Throwable ignored) {}
            installed=false;return false;
        }
    }

    private static void requirePublicInstance(Method method,Class<?> result,boolean finalRequired) {
        int flags=method.getModifiers();
        if(method.getReturnType()!=result || !Modifier.isPublic(flags) || Modifier.isStatic(flags)
            || (finalRequired && !Modifier.isFinal(flags)))throw new IllegalArgumentException("Unknown media owner method");
    }
    private static void requireField(Field field,Class<?> type) {
        if(field.getType()!=type || Modifier.isStatic(field.getModifiers()))throw new IllegalArgumentException("Unknown media owner field");
    }
    private static void requireFinalField(Field field,Class<?> type) {
        requireField(field,type);
        if(!Modifier.isFinal(field.getModifiers()))throw new IllegalArgumentException("Unknown media routing field");
    }
    private static Field taskField(Class<?> task,String name,Class<?> type) throws Exception {
        Field field=task.getDeclaredField(name);requireFinalField(field,type);field.setAccessible(true);return field;
    }
    private static Method taskInvoke(Class<?> task) {
        for(Method method:task.getDeclaredMethods()) {
            if(method.getName().equals("invoke") && method.getParameterTypes().length==0 && method.getReturnType()==void.class) {
                requirePublicInstance(method,void.class,true);return method;
            }
        }
        throw new IllegalArgumentException("Unknown media registry task");
    }
    private XC_MethodHook taskHook(Field owner,Field key) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {if(SELECTION_KEY.equals(key.get(param.thisObject)))registryChanged(owner.get(param.thisObject));}
                catch(Throwable unknown) {invalidateUninitialized();}
            }
        };
    }

    /** Null means unknown or selected media. Every successful capture must eventually close. */
    Snapshot capture(Object currentContext) {
        if(!installed || currentContext==null || currentContext.getClass()!=contextClass
            || Looper.myLooper()!=Looper.getMainLooper() || querying.get()!=null)return null;
        Probe probe=new Probe(currentContext,request);
        querying.set(probe);
        boolean retained=false;
        try {
            if(requestField.get(null)!=request || !SELECTION_KEY.equals(requestName.invoke(request)))return null;
            Object route=contextRoute.invoke(currentContext);
            if(route==null || !routeContract.isInstance(route))return null;
            probe.router=router(currentContext,route);
            if(probe.router!=null && !probe.router.handlers.containsKey(SELECTION_KEY)) {
                Snapshot initial=new Snapshot(this,currentContext,null,null,probe.router,true);
                if(!retain(initial))return null;
                probe.snapshot=initial;
            }
            Object result=routeQuery.invoke(route,request);
            Snapshot snapshot=probe.snapshot;
            if(snapshot!=null && snapshot.noHandler) {
                // Native E() takes its no-selection branch in this precisely observed absent-handler state.
                // An existing handler returning null or an unknown response never enters this branch.
                if(probe.invalid || probe.calls!=0 || probe.dispatchCalls!=1 || !probe.absentBefore
                    || !probe.absentAfter || result!=null || probe.dispatchResult!=null
                    || !currentOwner(snapshot) || !snapshot.live())return null;
                retained=true;return snapshot;
            }
            if(probe.invalid || probe.calls!=1 || snapshot==null || result==null || result!=probe.result
                || result.getClass()!=resultClass || !currentOwner(snapshot) || !MediaSelectionGate.knownEmpty(result)
                || !snapshot.live())return null;
            retained=true;return snapshot;
        } catch(Throwable unknown) {return null;}
        finally {
            querying.remove();
            if(!retained && probe.snapshot!=null)probe.snapshot.close();
        }
    }

    private Probe probe(XC_MethodHook.MethodHookParam param) {
        Probe value=querying.get();
        return installed && value!=null && param.args!=null && param.args.length==2
            && param.args[1]==value.request?value:null;
    }
    private void beforeQuery(XC_MethodHook.MethodHookParam param) {
        Probe probe=probe(param);if(probe==null)return;
        probe.calls++;
        if(probe.calls!=1) {probe.invalid=true;return;}
        try {
            Object vm=param.args[0];
            if(vm==null || vm.getClass()!=vmClass || vmContext.invoke(vm)!=probe.context || frameworkContext.get(vm)!=probe.context) {probe.invalid=true;return;}
            Object repo=vmRepository.get(vm);
            if(repo==null || repo.getClass()!=repositoryClass) {probe.invalid=true;return;}
            if(probe.snapshot!=null) {probe.invalid=true;return;}
            Snapshot snapshot=new Snapshot(this,probe.context,vm,repo,probe.router,false);
            // Register before QQ reads the queue. This also covers capture -> watch -> pending.
            if(!retain(snapshot)) {probe.invalid=true;return;}probe.snapshot=snapshot;
        } catch(Throwable unknown) {probe.invalid=true;}
    }
    private void afterQuery(XC_MethodHook.MethodHookParam param) {
        Probe probe=probe(param);if(probe==null)return;
        try {
            if(probe.invalid || probe.calls!=1 || probe.snapshot==null || param.hasThrowable()
                || probe.snapshot.vm.get()!=param.args[0] || !currentOwner(probe.snapshot)) {probe.invalid=true;return;}
            probe.result=param.getResult();
        } catch(Throwable unknown) {probe.invalid=true;}
    }
    private boolean currentOwner(Snapshot snapshot) {
        try {
            if(snapshot.noHandler) {
                Object context=snapshot.context.get(),route=snapshot.route.get(),messenger=snapshot.messenger.get(),map=snapshot.handlers.get();
                Router current=router(context,context==null?null:contextRoute.invoke(context));
                return current!=null && current.route==route && current.messenger==messenger && current.handlers==map
                    && !current.handlers.containsKey(SELECTION_KEY);
            }
            Object context=snapshot.context.get(),vm=snapshot.vm.get(),repo=snapshot.repository.get();
            return context!=null && vm!=null && repo!=null && vm.getClass()==vmClass && repo.getClass()==repositoryClass
                && vmContext.invoke(vm)==context && frameworkContext.get(vm)==context && vmRepository.get(vm)==repo;
        } catch(Throwable unknown) {return false;}
    }
    private Router router(Object context,Object route) {
        try {
            if(context==null || context.getClass()!=contextClass || route==null || route.getClass()!=proxyClass)return null;
            Object messenger=proxyMessenger.get(route);
            if(messenger==null || messenger.getClass()!=messengerClass || messengerOwner.get(messenger)!=context
                || messengerContext.invoke(messenger)!=context)return null;
            Object map=queryHandlers.get(messenger);
            if(map==null || map.getClass()!=ConcurrentHashMap.class)return null;
            return new Router(route,messenger,(ConcurrentHashMap<?,?>)map);
        } catch(Throwable unknown) {return null;}
    }
    private boolean retain(Snapshot snapshot) {
        synchronized(lock) {
            prune();
            if(snapshots.size()>=2) {snapshot.close();return false;}
            snapshots.add(new WeakReference<>(snapshot));return true;
        }
    }
    private void beforeDispatch(XC_MethodHook.MethodHookParam param) {
        Probe probe=querying.get();
        if(!installed || probe==null || param.args==null || param.args.length!=2 || param.args[1]!=request)return;
        probe.dispatchCalls++;
        if(probe.snapshot==null || !probe.snapshot.noHandler)return;
        Router owner=probe.router;
        if(probe.dispatchCalls!=1 || owner==null || param.thisObject!=owner.messenger || !SELECTION_KEY.equals(param.args[0])
            || !currentOwner(probe.snapshot)) {probe.invalid=true;return;}
        probe.absentBefore=true;
    }
    private void afterDispatch(XC_MethodHook.MethodHookParam param) {
        Probe probe=querying.get();
        if(!installed || probe==null || param.args==null || param.args.length!=2 || param.args[1]!=request
            || probe.snapshot==null || !probe.snapshot.noHandler)return;
        if(probe.invalid || probe.dispatchCalls!=1 || param.hasThrowable() || param.thisObject!=probe.router.messenger
            || !currentOwner(probe.snapshot)) {probe.invalid=true;return;}
        probe.absentAfter=true;probe.dispatchResult=param.getResult();
    }
    private void registryChanged(Object messenger) {
        if(!installed || messenger==null || messenger.getClass()!=messengerClass)return;
        synchronized(lock) {
            prune();
            for(WeakReference<Snapshot> reference:snapshots) {
                Snapshot snapshot=reference.get();
                if(snapshot!=null && snapshot.messenger.get()==messenger)snapshot.changed=true;
            }
        }
    }
    private void photoCreated(Object context) {
        if(!installed || context==null || context.getClass()!=contextClass)return;
        synchronized(lock) {
            prune();
            for(WeakReference<Snapshot> reference:snapshots) {
                Snapshot snapshot=reference.get();
                if(snapshot!=null && snapshot.noHandler && snapshot.context.get()==context)snapshot.changed=true;
            }
        }
    }
    private void invalidateUninitialized() {
        synchronized(lock) {
            for(WeakReference<Snapshot> reference:snapshots) {
                Snapshot snapshot=reference.get();if(snapshot!=null && snapshot.noHandler)snapshot.changed=true;
            }
        }
    }
    private void changed(Object repo) {
        if(!installed || repo==null || repo.getClass()!=repositoryClass)return;
        synchronized(lock) {
            prune();
            for(WeakReference<Snapshot> reference:snapshots) {
                Snapshot snapshot=reference.get();
                if(snapshot!=null && snapshot.repository.get()==repo)snapshot.changed=true;
            }
        }
    }
    /** Caller holds lock; no native values or media contents are consulted. */
    private void prune() {
        for(int index=snapshots.size()-1;index>=0;index--) {
            Snapshot snapshot=snapshots.get(index).get();
            if(snapshot==null || snapshot.closed)snapshots.remove(index);
        }
    }

    private static final class Probe {
        final Object context,request;
        int calls,dispatchCalls;
        boolean invalid,absentBefore,absentAfter;
        Snapshot snapshot;
        Object result,dispatchResult;
        Router router;
        Probe(Object context,Object request) {this.context=context;this.request=request;}
    }
    private static final class Router {
        final Object route,messenger;
        final ConcurrentHashMap<?,?> handlers;
        Router(Object route,Object messenger,ConcurrentHashMap<?,?> handlers) {this.route=route;this.messenger=messenger;this.handlers=handlers;}
    }
    static final class Snapshot {
        private final MediaQueueObserver observer;
        private final WeakReference<Object> context,vm,repository,route,messenger,handlers;
        private final boolean noHandler;
        private boolean changed,closed;
        private Snapshot(MediaQueueObserver observer,Object context,Object vm,Object repository,Router router,boolean noHandler) {
            this.observer=observer;this.context=new WeakReference<>(context);this.vm=new WeakReference<>(vm);this.repository=new WeakReference<>(repository);
            this.route=new WeakReference<>(router==null?null:router.route);this.messenger=new WeakReference<>(router==null?null:router.messenger);
            this.handlers=new WeakReference<>(router==null?null:router.handlers);this.noHandler=noHandler;
        }
        private boolean live() {
            synchronized(observer.lock) {
                return !closed && !changed && context.get()!=null
                    && (noHandler?route.get()!=null && messenger.get()!=null && handlers.get()!=null:vm.get()!=null && repository.get()!=null);
            }
        }
        boolean matches(Snapshot other) {
            if(other==null || observer!=other.observer)return false;
            synchronized(observer.lock) {
                Object owner=context.get(),viewModel=vm.get(),repo=repository.get();
                if(noHandler!=other.noHandler)return false;
                if(noHandler) {
                    return observer.installed && !closed && !changed && !other.closed && !other.changed && owner!=null
                        && owner==other.context.get() && route.get()!=null && route.get()==other.route.get()
                        && messenger.get()!=null && messenger.get()==other.messenger.get()
                        && handlers.get()!=null && handlers.get()==other.handlers.get();
                }
                return observer.installed && !closed && !changed && !other.closed && !other.changed
                    && owner!=null && viewModel!=null && repo!=null
                    && owner==other.context.get() && viewModel==other.vm.get() && repo==other.repository.get();
            }
        }
        void close() {
            synchronized(observer.lock) {
                if(closed)return;closed=true;
                context.clear();vm.clear();repository.clear();route.clear();messenger.clear();handlers.clear();observer.prune();
            }
        }
    }
}
