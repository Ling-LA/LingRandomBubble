package io.github.ling.randombubble;

import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import io.github.ling.randombubble.store.Repository;
import io.github.ling.randombubble.store.JsonCodec;
import org.json.JSONObject;
import org.json.JSONArray;

/** Test APK only: clipboard and explicit existing-library selections for device tests.
 * Does not send messages, add styles, or modify QQ files.
 */
public class DeviceInteractionInstrumentation extends Instrumentation {
    protected Bundle arguments;
    @Override public void onCreate(Bundle args) { arguments=args; super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle(); int status=0;
        try {
            Context context=getTargetContext();
            String operation=arguments.getString("operation","");
            if(operation.equals("restoreSelections")) {
                Repository repo=Repository.get(context);
                java.util.Set<String> keys=new java.util.HashSet<>();
                org.json.JSONArray requested=new org.json.JSONArray(arguments.getString("keys","[]"));
                for(int i=0;i<requested.length();i++) keys.add(requested.getString(i));
                repo.selectAll(false);
                org.json.JSONArray rows=repo.snapshot().getJSONArray("bubbles");
                for(int i=0;i<rows.length();i++) {
                    String key=JsonCodec.decode(rows.getJSONObject(i)).key();
                    if(keys.contains(key))repo.select(key,true);
                }
                result.putString("stream","PASS restored exact test selections\n");
            } else if(operation.equals("accountBubble")) {
                Repository.get(context).configureDecoration(arguments.getString("ids",""),Integer.parseInt(arguments.getString("seconds","1800")),arguments.getString("automatic","false").equals("true"),arguments.getString("manual","true").equals("true"));
                result.putString("stream","PASS explicit account decoration configuration\n");
            } else if(operation.equals("clipboard")) {
                String text=arguments.getString("text","");
                if(text.length()>120) throw new IllegalArgumentException("Test text too long");
                ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("LingBubble device test",text));
                result.putString("stream","PASS test clipboard set\n");
            } else if(operation.equals("diagnostics")) {
                Repository repo=Repository.get(context);
                Thread.sleep(6000);
                long deadline=android.os.SystemClock.uptimeMillis()+130000;
                // A diagnostic test process stays alive until the host bridge reconnects.
                while(android.os.SystemClock.uptimeMillis()<deadline && repo.diagnostics().contains("尚未收到"))
                    Thread.sleep(1000);
                result.putString("stream",repo.diagnostics()+"\n");
            } else if(operation.equals("configure")) {
                Repository repo=Repository.get(context);
                JSONObject document=repo.snapshot();
                JSONArray rows=document.getJSONArray("bubbles");
                String ids=","+arguments.getString("ids","")+",";
                int selected=0;
                java.util.Set<Integer> chosen=new java.util.HashSet<>();
                for(int i=0;i<rows.length();i++) {
                    JSONObject row=rows.getJSONObject(i);
                    int id=row.optInt("bubbleId",-1);
                    boolean choose=ids.contains(","+id+",") && !row.isNull("subBubbleId") && !chosen.contains(id);
                    if(choose) chosen.add(id);
                    row.put("selected",choose);
                    if(choose) selected++;
                }
                boolean enabled=arguments.getString("enabled","false").equals("true");
                if(enabled && selected==0) throw new IllegalArgumentException("Chosen owned bubble is absent from library");
                document.put("fixed",arguments.getString("fixed","true").equals("true")).put("enabled",enabled);
                JsonCodec.config(document.toString());
                android.content.SharedPreferences preferences=context.getSharedPreferences("config",Context.MODE_PRIVATE);
                String account=preferences.getString("currentAccount","unknown");
                String key=account.equals("unknown")?"state":"state."+account;
                if(!preferences.edit().putString(key,document.toString()).commit()) throw new IllegalStateException("Cannot save test configuration");
                result.putString("stream","PASS explicit test configuration; selected rows="+selected+", sending="+enabled+"\n");
            } else throw new IllegalArgumentException("Unknown test operation");
            status=-1;
        } catch(Throwable error) { result.putString("stream","FAILED "+error.getClass().getSimpleName()+" "+error.getMessage()+"\n"); }
        finish(status,result);
    }
}
