package io.github.ling.randombubble.hook;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import io.github.ling.randombubble.core.BubbleSpec;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads bubble ids QQ has already downloaded. Does not contact QQ's servers. */
final class BubbleScan {
    private BubbleScan() {}
    static List<BubbleSpec> collect(Context context) {
        Map<Integer,BubbleSpec> found=new LinkedHashMap<>();
        List<File> roots=new ArrayList<>();
        try { add(roots,context.getFilesDir()); } catch(Throwable ignored) { /* private dir unavailable */ }
        try { add(roots,context.getExternalFilesDir(null)); } catch(Throwable ignored) { /* no external files */ }
        try {
            ApplicationInfo info=context.getApplicationInfo();
            if(info!=null && info.dataDir!=null) add(roots,new File(info.dataDir,"files"));
        } catch(Throwable ignored) { /* data dir unavailable */ }
        for(File root:roots) {
            File[] kids=root.listFiles();
            if(kids==null) continue;
            for(File kid:kids) {
                String name=kid.getName().toLowerCase(Locale.ROOT);
                if(name.contains("bubble") || name.contains("vas_material")) walk(kid,0,found);
            }
        }
        return new ArrayList<>(found.values());
    }
    private static void walk(File dir,int depth,Map<Integer,BubbleSpec> out) {
        if(depth>5 || out.size()>=8000 || !dir.isDirectory()) return;
        File[] kids=dir.listFiles();
        if(kids==null) return;
        for(File kid:kids) {
            if(!kid.isDirectory()) continue;
            Integer id=numeric(kid.getName());
            if(id!=null && !out.containsKey(id)) {
                try { out.put(id,new BubbleSpec(17,17,0L,id,0,null,0)); }
                catch(IllegalArgumentException ignored) { /* not a usable id */ }
            }
            walk(kid,depth+1,out);
        }
    }
    private static Integer numeric(String name) {
        if(name==null || !name.matches("[1-9]\\d{1,8}")) return null;
        try { return Integer.valueOf(name); }
        catch(NumberFormatException e) { return null; }
    }
    private static void add(List<File> roots,File file) {
        if(file!=null && file.isDirectory()) roots.add(file);
    }
}
