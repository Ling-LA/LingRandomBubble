package io.github.ling.randombubble.hook;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import com.tencent.mobileqq.aio.input.edit.b.i;
import com.tencent.mobileqq.text.style.EmoticonSpan;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/** Isolated native-result classification fixtures. No QQ route, media, messages or preferences. */
public final class MediaSelectionDeviceChecks {
    private MediaSelectionDeviceChecks() {}
    private static void check(Consumer<String> passed,String name,boolean value) {
        if(!value)throw new AssertionError(name);passed.accept(name);
    }
    private static final class SubResult extends i {
        SubResult(List<?> media) {super(media,false);}
    }
    private static final class SimilarResult {
        int reads;
        public final List<?> b() {reads++;throw new AssertionError("Unknown result getter must not be called");}
    }
    private static final class OpaqueMedia {
        @Override public String toString() {throw new AssertionError("Media content must not be read");}
        @Override public int hashCode() {throw new AssertionError("Media content must not be hashed");}
        @Override public boolean equals(Object other) {throw new AssertionError("Media content must not be compared");}
    }
    /** Any operation except isEmpty would fail; this is not a real QQ media repository. */
    private static final class EmptyOnlyList extends AbstractList<Object> {
        final boolean empty;
        final boolean fail;
        int emptyReads;
        EmptyOnlyList(boolean empty,boolean fail) {this.empty=empty;this.fail=fail;}
        @Override public boolean isEmpty() {
            emptyReads++;
            if(fail)throw new IllegalStateException("Synthetic media state failure");
            return empty;
        }
        @Override public int size() {throw new AssertionError("Media size must not be read");}
        @Override public Object get(int index) {throw new AssertionError("Media elements must not be read");}
        @Override public Iterator<Object> iterator() {throw new AssertionError("Media elements must not be iterated");}
        @Override public String toString() {throw new AssertionError("Media list must not be stringified");}
    }

    public static void run(Consumer<String> passed) throws Exception {
        check(passed,"media gate rejects a missing native result",MediaSelectionGate.read(null)==MediaSelectionGate.State.UNKNOWN && !MediaSelectionGate.knownEmpty(null));
        SimilarResult similar=new SimilarResult();
        check(passed,"media gate rejects an unknown result without calling its getter",MediaSelectionGate.read(similar)==MediaSelectionGate.State.UNKNOWN && similar.reads==0);
        SubResult subclass=new SubResult(Collections.emptyList());
        check(passed,"media gate rejects native result subclasses before reading them",MediaSelectionGate.read(subclass)==MediaSelectionGate.State.UNKNOWN && subclass.fixtureReads==0);
        i failed=new i(Collections.emptyList(),false,new IllegalStateException("Synthetic getter failure"));
        check(passed,"media gate treats a native getter failure as unknown",MediaSelectionGate.read(failed)==MediaSelectionGate.State.UNKNOWN && failed.fixtureReads==1);
        check(passed,"media gate treats a missing native list as unknown",MediaSelectionGate.read(new i(null,false))==MediaSelectionGate.State.UNKNOWN);

        EmptyOnlyList empty=new EmptyOnlyList(true,false);i emptyResult=new i(empty,false);
        check(passed,"media gate recognizes an empty native list using only isEmpty",MediaSelectionGate.read(emptyResult)==MediaSelectionGate.State.EMPTY && empty.emptyReads==1 && emptyResult.fixtureReads==1);
        EmptyOnlyList selected=new EmptyOnlyList(false,false);i selectedResult=new i(selected,true);
        check(passed,"media gate recognizes nonempty native state without reading elements",MediaSelectionGate.read(selectedResult)==MediaSelectionGate.State.NON_EMPTY && selected.emptyReads==1 && selectedResult.fixtureReads==1);
        EmptyOnlyList unreadable=new EmptyOnlyList(true,true);
        check(passed,"media gate treats an isEmpty failure as unknown",MediaSelectionGate.read(new i(unreadable,false))==MediaSelectionGate.State.UNKNOWN && unreadable.emptyReads==1);
        check(passed,"media gate never uses quality or native toString to classify empty state",MediaSelectionGate.knownEmpty(new i(Collections.emptyList(),true)) && MediaSelectionGate.knownEmpty(new i(Collections.emptyList(),false)));

        // Opaque synthetic placeholders prove the barrier, not any real picture/sticker send path.
        Object picture=new OpaqueMedia(),favorite=new OpaqueMedia();
        check(passed,"media gate blocks an opaque selected picture fixture",!MediaSelectionGate.knownEmpty(new i(Collections.singletonList(picture),false)));
        check(passed,"media gate blocks an opaque selected favorite fixture",!MediaSelectionGate.knownEmpty(new i(Collections.singletonList(favorite),false)));
        List<Object> mutable=new ArrayList<>();mutable.add(picture);i changing=new i(mutable,false);
        MediaSelectionGate.read(changing);
        check(passed,"media classification preserves the selected list and its object",mutable.size()==1 && mutable.get(0)==picture);
        mutable.clear();
        check(passed,"media gate rereads empty state after native selection is cleared",MediaSelectionGate.knownEmpty(changing));
        mutable.add(favorite);
        check(passed,"media gate does not retain a previous empty authorization",!MediaSelectionGate.knownEmpty(changing));

        SpannableStringBuilder plain=new SpannableStringBuilder("synthetic plain draft");
        check(passed,"plain editable text cannot authorize delay with selected media",ComposerTextGate.supported(plain) && !MediaSelectionGate.knownEmpty(new i(Collections.singletonList(picture),false)));
        check(passed,"plain editable text cannot authorize delay with unknown media state",ComposerTextGate.supported(plain) && !MediaSelectionGate.knownEmpty(similar) && similar.reads==0);
        SpannableStringBuilder emoji=new SpannableStringBuilder("[smile]");emoji.setSpan(new EmoticonSpan(14,32,0),0,7,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        check(passed,"supported small emoticon cannot authorize delay with selected media",ComposerTextGate.supported(emoji) && !MediaSelectionGate.knownEmpty(new i(Collections.singletonList(favorite),false)));
        SpannableStringBuilder quoted=new SpannableStringBuilder("@fixture reply");quoted.setSpan(new com.tencent.qqnt.aio.at.a(),0,8,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ComposerTextGate.Snapshot replyText=ComposerTextGate.snapshot(quoted,true);
        try {
            check(passed,"supported reply mention cannot authorize delay with unknown media state",replyText!=null && !MediaSelectionGate.knownEmpty(null));
        } finally {if(replyText!=null)replyText.clear();}
    }
}
