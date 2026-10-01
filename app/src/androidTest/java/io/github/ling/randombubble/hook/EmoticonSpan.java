package com.tencent.mobileqq.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/** Test APK only: models QQ's confirmed class shape without loading QQ or artwork. */
public class EmoticonSpan extends ReplacementSpan {
    public int index;
    public int emojiType;
    protected int size;

    public EmoticonSpan(int index,int size,int emojiType) {
        this.index=index;this.size=size;this.emojiType=emojiType;
    }
    public void changeSize(int value) {size=value;}
    @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics) {throw new AssertionError("Classification must not measure an emoticon");}
    @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint paint) {throw new AssertionError("Classification must not render an emoticon");}
}
