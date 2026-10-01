package io.github.ling.randombubble.hook;

import android.text.Editable;
import android.text.Selection;
import android.text.Spanned;
import android.text.SpanWatcher;
import android.text.TextWatcher;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Read-only classification of QQ 9.3.50 inline text. Never renders or edits spans. */
final class ComposerTextGate {
    private static final String EMOTICON="com.tencent.mobileqq.text.style.EmoticonSpan";
    private ComposerTextGate() {}

    static boolean supported(Editable text) {
        try { return snapshot(text)!=null; } catch(Exception ignored) { return false; }
    }

    static Snapshot snapshot(Editable text) throws Exception {
        if(text==null || text.length()==0 || text.length()>12000)return null;
        List<InlineSpan> inline=new ArrayList<>();
        for(Object span:text.getSpans(0,text.length(),Object.class)) {
            int flags=text.getSpanFlags(span);
            if((flags&Spanned.SPAN_COMPOSING)!=0)return null;
            if(span==Selection.SELECTION_START || span==Selection.SELECTION_END || span instanceof TextWatcher || span instanceof SpanWatcher
                || span.getClass()==android.text.NoCopySpan.Concrete.class)continue;
            if(!span.getClass().getName().equals(EMOTICON))return null;
            int start=text.getSpanStart(span),end=text.getSpanEnd(span);
            // A zero-length span of this exact drawing class has no inline payload.
            // Preserve it in place and still track its identity/fields during the wait.
            if(start<0 || end<start || end>text.length())return null;
            inline.add(new InlineSpan(span,start,end,flags,integer(span,"index"),integer(span,"emojiType"),integer(span,"size")));
        }
        return new Snapshot(inline);
    }

    private static int integer(Object span,String name) throws Exception {
        Field field=span.getClass().getDeclaredField(name);
        if(field.getType()!=int.class)throw new IllegalArgumentException("Unknown inline span schema");
        field.setAccessible(true);
        return field.getInt(span);
    }

    static final class Snapshot {
        private final List<InlineSpan> inline;
        Snapshot(List<InlineSpan> inline) { this.inline=inline; }
        boolean matches(Editable text) {
            try {
                Snapshot now=snapshot(text);
                if(now==null || now.inline.size()!=inline.size())return false;
                for(InlineSpan expected:inline) {
                    boolean found=false;
                    for(InlineSpan current:now.inline)if(expected.same(current)) { found=true;break; }
                    if(!found)return false;
                }
                return true;
            }catch(Exception ignored) { return false; }
        }
    }
    private static final class InlineSpan {
        final Object object;
        final int start,end,flags,index,emojiType,size;
        InlineSpan(Object object,int start,int end,int flags,int index,int emojiType,int size) {
            this.object=object;this.start=start;this.end=end;this.flags=flags;this.index=index;this.emojiType=emojiType;this.size=size;
        }
        boolean same(InlineSpan other) {
            return object==other.object && start==other.start && end==other.end && flags==other.flags
                && index==other.index && emojiType==other.emojiType && size==other.size;
        }
    }
}
