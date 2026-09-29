package io.github.ling.randombubble.hook;

import android.content.Context;
import android.os.Bundle;
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
    private String lastBridgeError="";
    HostBridge(Context context,HostRuntime runtime) { this.context=context; this.runtime=runtime; }
    void start() { executor.scheduleWithFixedDelay(this::sync,0,2500,TimeUnit.MILLISECONDS); }
    synchronized void offer(BubbleSpec b) {
        if(!config.collect || seen.containsKey(b.key()) || pending.size()>=24) return;
        pending.put(b.key(),b);
    }
    private void sync() {
        Map<String,BubbleSpec> batch;
        synchronized(this) { batch=new LinkedHashMap<>(pending); }
        try {
            JSONArray a=new JSONArray(); for(BubbleSpec b:batch.values()) a.put(JsonCodec.encode(b));
            Bundle request=new Bundle(); request.putString("candidates",a.toString());
            request.putString("diagnostics",runtime.report());
            Bundle result=context.getContentResolver().call(ConfigProvider.URI,"sync",null,request);
            if(result==null) throw new IllegalStateException("Missing provider reply");
            Config next=JsonCodec.config(result.getString("config"));
            boolean captureChanged=next.collect!=config.collect;
            config=next;
            synchronized(this) {
                for(String key:batch.keySet()) { pending.remove(key); seen.put(key,Boolean.TRUE); }
                if(captureChanged) { seen.clear(); if(!next.collect) pending.clear(); }
            }
            runtime.bridgeHealthy=true; lastBridgeError="";
        } catch(Throwable e) {
            config=Config.off(); runtime.bridgeHealthy=false;
            String name=e.getClass().getSimpleName();
            runtime.setError("配置桥接失败："+name);
            if(!name.equals(lastBridgeError)) {
                lastBridgeError=name;
                XposedBridge.log("[LingBubble] config bridge unavailable: "+name);
            }
            // No permissive fallback: if the config is inaccessible, never modify sends.
        }
    }
}
