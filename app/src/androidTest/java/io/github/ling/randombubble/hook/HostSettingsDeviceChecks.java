package io.github.ling.randombubble.hook;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.ling.randombubble.store.DecorationCodec;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Isolated Android persistence checks. Never opens QQ or touches its preferences. */
public final class HostSettingsDeviceChecks {
    private static final String PREFS="LingBubble-host-config-device-test";
    private static final String FIRST="12345678", SECOND="87654321";
    private HostSettingsDeviceChecks() {}
    private interface Work { void run() throws Exception; }
    private static void check(Consumer<String> passed,String name,boolean value) {
        if(!value) throw new AssertionError(name); passed.accept(name);
    }
    private static void rejected(Consumer<String> passed,String name,Work work) throws Exception {
        boolean denied=false;
        try { work.run(); } catch(IllegalArgumentException | IllegalStateException | org.json.JSONException expected) { denied=true; }
        check(passed,name,denied);
    }
    private static JSONObject copy(JSONObject value) throws Exception { return new JSONObject(value.toString()); }
    public static void run(Context context,Consumer<String> passed) throws Exception {
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        Map<String,?> saved=prefs.getAll();
        try {
            if(!prefs.edit().clear().commit()) throw new IllegalStateException("Cannot initialize isolated host settings fixture");
            HostSettings settings=new HostSettings(context,PREFS);
            JSONObject defaults=settings.decoration(FIRST);
            check(passed,"embedded defaults are off without a command",!defaults.getBoolean("automatic") && !defaults.getBoolean("perMessage") && defaults.getJSONArray("ids").length()==0 && !defaults.has("command"));
            check(passed,"embedded defaults use host origin",settings.hostControlled(FIRST));
            JSONObject automatic=DecorationCodec.create(FIRST,"101,101,102",60,true,false);
            check(passed,"decoration pool deduplicates styles",automatic.getJSONArray("ids").length()==2);
            settings.configure(FIRST,automatic);
            HostSettings recreated=new HostSettings(context,PREFS);
            JSONObject persisted=recreated.decoration(FIRST);
            check(passed,"embedded settings survive recreated storage",persisted.getBoolean("automatic") && persisted.getInt("seconds")==60 && persisted.getString("generation").equals(automatic.getString("generation")));
            check(passed,"embedded host origin survives recreated storage",recreated.hostControlled(FIRST));
            JSONObject manual=DecorationCodec.create(FIRST,"101",1800,false,true); recreated.configure(FIRST,manual);
            JSONObject mirror=copy(manual);mirror.remove("command");mirror.remove("expires");recreated.adopt(FIRST,mirror.toString());
            check(passed,"embedded mirror preserves unconsumed manual command",recreated.decoration(FIRST).has("command"));
            check(passed,"embedded manual request consumed first time",new JSONObject(recreated.take(FIRST)).has("command"));
            JSONObject consumed=new JSONObject(new HostSettings(context,PREFS).take(FIRST));
            check(passed,"embedded manual request consumed only once",!consumed.has("command") && !consumed.has("expires"));
            JSONObject active=DecorationCodec.create(FIRST,"101,102",60,true,true); recreated.configure(FIRST,active);
            JSONObject stopped=DecorationCodec.stopped(recreated.decoration(FIRST),FIRST); recreated.configure(FIRST,stopped);
            check(passed,"embedded stop clears automatic and queued command",!recreated.decoration(FIRST).getBoolean("automatic") && !recreated.decoration(FIRST).has("command") && !recreated.decoration(FIRST).has("expires"));
            check(passed,"embedded stop advances configuration generation",!active.getString("generation").equals(stopped.getString("generation")));
            JSONObject other=recreated.decoration(SECOND);
            check(passed,"embedded settings isolate accounts",!other.getBoolean("automatic") && other.getJSONArray("ids").length()==0 && !other.has("command") && recreated.decoration(FIRST).getJSONArray("ids").length()==2);
            recreated.libraryEdited(FIRST);
            HostSettings dirtyRestart=new HostSettings(context,PREFS);
            check(passed,"embedded dirty flags survive recreated storage",dirtyRestart.dirtyLibrary(FIRST) && dirtyRestart.dirtyDecoration(FIRST));
            check(passed,"embedded dirty flags isolate accounts",!dirtyRestart.dirtyLibrary(SECOND) && !dirtyRestart.dirtyDecoration(SECOND));
            dirtyRestart.acknowledged(FIRST,true,false);
            check(passed,"embedded partial acknowledgement clears only library flag",!dirtyRestart.dirtyLibrary(FIRST) && dirtyRestart.dirtyDecoration(FIRST));
            dirtyRestart.acknowledged(FIRST,false,true);
            check(passed,"embedded full acknowledgement clears decoration flag",!new HostSettings(context,PREFS).dirtyDecoration(FIRST));
            JSONObject local=DecorationCodec.create(FIRST,"101,102",1800,true,false); dirtyRestart.configure(FIRST,local); dirtyRestart.acknowledged(FIRST,false,true);
            dirtyRestart.adopt(FIRST,local.toString());
            check(passed,"companion echo preserves embedded origin",new HostSettings(context,PREFS).hostControlled(FIRST));
            JSONObject remote=DecorationCodec.create(FIRST,"201,202",60,true,false); dirtyRestart.adopt(FIRST,remote.toString());
            check(passed,"remote generation switches to companion origin",!new HostSettings(context,PREFS).hostControlled(FIRST));
            check(passed,"remote generation persists its settings",new HostSettings(context,PREFS).decoration(FIRST).getJSONArray("ids").getInt(0)==201 && dirtyRestart.decoration(FIRST).getString("generation").equals(remote.getString("generation")));
            dirtyRestart.configure(FIRST,local);
            check(passed,"new embedded save restores host origin",new HostSettings(context,PREFS).hostControlled(FIRST));
            JSONObject legacy=copy(local);legacy.remove("perMessage");dirtyRestart.configure(FIRST,legacy);
            check(passed,"legacy embedded settings default per-message off",!new HostSettings(context,PREFS).decoration(FIRST).getBoolean("perMessage") && !new JSONObject(prefs.getString("decoration."+FIRST,"{}")).getBoolean("perMessage"));
            check(passed,"old codec create overload leaves per-message off",!DecorationCodec.create(FIRST,"101,102",60,true,false).getBoolean("perMessage"));
            JSONObject perMessage=DecorationCodec.create(FIRST,"301,301,302",86400,false,true,true);
            check(passed,"per-message mode works without timer rotation",perMessage.getBoolean("perMessage") && !perMessage.getBoolean("automatic") && perMessage.getJSONArray("ids").length()==2 && perMessage.getInt("seconds")==86400);
            check(passed,"per-message mode cannot queue a manual command",!perMessage.has("command") && !perMessage.has("expires"));
            dirtyRestart.configure(FIRST,perMessage);
            JSONObject messageRestart=new HostSettings(context,PREFS).decoration(FIRST);
            check(passed,"per-message mode survives storage recreation",messageRestart.getBoolean("perMessage") && !messageRestart.getBoolean("automatic"));
            dirtyRestart.adopt(FIRST,perMessage.toString());
            check(passed,"per-message companion echo preserves host settings",new HostSettings(context,PREFS).hostControlled(FIRST) && new HostSettings(context,PREFS).decoration(FIRST).getBoolean("perMessage"));
            JSONObject combined=DecorationCodec.create(FIRST,"301,302",60,true,false,true);
            dirtyRestart.adopt(FIRST,combined.toString());
            check(passed,"timer and per-message flags remain independent",dirtyRestart.decoration(FIRST).getBoolean("automatic") && dirtyRestart.decoration(FIRST).getBoolean("perMessage") && !dirtyRestart.hostControlled(FIRST));
            JSONObject messageStop=DecorationCodec.stopped(dirtyRestart.decoration(FIRST),FIRST);dirtyRestart.configure(FIRST,messageStop);
            check(passed,"embedded stop clears timer and per-message modes",!dirtyRestart.decoration(FIRST).getBoolean("automatic") && !dirtyRestart.decoration(FIRST).getBoolean("perMessage") && !dirtyRestart.decoration(FIRST).has("command"));
            check(passed,"per-message settings isolate accounts",!dirtyRestart.decoration(SECOND).getBoolean("perMessage"));
            rejected(passed,"decoration malformed JSON rejected",() -> DecorationCodec.validate("{broken",FIRST));
            rejected(passed,"decoration automatic string type rejected",() -> DecorationCodec.validate(copy(automatic).put("automatic","true").toString(),FIRST));
            rejected(passed,"decoration interval string type rejected",() -> DecorationCodec.validate(copy(automatic).put("seconds","60").toString(),FIRST));
            rejected(passed,"decoration per-message string type rejected",() -> DecorationCodec.validate(copy(perMessage).put("perMessage","true").toString(),FIRST));
            rejected(passed,"decoration per-message numeric type rejected",() -> DecorationCodec.validate(copy(perMessage).put("perMessage",1).toString(),FIRST));
            rejected(passed,"decoration per-message null type rejected",() -> DecorationCodec.validate(copy(perMessage).put("perMessage",JSONObject.NULL).toString(),FIRST));
            rejected(passed,"decoration pool string type rejected",() -> DecorationCodec.validate(copy(automatic).put("ids",new JSONArray().put("101").put(102)).toString(),FIRST));
            rejected(passed,"decoration manual integer expiry rejected",() -> DecorationCodec.validate(copy(manual).put("expires",100).toString(),FIRST));
            rejected(passed,"embedded wrong-owner configuration rejected",() -> dirtyRestart.configure(SECOND,automatic));
            rejected(passed,"decoration sub-minute interval rejected",() -> DecorationCodec.validate(copy(automatic).put("seconds",59).toString(),FIRST));
            rejected(passed,"decoration above-day interval rejected",() -> DecorationCodec.validate(copy(automatic).put("seconds",86401).toString(),FIRST));
            rejected(passed,"decoration single-style automatic rejected",() -> DecorationCodec.validate(copy(automatic).put("ids",new JSONArray().put(101)).toString(),FIRST));
            rejected(passed,"decoration repeated single-style automatic rejected",() -> DecorationCodec.validate(copy(automatic).put("ids",new JSONArray().put(101).put(101)).toString(),FIRST));
            rejected(passed,"decoration single-style per-message rejected",() -> DecorationCodec.validate(copy(perMessage).put("ids",new JSONArray().put(301)).toString(),FIRST));
            rejected(passed,"decoration repeated single-style per-message rejected",() -> DecorationCodec.validate(copy(perMessage).put("ids",new JSONArray().put(301).put(301)).toString(),FIRST));
            rejected(passed,"per-message create rejects one deduplicated style",() -> DecorationCodec.create(FIRST,"301,301",60,false,false,true));
            rejected(passed,"per-message configuration rejects manual command",() -> DecorationCodec.validate(copy(perMessage).put("command","manual").put("expires",System.currentTimeMillis()+30000).toString(),FIRST));
            rejected(passed,"embedded unknown account rejected",() -> dirtyRestart.decoration("unknown"));
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
            if(!restore.commit()) throw new IllegalStateException("Cannot restore isolated host settings fixture preferences");
        }
    }
}
