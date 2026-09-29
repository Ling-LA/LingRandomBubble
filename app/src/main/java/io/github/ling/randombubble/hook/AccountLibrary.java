package io.github.ling.randombubble.hook;

import android.content.Context;
import io.github.ling.randombubble.core.BubbleSpec;
import io.github.ling.randombubble.store.Config;
import io.github.ling.randombubble.store.JsonCodec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;

/** One JSON library per QQ account, stored in QQ's private files directory. */
final class AccountLibrary {
    private final Context context;
    private String account="unknown";
    private String json;
    AccountLibrary(Context context) { this.context=context.getApplicationContext(); }
    String account() { return account; }
    String mask() { return AccountRef.mask(account); }
    /** @return true when the login id changed */
    boolean refresh(Context context) {
        String next=AccountRef.current(context);
        if(next.equals(account)) return false;
        String previous=account;
        String carried=json;
        save();
        account=next;
        if(load(account)) return true;
        if("unknown".equals(previous) && carried!=null) {
            json=carried;
            save();
        } else json=null;
        return true;
    }
    Config configOrNull() {
        if(json==null) return null;
        try { return JsonCodec.config(json); } catch(Exception e) { return null; }
    }
    String json() { return json; }
    void replaceJson(String value) {
        json=value;
        save();
    }
    Config add(BubbleSpec spec,Config current) {
        try {
            JSONObject document=json==null?JsonCodec.defaults():new JSONObject(json);
            JSONArray bubbles=document.getJSONArray("bubbles");
            boolean found=false;
            for(int i=0;i<bubbles.length();i++) {
                if(JsonCodec.decode(bubbles.getJSONObject(i)).key().equals(spec.key())) {
                    bubbles.getJSONObject(i).put("selected",true);
                    found=true;
                    break;
                }
            }
            if(!found && bubbles.length()<JsonCodec.MAX_LIBRARY)
                bubbles.put(JsonCodec.encode(spec).put("name",spec.label()).put("selected",true));
            document.put("enabled",true);
            if(!document.has("avoidRepeat")) document.put("avoidRepeat",true);
            json=document.toString();
            save();
            return JsonCodec.config(json);
        } catch(Exception e) {
            return current;
        }
    }
    private boolean load(String id) {
        File file=new File(dir(),fileName(id));
        if(!file.isFile()) return false;
        try {
            byte[] data=java.nio.file.Files.readAllBytes(file.toPath());
            if(data.length==0 || data.length>JsonCodec.MAX_JSON_CHARS) return false;
            String text=new String(data,StandardCharsets.UTF_8);
            JsonCodec.config(text);
            json=text;
            return true;
        } catch(Exception e) { return false; }
    }
    private void save() {
        if(json==null) return;
        try {
            File folder=dir();
            if(!folder.isDirectory() && !folder.mkdirs()) return;
            File file=new File(folder,fileName(account));
            java.nio.file.Files.write(file.toPath(),json.getBytes(StandardCharsets.UTF_8));
        } catch(Exception ignored) { /* keep the in-memory library */ }
    }
    private File dir() { return new File(context.getFilesDir(),"ling-bubble"); }
    private static String fileName(String id) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();
            for(byte b:digest) hex.append(String.format("%02x",b));
            return hex+".json";
        } catch(Exception e) { return "unknown.json"; }
    }
}
