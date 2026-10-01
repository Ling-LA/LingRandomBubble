package io.github.ling.randombubble.hook;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import de.robv.android.xposed.XposedBridge;
import io.github.ling.randombubble.core.BubbleSpec;
import io.github.ling.randombubble.store.Config;
import io.github.ling.randombubble.store.ConfigProvider;
import io.github.ling.randombubble.store.JsonCodec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;

/** No Binder calls or disk IO on the QQ send hook. All sync runs on one worker. */
final class HostBridge {
    private final Context context;
    private final HostRuntime runtime;
    private final Map<String,BubbleSpec> pending=new LinkedHashMap<>();
    private final Map<String,Boolean> seen=new LinkedHashMap<String,Boolean>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Boolean> e) { return size()>4096; }
    };
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"LingBubble-Sync"); t.setDaemon(true); return t;
    });
    private final ScheduledExecutorService controls=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"LingBubble-Timer");t.setDaemon(true);return t;
    });
    volatile Config config=Config.off();
    private final AccountLibrary library;
    private final HostSettings settings;
    private volatile boolean localReady;
    private volatile long controlHeartbeat;
    private volatile boolean hostOrigin=true;
    private volatile org.json.JSONObject controlSnapshot=new org.json.JSONObject();
    private volatile String controlOwner="unknown";
    private volatile long ticks,syncFailures;
    private final Object controlLock=new Object();
    private long editEpoch;
    private String lastBridgeError="";
    /** False once the standalone module app turned out to be unreachable (embedded NPatch install). */
    private volatile boolean appReachable=true;
    private volatile boolean providerEverConnected;
    private volatile long nextProviderTry;
    private volatile long lastProviderSuccess;
    private volatile String providerOwner="unknown";
    private File logFile;
    interface Edit { void apply(org.json.JSONObject document) throws Exception; }
    /** Applies one change from the in-QQ settings panel to this account's library. Returns an error text or null. */
    synchronized String edit(Edit change) {return editForAccount(library.account(),change);}
    synchronized String editForAccount(String owner,Edit change) {
        try {
            checkOwner(owner);org.json.JSONObject document=library.document();change.apply(document);
            document.put("enabled",false);config=library.store(document);
            String failure=library.takeSaveError();if(failure!=null)throw new IllegalStateException(failure);
            settings.libraryEdited(owner);editEpoch++;runtime.log("QQ 内设置已保存；勾选 "+config.selected.size());return null;
        }catch(Exception e){return message(e);}
    }
    synchronized org.json.JSONObject document() {
        try {refreshAccount();return library.document();}catch(Exception e){return JsonCodec.defaults();}
    }
    synchronized String accountIdentity() {refreshAccount();return library.account();}
    synchronized org.json.JSONObject decorationSettings() {
        try {refreshAccount();return settings.decoration(library.account());}catch(Exception e){return new org.json.JSONObject();}
    }
    synchronized String selectedDecorationIds() {
        java.util.LinkedHashSet<Integer> ids=new java.util.LinkedHashSet<>();
        org.json.JSONArray rows=document().optJSONArray("bubbles");
        if(rows!=null)for(int i=0;i<rows.length();i++){org.json.JSONObject row=rows.optJSONObject(i);if(row!=null && row.optBoolean("selected") && row.optInt("bubbleId")>0)ids.add(row.optInt("bubbleId"));}
        StringBuilder out=new StringBuilder();for(int id:ids){if(out.length()>0)out.append(',');out.append(id);}return out.toString();
    }
    synchronized String configureDecoration(String ids,int seconds,boolean automatic,boolean manual) {
        return configureDecorationForAccount(library.account(),ids,seconds,automatic,manual);
    }
    synchronized String configureDecorationForAccount(String owner,String ids,int seconds,boolean automatic,boolean manual) {
        return configureDecorationForAccount(owner,ids,seconds,automatic,manual,false);
    }
    synchronized String configureDecorationForAccount(String owner,String ids,int seconds,boolean automatic,boolean manual,boolean perMessage) {
        try {checkOwner(owner);synchronized(controlLock){settings.configure(owner,io.github.ling.randombubble.store.DecorationCodec.create(owner,ids,seconds,automatic,manual,perMessage));editEpoch++;pollLocal();}return null;}
        catch(Exception e){return message(e);}
    }
    synchronized String stopDecoration() {return stopDecorationForAccount(library.account());}
    synchronized String stopDecorationForAccount(String owner) {
        try {checkOwner(owner);synchronized(controlLock){settings.configure(owner,io.github.ling.randombubble.store.DecorationCodec.stopped(settings.decoration(owner),owner));editEpoch++;pollLocal();}return null;}
        catch(Exception e){return message(e);}
    }
    private synchronized boolean refreshAccount() {
        boolean changed=library.refresh(context);
        if(changed) {
            editEpoch++;runtime.permit.clear();pending.clear();seen.clear();localReady=false;
            Config stored=library.configOrNull();config=stored==null?Config.off():stored;
        }
        return changed;
    }
    private void checkOwner(String expected) {
        refreshAccount();String current=library.account();io.github.ling.randombubble.store.DecorationCodec.owner(current);
        if(!current.equals(expected))throw new IllegalStateException("账号已变化，请重新打开面板");
    }
    private static String message(Exception error) {return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
    boolean controlsHealthy() {
        return localReady && android.os.SystemClock.elapsedRealtime()-controlHeartbeat<10000L
            && (hostOrigin || (runtime.bridgeHealthy && controlOwner.equals(providerOwner)
                && android.os.SystemClock.elapsedRealtime()-lastProviderSuccess<15000L)) && controlOwner.equals(AccountRef.current(context));
    }
    String controlSource() {return hostOrigin?"QQ 本机":"独立应用同步";}
    boolean perMessageEnabled() {return runtime.versionSupported && controlSnapshot.optBoolean("perMessage",false);}
    org.json.JSONObject controlSettings() {
        try {return new org.json.JSONObject(controlSnapshot.toString());}catch(Exception e){return new org.json.JSONObject();}
    }
    String timerStatus() {return "心跳 "+ticks+"；同步异常 "+syncFailures+"；最近心跳 "+Math.max(0,(android.os.SystemClock.elapsedRealtime()-controlHeartbeat)/1000)+" 秒前";}
    boolean generationCurrent(String owner,String generation) {
        try {return owner.equals(AccountRef.current(context)) && generation.equals(settings.decoration(owner).optString("generation"));}catch(Throwable e){return false;}
    }
    private void pollLocal() {
        synchronized(controlLock) {
        try {
            String owner=AccountRef.current(context);if("unknown".equals(owner)){localReady=false;return;}
            String raw=settings.take(owner);org.json.JSONObject snapshot=new org.json.JSONObject(raw);
            hostOrigin=settings.hostControlled(owner);controlOwner=owner;controlSnapshot=snapshot;
            controlHeartbeat=android.os.SystemClock.elapsedRealtime();localReady=true;ticks++;
            runtime.decoration.poll(raw,owner);
        }catch(Throwable e){localReady=false;runtime.setError("QQ 内配置："+e.getClass().getSimpleName());}
        }
    }
    boolean appReachable() { return appReachable; }
    boolean canSend() { return !providerEverConnected || runtime.bridgeHealthy; }
    void writeLog(String row) {
        try { executor.execute(() -> appendLog(row)); }
        catch(Throwable ignored) { /* diagnostics must not interrupt a send */ }
    }
    private void appendLog(String row) {
        try {
            if(logFile==null) {
                File dir=context.getFilesDir();
                if(dir==null) dir=context.getExternalFilesDir(null);
                if(dir==null) return;
                logFile=new File(dir,"LingRandomBubble-log.txt");
            }
            try(FileOutputStream out=new FileOutputStream(logFile,logFile.length()<262144)) {
                out.write((row+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch(Throwable ignored) { /* logging must not affect sending */ }
    }
    String logPath() { return logFile==null?"QQ 应用私有目录（可在面板复制诊断）":logFile.getAbsolutePath(); }
    String libraryMask() { return library==null?"未识别账号":library.mask(); }
    HostBridge(Context context,HostRuntime runtime) {
        this.context=context; this.runtime=runtime; this.library=new AccountLibrary(context); this.settings=new HostSettings(context);
    }
    void start() {
        controls.scheduleWithFixedDelay(this::pollLocal,1000,1000,TimeUnit.MILLISECONDS);
        executor.scheduleWithFixedDelay(this::safeSync,3000,2500,TimeUnit.MILLISECONDS);
    }
    private void safeSync() {
        try {sync();}catch(Throwable e){syncFailures++;runtime.setError("库同步暂缓："+e.getClass().getSimpleName());}
    }
    synchronized void loadAccount() {
        try {
            boolean changed=refreshAccount();
            Config stored=library.configOrNull();
            if(stored!=null && (changed || config.selected.isEmpty())) config=stored;
            int styles=stored==null?0:stored.selected.size();
            runtime.log("读取账号气泡库 "+library.mask()+" 样式 "+styles+saveFailure());
        } catch(Throwable ignored) { /* never take down QQ while resolving the account */ }
    }
    String favorite(BubbleSpec b) {
        boolean added; synchronized(this){added=remember(b);}
        try {executor.execute(this::sync);}catch(Throwable ignored){}
        return added?"added":"selected";
    }
    private void adopt(Config next) {
        if(!next.enabled || next.fixed!=config.fixed || next.groups!=config.groups
                || next.privateChats!=config.privateChats || !next.selected.equals(config.selected)) runtime.permit.clear();
        config=next;
    }
    private boolean remember(BubbleSpec b) {
        refreshAccount();
        boolean added=true;
        try {org.json.JSONArray rows=library.document().getJSONArray("bubbles");for(int i=0;i<rows.length();i++)if(JsonCodec.decode(rows.getJSONObject(i)).key().equals(b.key())){added=false;break;}}catch(Exception ignored){}
        Config next=library.add(b,config);
        if(next!=null) config=next;
        if(added)editEpoch++;
        java.util.HashSet<String> looks=new java.util.HashSet<>();
        for(BubbleSpec s:config.selected) if(s!=null) looks.add(io.github.ling.randombubble.core.BubblePicker.visual(s));
        String saved=saveFailure();
        runtime.log((added?"已收藏一种气泡":"气泡已在库中")+" "+b.label()+" "+library.mask()+" 不同样式 "+looks.size()+(saved.isEmpty()?" 已写入本机":saved)
            +(runtime.isEquipped(b)?" 与装扮相同":""));
        return added;
    }
    private String configJson() { return library.json(); }
    synchronized void offer(BubbleSpec b) { harvest(b); }
    /** Bubbles seen while reading chat. Written locally; the standalone app is not required. */
    synchronized void harvest(BubbleSpec b) {
        if(b==null || !config.collect || pending.size()>=256 || seen.containsKey(b.key()) || pending.containsKey(b.key())) return;
        pending.put(b.key(),b);
    }
    private synchronized void flushHarvest() {
        Map<String,BubbleSpec> batch;
        synchronized(this) {
            if(!config.collect || pending.isEmpty()) return;
            batch=new LinkedHashMap<>(pending);
            pending.clear();
        }
        String before=library.json();Config next=library.addAll(batch.values(),config);
        if(next!=null)config=next;
        if(!java.util.Objects.equals(before,library.json()))editEpoch++;
        for(BubbleSpec b:batch.values())seen.put(b.key(),Boolean.TRUE);
    }
    private void scanLocal() {
        try {
            java.util.List<BubbleSpec> found=BubbleScan.collect(context);
            int added=0;
            for(BubbleSpec b:found) {
                int before=config.selected.size();
                Config next=library.add(b,config);
                if(next!=null) config=next;
                if(config.selected.size()>before) added++;
            }
            runtime.log("本地气泡素材 "+found.size()+" 个，新入库 "+added+" 样式 "+config.selected.size()+saveFailure());
        } catch(Throwable e) { runtime.log("扫描本地气泡失败 "+e.getClass().getSimpleName()); }
    }
    private void sync() {
        refreshAccount();flushHarvest();
        final String owner,libraryJson,decorationJson;
        final boolean dirtyLibrary,dirtyDecoration;
        final long epoch;
        synchronized(this) {
            if(refreshAccount()) {
                editEpoch++;runtime.permit.clear();Config stored=library.configOrNull();config=stored==null?Config.off():stored;
                runtime.log("切换账号气泡库 "+library.mask()+" 样式 "+config.selected.size()+saveFailure());
            }
            owner=library.account();epoch=editEpoch;
            if("unknown".equals(owner)){localReady=false;return;}
            libraryJson=library.json();
            try {dirtyLibrary=settings.dirtyLibrary(owner);dirtyDecoration=settings.dirtyDecoration(owner);decorationJson=settings.decoration(owner).toString();}
            catch(Exception e){localReady=false;runtime.setError("QQ 内配置读取失败 "+e.getClass().getSimpleName());return;}
        }
        if(!providerAvailable()) {runtime.bridgeHealthy=false;appReachable=false;return;}
        if(!providerDue()) return;
        try {
            // Avoid Binder's transaction limit. Large libraries remain managed in QQ.
            boolean controlsOnly=libraryJson!=null && libraryJson.length()>131072;
            Bundle request=new Bundle();request.putString("account",owner);request.putBoolean("controlsOnly",controlsOnly);request.putString("diagnostics",runtime.report());
            if(!controlsOnly)request.putString("library",libraryJson);
            if(!controlsOnly && dirtyLibrary && libraryJson!=null)request.putString("hostConfig",libraryJson);
            if(dirtyDecoration) {org.json.JSONObject mirror=new org.json.JSONObject(decorationJson);mirror.remove("command");mirror.remove("expires");request.putString("hostDecoration",mirror.toString());}
            Bundle result=context.getContentResolver().call(ConfigProvider.URI,"sync",null,request);
            if(result==null)throw new IllegalStateException("Missing provider reply");
            synchronized(this) {
                refreshAccount();
                if(!owner.equals(library.account()) || epoch!=editEpoch)return;
                boolean librarySaved=result.containsKey("config") && (!dirtyLibrary || result.getBoolean("hostConfigSaved")),decorationSaved=!dirtyDecoration || result.getBoolean("hostDecorationSaved");
                if(librarySaved) {
                    String reply=result.getString("config");Config next=JsonCodec.config(reply);boolean captureChanged=next.collect!=config.collect;
                    library.replaceJson(reply);String failure=library.takeSaveError();if(failure!=null)throw new IllegalStateException(failure);adopt(next);
                    if(captureChanged){seen.clear();if(!next.collect)pending.clear();}
                }
                if(decorationSaved)settings.adopt(owner,result.getString("decoration"));
                settings.acknowledged(owner,dirtyLibrary && librarySaved,dirtyDecoration && decorationSaved);
                runtime.bridgeHealthy=true;providerEverConnected=true;lastBridgeError="";appReachable=true;
                providerOwner=owner;lastProviderSuccess=android.os.SystemClock.elapsedRealtime();
            }
        }catch(Throwable e){noteBridge(e);}
    }
    private boolean providerAvailable() {
        try {return context.getPackageManager().resolveContentProvider(ConfigProvider.AUTHORITY,0)!=null;}
        catch(Throwable e){return false;}
    }
    private boolean providerDue() {
        return appReachable || android.os.SystemClock.uptimeMillis()>=nextProviderTry;
    }
    private String saveFailure() {
        String error=library.takeSaveError();
        return error==null?"":" 写入失败 "+error;
    }
    private void noteBridge(Throwable e) {
        String name=e.getClass().getSimpleName();
        runtime.setError("配置桥接失败："+name);
        runtime.bridgeHealthy=false;
        runtime.permit.clear();
        // A stopped/restarted provider is transient, not proof of embedded-only mode.
        nextProviderTry=android.os.SystemClock.uptimeMillis()+2500;
        if(appReachable) {
            appReachable=false;
            runtime.log("可选独立应用同步暂不可达（"+name+"）；QQ 本机控制仍可使用");
        }
        if(!name.equals(lastBridgeError)) {
            lastBridgeError=name;
            XposedBridge.log("[LingBubble] config bridge unavailable: "+name);
        }
    }
}
