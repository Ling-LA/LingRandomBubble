package io.github.ling.randombubble.hook;

import android.text.Editable;
import android.text.Selection;
import android.text.Spanned;
import android.text.SpanWatcher;
import android.text.TextWatcher;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Read-only classification of QQ 9.3.50 inline text. Never renders or edits spans. */
final class ComposerTextGate {
    private static final String EMOTICON="com.tencent.mobileqq.text.style.EmoticonSpan";
    private static final String REPLY_MENTION="com.tencent.qqnt.aio.at.a";
    private static final String MENTION_BASE="com.tencent.qqnt.aio.at.c";
    private ComposerTextGate() {}

    static boolean supported(Editable text) {
        try { return snapshot(text)!=null; } catch(Exception ignored) { return false; }
    }

    static Snapshot snapshot(Editable text) throws Exception {
        return snapshot(text,false);
    }

    /** The caller must confirm a native active reply before allowing its mention spans. */
    static Snapshot snapshot(Editable text,boolean allowReplyMentions) throws Exception {
        if(text==null || text.length()==0 || text.length()>12000)return null;
        List<InlineSpan> inline=new ArrayList<>();
        boolean retained=false;
        try {
        for(Object span:text.getSpans(0,text.length(),Object.class)) {
            int flags=text.getSpanFlags(span);
            if((flags&Spanned.SPAN_COMPOSING)!=0)return null;
            if(span==Selection.SELECTION_START || span==Selection.SELECTION_END || span instanceof TextWatcher || span instanceof SpanWatcher
                || span.getClass()==android.text.NoCopySpan.Concrete.class)continue;
            int start=text.getSpanStart(span),end=text.getSpanEnd(span);
            if(start<0 || end<start || end>text.length())return null;
            if(span.getClass().getName().equals(EMOTICON)) {
                // Exact emoticon residues may have no covering range; never edit them.
                inline.add(new InlineSpan(span,start,end,flags,integer(span,"index"),integer(span,"emojiType"),integer(span,"size"),null));
            } else if(allowReplyMentions && span.getClass().getName().equals(REPLY_MENTION) && end>start) {
                inline.add(new InlineSpan(span,start,end,flags,0,0,0,mentionPayload(span)));
            } else return null;
        }
        Snapshot result=new Snapshot(inline,allowReplyMentions);retained=true;return result;
        } finally {if(!retained)for(InlineSpan span:inline)if(span.payload!=null)Arrays.fill(span.payload,(byte)0);}
    }

    private static int integer(Object span,String name) throws Exception {
        Field field=span.getClass().getDeclaredField(name);
        if(field.getType()!=int.class)throw new IllegalArgumentException("Unknown inline span schema");
        field.setAccessible(true);
        return field.getInt(span);
    }

    private static byte[] mentionPayload(Object span) throws Exception {
        Class<?> base=span.getClass().getSuperclass();
        if(base==null || !base.getName().equals(MENTION_BASE))throw new IllegalArgumentException("Unknown mention span schema");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        // QQ's confirmed constructor assigns UID, UIN and the two nicknames here.
        // Read fields only: drawable/measurement methods can mutate render caches.
        for(String name:new String[]{"d","e","r","s"}) {
            Field field=base.getDeclaredField(name);
            if(field.getType()!=String.class || Modifier.isStatic(field.getModifiers()))throw new IllegalArgumentException("Unknown mention payload field");
            field.setAccessible(true);Object raw=field.get(span);
            if(!(raw instanceof String))throw new IllegalArgumentException("Missing mention payload");
            String value=(String)raw;int length=value.length();
            digest.update((byte)(length>>>24));digest.update((byte)(length>>>16));
            digest.update((byte)(length>>>8));digest.update((byte)length);
            // Preserve every UTF-16 code unit, including unmatched surrogates.
            for(int i=0;i<length;i++) {char unit=value.charAt(i);digest.update((byte)(unit>>>8));digest.update((byte)unit);}
        }
        return digest.digest();
    }

    static final class Snapshot {
        private final List<InlineSpan> inline;
        private final boolean allowReplyMentions;
        private boolean cleared;
        Snapshot(List<InlineSpan> inline,boolean allowReplyMentions) { this.inline=inline;this.allowReplyMentions=allowReplyMentions; }
        synchronized boolean matches(Editable text) {
            if(cleared)return false;
            Snapshot now=null;
            try {
                now=snapshot(text,allowReplyMentions);
                if(now==null || now.inline.size()!=inline.size())return false;
                for(InlineSpan expected:inline) {
                    boolean found=false;
                    for(InlineSpan current:now.inline)if(expected.same(current)) { found=true;break; }
                    if(!found)return false;
                }
                return true;
            }catch(Exception ignored) { return false; }
            finally {if(now!=null)now.clear();}
        }
        synchronized void clear() {
            if(cleared)return;cleared=true;
            for(InlineSpan span:inline)if(span.payload!=null)Arrays.fill(span.payload,(byte)0);
            inline.clear();
        }
    }
    private static final class InlineSpan {
        final Object object;
        final int start,end,flags,index,emojiType,size;
        final byte[] payload;
        InlineSpan(Object object,int start,int end,int flags,int index,int emojiType,int size,byte[] payload) {
            this.object=object;this.start=start;this.end=end;this.flags=flags;this.index=index;this.emojiType=emojiType;this.size=size;
            this.payload=payload;
        }
        boolean same(InlineSpan other) {
            return object==other.object && start==other.start && end==other.end && flags==other.flags
                && index==other.index && emojiType==other.emojiType && size==other.size
                && (payload==null?other.payload==null:other.payload!=null && MessageDigest.isEqual(payload,other.payload));
        }
    }
}
