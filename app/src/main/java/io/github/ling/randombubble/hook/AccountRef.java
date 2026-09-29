package io.github.ling.randombubble.hook;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Reads the already-saved login id. Does not call into QQ runtime during startup. */
final class AccountRef {
    private static final String[] KEYS={"current_account_uin","currentAccountUin","last_login_uin","loginUin","login_account","uin"};
    private AccountRef() {}
    static String current(Context context) {
        try {
            File dir=new File(context.getApplicationInfo().dataDir,"shared_prefs");
            File[] files=dir.listFiles();
            if(files==null) return "unknown";
            for(File file:files) {
                if(!file.isFile() || !file.getName().endsWith(".xml") || file.length()>262144L) continue;
                String text=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
                for(String key:KEYS) {
                    String uin=normalize(readPref(text,key));
                    if(uin!=null) return uin;
                }
            }
        } catch(Throwable ignored) { /* startup must stay alive */ }
        return "unknown";
    }
    private static String readPref(String xml,String key) {
        String stringTag="<string name=\""+key+"\">";
        int at=xml.indexOf(stringTag);
        if(at>=0) {
            int start=at+stringTag.length();
            int end=xml.indexOf('<',start);
            if(end>start) return xml.substring(start,end);
        }
        String longTag="<long name=\""+key+"\" value=\"";
        at=xml.indexOf(longTag);
        if(at>=0) {
            int start=at+longTag.length();
            int end=xml.indexOf('"',start);
            if(end>start) return xml.substring(start,end);
        }
        return null;
    }
    static String normalize(String raw) {
        if(raw==null) return null;
        String digits=raw.trim();
        if(!digits.matches("[1-9]\\d{4,12}")) return null;
        return digits;
    }
    static String mask(String uin) {
        if(uin==null || "unknown".equals(uin) || uin.length()<4) return "未识别账号";
        return "尾号"+uin.substring(uin.length()-4);
    }
}
