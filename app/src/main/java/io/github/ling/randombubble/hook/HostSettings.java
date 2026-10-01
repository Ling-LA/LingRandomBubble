package io.github.ling.randombubble.hook;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.ling.randombubble.store.DecorationCodec;
import org.json.*;

/** QQ-private persistent controls; no external module package is required. */
final class HostSettings {
    private final Context context;
    private final String name;
    private SharedPreferences storage;
    HostSettings(Context context) {this(context,"LingBubble-host-config");}
    HostSettings(Context context,String name) {this.context=context;this.name=name;}
    private SharedPreferences prefs() {
        if(storage==null)storage=context.getSharedPreferences(name,Context.MODE_PRIVATE);
        if(storage==null)throw new IllegalStateException("QQ 内配置存储不可用");return storage;
    }
    synchronized JSONObject decoration(String owner) throws JSONException {
        DecorationCodec.owner(owner);String raw=prefs().getString("decoration."+owner,null);
        return raw==null?DecorationCodec.defaults(owner):DecorationCodec.validate(raw,owner);
    }
    synchronized void configure(String owner,JSONObject j) throws JSONException {
        j=DecorationCodec.validate(j.toString(),owner);
        if(!prefs().edit().putString("decoration."+owner,j.toString()).putString("origin."+owner,"host")
            .putBoolean("dirtyDecoration."+owner,true).commit())throw new IllegalStateException("QQ 内配置保存失败");
    }
    synchronized boolean hostControlled(String owner) {return !"companion".equals(prefs().getString("origin."+owner,"host"));}
    synchronized String take(String owner) throws JSONException {
        JSONObject j=decoration(owner);String raw=j.toString();
        if(j.has("command")) {j.remove("command");j.remove("expires");if(!prefs().edit().putString("decoration."+owner,j.toString()).commit())throw new IllegalStateException("手动请求消费失败");}
        return raw;
    }
    synchronized void adopt(String owner,String raw) throws JSONException {
        JSONObject j;
        if(raw==null || !new JSONObject(raw).has("generation"))j=DecorationCodec.defaults(owner);
        else j=DecorationCodec.validate(raw,owner);
        JSONObject local=decoration(owner);
        boolean same=j.optString("generation").equals(local.optString("generation"));
        // A mirror intentionally omits manual commands; retain an unconsumed local request.
        if(same && !j.optBoolean("perMessage",false) && !j.has("command") && local.has("command") && local.optLong("expires",0)>System.currentTimeMillis()) {
            j.put("command",local.getString("command")).put("expires",local.getLong("expires"));
        }
        SharedPreferences.Editor edit=prefs().edit().putString("decoration."+owner,j.toString());
        if(!same)edit.putString("origin."+owner,"companion");
        if(!edit.commit())throw new IllegalStateException("装扮配置同步保存失败");
    }
    synchronized boolean dirtyLibrary(String owner) {return prefs().getBoolean("dirtyLibrary."+owner,false);}
    synchronized boolean dirtyDecoration(String owner) {return prefs().getBoolean("dirtyDecoration."+owner,false);}
    synchronized void libraryEdited(String owner) {if(!prefs().edit().putBoolean("dirtyLibrary."+owner,true).commit())throw new IllegalStateException("QQ 内编辑状态保存失败");}
    synchronized void acknowledged(String owner,boolean library,boolean decoration) {
        if(!library && !decoration)return;
        SharedPreferences.Editor e=prefs().edit();if(library)e.putBoolean("dirtyLibrary."+owner,false);if(decoration)e.putBoolean("dirtyDecoration."+owner,false);
        if(!e.commit())throw new IllegalStateException("同步状态保存失败");
    }
}
