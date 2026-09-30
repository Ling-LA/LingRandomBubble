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
        @Override protected boolean removeEldestEntry(Map.Entry<String,Boolean> e) { return size()>256; }
    };
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"LingBubble-Sync"); t.setDaemon(true); return t;
    });
    volatile Config config=Config.off();
    private final AccountLibrary library;
    private String lastBridgeError="";
    /** False once the standalone module app turned out to be unreachable (embedded NPatch install). */
    private volatile boolean appReachable=true;
    private volatile long nextProviderTry;
    private File logFile;
    interface Edit { void apply(org.json.JSONObject document) throws Exception; }
    /** Applies one change from the in-QQ settings panel to this account's library. Returns an error text or null. */
    synchronized String edit(Edit change) {
        try {
            org.json.JSONObject document=library.document();
            change.apply(document);
            config=library.store(document);
            String failure=saveFailure();
            runtime.log("设置已更新 样式 "+config.selected.size()+failure);
            return failure.isEmpty()?null:failure.trim();
        } catch(Exception e) {
            return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        }
    }
    org.json.JSONObject document() {
        try { return library.document(); } catch(Exception e) { return JsonCodec.defaults(); }
    }
    boolean appReachable() { return appReachable; }
    void writeLog(String row) {
        try {
            if(logFile==null) {
                File dir=context.getExternalFilesDir(null);
                if(dir==null) dir=context.getFilesDir();
                logFile=new File(dir,"LingRandomBubble-log.txt");
                try {
                    File download=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"LingRandomBubble-log.txt");
                    if(download.getParentFile()!=null) download.getParentFile().mkdirs();
                    logFile=download;
                } catch(Throwable ignored) { /* keep the app-specific file */ }
            }
            try(FileOutputStream out=new FileOutputStream(logFile,true)) {
                out.write((row+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch(Throwable ignored) { /* logging must not affect sending */ }
    }
    String logPath() { return logFile==null?"Download/LingRandomBubble-log.txt":logFile.getAbsolutePath(); }
    String libraryMask() { return library==null?"未识别账号":library.mask(); }
    HostBridge(Context context,HostRuntime runtime) {
        this.context=context; this.runtime=runtime; this.library=new AccountLibrary(context);
    }
    void start() {
        executor.scheduleWithFixedDelay(this::sync,3000,2500,TimeUnit.MILLISECONDS);
        executor.schedule(this::scanLocal,6,TimeUnit.SECONDS);
    }
    void loadAccount() {
        try {
            boolean changed=library.refresh(context);
            prepareSending();
            Config stored=library.configOrNull();
            if(stored!=null && (changed || config.selected.isEmpty())) config=stored;
            int styles=stored==null?0:stored.selected.size();
            runtime.log("读取账号气泡库 "+library.mask()+" 样式 "+styles+saveFailure());
        } catch(Throwable ignored) { /* never take down QQ while resolving the account */ }
    }
    String favorite(BubbleSpec b) {
        boolean added=remember(b);
        if(!providerDue()) return added?"added":"selected";
        try {
            Bundle request=new Bundle();
            request.putString("account",library.account());
            request.putString("favorite",JsonCodec.encode(b).toString());
            request.putString("library",library.json());
            request.putString("diagnostics",runtime.report());
            Bundle result=context.getContentResolver().call(ConfigProvider.URI,"sync",null,request);
            if(result==null) throw new IllegalStateException("Missing provider reply");
            adopt(JsonCodec.config(result.getString("config")));
            if(result.getString("config")!=null) library.replaceJson(configJson());
            runtime.bridgeHealthy=true; lastBridgeError=""; appReachable=true;
            String status=result.getString("favoriteStatus");
            return status==null?(added?"added":"selected"):status;
        } catch(Throwable e) {
            noteBridge(e);
            return added?"added":"selected";
        }
    }
    private void adopt(Config next) {
        if(next.selected.isEmpty() && !config.selected.isEmpty())
            config=new Config(true,config.collect,config.fixed,config.avoidRepeat,config.groups,config.privateChats,config.selected);
        else config=next;
    }
    private boolean remember(BubbleSpec b) {
        library.refresh(context);
        java.util.ArrayList<BubbleSpec> before=new java.util.ArrayList<>(config.selected);
        boolean added=!before.contains(b);
        Config next=library.add(b,config);
        if(next!=null) config=next;
        runtime.bridgeHealthy=true;
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
        if(b==null || !config.collect || seen.containsKey(b.key()) || pending.containsKey(b.key())) return;
        pending.put(b.key(),b);
    }
    private void flushHarvest() {
        Map<String,BubbleSpec> batch;
        synchronized(this) {
            if(!config.collect || pending.isEmpty()) return;
            batch=new LinkedHashMap<>(pending);
            pending.clear();
        }
        int added=0;
        for(BubbleSpec b:batch.values()) {
            int before=config.selected.size();
            Config next=library.add(b,config);
            if(next!=null) config=next;
            if(config.selected.size()>before) added++;
            synchronized(this) { seen.put(b.key(),Boolean.TRUE); }
        }
        if(added>0) runtime.log("自动收录 "+added+" 种 样式 "+config.selected.size()+saveFailure());
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
    /** One-time correction after the invisible switches turned fixed mode on by accident. */
    private void prepareSending() {
        try {
            org.json.JSONObject doc=library.document();
            if(doc.optBoolean("panelReady",false)) return;
            doc.put("collect",true);
            doc.put("fixed",false);
            doc.put("enabled",true);
            doc.put("groups",true);
            doc.put("privateChats",true);
            doc.put("avoidRepeat",true);
            doc.put("panelReady",true);
            config=library.store(doc);
            runtime.log("已改为随机发送，并打开自动收录"+saveFailure());
        } catch(Throwable e) { runtime.log("初始化发送方式失败 "+e.getClass().getSimpleName()); }
    }
    private void sync() {
        flushHarvest();
        if(library.refresh(context)) {
            Config stored=library.configOrNull();
            if(stored!=null) config=stored;
            runtime.log("切换账号气泡库 "+library.mask()+" 样式 "+(stored==null?0:stored.selected.size())+saveFailure());
        }
        if(!providerDue()) return;
        Map<String,BubbleSpec> batch;
        synchronized(this) { batch=new LinkedHashMap<>(pending); }
        try {
            JSONArray a=new JSONArray(); for(BubbleSpec b:batch.values()) a.put(JsonCodec.encode(b));
            Bundle request=new Bundle();             request.putString("account",library.account());
            request.putString("candidates",a.toString());
            request.putString("library",library.json());
            request.putString("diagnostics",runtime.report());
            Bundle result=context.getContentResolver().call(ConfigProvider.URI,"sync",null,request);
            if(result==null) throw new IllegalStateException("Missing provider reply");
            Config next=JsonCodec.config(result.getString("config"));
            boolean captureChanged=next.collect!=config.collect;
            adopt(next);
            synchronized(this) {
                for(String key:batch.keySet()) { pending.remove(key); seen.put(key,Boolean.TRUE); }
                if(captureChanged) { seen.clear(); if(!next.collect) pending.clear(); }
            }
            runtime.bridgeHealthy=true; lastBridgeError=""; appReachable=true;
        } catch(Throwable e) {
            noteBridge(e);
            // Keep the last collected bubbles. A failed sync must not put the account bubble back.
        }
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
        // Unknown authority: the module APK is embedded into QQ and its own app is not installed.
        nextProviderTry=android.os.SystemClock.uptimeMillis()+120000;
        if(appReachable) {
            appReachable=false;
            runtime.log("模块独立 App 不可达（"+name+"），配置只保存在 QQ 本地，设置面板在 QQ 内显示");
        }
        if(!name.equals(lastBridgeError)) {
            lastBridgeError=name;
            XposedBridge.log("[LingBubble] config bridge unavailable: "+name);
        }
    }
}
