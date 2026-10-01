package io.github.ling.randombubble.hook;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import de.robv.android.xposed.XposedHelpers;
import io.github.ling.randombubble.core.DecorationSettings;
import io.github.ling.randombubble.core.RotationTiming;
import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** State transitions run on the main thread; only QQ's normal mall API sets styles. */
final class AccountDecoration {
    private final Context context;
    private final HostRuntime runtime;
    private final Handler main=new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private OfficialBubbleApi api;
    private ClassLoader loader;
    private volatile WeakReference<Object> handler=new WeakReference<>(null);
    private volatile String status="关闭；账号装扮由 QQ 管理";
    private String account="",generation="",blocked="",command="";
    private JSONObject settings=new JSONObject(),queuedManual;
    private long lastAttempt=-1,retryAt;
    private int failures,lastConfirmed;
    private final java.util.Set<Integer> unavailable=new java.util.HashSet<>();
    private Operation pending;
    private MessageRequest message;
    private static final class MessageRequest {
        JSONObject config;String owner;BooleanSupplier valid;Consumer<Boolean> done;
        long expires=SystemClock.elapsedRealtime()+30000;boolean finished;int checked;
    }
    private static final class Operation {
        String owner,generation;int bubble,font;Object handler;JSONObject template;
        boolean submitted,reconciling,finished;MessageRequest message;
    }
    AccountDecoration(Context c,HostRuntime r){context=c;runtime=r;}
    private SharedPreferences prefs(){if(preferences==null)preferences=context.getSharedPreferences("LingBubble-decoration",Context.MODE_PRIVATE);if(preferences==null)throw new IllegalStateException("冷却存储尚未就绪");return preferences;}
    void observe(Object h){if(h!=null)handler=new WeakReference<>(h);}
    String status(){return status;}
    void install(ClassLoader l) throws Throwable {loader=l;api=new OfficialBubbleApi(context,l,runtime);api.installLearner();}
    void poll(String raw,String owner){
        if(raw==null || raw.length()>262144)return;
        main.post(()->{try {accept(new JSONObject(raw),owner);tick();}catch(Throwable e){status="轮换暂缓："+e.getClass().getSimpleName();runtime.setError(status);}});
    }
    private void accept(JSONObject j,String owner) throws Exception {
        if(!owner.equals(j.optString("account")))return;
        String next=j.optString("generation");
        if(!runtime.bridge.generationCurrent(owner,next))return;
        if(!owner.equals(account)) {
            cancelPending("账号已变化，取消旧请求");finishMessage(message,false);message=null;
            account=owner;generation="";command="";lastAttempt=-1;lastConfirmed=0;queuedManual=null;
            // Earlier releases mistook a temporary background pause for a terminal failure.
            if(prefs().getInt("blockPolicy."+owner,0)<2 && !prefs().edit().remove("blocked."+owner).remove("reason."+owner).putInt("blockPolicy."+owner,2).commit())
                throw new IllegalStateException("保护状态迁移失败");
        }
        if(!next.equals(generation)) {
            generation=next;queuedManual=null;retryAt=0;failures=0;unavailable.clear();
            blocked=prefs().getString("blocked."+owner,"");
            if(pending!=null && !pending.submitted)cancelPending("配置已改变，取消尚未提交的切换");
            if(message!=null && !next.equals(message.config.optString("generation"))){finishMessage(message,false);message=null;}
        }
        settings=j;
        if(!j.optBoolean("automatic") && !j.optBoolean("perMessage") && pending==null)status="关闭；账号装扮由 QQ 管理";
        String requested=j.optString("command");
        if(!requested.isEmpty() && !requested.equals(command)){command=requested;queuedManual=j;blocked="";}
    }
    /** Holds one physical Send click outside the kernel until a style is confirmed. */
    void beforeMessage(JSONObject config,String owner,BooleanSupplier valid,Consumer<Boolean> done) {
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->beforeMessage(config,owner,valid,done));return;}
        MessageRequest request=new MessageRequest();request.config=config;request.owner=owner;request.valid=valid;request.done=done;
        try {
            if(message!=null || !config.optBoolean("perMessage") || !valid.getAsBoolean()){finishMessage(request,false);return;}
            accept(config,owner);
            message=request;
            main.postDelayed(()->{if(message==request && !request.finished){finishMessage(request,false);message=null;if(pending!=null && pending.message==request && !pending.submitted)cancelPending("逐消息等待超时，保留输入框");}},30000);
            tick();
        }catch(Throwable e){finishMessage(request,false);if(message==request)message=null;runtime.setError("逐消息切换暂缓："+e.getClass().getSimpleName());}
    }
    private void tick() throws Throwable {
        if(pending!=null)return;
        if(!runtime.bridge.generationCurrent(account,generation)) {
            if(message!=null){finishMessage(message,false);message=null;}
            status="等待最新配置";return;
        }
        if(message!=null) {
            MessageRequest request=message;
            if(!valid(request)){finishMessage(request,false);message=null;return;}
            if(generation.equals(blocked)){status=prefs().getString("reason."+account,"轮换已暂停，请重新保存配置");finishMessage(request,false);message=null;return;}
            long spacing=lastAttempt<0?0:Math.max(0,1000-(SystemClock.elapsedRealtime()-lastAttempt));
            spacing=Math.max(spacing,Math.max(0,retryAt-SystemClock.elapsedRealtime()));
            if(SystemClock.elapsedRealtime()+spacing>=request.expires){status="逐消息查询冷却中，请稍后再试";finishMessage(request,false);message=null;return;}
            if(spacing>0){main.postDelayed(this::safeTick,spacing);return;}
            begin(request.config,false,request);return;
        }
        if(!runtime.versionSupported || !runtime.bridge.controlsHealthy()) {status="等待有效的当前账号配置";return;}
        if(generation.equals(blocked)){status=prefs().getString("reason."+account,"轮换已暂停，请重新保存配置");return;}
        boolean manual=queuedManual!=null;
        JSONObject j=manual?queuedManual:settings;
        if(manual && j.optLong("expires")<System.currentTimeMillis()){queuedManual=null;status="手动请求已过期，请重新点击";return;}
        if(!manual && !j.optBoolean("automatic")){if(j.optBoolean("perMessage"))status="逐消息已开启；等待普通文字发送点击";return;}
        if(!foreground()){status="轮换暂停：等待 QQ 前台且屏幕解锁";return;}
        int seconds=j.getInt("seconds");DecorationSettings.interval(seconds);
        long now=SystemClock.elapsedRealtime(),period=(manual?60:seconds)*1000L;
        long remaining=lastAttempt<0?RotationTiming.remaining(System.currentTimeMillis(),prefs().getLong("last."+account,0),period):RotationTiming.remaining(now,lastAttempt,period);
        remaining=Math.max(remaining,Math.max(0,retryAt-now));
        if(remaining>0){status="当前气泡 "+lastConfirmed+"；距下次尝试 "+((remaining+999)/1000)+" 秒";return;}
        begin(j,manual,null);
    }
    private void safeTick(){try{tick();}catch(Throwable e){status="轮换暂缓："+e.getClass().getSimpleName();runtime.setError(status);if(message!=null){finishMessage(message,false);message=null;}}}
    private void begin(JSONObject j,boolean manual,MessageRequest request) throws Throwable {
        if(!foreground() || !runtime.bridge.controlsHealthy()){status="轮换暂停：等待 QQ 前台和有效配置";reject(request);return;}
        Object h=resolveHandler();if(h==null){status="等待 QQ 装扮接口就绪";reject(request);return;}
        int current=(Integer)XposedHelpers.callMethod(h,"getSelfBubbleId");lastConfirmed=current;
        JSONArray ids=j.getJSONArray("ids");java.util.ArrayList<Integer> candidates=new java.util.ArrayList<>();
        for(int i=0;i<ids.length();i++){int id=ids.getInt(i);if(id<=0 || id>999999999)throw new IllegalArgumentException("编号无效");if(id!=current && !unavailable.contains(id))candidates.add(id);}
        if(!manual && candidates.isEmpty()){status="候选中暂时没有另一款可用气泡，请调整编号后保存";reject(request);return;}
        int target=manual?ids.getInt(0):candidates.isEmpty()?current:candidates.get(new java.security.SecureRandom().nextInt(candidates.size()));
        if(target==current){queuedManual=null;status="已是所选账号装扮 "+current;reject(request);return;}
        if(api==null){status="等待商城接口初始化";reject(request);return;}
        JSONObject template;
        try{template=api.template(account,target);}catch(IllegalStateException e){status=e.getMessage();reject(request);return;}
        Operation op=new Operation();op.owner=account;op.generation=generation;op.bubble=target;op.handler=h;op.font=(Integer)XposedHelpers.callMethod(h,"getSelfFontId");op.template=template;op.message=request;
        if(op.font<0){status="等待有效字体状态";reject(request);return;}
        if(!prefs().edit().putLong("last."+account,System.currentTimeMillis()).commit())throw new IllegalStateException("冷却保存失败");
        lastAttempt=SystemClock.elapsedRealtime();pending=op;queuedManual=null;
        main.postDelayed(()->{if(active(op)){if(op.submitted)terminal(op,"提交结果超时，请核对商城装扮并重新保存配置");else if(!foreground())pause(op,"后台查询已暂停，回到前台后恢复");else temporary(op,"查询响应超时");}},65000);
        status="正在核对商城使用权益 "+target;runtime.log(status);
        api.detail(target,(detail,error)->main.post(()->{
            if(!active(op) || op.submitted)return;
            try {
                if(!op.generation.equals(generation) || !runtime.bridge.generationCurrent(op.owner,op.generation) || !foreground() || !runtime.bridge.controlsHealthy() || (op.message!=null && !valid(op.message))){pause(op,"环境已变化，取消尚未提交的切换");return;}
                if(error!=null){temporary(op,"商城查询暂时失败");return;}
                if(!api.authorized(detail,op.bubble)){
                    JSONObject base=detail.getJSONObject("baseInfo");
                    if(base.getInt("appId")!=2 || base.getInt("itemId")!=op.bubble){terminal(op,"商城响应款式不匹配；轮换已暂停");return;}
                    failures=0;retryAt=0;
                    unavailable.add(op.bubble);status="跳过未确认权益的气泡 "+op.bubble;runtime.log(status);
                    if(op.message!=null && ++op.message.checked<3 && valid(op.message)) {op.finished=true;pending=null;main.postDelayed(this::safeTick,1000);}
                    else finish(op,false);
                    return;
                }
                failures=0;retryAt=0;
                op.submitted=true;status="权益已确认，提交商城正常设置 "+op.bubble;runtime.log(status);
                api.set(op.template,(response,setError)->main.post(()->{
                    if(!active(op))return;
                    if(setError!=null){reconcile(op);return;}
                    try {if(!response.has("ret") || response.getInt("ret")!=0){terminal(op,"商城拒绝设置；轮换已暂停");return;}verify(op);}
                    catch(Throwable e){terminal(op,"商城设置响应不匹配；轮换已暂停");}
                }));
            }catch(Throwable e){terminal(op,"商城权益响应不匹配；轮换已暂停");}
        }));
    }
    private Object resolveHandler() throws Throwable {
        Object h=handler.get();
        if(h!=null)try {Object app=io.github.ling.randombubble.core.Reflect.get(h,"e");if(account.equals(XposedHelpers.callMethod(app,"getCurrentAccountUin")))return h;}catch(Throwable ignored){}
        Object mobile=XposedHelpers.getStaticObjectField(Class.forName("mqq.app.MobileQQ",false,loader),"sMobileQQ");
        Object app=XposedHelpers.callMethod(mobile,"peekAppRuntime");
        if(app==null || !account.equals(XposedHelpers.callMethod(app,"getCurrentAccountUin")))return null;
        Object key=XposedHelpers.getStaticObjectField(Class.forName("com.tencent.mobileqq.app.BusinessHandlerFactory",false,loader),"SVIP_HANDLER");
        h=XposedHelpers.callMethod(app,"getBusinessHandler",key);observe(h);return h;
    }
    private void verify(Operation op){
        status="核对服务器当前装扮 "+op.bubble;runtime.log(status);
        api.detail(op.bubble,(after,error)->main.post(()->{
            if(!active(op))return;
            try {
                if(error!=null){if(!op.reconciling){reconcile(op);return;}terminal(op,"设置结果尚不确定；轮换已暂停");return;}
                if(!api.authorized(after,op.bubble) || !after.has("isSetup") || !io.github.ling.randombubble.core.ServerFlags.value(after.get("isSetup"))){terminal(op,"服务器当前装扮未匹配；轮换已暂停");return;}
                if((Integer)XposedHelpers.callMethod(op.handler,"getSelfFontId")!=op.font){pause(op,"字体状态变化，停止本次刷新");return;}
                XposedHelpers.callMethod(op.handler,"setSelfBubbleId",op.bubble);XposedHelpers.callMethod(op.handler,"updateSelfMsgBubbleId");
                if((Integer)XposedHelpers.callMethod(op.handler,"getSelfBubbleId")!=op.bubble){terminal(op,"QQ 装扮刷新未匹配；轮换已暂停");return;}
                lastConfirmed=op.bubble;failures=0;retryAt=0;status="服务器已确认账号气泡 "+op.bubble+"；字体保留";runtime.log(status);
                finish(op,true);
            }catch(Throwable e){terminal(op,"服务器装扮核对异常；轮换已暂停");}
        }));
    }
    private void reconcile(Operation op){if(op.reconciling){terminal(op,"提交结果尚不确定；轮换已暂停");return;}op.reconciling=true;verify(op);}
    private boolean active(Operation op){
        if(op.finished || pending!=op)return false;
        if(!op.owner.equals(AccountRef.current(context))){pause(op,"账号已变化，取消旧请求");return false;}return true;
    }
    private boolean valid(MessageRequest request){
        try{return !request.finished && SystemClock.elapsedRealtime()<request.expires && request.owner.equals(account)
            && request.config.optString("generation").equals(generation) && settings.optBoolean("perMessage") && foreground()
            && runtime.bridge.generationCurrent(request.owner,request.config.optString("generation"))
            && runtime.bridge.controlsHealthy() && request.valid.getAsBoolean();}catch(Throwable e){return false;}
    }
    private void finish(Operation op,boolean success){
        if(op.finished)return;op.finished=true;if(pending==op)pending=null;
        if(op.message!=null){boolean send=success && valid(op.message);finishMessage(op.message,send);if(message==op.message)message=null;}
        if(message!=null)main.post(this::safeTick);
    }
    private void finishMessage(MessageRequest request,boolean success){if(request==null || request.finished)return;request.finished=true;try{request.done.accept(success);}catch(Throwable e){runtime.setError("发送恢复被取消："+e.getClass().getSimpleName());}}
    private void reject(MessageRequest request){if(request!=null){finishMessage(request,false);if(message==request)message=null;}}
    private void cancelPending(String reason){if(pending!=null){status=reason;finish(pending,false);}}
    private void pause(Operation op,String reason){status=reason;runtime.log(reason);finish(op,false);}
    private void temporary(Operation op,String reason){
        if(op.submitted){terminal(op,"提交结果尚不确定；轮换已暂停");return;}
        failures++;if(failures>=3){terminal(op,reason+"连续三次，请重新保存配置");return;}
        long delay=RotationTiming.retryDelay(failures,op.message!=null?1000L:settings.optInt("seconds",60)*1000L);
        retryAt=SystemClock.elapsedRealtime()+delay;status=reason+"，"+(delay/1000)+" 秒后再尝试";runtime.log(status);finish(op,false);
    }
    private void terminal(Operation op,String reason){
        if(op.owner.equals(account) && op.generation.equals(generation)){
            blocked=op.generation;try{prefs().edit().putString("blocked."+account,blocked).putString("reason."+account,reason).commit();}catch(Throwable ignored){}
        }
        status=reason;runtime.log(reason);finish(op,false);
    }
    private boolean foreground(){
        android.app.ActivityManager.RunningAppProcessInfo process=new android.app.ActivityManager.RunningAppProcessInfo();android.app.ActivityManager.getMyMemoryState(process);
        android.os.PowerManager power=(android.os.PowerManager)context.getSystemService(Context.POWER_SERVICE);android.app.KeyguardManager keyguard=(android.app.KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        return runtime.currentActivity()!=null && process.importance==android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && power!=null && power.isInteractive() && keyguard!=null && !keyguard.isDeviceLocked();
    }
}
