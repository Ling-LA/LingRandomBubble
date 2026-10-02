package com.tencent.qqnt.aio.at;

import android.graphics.drawable.Drawable;
import android.text.style.DynamicDrawableSpan;

/** Test APK only: synthetic confirmed superclass fields, with no QQ data or artwork. */
public class c extends DynamicDrawableSpan {
    private String d;
    private String e;
    private String r;
    private String s;
    public int q;
    public Drawable v;

    public c(String uid,String uin,String nickname,String alternateNickname) {
        d=uid;e=uin;s=nickname;r=alternateNickname;
    }
    public void changePayload(String name,String value) throws Exception {
        java.lang.reflect.Field field=c.class.getDeclaredField(name);field.setAccessible(true);field.set(this,value);
    }
    public String[] payload() {return new String[]{d,e,r,s};}
    @Override public Drawable getDrawable() {throw new AssertionError("Classification must not render a mention");}
}
