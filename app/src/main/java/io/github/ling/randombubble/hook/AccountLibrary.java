package io.github.ling.randombubble.hook;

import android.content.Context;
import io.github.ling.randombubble.core.BubbleSpec;
import io.github.ling.randombubble.store.Config;
import io.github.ling.randombubble.store.JsonCodec;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;

/** One JSON library per QQ account, stored in QQ's private files directory. */
final class AccountLibrary {
    private final Context context;
    private String account="unknown";
    private String json;
    private String saveError;
    AccountLibrary(Context context) {
        Context app=null;
        try { app=context.getApplicationContext(); } catch(Throwable ignored) { /* some host wrappers return null here */ }
        this.context=app!=null?app:context;
    }
    synchronized String account() { return account; }
    synchronized String mask() { return AccountRef.mask(account); }
    /** Editable copy of the current account's library. */
    synchronized JSONObject document() throws org.json.JSONException {
        return json==null?JsonCodec.defaults():new JSONObject(json);
    }
    /** Validates, persists and returns the parsed configuration. */
    synchronized Config store(JSONObject document) throws org.json.JSONException {
        String text=document.toString();
        Config parsed=JsonCodec.config(text);
        json=text;
        save();
        return parsed;
    }
    /** @return true when the login id changed */
    synchronized boolean refresh(Context context) {
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
    synchronized Config configOrNull() {
        if(json==null) return null;
        try { return JsonCodec.config(json); } catch(Exception e) { return null; }
    }
    synchronized String json() { return json; }
    synchronized void replaceJson(String value) {
        json=value;
        save();
    }
    synchronized Config add(BubbleSpec spec,Config current) {
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
            if(!found) bubbles.put(JsonCodec.encode(spec).put("name",spec.label()).put("selected",true));
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
            byte[] data=read(file);
            if(data.length==0 || data.length>JsonCodec.MAX_JSON_CHARS) return false;
            String text=new String(data,StandardCharsets.UTF_8);
            JsonCodec.config(text);
            json=text;
            return true;
        } catch(Exception e) { return false; }
    }
    synchronized String takeSaveError() { String error=saveError; saveError=null; return error; }
    private void save() {
        saveError=null;
        if(json==null) return;
        File tmp=null;
        try {
            File folder=dir();
            if(!folder.isDirectory() && !folder.mkdirs()) { saveError="无法创建目录"; return; }
            File file=new File(folder,fileName(account));
            tmp=new File(folder,fileName(account)+".tmp");
            try(FileOutputStream out=new FileOutputStream(tmp)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.flush();
                try { if(out.getFD()!=null) out.getFD().sync(); } catch(Throwable ignored) { /* the bytes are already flushed */ }
            }
            if(file.exists() && !file.delete()) { saveError="无法替换旧库"; return; }
            if(!tmp.renameTo(file)) { saveError="无法保存"; return; }
            tmp=null;
        } catch(Exception e) {
            String where=e.getStackTrace().length==0?"":"@"+e.getStackTrace()[0].getMethodName();
            saveError=e.getClass().getSimpleName()+where;
        }
        finally { if(tmp!=null) tmp.delete(); }
    }
    private static byte[] read(File file) throws java.io.IOException {
        try(java.io.FileInputStream in=new java.io.FileInputStream(file); java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096]; int n;
            while((n=in.read(buffer))>=0) out.write(buffer,0,n);
            return out.toByteArray();
        }
    }
    /** Host getFilesDir() is null inside some embedded loaders; fall through until one directory exists. */
    private File dir() {
        File base=filesBase();
        File folder=new File(base,"ling-bubble");
        if(!folder.isDirectory()) folder.mkdirs();
        return folder;
    }
    private File filesBase() {
        if(context==null) return downloadFallback();
        File direct=callFilesDir(context);
        if(direct!=null) return direct;
        try {
            android.content.pm.ApplicationInfo info=context.getApplicationInfo();
            if(info!=null && info.dataDir!=null) {
                File files=new File(info.dataDir,"files");
                if(files.isDirectory() || files.mkdirs()) return files;
            }
        } catch(Throwable ignored) { /* data dir unavailable */ }
        try {
            File external=context.getExternalFilesDir(null);
            if(external!=null) return external;
        } catch(Throwable ignored) { /* no external files dir */ }
        return downloadFallback();
    }
    private static File downloadFallback() {
        File download=new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),"LingRandomBubble");
        if(!download.isDirectory()) download.mkdirs();
        return download;
    }
    private static File callFilesDir(Context context) {
        if(context==null) return null;
        try { return context.getFilesDir(); }
        catch(Throwable ignored) { return null; }
    }
    private static String fileName(String id) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();
            for(byte b:digest) hex.append(String.format("%02x",b));
            return hex+".json";
        } catch(Exception e) { return "unknown.json"; }
    }
}
