package io.github.ling.randombubble.hook;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.NoCopySpan;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ImageSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StyleSpan;
import com.tencent.mobileqq.text.style.EmoticonSpan;
import java.util.function.Consumer;

/** Android span classification and snapshot checks; no QQ, messages, views, or preferences. */
public final class ComposerTextDeviceChecks {
    private static final int FLAGS=Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
    private ComposerTextDeviceChecks() {}
    private static void check(Consumer<String> passed,String name,boolean value) {
        if(!value)throw new AssertionError(name);passed.accept(name);
    }
    private static SpannableStringBuilder plain() {return new SpannableStringBuilder("A [smile] Z");}
    private static EmoticonSpan emoji(Editable text,int start,int end) {
        EmoticonSpan span=new EmoticonSpan(14,32,0);text.setSpan(span,start,end,start==end?Spanned.SPAN_MARK_MARK:FLAGS);return span;
    }
    private static SpannableStringBuilder emoticon() {
        SpannableStringBuilder text=plain();emoji(text,2,9);return text;
    }
    private static EmoticonSpan first(Editable text) {return text.getSpans(0,text.length(),EmoticonSpan.class)[0];}
    private static final class SubEmoticonSpan extends EmoticonSpan {
        SubEmoticonSpan() {super(14,32,0);}
    }
    private static final class OtherReplacement extends ReplacementSpan {
        @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics) {return 0;}
        @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint paint) {}
    }
    private static final class MentionStyle extends CharacterStyle {
        @Override public void updateDrawState(TextPaint paint) {}
    }
    private static final class UnknownMarker implements NoCopySpan {}

    public static void run(Consumer<String> passed) throws Exception {
        check(passed,"composer rejects null draft",!ComposerTextGate.supported(null));
        check(passed,"composer rejects empty draft",!ComposerTextGate.supported(new SpannableStringBuilder()));
        StringBuilder longText=new StringBuilder();for(int i=0;i<12000;i++)longText.append('a');
        check(passed,"composer accepts supported text length boundary",ComposerTextGate.supported(new SpannableStringBuilder(longText)));
        longText.append('a');
        check(passed,"composer rejects overlong draft",!ComposerTextGate.supported(new SpannableStringBuilder(longText)));
        check(passed,"composer accepts plain text and unicode emoji",ComposerTextGate.supported(new SpannableStringBuilder("text \uD83D\uDE42")));
        SpannableStringBuilder text=plain();Selection.setSelection(text,1);text.setSpan(new NoCopySpan.Concrete(),1,1,Spanned.SPAN_MARK_MARK);
        check(passed,"composer accepts exact framework editing markers",ComposerTextGate.supported(text));
        text=plain();text.setSpan(new UnknownMarker(),1,1,Spanned.SPAN_MARK_MARK);
        check(passed,"composer rejects unknown no-copy marker",!ComposerTextGate.supported(text));
        check(passed,"composer accepts text with exact QQ small emoticon",ComposerTextGate.supported(emoticon()));
        text=new SpannableStringBuilder("[smile]");emoji(text,0,text.length());
        check(passed,"composer accepts only an exact QQ small emoticon",ComposerTextGate.supported(text));
        text=new SpannableStringBuilder("[smile][smile]");emoji(text,0,7);emoji(text,7,14);
        check(passed,"composer accepts multiple exact QQ small emoticons",ComposerTextGate.supported(text));
        text=plain();emoji(text,2,2);
        check(passed,"composer accepts exact zero-length emoticon residue",ComposerTextGate.supported(text));
        text=plain();text.setSpan(new SubEmoticonSpan(),2,9,FLAGS);
        check(passed,"composer rejects QQ emoticon subclasses",!ComposerTextGate.supported(text));
        text=plain();text.setSpan(new OtherReplacement(),2,9,FLAGS);
        check(passed,"composer rejects unknown replacement span",!ComposerTextGate.supported(text));
        text=plain();text.setSpan(new ImageSpan(new ColorDrawable()),2,9,FLAGS);
        check(passed,"composer rejects image spans",!ComposerTextGate.supported(text));
        text=plain();text.setSpan(new MentionStyle(),2,9,FLAGS);
        check(passed,"composer rejects unknown mention-style spans",!ComposerTextGate.supported(text));
        text=emoticon();text.setSpan(new ImageSpan(new ColorDrawable()),0,1,FLAGS);
        check(passed,"composer rejects emoticon draft mixed with an image",!ComposerTextGate.supported(text));
        text=emoticon();text.setSpan(new MentionStyle(),0,1,FLAGS);
        check(passed,"composer rejects emoticon draft mixed with mention styling",!ComposerTextGate.supported(text));
        text=emoticon();text.setSpan(first(text),2,9,FLAGS|Spanned.SPAN_COMPOSING);
        check(passed,"composer rejects composing QQ emoticon",!ComposerTextGate.supported(text));
        text=plain();text.setSpan(new StyleSpan(Typeface.BOLD),1,1,Spanned.SPAN_MARK_MARK);
        check(passed,"composer rejects unknown zero-length rich span",!ComposerTextGate.supported(text));

        text=emoticon();ComposerTextGate.Snapshot saved=ComposerTextGate.snapshot(text);
        check(passed,"emoticon snapshot matches unchanged draft",saved.matches(text));
        EmoticonSpan old=first(text);text.removeSpan(old);emoji(text,2,9);
        check(passed,"emoticon snapshot rejects same-value object replacement",!saved.matches(text));
        text=emoticon();saved=ComposerTextGate.snapshot(text);text.setSpan(first(text),1,9,FLAGS);
        check(passed,"emoticon snapshot rejects span range change",!saved.matches(text));
        text=emoticon();saved=ComposerTextGate.snapshot(text);text.setSpan(first(text),2,9,Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        check(passed,"emoticon snapshot rejects span flag change",!saved.matches(text));
        text=emoticon();saved=ComposerTextGate.snapshot(text);first(text).index++;
        check(passed,"emoticon snapshot rejects in-place index change",!saved.matches(text));
        text=emoticon();saved=ComposerTextGate.snapshot(text);first(text).emojiType++;
        check(passed,"emoticon snapshot rejects in-place emoji type change",!saved.matches(text));
        text=emoticon();saved=ComposerTextGate.snapshot(text);first(text).changeSize(33);
        check(passed,"emoticon snapshot rejects in-place size change",!saved.matches(text));
        text=emoticon();saved=ComposerTextGate.snapshot(text);emoji(text,0,1);
        check(passed,"emoticon snapshot rejects added emoticon",!saved.matches(text));
        text=emoticon();saved=ComposerTextGate.snapshot(text);text.removeSpan(first(text));
        check(passed,"emoticon snapshot rejects removed emoticon",!saved.matches(text));
        text=emoticon();saved=ComposerTextGate.snapshot(text);text.setSpan(new ImageSpan(new ColorDrawable()),0,1,FLAGS);
        check(passed,"emoticon snapshot rejects newly attached image",!saved.matches(text));
        text=plain();EmoticonSpan residue=emoji(text,2,2);saved=ComposerTextGate.snapshot(text);residue.index++;
        check(passed,"emoticon snapshot tracks zero-length residue fields",!saved.matches(text));
    }
}
