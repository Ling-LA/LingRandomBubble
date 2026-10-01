package io.github.ling.randombubble.hook;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.PowerManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.text.Editable;
import android.text.Selection;
import android.text.Spanned;
import android.text.SpanWatcher;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import io.github.ling.randombubble.core.ActiveConversationChain;
import io.github.ling.randombubble.core.ConversationMatch;
import io.github.ling.randombubble.core.SendReplayGuard;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** Delays an explicitly enabled native text-send click until the official decoration is confirmed.
 * Never replaces QQ listeners, edits input, or invokes the kernel message API.
 */
final class UiTapGate {
    private static final long CLICK_MS=1500L, REPLAY_MS=45000L;
    private final HostRuntime runtime;
    private WeakReference<View> downButton=new WeakReference<>(null);
    private WeakReference<Activity> downActivity=new WeakReference<>(null);
    private long downAt;
    private float downX,downY;
    private long lastDownTime=-1,lastEventTime=-1;
    private int lastAction=-1;
    private WeakReference<Activity> lastEventActivity=new WeakReference<>(null);
    private WeakReference<View> physicalButton=new WeakReference<>(null);
    private WeakReference<Activity> physicalActivity=new WeakReference<>(null);
    private long physicalAt;
    private final List<WeakReference<Object>> aioContexts=new ArrayList<>();
    private volatile Attempt pending;
    private volatile boolean activeContextHookReady;
    private boolean activeContextHookAttempted;
    private View replaying;
    UiTapGate(HostRuntime runtime) { this.runtime=runtime; }
    void sendViewEvent(View view,MotionEvent event) {
        if(!qqId(view,"send_btn") || !(view instanceof TextView) || !sendLabel(((TextView)view).getText())) return;
        Activity activity=runtime.currentActivity();
        if(activity!=null) event(activity,event);
    }
    void event(Activity activity,MotionEvent event) {
        if(activity==null || event==null) return;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_POINTER_DOWN) { reset(); runtime.permit.clear(); return; }
        if(action!=MotionEvent.ACTION_DOWN && action!=MotionEvent.ACTION_UP) return;
        // Activity and exact send View observe the same gesture; the second observation
        // must not overwrite the physical token created by the first ACTION_UP.
        if(lastEventActivity.get()==activity && lastAction==action && lastDownTime==event.getDownTime() && lastEventTime==event.getEventTime()) return;
        lastEventActivity=new WeakReference<>(activity);lastAction=action;lastDownTime=event.getDownTime();lastEventTime=event.getEventTime();
        try {
            if(action==MotionEvent.ACTION_DOWN) {
                clearDown(); clearPhysical(); runtime.permit.clear();
                if((!runtime.bridge.config.enabled && !runtime.bridge.perMessageEnabled()) || !runtime.versionSupported || event.getPointerCount()!=1) return;
                View button=hitSend(activity.getWindow().getDecorView(),(int)event.getRawX(),(int)event.getRawY());
                if(button==null) return;
                downButton=new WeakReference<>(button); downActivity=new WeakReference<>(activity);
                downAt=SystemClock.uptimeMillis(); downX=event.getRawX(); downY=event.getRawY();
            } else {
                View button=downButton.get(); Activity started=downActivity.get(); long time=SystemClock.uptimeMillis()-downAt;
                clearDown();
                if(button==null || started!=activity || time<0 || time>700 || event.getPointerCount()!=1) return;
                float density=activity.getResources().getDisplayMetrics().density;
                if(Math.hypot(event.getRawX()-downX,event.getRawY()-downY)>32*density) return;
                if(hitSend(activity.getWindow().getDecorView(),(int)event.getRawX(),(int)event.getRawY())!=button) return;
                if(runtime.bridge.perMessageEnabled()) {
                    physicalButton=new WeakReference<>(button);physicalActivity=new WeakReference<>(activity);physicalAt=SystemClock.uptimeMillis();
                }
                List<EditText> editors=new ArrayList<>(); int[] budget={1200};
                findEditors(activity.getWindow().getDecorView(),editors,budget,0);
                if(editors.size()!=1) { runtime.log("发送许可未生成：匹配输入框数量 "+editors.size()); return; }
                String text=editors.get(0).getText().toString();
                runtime.log("发送点击匹配输入框 "+editors.get(0).getClass().getName());
                runtime.arm(text);
            }
        } catch(Throwable e) { reset(); runtime.permit.clear(); runtime.setError("发送点击观察："+e.getClass().getSimpleName()); }
    }
    void reset() {
        clearDown();clearPhysical();lastEventActivity.clear();lastAction=-1;lastDownTime=-1;lastEventTime=-1;
        if(pending!=null)pending.cancelled=true;
    }
    private void clearDown() { downButton.clear(); downActivity.clear(); downAt=0; }
    private void clearPhysical() {physicalButton.clear();physicalActivity.clear();physicalAt=0;}
    /** Observed from AIOContextImpl construction; references never retain an old conversation. */
    void observeAio(Object value) {
        if(value==null || !value.getClass().getName().equals("com.tencent.aio.runtime.AIOContextImpl"))return;
        installActiveContextHooks(value.getClass().getClassLoader());
        synchronized(aioContexts) {
            for(int i=aioContexts.size()-1;i>=0;i--) {Object existing=aioContexts.get(i).get();if(existing==null)aioContexts.remove(i);else if(existing==value)return;}
            aioContexts.add(new WeakReference<>(value));while(aioContexts.size()>12)aioContexts.remove(0);
        }
    }
    /** Observe QQ's actual current-pointer and Fragment lifecycle, never a cached constructor's age. */
    private synchronized void installActiveContextHooks(ClassLoader loader) {
        if(activeContextHookAttempted || !runtime.versionSupported)return;
        activeContextHookAttempted=true;
        try {
            Class<?> fragmentClass=XposedHelpers.findClass("com.tencent.aio.main.fragment.ChatFragment",loader);
            XposedHelpers.findAndHookMethod(fragmentClass,"access$setCurrentContext$p",fragmentClass,WeakReference.class,new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Attempt attempt=pending;
                    if(attempt==null || attempt.fragment.get()!=param.args[0])return;
                    Object next=param.args[1] instanceof WeakReference?((WeakReference<?>)param.args[1]).get():null;
                    if(next==null || next!=attempt.context.get())cancelForFragment(param.args[0]);
                }
            });
            XC_MethodHook leaving=new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {cancelForFragment(param.thisObject);}
            };
            XposedHelpers.findAndHookMethod(fragmentClass,"onPause",leaving);
            XposedHelpers.findAndHookMethod(fragmentClass,"onDestroyView",leaving);
            XposedHelpers.findAndHookMethod(fragmentClass,"onNewIntent",Bundle.class,leaving);
            XposedHelpers.findAndHookMethod(fragmentClass,"onHiddenChanged",boolean.class,new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {if(Boolean.TRUE.equals(param.args[0]))cancelForFragment(param.thisObject);}
            });
            activeContextHookReady=true;
            runtime.log("逐消息 QQ 当前会话指针及生命周期观察已就绪");
        }catch(Throwable e) {runtime.log("逐消息当前会话观察未就绪 "+e.getClass().getSimpleName()+"；保留 QQ 原发送");}
    }
    private void cancelForFragment(Object fragment) {
        Attempt attempt=pending;
        if(attempt!=null && fragment!=null && attempt.fragment.get()==fragment && !attempt.finished) {attempt.revision++;attempt.cancelled=true;}
    }
    /** True consumes this original UI click. Only this exact physical gesture may be restored. */
    boolean deferClick(View button) {
        if(button==replaying)return false;
        if(!runtime.bridge.perMessageEnabled() || !qqId(button,"send_btn"))return false;
        Activity activity=runtime.currentActivity();long age=SystemClock.uptimeMillis()-physicalAt;
        if(physicalButton.get()!=button || physicalActivity.get()!=activity || age<0 || age>CLICK_MS)return false;
        clearPhysical();
        if(pending!=null) {Toast.makeText(activity,"正在确认装扮，请稍候；不会重复发送",Toast.LENGTH_SHORT).show();return true;}
        try {
            if(!runtime.versionSupported || !runtime.bridge.controlsHealthy() || !foreground(activity) ||
                !button.getClass().getName().equals("com.tencent.mobileqq.aio.input.AIOInputSendBtn"))return ordinary("原生按钮或前台条件未匹配");
            if(!activeContextHookReady)return ordinary("当前会话切换观察未就绪");
            View root=activity.getWindow().getDecorView();EditText editor=uniqueComposer(root);
            View composer=editor==null?null:composerContainer(button,editor);
            if(editor==null || composer==null || !plainEditor(editor) || richDraft(composer))return ordinary("本次输入不是已适配的普通文字；"+inputDiagnostic(editor,composer));
            Binding binding=binding(button,activity,true);
            if(binding==null)return ordinary("未匹配唯一会话上下文");
            int reply=replyState(binding);
            if(reply!=0)return ordinary(reply==1?"当前仍有引用回复":"当前逻辑回复状态未能确认");
            Object movement=editor.getMovementMethod();
            if(movement!=null && movement.getClass().getName().startsWith("com.tencent.mobileqq.aio.reply."))
                runtime.log("逐消息已核对取消回复残留：replyTag=false,drawables=false,replyData=false");
            String owner=AccountRef.current(activity);JSONObject config=runtime.bridge.controlSettings();
            if("unknown".equals(owner) || !owner.equals(config.optString("account")) || !config.optBoolean("perMessage") || config.optString("generation").isEmpty())return ordinary("账号或配置未就绪");
            Object listener=listener(button);if(listener==null)return ordinary("发送监听器不可确认");
            Attempt attempt=new Attempt(activity,button,editor,root,composer,binding,listener,owner,config.optString("generation"));
            try {attempt.watch();} catch(Throwable e) {attempt.finished=true;attempt.unwatch();return ordinary("无法观察输入变更");}
            pending=attempt;runtime.messageHeld();runtime.log("逐消息物理点击已延后，等待商城确认");
            try {runtime.decoration.beforeMessage(config,owner,() -> valid(attempt),success -> finish(attempt,success));}
            catch(Throwable e) {finish(attempt,false);}
            return true;
        } catch(Throwable e) {runtime.log("逐消息前置检查失败 "+e.getClass().getSimpleName()+"；本次沿用 QQ 原发送");return false;}
    }
    private boolean ordinary(String reason) {runtime.log("逐消息跳过："+reason+"；本次沿用 QQ 原发送");return false;}
    private boolean valid(Attempt attempt) {
        if(pending!=attempt || attempt.finished || attempt.cancelled || attempt.revision!=0)return false;
        long now=SystemClock.elapsedRealtime();if(now<attempt.started || now-attempt.started>=REPLAY_MS)return false;
        try {
            Activity activity=attempt.activity.get();View button=attempt.button.get(),root=attempt.root.get();EditText editor=attempt.editor.get();
            if(activity==null || button==null || root==null || editor==null || runtime.currentActivity()!=activity || !foreground(activity) || !runtime.bridge.controlsHealthy())return false;
            if(activity.getWindow().getDecorView()!=root || button.getRootView()!=root || !button.isAttachedToWindow() || button.getWindowToken()!=attempt.window ||
                !button.hasWindowFocus() || !button.isShown() || !button.isEnabled() || !button.isClickable() || listener(button)!=attempt.listener.get())return false;
            View composer=composerContainer(button,editor);
            if(uniqueComposer(root)!=editor || composer==null || composer!=attempt.composer.get() || editor.getText()!=attempt.editable.get() || !plainEditor(editor) || richDraft(composer) || !MessageDigest.isEqual(attempt.textHash,hash(editor.getText())))return false;
            if(!attempt.owner.equals(AccountRef.current(activity)))return false;
            JSONObject config=runtime.bridge.controlSettings();
            if(!config.optBoolean("perMessage") || !attempt.owner.equals(config.optString("account")) || !attempt.generation.equals(config.optString("generation")))return false;
            Binding current=binding(button,activity);
            return activeContextHookReady && current!=null && replyState(current)==0 && current.manager==attempt.manager.get() && current.pieRoot==attempt.pieRoot.get()
                && ActiveConversationChain.sameCurrent(attempt.pie.get(),attempt.context.get(),attempt.param.get(),current.pie,current.context,current.param)
                && ConversationMatch.same(attempt.fragment.get(),attempt.fragmentRoot.get(),attempt.type,attempt.peer,attempt.guild,
                    current.fragment,current.root,current.type,current.peer,current.guild);
        } catch(Throwable ignored) {return false;}
    }
    private void finish(Attempt attempt,boolean confirmed) {
        if(attempt.finished || attempt.finishing)return;
        attempt.finishing=true;
        boolean contextValid=confirmed && valid(attempt);
        attempt.unwatch();
        // Removing an observer can synchronously notify QQ's own span observers.
        // Recheck before consuming rather than replaying a possibly modified draft.
        contextValid=contextValid && valid(attempt);
        boolean resume=attempt.guard.consume(attempt.revision,SystemClock.elapsedRealtime(),contextValid);
        runtime.messageFinished(resume);
        attempt.finished=true;if(pending==attempt)pending=null;
        Activity activity=attempt.activity.get();View button=attempt.button.get();
        if(resume && button!=null) {
            replaying=button;
            try {button.performClick();runtime.log("逐消息服务器确认后恢复原发送点击一次");}
            catch(Throwable e) {runtime.log("逐消息恢复原点击失败 "+e.getClass().getSimpleName()+"；不重试");if(activity!=null)Toast.makeText(activity,"发送点击未完成，请检查当前输入后手动确认",Toast.LENGTH_LONG).show();}
            finally {replaying=null;}
        } else {
            runtime.log("逐消息延后发送已取消；未自动发送或重试");
            if(activity!=null && !activity.isFinishing() && !activity.isDestroyed())Toast.makeText(activity,"逐消息切换已取消，保留当前输入；请确认后再试",Toast.LENGTH_LONG).show();
        }
        java.util.Arrays.fill(attempt.textHash,(byte)0);
    }
    private Binding binding(View button,Activity activity) throws Throwable {return binding(button,activity,false);}
    private Binding binding(View button,Activity activity,boolean diagnose) throws Throwable {
        Object fragment=null;View frame=null;int frameMatches=0;List<Object> candidates=new ArrayList<>();
        synchronized(aioContexts) {for(WeakReference<Object> ref:aioContexts) {Object value=ref.get();if(value!=null)candidates.add(value);}}
        // Cached wrappers only locate a unique real Fragment containing the button.
        // Their retained Contact tuples are not evidence of the current conversation.
        for(Object observed:candidates) {
            try {
                Object candidate=XposedHelpers.callMethod(observed,"c");Object candidateFrame=XposedHelpers.callMethod(candidate,"getView");
                if(!exactClass(candidate,"com.tencent.aio.main.fragment.ChatFragment") || !(candidateFrame instanceof View)
                    || XposedHelpers.callMethod(candidate,"getActivity")!=activity || !contains((View)candidateFrame,button) || !((View)candidateFrame).isShown())continue;
                frameMatches++;
                if(fragment!=null && (fragment!=candidate || frame!=candidateFrame))return bindingFailure(diagnose,"不同真实 Fragment 或根视图",candidates.size(),frameMatches);
                fragment=candidate;frame=(View)candidateFrame;
            }catch(Throwable ignored) { /* Historical wrappers cannot authorize a send. */ }
        }
        if(fragment==null)return bindingFailure(diagnose,"未匹配真实 Fragment",candidates.size(),frameMatches);
        try {
            Object manager=XposedHelpers.getObjectField(fragment,"chatPieManager");
            if(!exactClass(manager,"com.tencent.aio.base.chat.ChatPieManager"))return bindingFailure(diagnose,"当前管理器类型未适配",candidates.size(),frameMatches);
            Object pie=XposedHelpers.callMethod(manager,"e"),fieldPie=XposedHelpers.getObjectField(manager,"b");
            if(!exactClass(pie,"com.tencent.aio.base.chat.ChatPie"))return bindingFailure(diagnose,"当前 ChatPie 未就绪",candidates.size(),frameMatches);
            Object context=XposedHelpers.callMethod(pie,"k"),fieldContext=XposedHelpers.getObjectField(pie,"f"),currentRef=XposedHelpers.getObjectField(fragment,"currentContext");
            if(!exactClass(context,"com.tencent.aio.runtime.AIOContextImpl") || !(currentRef instanceof WeakReference))return bindingFailure(diagnose,"当前上下文未就绪",candidates.size(),frameMatches);
            Object param=XposedHelpers.callMethod(context,"g"),pieParam=XposedHelpers.callMethod(pie,"l"),contextFragment=XposedHelpers.callMethod(context,"c");
            if(!exactClass(param,"com.tencent.aio.data.AIOParam") || pieParam!=XposedHelpers.getObjectField(pie,"h")
                || !ActiveConversationChain.consistent(fragment,contextFragment,pie,fieldPie,context,fieldContext,((WeakReference<?>)currentRef).get(),pieParam,param))
                return bindingFailure(diagnose,"当前活动链指针不一致",candidates.size(),frameMatches);
            Object rawRoot=XposedHelpers.getObjectField(pie,"d");
            if(!(rawRoot instanceof View) || !contains(frame,(View)rawRoot) || !contains((View)rawRoot,button) || !((View)rawRoot).isShown()
                || !((View)rawRoot).isAttachedToWindow() || ((View)rawRoot).getWindowToken()!=button.getWindowToken())
                return bindingFailure(diagnose,"当前 ChatPie 根视图未包含发送按钮",candidates.size(),frameMatches);
            Object session=XposedHelpers.callMethod(param,"p"),contact=XposedHelpers.callMethod(session,"b");
            if(!exactClass(contact,"com.tencent.aio.data.AIOContact"))return bindingFailure(diagnose,"当前 Contact 类型未适配",candidates.size(),frameMatches);
            Object rawType=XposedHelpers.getObjectField(contact,"d"),rawPeer=XposedHelpers.getObjectField(contact,"e"),rawGuild=XposedHelpers.getObjectField(contact,"f");
            if(!(rawType instanceof Integer) || !(rawPeer instanceof String) || !(rawGuild instanceof String))
                return bindingFailure(diagnose,"当前 Contact 字段类型未适配：d:"+fieldType(rawType)+",e:"+fieldType(rawPeer)+",f:"+fieldType(rawGuild),candidates.size(),frameMatches);
            int type=(Integer)rawType;String peer=(String)rawPeer,guild=(String)rawGuild;
            if((type!=1 && type!=2) || peer.isEmpty() || !guild.isEmpty())return bindingFailure(diagnose,"当前会话类型未适配",candidates.size(),frameMatches);
            if(diagnose) {
                int historical=0,stale=0,unread=0;
                for(Object observed:candidates)if(observed!=context)try {
                    if(XposedHelpers.callMethod(observed,"c")!=fragment)continue;
                    historical++;
                    Object oldParam=XposedHelpers.callMethod(observed,"g"),oldSession=XposedHelpers.callMethod(oldParam,"p"),oldContact=XposedHelpers.callMethod(oldSession,"b");
                    if(!exactClass(oldContact,"com.tencent.aio.data.AIOContact")) {unread++;continue;}
                    Object oldType=XposedHelpers.getObjectField(oldContact,"d"),oldPeer=XposedHelpers.getObjectField(oldContact,"e"),oldGuild=XposedHelpers.getObjectField(oldContact,"f");
                    if(!(oldType instanceof Integer) || !(oldPeer instanceof String) || !(oldGuild instanceof String)) {unread++;continue;}
                    if(type!=(Integer)oldType || !peer.equals(oldPeer) || !guild.equals(oldGuild))stale++;
                }catch(Throwable ignored) {unread++;}
                runtime.log("逐消息已确认 QQ 活动会话：contexts="+candidates.size()+",matchingFrames="+frameMatches+",source=currentChatPie,chainSame=true,activeRootContainsButton=true,historicalWrappers="+historical+",staleContacts="+stale+",unreadHistorical="+unread);
            }
            return new Binding(fragment,frame,manager,pie,context,param,(View)rawRoot,type,peer,guild);
        }catch(Throwable e) {return bindingFailure(diagnose,"当前活动链解析失败 "+e.getClass().getSimpleName(),candidates.size(),frameMatches);}
    }
    private Binding bindingFailure(boolean diagnose,String reason,int contexts,int frames) {
        if(diagnose)runtime.log("逐消息当前会话匹配失败：contexts="+contexts+",matchingFrames="+frames+",reason="+reason);return null;
    }
    private static boolean exactClass(Object value,String name) {return value!=null && value.getClass().getName().equals(name);}
    private static String fieldType(Object value) {return value==null?"null":value.getClass().getName();}
    private static boolean contains(View root,View child) {
        View cursor=child;
        for(int i=0;i<64 && cursor!=null;i++) {if(cursor==root)return true;android.view.ViewParent parent=cursor.getParent();cursor=parent instanceof View?(View)parent:null;}return false;
    }
    private static Object listener(View button) {
        Object info=XposedHelpers.getObjectField(button,"mListenerInfo");return info==null?null:XposedHelpers.getObjectField(info,"mOnClickListener");
    }
    private static EditText uniqueComposer(View root) {
        List<EditText> editors=new ArrayList<>();int[] budget={1200};strictEditors(root,editors,budget,0);return budget[0]>=0 && editors.size()==1?editors.get(0):null;
    }
    private static void strictEditors(View view,List<EditText> out,int[] budget,int depth) {
        if(depth>32 || --budget[0]<0 || !view.isShown())return;
        if(view instanceof EditText && qqId(view,"input") && view.hasFocus() && view.isEnabled())out.add((EditText)view);
        if(view instanceof ViewGroup) {ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)strictEditors(group.getChildAt(i),out,budget,depth+1);}
    }
    private static View composerContainer(View button,EditText editor) {
        android.view.ViewParent parent=editor.getParent();
        for(int i=0;i<32 && parent instanceof View;i++) {
            View candidate=(View)parent;if(contains(candidate,button))return candidate;parent=parent.getParent();
        }
        return null;
    }
    private static boolean plain(Editable text) {
        if(text==null || text.length()==0 || text.length()>12000)return false;
        for(Object span:text.getSpans(0,text.length(),Object.class)) {
            if((text.getSpanFlags(span)&Spanned.SPAN_COMPOSING)!=0)return false;
            if(span==Selection.SELECTION_START || span==Selection.SELECTION_END || span instanceof TextWatcher || span instanceof SpanWatcher)continue;
            // Android's editing machinery adds payload-free Concrete marker objects
            // even for adb-entered plain text. Accept this exact framework class;
            // arbitrary NoCopySpan implementations can still represent QQ content.
            if(span.getClass()==android.text.NoCopySpan.Concrete.class)continue;
            return false;
        }
        return true;
    }
    private static boolean plainEditor(EditText editor) {
        if(!plain(editor.getText()))return false;
        // QQ 9.3.50 h.n() clears the gja tag and compound drawables but leaves
        // aio.reply.a movement installed. Movement alone does not prove a reply.
        int key=editor.getResources().getIdentifier("gja","id","com.tencent.mobileqq");
        if(key==0 || editor.getTag(key)!=null)return false;
        for(android.graphics.drawable.Drawable drawable:editor.getCompoundDrawables())if(drawable!=null)return false;
        for(android.graphics.drawable.Drawable drawable:editor.getCompoundDrawablesRelative())if(drawable!=null)return false;
        return true;
    }
    /** QQ's GetReplyData route is a read-only query; never clears or edits a reply. */
    private static int replyState(Binding binding) {
        try {
            Class<?> intent=XposedHelpers.findClass("com.tencent.mobileqq.aio.input.reply.InputReplyMsgIntent$GetReplyData",binding.context.getClass().getClassLoader());
            Object request=XposedHelpers.getStaticObjectField(intent,"d");
            if(request==null || request.getClass()!=intent)return -1;
            Object route=XposedHelpers.callMethod(binding.context,"e"),result=XposedHelpers.callMethod(route,"k",request);
            if(!exactClass(result,"com.tencent.mobileqq.aio.input.reply.a$a"))return -1;
            return XposedHelpers.callMethod(result,"a")==null?0:1;
        }catch(Throwable ignored) {return -1;}
    }
    /** Scan the current composer only; a quoted message in chat history is not a reply draft. */
    private static boolean richDraft(View root) {return richDraftReason(root)!=null;}
    private static String richDraftReason(View root) {
        if(root==null)return "输入容器未匹配";
        int[] budget={1200};String found=richDraftReason(root,budget,0);return found!=null?found:(budget[0]<0?"输入容器扫描预算不足":null);
    }
    private static String richDraftReason(View view,int[] budget,int depth) {
        if(depth>32 || --budget[0]<0 || !view.isShown())return null;
        String type=view.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if(type.startsWith("com.tencent.mobileqq.aio.input.") && (type.contains("reply") || type.contains("attachment") || type.contains("picpreview")))return viewDiagnostic(view);
        if(view.getId()!=View.NO_ID)try {
            String name=view.getResources().getResourceEntryName(view.getId()).toLowerCase(java.util.Locale.ROOT);
            if(name.equals("reply_source") || name.equals("reply_panel") || name.equals("reply_layout") || name.equals("input_reply") || name.equals("attachment_preview") || name.equals("input_pic_preview"))return viewDiagnostic(view);
        }catch(android.content.res.Resources.NotFoundException ignored) {}
        if(view instanceof ViewGroup) {ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){String found=richDraftReason(group.getChildAt(i),budget,depth+1);if(found!=null)return found;}}
        return null;
    }
    private static String viewDiagnostic(View view) {
        String id="none";
        if(view.getId()!=View.NO_ID)try {id=view.getResources().getResourceName(view.getId());}catch(android.content.res.Resources.NotFoundException ignored){id="unresolved";}
        return "view="+view.getClass().getName()+",id="+id;
    }
    /** Type names and structural flags only: never span values, text, IDs of peers, or tags. */
    private static String inputDiagnostic(EditText editor,View composer) {
        if(editor==null)return "唯一原生输入框未匹配";
        StringBuilder result=new StringBuilder("spanClasses=");
        Object[] spans=editor.getText().getSpans(0,editor.getText().length(),Object.class);
        if(spans.length==0)result.append("none");
        for(int i=0;i<Math.min(spans.length,12);i++) {if(i>0)result.append(',');result.append(spans[i].getClass().getName());}
        if(spans.length>12)result.append(",...");
        Object movement=editor.getMovementMethod();result.append(";movement=").append(movement==null?"none":movement.getClass().getName());
        result.append(";drawables=").append(drawableFlags(editor.getCompoundDrawables())).append('/').append(drawableFlags(editor.getCompoundDrawablesRelative()));
        String rich=richDraftReason(composer);result.append(";rich=").append(rich==null?"none":rich);return result.toString();
    }
    private static String drawableFlags(android.graphics.drawable.Drawable[] drawables) {
        StringBuilder flags=new StringBuilder();for(android.graphics.drawable.Drawable drawable:drawables)flags.append(drawable==null?'0':'1');return flags.toString();
    }
    private static byte[] hash(CharSequence value) throws Exception {
        byte[] bytes=value.toString().getBytes(StandardCharsets.UTF_8);
        try {return MessageDigest.getInstance("SHA-256").digest(bytes);} finally {java.util.Arrays.fill(bytes,(byte)0);}
    }
    private boolean foreground(Activity activity) {
        if(activity==null || activity.isFinishing() || activity.isDestroyed() || runtime.currentActivity()!=activity)return false;
        PowerManager power=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);KeyguardManager keyguard=(KeyguardManager)activity.getSystemService(Context.KEYGUARD_SERVICE);
        return power!=null && power.isInteractive() && keyguard!=null && !keyguard.isDeviceLocked();
    }
    private static final class Binding {
        final Object fragment,manager,pie,context,param;final View root,pieRoot;final int type;final String peer,guild;
        Binding(Object fragment,View root,Object manager,Object pie,Object context,Object param,View pieRoot,int type,String peer,String guild) {
            this.fragment=fragment;this.root=root;this.manager=manager;this.pie=pie;this.context=context;this.param=param;this.pieRoot=pieRoot;this.type=type;this.peer=peer;this.guild=guild;
        }
    }
    private static final class Attempt implements TextWatcher,SpanWatcher,android.text.NoCopySpan {
        final WeakReference<Activity> activity;final WeakReference<View> button,root,fragmentRoot,composer,pieRoot;final WeakReference<EditText> editor;
        final WeakReference<Editable> editable;final WeakReference<Object> fragment,listener,manager,pie,context,param;final Object window;
        final String owner,generation,peer,guild;final int type;final byte[] textHash;final long started=SystemClock.elapsedRealtime();
        final SendReplayGuard guard=new SendReplayGuard(0,started,REPLAY_MS);
        volatile long revision;volatile boolean cancelled;boolean finished,finishing;
        Attempt(Activity activity,View button,EditText editor,View root,View composer,Binding binding,Object listener,String owner,String generation) throws Exception {
            this.activity=new WeakReference<>(activity);this.button=new WeakReference<>(button);this.editor=new WeakReference<>(editor);this.root=new WeakReference<>(root);
            this.composer=new WeakReference<>(composer);
            this.editable=new WeakReference<>(editor.getText());this.fragment=new WeakReference<>(binding.fragment);this.fragmentRoot=new WeakReference<>(binding.root);
            this.manager=new WeakReference<>(binding.manager);this.pie=new WeakReference<>(binding.pie);this.context=new WeakReference<>(binding.context);this.param=new WeakReference<>(binding.param);this.pieRoot=new WeakReference<>(binding.pieRoot);
            this.listener=new WeakReference<>(listener);window=button.getWindowToken();this.owner=owner;this.generation=generation;peer=binding.peer;guild=binding.guild;type=binding.type;textHash=hash(editor.getText());
        }
        void watch() {EditText input=editor.get();Editable text=editable.get();if(input!=null)input.addTextChangedListener(this);if(text!=null)text.setSpan(this,0,text.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);}
        void unwatch() {EditText input=editor.get();Editable text=editable.get();try {if(text!=null)text.removeSpan(this);}catch(Throwable ignored){}try {if(input!=null)input.removeTextChangedListener(this);}catch(Throwable ignored){}}
        public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
        public void onTextChanged(CharSequence s,int start,int before,int count) {revision++;cancelled=true;}
        public void afterTextChanged(Editable text) {}
        public void onSpanAdded(android.text.Spannable text,Object span,int start,int end) {changed(span);}
        public void onSpanRemoved(android.text.Spannable text,Object span,int start,int end) {changed(span);}
        public void onSpanChanged(android.text.Spannable text,Object span,int oldStart,int oldEnd,int newStart,int newEnd) {changed(span);}
        private void changed(Object span) {if(span!=this && !finished){revision++;cancelled=true;}}
    }
    private static boolean inside(View v,int x,int y) {
        Rect r=new Rect(); return v.isShown() && v.getGlobalVisibleRect(r) && r.contains(x,y);
    }
    private static View hitSend(View root,int x,int y) {
        // QQ 9.3.50 on the device uses id/send_btn. A transparent skin overlay
        // can hide it from the last-child hit path, although QQ still dispatches
        // the real click to this button. Require one visible, enabled exact ID.
        List<View> known=new ArrayList<>(); int[] knownBudget={1200};
        findKnownSend(root,x,y,known,knownBudget,0);
        if(knownBudget[0]<0 || known.size()>1) return null;
        if(known.size()==1) return known.get(0);
        List<View> path=new ArrayList<>(); int[] budget={1200};
        if(!hitPath(root,x,y,path,budget,0)) return null;
        View labeled=null;
        // Only consider the actual touched branch; no search of unrelated siblings.
        // QQ's clickable ancestor is often the full-width input bar. The compact 发送 label
        // on that same path is still the button the user pressed.
        for(int i=path.size()-1;i>=0;i--) {
            View v=path.get(i);
            boolean marked=(v instanceof TextView && sendLabel(((TextView)v).getText())) || sendLabel(v.getContentDescription());
            if(marked && compact(v)) labeled=v;
            if(labeled!=null && v.isClickable() && v.isEnabled()) return compact(v) ? v : labeled;
        }
        return labeled!=null && labeled.isClickable() ? labeled : null;
    }
    private static boolean qqId(View v,String name) {
        if(v.getId()==View.NO_ID) return false;
        try { return "com.tencent.mobileqq".equals(v.getResources().getResourcePackageName(v.getId()))
                && name.equals(v.getResources().getResourceEntryName(v.getId())); }
        catch(android.content.res.Resources.NotFoundException e) { return false; }
    }
    private static void findKnownSend(View v,int x,int y,List<View> out,int[] budget,int depth) {
        if(depth>32 || --budget[0]<0 || !v.isShown()) return;
        if(qqId(v,"send_btn") && v instanceof TextView && sendLabel(((TextView)v).getText())
                && v.isClickable() && v.isEnabled() && compact(v) && inside(v,x,y)) out.add(v);
        if(v instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)v;
            for(int i=0;i<group.getChildCount();i++) findKnownSend(group.getChildAt(i),x,y,out,budget,depth+1);
        }
    }
    private static boolean compact(View v) {
        float d=v.getResources().getDisplayMetrics().density;
        return v.getHeight()<=180*d && v.getWidth()<=320*d;
    }
    private static boolean sendLabel(CharSequence s) {
        if(s==null) return false;
        String t=s.toString().trim(); return t.equals("发送") || t.equalsIgnoreCase("send");
    }
    private static boolean hitPath(View v,int x,int y,List<View> path,int[] budget,int depth) {
        if(depth>32 || --budget[0]<0 || !inside(v,x,y)) return false;
        path.add(v);
        if(v instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)v;
            for(int i=group.getChildCount()-1;i>=0;i--) {
                View child=group.getChildAt(i);
                if(hitPath(child,x,y,path,budget,depth+1)) break;
            }
        }
        return true;
    }
    private static void findEditors(View v,List<EditText> out,int[] budget,int depth) {
        if(depth>32 || --budget[0]<0 || !v.isShown()) return;
        if(v instanceof EditText) {
            // Exact QQ composer resource observed on the device; keep focus and
            // uniqueness requirements rather than accepting arbitrary EditTexts.
            if(qqId(v,"input") && v.hasFocus() && v.isEnabled()) { out.add((EditText)v); return; }
            for(Class<?> c=v.getClass();c!=null;c=c.getSuperclass())
                if(c.getName().equals("com.tencent.mobileqq.aio.input.edit.AIOEditText")) { out.add((EditText)v); break; }
        }
        if(v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) findEditors(g.getChildAt(i),out,budget,depth+1);
        }
    }
}
