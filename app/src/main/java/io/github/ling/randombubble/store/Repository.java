package io.github.ling.randombubble.store;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.ling.randombubble.core.BubbleSpec;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Called only in the module app's process; QQ uses the checked provider. */
public final class Repository {
    private static Repository instance;
    private final SharedPreferences prefs;
    private String lastDiagnostics = "尚未收到 QQ 进程的诊断。安装 APK 不等于模块已注入。";
    private long heartbeat;
    private String account="unknown";
    private Repository(Context c) {
        prefs = c.getApplicationContext().getSharedPreferences("config", Context.MODE_PRIVATE);
        account=prefs.getString("currentAccount","unknown");
        selectAllExisting();
    }
    public synchronized String accountLabel() {
        if(account==null || "unknown".equals(account) || account.length()<4) return "未识别账号";
        return "尾号"+account.substring(account.length()-4);
    }
    public synchronized void useAccount(String uin) {
        if(uin==null || !uin.matches("[1-9]\\d{4,12}") || uin.equals(account)) return;
        String key="state."+uin;
        if(!prefs.contains(key) && prefs.contains("state")) prefs.edit().putString(key,prefs.getString("state","")).commit();
        account=uin;
        prefs.edit().putString("currentAccount",uin).commit();
    }
    /** Bubbles participate in sending unless the user later unchecks one. */
    private void selectAllExisting() {
        if(prefs.getBoolean("selectedByDefault",false)) return;
        try {
            JSONObject j=snapshot(); JSONArray a=j.getJSONArray("bubbles"); boolean changed=false;
            for(int i=0;i<a.length();i++) if(!a.getJSONObject(i).optBoolean("selected",false)) {
                a.getJSONObject(i).put("selected",true); changed=true;
            }
            if(changed) save(j);
        } catch(JSONException ignored) { /* keep the current library */ }
        prefs.edit().putBoolean("selectedByDefault",true).apply();
    }
    public static synchronized Repository get(Context c) {
        if (instance == null) instance = new Repository(c);
        return instance;
    }
    private String stateKey() { return "unknown".equals(account)?"state":"state."+account; }
    public synchronized JSONObject snapshot() {
        try { return JsonCodec.object(prefs.getString(stateKey(), JsonCodec.defaults().toString())); }
        catch (JSONException e) { return JsonCodec.defaults(); }
    }
    private void save(JSONObject j) throws JSONException {
        JsonCodec.config(j.toString()); // Validate before committing anything.
        if (!prefs.edit().putString(stateKey(), j.toString()).commit()) throw new JSONException("Cannot save configuration");
    }
    public synchronized void setFlag(String key, boolean value) throws JSONException {
        if (!(key.equals("enabled") || key.equals("collect") || key.equals("fixed") || key.equals("avoidRepeat")
                || key.equals("groups") || key.equals("privateChats"))) throw new JSONException("Unknown setting");
        JSONObject j = snapshot();
        if (key.equals("enabled") && value && JsonCodec.config(j.toString()).selected.isEmpty())
            throw new JSONException("请先至少勾选一个气泡");
        j.put(key,value); save(j);
    }
    public synchronized void select(String id, boolean value) throws JSONException {
        JSONObject j = snapshot(); JSONArray a = j.getJSONArray("bubbles");
        for (int i=0; i<a.length(); i++) if (JsonCodec.decode(a.getJSONObject(i)).key().equals(id)) a.getJSONObject(i).put("selected",value);
        if (JsonCodec.config(j.toString()).selected.isEmpty()) j.put("enabled",false);
        save(j);
    }
    public synchronized void rename(String id, String name) throws JSONException {
        if (name == null || name.trim().isEmpty() || name.length()>48) throw new JSONException("名称需为 1～48 字");
        JSONObject j = snapshot(); JSONArray a = j.getJSONArray("bubbles");
        for (int i=0; i<a.length(); i++) if (JsonCodec.decode(a.getJSONObject(i)).key().equals(id)) a.getJSONObject(i).put("name",name.trim());
        save(j);
    }
    public synchronized void remove(String id) throws JSONException {
        JSONObject j = snapshot(); JSONArray a = j.getJSONArray("bubbles"), result = new JSONArray();
        for (int i=0;i<a.length();i++) if (!JsonCodec.decode(a.getJSONObject(i)).key().equals(id)) result.put(a.getJSONObject(i));
        j.put("bubbles",result);
        if (JsonCodec.config(j.toString()).selected.isEmpty()) j.put("enabled",false);
        save(j);
    }
    /** One explicit favorite from the QQ menu. Stores bubble fields only and selects it for sending. */
    public synchronized String favorite(String encoded) throws JSONException {
        if(encoded==null || encoded.length()>4096) throw new JSONException("气泡数据无效");
        BubbleSpec b=JsonCodec.decode(new JSONObject(encoded));
        JSONObject j=snapshot(); JSONArray a=j.getJSONArray("bubbles");
        boolean found=false;
        for(int i=0;i<a.length();i++) if(JsonCodec.decode(a.getJSONObject(i)).key().equals(b.key())) {
            a.getJSONObject(i).put("selected",true); found=true; break;
        }
        if(!found) a.put(JsonCodec.encode(b).put("name",b.label()).put("selected",true));
        j.put("enabled",true); save(j); return found ? "selected" : "added";
    }
    /** Adds bubbles saved by the QQ process without removing ones edited in this app. */
    public synchronized int importHost(String raw) throws JSONException {
        if(raw==null || raw.length()>JsonCodec.MAX_JSON_CHARS) return 0;
        JSONObject incoming=JsonCodec.object(raw);
        JSONArray extra=incoming.getJSONArray("bubbles");
        if(extra.length()==0) return 0;
        JSONObject j=snapshot(); JSONArray a=j.getJSONArray("bubbles");
        Set<String> known=new HashSet<>();
        for(int i=0;i<a.length();i++) known.add(JsonCodec.decode(a.getJSONObject(i)).key());
        int added=0;
        for(int i=0;i<extra.length();i++) {
            JSONObject row=extra.getJSONObject(i);
            BubbleSpec b=JsonCodec.decode(row);
            if(known.add(b.key())) {
                String name=row.optString("name",b.label());
                if(name.length()>48) name=name.substring(0,48);
                a.put(JsonCodec.encode(b).put("name",name).put("selected",row.optBoolean("selected",true)));
                added++;
            }
        }
        if(added>0) { j.put("enabled",true); save(j); }
        return added;
    }
    /** Host suggestions cannot enable features or select a bubble. */
    public synchronized void observe(String encoded) throws JSONException {
        if (encoded == null || encoded.length()>32768) return;
        JSONObject j=snapshot(); if (!j.optBoolean("collect",false)) return;
        JSONArray incoming=new JSONArray(encoded), a=j.getJSONArray("bubbles");
        if (incoming.length()>24) return;
        Set<String> known=new HashSet<>();
        for(int i=0;i<a.length();i++) known.add(JsonCodec.decode(a.getJSONObject(i)).key());
        boolean changed=false;
        for(int i=0;i<incoming.length();i++) {
            BubbleSpec b=JsonCodec.decode(incoming.getJSONObject(i));
            if(known.add(b.key())) {
                a.put(JsonCodec.encode(b).put("name",b.label()).put("selected",true)); changed=true;
            }
        }
        if(changed) save(j);
    }
    public synchronized String exportLibrary() throws JSONException {
        JSONArray a=snapshot().getJSONArray("bubbles"), clean=new JSONArray();
        for(int i=0;i<a.length();i++) {
            JSONObject row=a.getJSONObject(i);
            clean.put(JsonCodec.encode(JsonCodec.decode(row)).put("name",row.optString("name","气泡")));
        }
        return new JSONObject().put("schema",1).put("format","LingRandomBubble-library")
                .put("bubbles",clean).toString(2);
    }
    public synchronized int importLibrary(String raw) throws JSONException {
        JSONObject input=JsonCodec.object(raw);
        if(input.optInt("schema",0)!=1 || !input.optString("format","").equals("LingRandomBubble-library"))
            throw new JSONException("不是本模块的 schema=1 气泡库");
        JSONArray incoming=input.getJSONArray("bubbles");
        JSONObject j=snapshot(); JSONArray a=j.getJSONArray("bubbles"); Set<String> ids=new HashSet<>();
        for(int i=0;i<a.length();i++) ids.add(JsonCodec.decode(a.getJSONObject(i)).key());
        int added=0;
        for(int i=0;i<incoming.length();i++) {
            JSONObject row=incoming.getJSONObject(i); BubbleSpec b=JsonCodec.decode(row);
            if(ids.add(b.key())) {
                String name=row.optString("name",b.label());
                if(name.length()>48) name=name.substring(0,48);
                a.put(JsonCodec.encode(b).put("name",name).put("selected",true)); added++;
            }
        }
        j.put("enabled",false); save(j); return added;
    }
    public synchronized void clearLibrary() throws JSONException {
        JSONObject j=snapshot(); j.put("enabled",false); j.put("collect",false); j.put("bubbles",new JSONArray()); save(j);
    }
    public synchronized void diagnostics(String text) {
        if(text!=null && text.length()<12000) { lastDiagnostics=text; heartbeat=System.currentTimeMillis(); }
    }
    public synchronized String diagnostics() {
        long age=heartbeat==0 ? -1 : Math.max(0,(System.currentTimeMillis()-heartbeat)/1000);
        return "最近进程报告："+(age<0 ? "尚未收到" : age+" 秒前")+"\n\n"+lastDiagnostics;
    }
}
