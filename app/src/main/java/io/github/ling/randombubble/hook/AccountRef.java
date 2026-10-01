package io.github.ling.randombubble.hook;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** Prefer QQ's actual current runtime; only unambiguous current-account prefs may bootstrap it. */
final class AccountRef {
    private static final String[] KEYS={"current_account_uin","currentAccountUin"};
    private AccountRef() {}
    static String current(Context context) {
        String runtime=runtimeAccount(context);
        if(runtime!=null) return runtime;
        Set<String> candidates=new HashSet<>();
        try {
            File dir=new File(context.getApplicationInfo().dataDir,"shared_prefs");
            File[] files=dir.listFiles();
            if(files==null) return "unknown";
            for(File file:files) {
                if(!file.isFile() || !file.getName().endsWith(".xml") || file.length()>262144L) continue;
                String text=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
                for(String key:KEYS) {
                    String raw=readPref(text,key);
                    if(raw==null) continue;
                    String uin=normalize(raw);
                    if(uin==null) return "unknown";
                    candidates.add(uin);
                }
            }
        } catch(Throwable ignored) { return "unknown"; }
        return candidates.size()==1?candidates.iterator().next():"unknown";
    }
    /** null means no current runtime exists; unknown means it exists but is not trustworthy yet. */
    private static String runtimeAccount(Context context) {
        try {
            if(context==null) return "unknown";
            Class<?> mobileClass=Class.forName("mqq.app.MobileQQ",false,context.getClassLoader());
            Field singleton=mobileClass.getDeclaredField("sMobileQQ");singleton.setAccessible(true);
            Object mobile=singleton.get(null);
            if(mobile==null) return null;
            Object app=noArgs(mobile,"peekAppRuntime");
            if(app==null) return null;
            Object value=noArgs(app,"getCurrentAccountUin");
            String owner=value instanceof String?normalize((String)value):null;
            return owner==null?"unknown":owner;
        } catch(ClassNotFoundException e) { return null; }
        catch(Throwable ignored) { return "unknown"; }
    }
    private static Object noArgs(Object receiver,String name) throws ReflectiveOperationException {
        for(Class<?> type=receiver.getClass();type!=null;type=type.getSuperclass()) {
            for(Method method:type.getDeclaredMethods()) {
                if(method.getName().equals(name) && method.getParameterTypes().length==0) {
                    method.setAccessible(true);return method.invoke(receiver);
                }
            }
        }
        throw new NoSuchMethodException(name);
    }
    private static String readPref(String xml,String key) {
        String stringTag="<string name=\""+key+"\">";
        int at=xml.indexOf(stringTag);
        if(at>=0) {
            int start=at+stringTag.length();
            int end=xml.indexOf('<',start);
            if(end>=start) return xml.substring(start,end);
        }
        String longTag="<long name=\""+key+"\" value=\"";
        at=xml.indexOf(longTag);
        if(at>=0) {
            int start=at+longTag.length();
            int end=xml.indexOf('"',start);
            if(end>=start) return xml.substring(start,end);
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
