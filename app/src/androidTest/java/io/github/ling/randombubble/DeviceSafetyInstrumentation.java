package io.github.ling.randombubble;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import io.github.ling.randombubble.core.BubbleSpec;
import io.github.ling.randombubble.store.ConfigProvider;
import io.github.ling.randombubble.store.JsonCodec;
import io.github.ling.randombubble.store.Repository;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;
import java.util.Set;

/** Real Android configuration tests; never sends messages or changes QQ data. */
public final class DeviceSafetyInstrumentation extends DeviceInteractionInstrumentation {
    private int passed;
    private void check(String name,boolean value) {
        if(!value) throw new AssertionError(name);
        passed++;
        Bundle progress=new Bundle(); progress.putString("stream","PASS "+name+"\n"); sendStatus(0,progress);
    }
    @Override public void onStart() {
        if(arguments!=null && arguments.containsKey("operation")) { super.onStart(); return; }
        Bundle result=new Bundle(); int status=0;
        Context context=getTargetContext();
        SharedPreferences prefs=context.getSharedPreferences("config",Context.MODE_PRIVATE);
        Map<String,?> saved=prefs.getAll();
        Repository fixtureRepository=null;
        java.lang.reflect.Field accountField=null;
        Object savedAccount=null;
        try {
            io.github.ling.randombubble.hook.HostSettingsDeviceChecks.run(context,name -> check(name,true));
            io.github.ling.randombubble.hook.AccountLibraryDeviceChecks.run(context,name -> check(name,true));
            JSONObject row=JsonCodec.encode(new BubbleSpec(17,17,0L,101,201,null,0));
            JSONObject legacy=JsonCodec.defaults(); legacy.remove("safetyVersion");
            legacy.put("enabled",true).put("collect",true);
            legacy.put("bubbles",new JSONArray().put(new JSONObject(row.toString()).put("selected",true)));
            fixtureRepository=Repository.get(context);
            accountField=Repository.class.getDeclaredField("account");accountField.setAccessible(true);
            savedAccount=accountField.get(fixtureRepository);
            // Force a distinct in-memory account before clearing storage so reruns cannot
            // short-circuit useAccount and skip migration of the synthetic legacy document.
            fixtureRepository.useAccount("87654321");
            if(!prefs.edit().clear().putString("state",legacy.toString()).commit())throw new IllegalStateException("Cannot initialize isolated repository fixture");
            Repository repo=fixtureRepository;
            repo.useAccount("12345678");
            JSONObject migrated=repo.snapshot();
            check("legacy sending disabled",!migrated.getBoolean("enabled"));
            check("legacy collection disabled",!migrated.getBoolean("collect"));
            check("legacy rows deselected",!migrated.getJSONArray("bubbles").getJSONObject(0).getBoolean("selected"));
            check("server numeric flags accepted",io.github.ling.randombubble.core.ServerFlags.value(1) && !io.github.ling.randombubble.core.ServerFlags.value(0));
            boolean flagRejected=false;try{io.github.ling.randombubble.core.ServerFlags.value(null);}catch(IllegalArgumentException expected){flagRejected=true;}
            check("missing server flag rejected",flagRejected);
            flagRejected=false;try{io.github.ling.randombubble.core.ServerFlags.value(2);}catch(IllegalArgumentException expected){flagRejected=true;}
            check("unknown server flag rejected",flagRejected);
            repo.clearLibrary(); repo.favorite(row.toString());
            check("favorite does not enable sending",!repo.snapshot().getBoolean("enabled"));
            check("favorite does not select styles",JsonCodec.config(repo.snapshot().toString()).selected.isEmpty());
            repo.select(new BubbleSpec(17,17,0L,101,201,null,0).key(),true);
            repo.setFlag("enabled",true); repo.setFlag("enabled",false);
            check("off switch is retained",!repo.snapshot().getBoolean("enabled"));
            repo.clearLibrary();
            repo.importHost(new JSONObject().put("bubbles",new JSONArray().put(new JSONObject(row.toString()).put("selected",true))).toString());
            check("host import cannot enable sending",!repo.snapshot().getBoolean("enabled"));
            check("host import cannot select styles",JsonCodec.config(repo.snapshot().toString()).selected.isEmpty());
            repo.setFlag("collect",true);
            JSONObject second=JsonCodec.encode(new BubbleSpec(17,17,0L,102,202,null,0));
            repo.observe(new JSONArray().put(second).toString());
            check("harvest does not select styles",JsonCodec.config(repo.snapshot().toString()).selected.isEmpty());
            repo.select(new BubbleSpec(17,17,0L,101,201,null,0).key(),true); repo.setFlag("enabled",true);
            check("explicit opt-in enables sending",repo.snapshot().getBoolean("enabled"));
            repo.importLibrary(repo.exportLibrary());
            check("library import disables sending",!repo.snapshot().getBoolean("enabled"));
            Bundle reply=context.getContentResolver().call(ConfigProvider.URI,"sync",null,new Bundle());
            check("same UID provider reply",reply!=null && reply.getString("config")!=null);
            check("provider preserves off switch",!JsonCodec.config(reply.getString("config")).enabled);
            check("same UID cannot consume decoration request",reply.getString("decoration")==null);
            repo.useAccount("12345678");
            check("rotation defaults off",!repo.decorationSettings().getBoolean("automatic") && !repo.decorationSettings().getBoolean("perMessage"));
            boolean rejected=false;
            try { repo.configureDecoration("101,102",59,true,false); } catch(IllegalArgumentException expected) { rejected=true; }
            check("sub-minute rotation rejected",rejected);
            rejected=false;
            try { repo.configureDecoration("101",60,true,false); } catch(IllegalArgumentException expected) { rejected=true; }
            check("single-style rotation rejected",rejected);
            repo.configureDecoration("101,102",60,true,false);
            check("high frequency saved explicitly",repo.decorationSettings().getInt("seconds")==60 && repo.decorationSettings().getBoolean("automatic"));
            check("legacy standalone overload leaves per-message off",!repo.decorationSettings().getBoolean("perMessage"));
            repo.configureDecoration("101,102",1800,true,false);
            check("low frequency saved explicitly",repo.decorationSettings().getInt("seconds")==1800);
            repo.configureDecoration("101",1800,false,true);
            check("manual request expires",repo.decorationSettings().getLong("expires")>System.currentTimeMillis());
            check("manual request consumed once",new JSONObject(repo.takeDecoration()).has("command") && !new JSONObject(repo.takeDecoration()).has("command"));
            rejected=false;
            try { repo.configureDecoration("101,101",60,false,false,true); } catch(IllegalArgumentException expected) { rejected=true; }
            check("standalone single-style per-message mode rejected",rejected);
            repo.configureDecoration("101,102",86400,false,true,true);
            check("standalone per-message mode is independent of timer",repo.decorationSettings().getBoolean("perMessage") && !repo.decorationSettings().getBoolean("automatic") && repo.decorationSettings().getInt("seconds")==86400);
            check("standalone per-message mode cannot queue manual command",!repo.decorationSettings().has("command") && !repo.decorationSettings().has("expires"));
            repo.configureDecoration("101,102",60,true,false,true);
            check("standalone allows both independent mode flags",repo.decorationSettings().getBoolean("perMessage") && repo.decorationSettings().getBoolean("automatic"));
            repo.stopDecoration();
            check("standalone stop disables both rotation modes",!repo.decorationSettings().getBoolean("perMessage") && !repo.decorationSettings().getBoolean("automatic") && !repo.decorationSettings().has("command"));
            repo.configureDecoration("101,102",60,false,false,true);
            repo.useAccount("87654321");
            check("account rotation isolation",!repo.decorationSettings().optBoolean("automatic") && !repo.decorationSettings().getBoolean("perMessage") && !repo.decorationSettings().has("command"));
            check("account switch cannot enable old path",!repo.snapshot().optBoolean("enabled"));
            repo.favorite(row.toString()); repo.favorite(second.toString());
            repo.selectAll(true);
            check("select all covers library",JsonCodec.config(repo.snapshot().toString()).selected.size()==2);
            check("select all cannot enable rotation",!repo.decorationSettings().optBoolean("automatic") && !repo.snapshot().optBoolean("enabled"));
            check("checked IDs feed decoration form",repo.selectedDecorationIds().equals("101,102"));
            repo.selectAll(false);
            check("deselect all clears selection",JsonCodec.config(repo.snapshot().toString()).selected.isEmpty());
            StringBuilder largePool=new StringBuilder();for(int i=0;i<1000;i++){if(i>0)largePool.append(',');largePool.append(2000000+i);}
            repo.configureDecoration(largePool.toString(),1800,true,false,true);
            check("1000-style pool accepted",repo.decorationSettings().getJSONArray("ids").length()==1000);
            repo.clearLibrary();
            check("clear library stops rotation",!repo.decorationSettings().optBoolean("automatic") && !repo.decorationSettings().getBoolean("perMessage") && !repo.decorationSettings().has("command"));
            result.putString("stream","RESULT: "+passed+" Android device safety tests passed. QQ delivery NOT tested.\n");
            status=-1;
        } catch(Throwable e) {
            result.putString("stream","FAILED: "+e.getClass().getSimpleName()+" "+e.getMessage()+"\n");
        } finally {
            SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> entry:saved.entrySet()) {
                Object value=entry.getValue(); String key=entry.getKey();
                if(value instanceof String) restore.putString(key,(String)value);
                else if(value instanceof Boolean) restore.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer) restore.putInt(key,(Integer)value);
                else if(value instanceof Long) restore.putLong(key,(Long)value);
                else if(value instanceof Float) restore.putFloat(key,(Float)value);
                else if(value instanceof Set) restore.putStringSet(key,(Set<String>)value);
            }
            if(!restore.commit()) { status=0; result.putString("stream","FAILED: restore test preferences\n"); }
            if(accountField!=null && fixtureRepository!=null) {
                try {synchronized(fixtureRepository){accountField.set(fixtureRepository,savedAccount);}}
                catch(Exception error) {status=0;result.putString("stream","FAILED: restore repository account\n");}
            }
        }
        finish(status,result);
    }
}
