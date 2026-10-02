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
import com.tencent.qqnt.aio.at.a;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
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
    private static SpannableStringBuilder mention() {
        SpannableStringBuilder text=new SpannableStringBuilder("@fixture reply");text.setSpan(new a(),0,8,FLAGS);return text;
    }
    private static a firstMention(Editable text) {return text.getSpans(0,text.length(),a.class)[0];}
    private static boolean rejectedReply(Editable text) {
        try {return ComposerTextGate.snapshot(text,true)==null;}catch(Exception ignored) {return true;}
    }
    private static final class SubReplyMention extends a {}
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

        text=mention();
        check(passed,"ordinary composer still rejects exact QQ mention",ComposerTextGate.snapshot(text)==null);
        saved=ComposerTextGate.snapshot(text,true);
        check(passed,"confirmed reply composer accepts exact QQ mention",saved!=null && saved.matches(text));
        text=new SpannableStringBuilder("@fixture @other [smile]");text.setSpan(new a(),0,8,FLAGS);text.setSpan(new a(),9,15,FLAGS);emoji(text,16,23);
        saved=ComposerTextGate.snapshot(text,true);
        check(passed,"confirmed reply accepts multiple native mentions and small emoticon",saved!=null && saved.matches(text));
        text=plain();text.setSpan(new com.tencent.qqnt.aio.at.c("fixture-uid","fixture-uin","fixture-name","fixture-name"),0,1,FLAGS);
        check(passed,"confirmed reply rejects generic mention superclass",ComposerTextGate.snapshot(text,true)==null);
        text=plain();text.setSpan(new SubReplyMention(),0,1,FLAGS);
        check(passed,"confirmed reply rejects mention subclasses",ComposerTextGate.snapshot(text,true)==null);
        text=mention();text.setSpan(firstMention(text),0,8,FLAGS|Spanned.SPAN_COMPOSING);
        check(passed,"confirmed reply rejects composing mention",ComposerTextGate.snapshot(text,true)==null);
        text=plain();text.setSpan(new a(),1,1,Spanned.SPAN_MARK_MARK);
        check(passed,"confirmed reply rejects unverified zero-length mention residue",ComposerTextGate.snapshot(text,true)==null);
        text=mention();text.setSpan(new ImageSpan(new ColorDrawable()),9,10,FLAGS);
        check(passed,"confirmed reply mention does not authorize an image",ComposerTextGate.snapshot(text,true)==null);
        text=mention();text.setSpan(new MentionStyle(),9,10,FLAGS);
        check(passed,"confirmed reply still rejects unknown mention styling",ComposerTextGate.snapshot(text,true)==null);

        text=mention();saved=ComposerTextGate.snapshot(text,true);a oldMention=firstMention(text);text.removeSpan(oldMention);text.setSpan(new a(),0,8,FLAGS);
        check(passed,"reply mention snapshot rejects equal-value object replacement",!saved.matches(text));
        text=mention();saved=ComposerTextGate.snapshot(text,true);text.setSpan(firstMention(text),1,8,FLAGS);
        check(passed,"reply mention snapshot rejects range change",!saved.matches(text));
        text=mention();saved=ComposerTextGate.snapshot(text,true);text.setSpan(firstMention(text),0,8,Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        check(passed,"reply mention snapshot rejects flag change",!saved.matches(text));
        for(String name:new String[]{"d","e","r","s"}) {
            text=mention();saved=ComposerTextGate.snapshot(text,true);firstMention(text).changePayload(name,"fixture-changed");
            check(passed,"reply mention snapshot rejects stable payload field change "+name,!saved.matches(text));
        }
        text=mention();firstMention(text).changePayload("d",null);
        check(passed,"confirmed reply rejects null mention payload",rejectedReply(text));
        text=mention();firstMention(text).changePayload("d","ab");firstMention(text).changePayload("e","c");saved=ComposerTextGate.snapshot(text,true);
        firstMention(text).changePayload("d","a");firstMention(text).changePayload("e","bc");
        check(passed,"mention payload hash separates adjacent field lengths",!saved.matches(text));
        text=mention();firstMention(text).changePayload("s","\uD800");saved=ComposerTextGate.snapshot(text,true);firstMention(text).changePayload("s","\uD801");
        check(passed,"mention payload hash preserves unmatched UTF16 surrogates",!saved.matches(text));
        text=mention();saved=ComposerTextGate.snapshot(text,true);firstMention(text).changePayload("d",new String("fixture-uid"));
        check(passed,"mention payload compares string value without retaining string identity",saved.matches(text));
        text=mention();saved=ComposerTextGate.snapshot(text,true);firstMention(text).q++;firstMention(text).v=new ColorDrawable();
        check(passed,"mention drawing cache updates do not invalidate stable payload",saved.matches(text));
        text=mention();saved=ComposerTextGate.snapshot(text,true);text.setSpan(new a(),9,14,FLAGS);
        check(passed,"reply mention snapshot rejects added mention",!saved.matches(text));
        text=mention();saved=ComposerTextGate.snapshot(text,true);text.removeSpan(firstMention(text));
        check(passed,"reply mention snapshot rejects removed mention",!saved.matches(text));
        text=plain();saved=ComposerTextGate.snapshot(text);text.setSpan(new a(),0,1,FLAGS);
        check(passed,"ordinary snapshot cannot inherit reply mention authorization",!saved.matches(text));

        text=mention();saved=ComposerTextGate.snapshot(text,true);String before=text.toString();a retained=firstMention(text);String[] payloadBefore=retained.payload();
        Field listField=ComposerTextGate.Snapshot.class.getDeclaredField("inline");listField.setAccessible(true);
        Object captured=((List<?>)listField.get(saved)).get(0);Field payloadField=captured.getClass().getDeclaredField("payload");payloadField.setAccessible(true);byte[] privateHash=(byte[])payloadField.get(captured);
        saved.clear();boolean zero=true;for(byte value:privateHash)if(value!=0)zero=false;
        check(passed,"reply mention snapshot clears private payload digest",zero);
        saved.clear();check(passed,"cleared reply mention snapshot cannot authorize a draft again",!saved.matches(text));
        check(passed,"snapshot cleanup leaves native mention draft and payload unchanged",before.equals(text.toString()) && firstMention(text)==retained && text.getSpanStart(retained)==0 && text.getSpanEnd(retained)==8 && Arrays.equals(payloadBefore,retained.payload()));
        text=emoticon();saved=ComposerTextGate.snapshot(text);saved.clear();
        check(passed,"ordinary snapshot cleanup also prevents later reuse",!saved.matches(text));
    }
}
