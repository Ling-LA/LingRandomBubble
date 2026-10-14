package io.github.ling.randombubble.hook;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import io.github.ling.randombubble.core.NameLookupQueue;
import io.github.ling.randombubble.store.LibraryRows;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import org.json.JSONArray;
import org.json.JSONObject;

/** Enrich new captures and old unnamed entries without blocking the send/timer path. */
final class AutomaticBubbleNames {
    private final Context context;private final HostRuntime runtime;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final NameLookupQueue queue=new NameLookupQueue();
    private String lastOwner="",lastRaw; // Only the bridge worker touches these two fields.
    private volatile String status="收录后自动补全名称";
    private boolean paused,failed;
    private NameLookupQueue.Attempt outstanding;
    AutomaticBubbleNames(Context context,HostRuntime runtime){this.context=context;this.runtime=runtime;}
    String status(){return status;}
    /** Called on the existing sync worker: large JSON scans stay off the UI/timer threads. */
    void refresh(String owner,String raw) {
        if(raw==null)return;
        try {
            if(!owner.equals(lastOwner) || !raw.equals(lastRaw)) {
                LinkedHashSet<Integer> missing=new LinkedHashSet<>();JSONArray rows=new JSONObject(raw).getJSONArray("bubbles");
                for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.optInt("bubbleId")>0 && LibraryRows.officialName(row).isEmpty())missing.add(row.optInt("bubbleId"));}
                lastOwner=owner;lastRaw=raw;
                main.post(()->{if(!owner.equals(queue.owner())){paused=false;failed=false;}queue.update(owner,missing);pump();});
            }else main.post(this::pump);
        }catch(Exception e){status="名称等待下次同步";}
    }
    void toggle(){main.post(()->{
        if(!paused && !failed && queue.size()>0){paused=true;status="继续补全名称 · 剩余 "+queue.size()+" 款";}
        else{paused=false;failed=false;queue.retry(SystemClock.elapsedRealtime());pump();}
    });}
    private void pump() {
        try{pumpSafely();}catch(Throwable error){
            if(outstanding!=null)complete(outstanding,false);
            status="名称查询暂缓 · 点击重试";
        }
    }
    private void pumpSafely() {
        if(paused){status="继续补全名称 · 剩余 "+queue.size()+" 款";return;}
        if(queue.size()==0){status="商城名称已补全";return;}
        if(!queue.owner().equals(AccountRef.current(context)))return;
        if(queue.inFlight())return;
        if(!runtime.decoration.nameLookupReady()) {status="名称待补全 "+queue.size()+" 款 · 自动等待";return;}
        NameLookupQueue.Attempt attempt=queue.poll(SystemClock.elapsedRealtime());
        if(attempt==null){status=failed?"名称查询暂缓 · 点击重试":"名称自动补全中 · 剩余 "+queue.size()+" 款";return;}
        outstanding=attempt;status="暂停名称补全 · 剩余 "+queue.size()+" 款";
        // AccountDecoration owns the 20s deadline and ignores duplicate/late API callbacks.
        runtime.decoration.queryBubbleName(attempt.id,(detail,error)->{
            String name=error==null?LibraryRows.detailName(detail,attempt.id):"";
            if(name.isEmpty()){complete(attempt,false);return;}
            runtime.bridge.rememberBubbleName(attempt.owner,attempt.id,name,failure->main.post(()->complete(attempt,failure==null)));
        });
    }
    private void complete(NameLookupQueue.Attempt attempt,boolean success) {
        if(outstanding==attempt)outstanding=null;
        if(!queue.finish(attempt,success,SystemClock.elapsedRealtime()))return;
        failed=!success;status=paused?"继续补全名称 · 剩余 "+queue.size()+" 款":success?"名称自动补全中 · 剩余 "+queue.size()+" 款":"名称查询暂缓 · 点击重试";
        // The worker's regular refresh resumes the queue; no recursive network loop.
    }
}
