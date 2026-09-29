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
    private File logFile;
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
    }
    void loadAccount() {
        try {
            boolean changed=library.refresh(context);
            Config stored=library.configOrNull();
            if(stored!=null && (changed || config.selected.isEmpty())) config=stored;
            if(changed) runtime.log("读取账号气泡库 "+library.mask());
        } catch(Throwable ignored) { /* never take down QQ while resolving the account */ }
    }
    String favorite(BubbleSpec b) {
        boolean added=remember(b);
        try {
            Bundle request=new Bundle();
            request.putString("account",library.account());
            request.putString("favorite",JsonCodec.encode(b).toString());
            request.putString("diagnostics",runtime.report());
            Bundle result=context.getContentResolver().call(ConfigProvider.URI,"sync",null,request);
            if(result==null) throw new IllegalStateException("Missing provider reply");
            adopt(JsonCodec.config(result.getString("config")));
            if(result.getString("config")!=null) library.replaceJson(configJson());
            runtime.bridgeHealthy=true; lastBridgeError="";
            String status=result.getString("favoriteStatus");
            return status==null?(added?"added":"selected"):status;
        } catch(Throwable e) {
            runtime.setError("配置桥接失败："+e.getClass().getSimpleName());
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
        runtime.log((added?"已收藏一种气泡":"气泡已在库中")+" "+library.mask());
        return added;
    }
    private String configJson() { return library.json(); }
    synchronized void offer(BubbleSpec b) {
        if(!config.collect || seen.containsKey(b.key()) || pending.size()>=24) return;
        pending.put(b.key(),b);
    }
    private void sync() {
        if(library.refresh(context)) {
            Config stored=library.configOrNull();
            if(stored!=null) config=stored;
            runtime.log("切换账号气泡库 "+library.mask());
        }
        Map<String,BubbleSpec> batch;
        synchronized(this) { batch=new LinkedHashMap<>(pending); }
        try {
            JSONArray a=new JSONArray(); for(BubbleSpec b:batch.values()) a.put(JsonCodec.encode(b));
            Bundle request=new Bundle(); request.putString("account",library.account());
            request.putString("candidates",a.toString());
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
            runtime.bridgeHealthy=true; lastBridgeError="";
        } catch(Throwable e) {
            String name=e.getClass().getSimpleName();
            runtime.setError("配置桥接失败："+name);
            if(!name.equals(lastBridgeError)) {
                lastBridgeError=name;
                XposedBridge.log("[LingBubble] config bridge unavailable: "+name);
            }
            // Keep the last collected bubbles. A failed sync must not put the account bubble back.
        }
    }
}
