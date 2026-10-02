package com.tencent.mobileqq.aio.reply;

import android.graphics.drawable.Drawable;
import android.text.style.DynamicDrawableSpan;
import android.widget.TextView;

/** Test APK only: QQ's confirmed tag shape. Classification must not render it. */
public class d extends DynamicDrawableSpan {
    public Drawable e;
    public TextView f;
    public CharSequence h;
    public d(TextView editor,Drawable drawable,CharSequence preview) {
        f=new TextView(editor.getContext());f.setText(preview);e=drawable;h=preview;
    }
    @Override public Drawable getDrawable() {throw new AssertionError("Reply classification must not render the tag");}
}
