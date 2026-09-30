package io.github.ling.randombubble.store;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import org.json.JSONException;

/** Not a public control API. Exported only so the injected QQ process can sync metadata. */
public final class ConfigProvider extends ContentProvider {
    public static final String AUTHORITY="io.github.ling.randombubble.config";
    public static final Uri URI=Uri.parse("content://"+AUTHORITY);
    @Override public boolean onCreate() { return true; }
    private void authorize() {
        int uid=Binder.getCallingUid();
        if(uid==Process.myUid()) return;
        String[] packages=getContext().getPackageManager().getPackagesForUid(uid);
        if(packages!=null) for(String p:packages) if("com.tencent.mobileqq".equals(p)) return;
        throw new SecurityException("Only QQ may synchronize bubble metadata");
    }
    @Override public Bundle call(String method,String arg,Bundle extras) {
        authorize();
        if(!"sync".equals(method)) throw new IllegalArgumentException("Unknown method");
        Repository repo=Repository.get(getContext());
        String favoriteStatus=null;
        if(extras!=null) {
            repo.useAccount(extras.getString("account"));
            String favorite=extras.getString("favorite");
            if(favorite!=null) {
                try { favoriteStatus=repo.favorite(favorite); }
                catch(JSONException e) { favoriteStatus=e.getMessage()==null?"气泡数据无效":e.getMessage(); }
            }
            try { repo.observe(extras.getString("candidates")); } catch(JSONException ignored) { /* reject malformed batch */ }
            try { repo.importHost(extras.getString("library")); } catch(JSONException ignored) { /* keep the module copy */ }
            repo.diagnostics(extras.getString("diagnostics"));
        }
        Bundle reply=new Bundle(); reply.putString("config",repo.snapshot().toString());
        if(favoriteStatus!=null) reply.putString("favoriteStatus",favoriteStatus);
        return reply;
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String sort) { authorize(); throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { authorize(); return "application/json"; }
    @Override public Uri insert(Uri u,ContentValues v) { authorize(); throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u,String s,String[] a) { authorize(); throw new UnsupportedOperationException(); }
    @Override public int update(Uri u,ContentValues v,String s,String[] a) { authorize(); throw new UnsupportedOperationException(); }
}
