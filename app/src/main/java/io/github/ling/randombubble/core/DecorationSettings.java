package io.github.ling.randombubble.core;

import java.util.LinkedHashSet;

/** Validation shared by timer and independently enabled per-message rotation. */
public final class DecorationSettings {
    public static final int MIN_SECONDS=60, MAX_SECONDS=86400;
    public static int[] ids(String raw) {
        if(raw==null || raw.length()>262144) throw new IllegalArgumentException("气泡编号过长");
        LinkedHashSet<Integer> ids=new LinkedHashSet<>();
        for(String value:raw.trim().split("[,，\\s]+")) {
            if(!value.matches("[1-9]\\d{0,8}")) throw new IllegalArgumentException("请输入正整数气泡编号，以逗号分隔");
            ids.add(Integer.parseInt(value));
        }
        if(ids.isEmpty()) throw new IllegalArgumentException("请至少选择一款有权使用的气泡");
        int[] result=new int[ids.size()]; int i=0; for(int id:ids) result[i++]=id;
        return result;
    }
    public static void interval(int seconds) {
        if(seconds<MIN_SECONDS || seconds>MAX_SECONDS) throw new IllegalArgumentException("间隔须为 60 至 86400 秒");
    }
    public static void rotationPool(int uniqueStyles,boolean automatic,boolean perMessage) {
        if((automatic || perMessage) && uniqueStyles<2) throw new IllegalArgumentException("轮换至少需要两款不同气泡");
    }
}
