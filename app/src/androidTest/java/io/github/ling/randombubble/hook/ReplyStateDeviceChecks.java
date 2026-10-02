package io.github.ling.randombubble.hook;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.method.ArrowKeyMovementMethod;
import android.widget.EditText;
import android.widget.TextView;
import com.tencent.mobileqq.aio.input.l;
import com.tencent.mobileqq.aio.reply.a;
import com.tencent.mobileqq.aio.reply.d;
import java.lang.reflect.Field;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Isolated Android reply-state fixtures. No QQ context, views, messages or preferences. */
public final class ReplyStateDeviceChecks {
    private static final int KEY=0x7f123456;
    private ReplyStateDeviceChecks() {}
    private static void check(Consumer<String> passed,String name,boolean result) {
        if(!result)throw new AssertionError(name);passed.accept(name);
    }
    public static void run(Context context,Consumer<String> passed) throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper()) {runUi(context,passed);return;}
        FutureTask<Void> task=new FutureTask<>(()->{runUi(context,passed);return null;});
        if(!new Handler(Looper.getMainLooper()).post(task))throw new IllegalStateException("Fixture UI thread unavailable");
        task.get(20,TimeUnit.SECONDS);
    }
    private static EditText editor(Context context) {EditText editor=new EditText(context);editor.setText("synthetic draft");return editor;}
    private static Drawable drawable() {ColorDrawable value=new ColorDrawable(0xff112233);value.setBounds(0,0,16,16);return value;}
    private static final class Fixture {
        final EditText editor;
        final l data;
        final d tag;
        Fixture(Context context) {this(context,"synthetic nickname","synthetic quoted text",17L,23L);}
        Fixture(Context context,String nickname,String quoted,long sequence,long messageId) {
            editor=editor(context);data=new l(nickname,quoted,sequence,messageId);
            tag=new d(editor,drawable(),new SpannableStringBuilder("synthetic preview"));
            editor.setMovementMethod(new a());editor.setTag(KEY,tag);editor.setCompoundDrawables(null,tag.e,null,null);
        }
        ReplyStateGate.Snapshot save() throws Exception {return ReplyStateGate.snapshot(editor,data,KEY);}
    }
    private static final class TagSubclass extends d {
        TagSubclass(EditText editor,Drawable top,CharSequence text) {super(editor,top,text);}
    }
    private static final class MovementSubclass extends a {}
    private static void set(l data,String name,Object value) throws Exception {
        Field field=l.class.getDeclaredField(name);field.setAccessible(true);field.set(data,value);
    }

    private static void runUi(Context context,Consumer<String> passed) throws Exception {
        check(passed,"reply gate rejects missing editor",ReplyStateGate.snapshot(null,null,KEY)==null);
        EditText input=editor(context);
        check(passed,"reply gate rejects unknown resource key",ReplyStateGate.snapshot(input,null,0)==null);
        check(passed,"production reply gate requires the real QQ resource",ReplyStateGate.snapshot(input,null)==null);
        ReplyStateGate.Snapshot saved=ReplyStateGate.snapshot(input,null,KEY);
        check(passed,"ordinary snapshot is inactive and matches known empty reply",saved!=null && !saved.active() && saved.matches(input,null));
        input.setMovementMethod(new a());saved=ReplyStateGate.snapshot(input,null,KEY);
        check(passed,"ordinary snapshot accepts unchanged cancelled-reply movement",saved!=null && saved.matches(input,null));
        input.setMovementMethod(new a());
        check(passed,"ordinary snapshot rejects movement identity replacement",!saved.matches(input,null));
        input=editor(context);saved=ReplyStateGate.snapshot(input,null,KEY);input.setMovementMethod(new a());
        check(passed,"ordinary snapshot rejects adding movement after capture",!saved.matches(input,null));
        input=editor(context);input.setTag(KEY,new Object());
        check(passed,"ordinary gate rejects an unknown reply tag",ReplyStateGate.snapshot(input,null,KEY)==null);
        input=editor(context);input.setCompoundDrawables(null,drawable(),null,null);
        check(passed,"ordinary gate rejects a leftover reply drawable",ReplyStateGate.snapshot(input,null,KEY)==null);
        input=editor(context);
        check(passed,"reply gate rejects unknown logical result data",ReplyStateGate.snapshot(input,new Object(),KEY)==null);
        check(passed,"logical reply without a tag remains unsupported",ReplyStateGate.snapshot(input,new l("n","q",1,2),KEY)==null);

        Fixture f=new Fixture(context);saved=f.save();
        check(passed,"active known reply snapshot preserves its complete native state",saved!=null && saved.active() && saved.matches(f.editor,f.data));
        f=new Fixture(context,"","",0L,0L);
        check(passed,"known reply accepts empty strings and zero identifiers",f.save()!=null);
        f=new Fixture(context,"n","q",-17L,-23L);
        check(passed,"reply gate does not guess signed identifier semantics",f.save()!=null);
        f=new Fixture(context,null,"q",1L,2L);
        check(passed,"reply gate rejects missing known nickname field",f.save()==null);
        f=new Fixture(context,"n",null,1L,2L);
        check(passed,"reply gate rejects missing known quoted-text field",f.save()==null);
        f=new Fixture(context);f.editor.setTag(KEY,new Object());
        check(passed,"active reply rejects an unknown tag class",f.save()==null);
        f=new Fixture(context);f.editor.setTag(KEY,new TagSubclass(f.editor,f.tag.e,f.tag.h));
        check(passed,"active reply rejects a tag subclass",f.save()==null);
        f=new Fixture(context);f.tag.f=editor(context);
        check(passed,"active reply requires the exact internal preview TextView",f.save()==null);
        f=new Fixture(context);f.tag.e=null;
        check(passed,"active reply requires an initialized native drawable cache",f.save()==null);
        f=new Fixture(context);f.tag.h=null;
        check(passed,"active reply requires known preview metadata",f.save()==null);
        f=new Fixture(context);f.editor.setCompoundDrawables(null,null,null,null);
        check(passed,"active reply requires its native top drawable",f.save()==null);
        f=new Fixture(context);f.editor.setCompoundDrawables(null,drawable(),null,null);
        check(passed,"active reply top drawable must be the exact tag cache",f.save()==null);
        f=new Fixture(context);f.editor.setCompoundDrawables(drawable(),f.tag.e,null,null);
        check(passed,"active reply rejects an additional left drawable",f.save()==null);
        f=new Fixture(context);f.editor.setCompoundDrawables(null,f.tag.e,drawable(),null);
        check(passed,"active reply rejects an additional right drawable",f.save()==null);
        f=new Fixture(context);f.editor.setCompoundDrawables(null,f.tag.e,null,drawable());
        check(passed,"active reply rejects an additional bottom drawable",f.save()==null);
        f=new Fixture(context);f.editor.setMovementMethod(new ArrowKeyMovementMethod());
        check(passed,"active reply rejects an unknown movement class",f.save()==null);
        f=new Fixture(context);f.editor.setMovementMethod(new MovementSubclass());
        check(passed,"active reply rejects a movement subclass",f.save()==null);

        f=new Fixture(context);saved=f.save();
        check(passed,"reply snapshot rejects value-equal logical object replacement",!saved.matches(f.editor,new l("synthetic nickname","synthetic quoted text",17L,23L)));
        f=new Fixture(context);saved=f.save();f.editor.setTag(KEY,new d(f.editor,f.tag.e,f.tag.h));
        check(passed,"reply snapshot rejects tag identity replacement",!saved.matches(f.editor,f.data));
        f=new Fixture(context);saved=f.save();f.tag.e=drawable();f.editor.setCompoundDrawables(null,f.tag.e,null,null);
        check(passed,"reply snapshot rejects drawable and cache identity replacement",!saved.matches(f.editor,f.data));
        f=new Fixture(context);saved=f.save();f.editor.setMovementMethod(new a());
        check(passed,"reply snapshot rejects replacement native movement identity",!saved.matches(f.editor,f.data));
        f=new Fixture(context);saved=f.save();((SpannableStringBuilder)f.tag.h).append("changed");
        check(passed,"reply snapshot rejects an in-place preview edit",!saved.matches(f.editor,f.data));
        f=new Fixture(context);saved=f.save();f.tag.h=new SpannableStringBuilder("synthetic preview");
        check(passed,"reply snapshot rejects value-equal preview object replacement",!saved.matches(f.editor,f.data));
        f=new Fixture(context);saved=f.save();f.tag.f=new TextView(context);
        check(passed,"reply snapshot rejects replacement preview-view identity",!saved.matches(f.editor,f.data));
        f=new Fixture(context);saved=f.save();set(f.data,"a","changed synthetic nickname");
        check(passed,"reply snapshot rejects an in-place nickname field change",!saved.matches(f.editor,f.data));
        f=new Fixture(context);saved=f.save();set(f.data,"b","changed synthetic quoted text");
        check(passed,"reply snapshot rejects an in-place quoted-text field change",!saved.matches(f.editor,f.data));
        f=new Fixture(context);saved=f.save();set(f.data,"c",18L);
        check(passed,"reply snapshot rejects an in-place sequence change",!saved.matches(f.editor,f.data));
        f=new Fixture(context);saved=f.save();set(f.data,"d",24L);
        check(passed,"reply snapshot rejects an in-place message identifier change",!saved.matches(f.editor,f.data));
        f=new Fixture(context);saved=f.save();f.editor.setTag(KEY,null);f.editor.setCompoundDrawables(null,null,null,null);
        check(passed,"reply snapshot rejects a cleared logical and visual reply",!saved.matches(f.editor,null));
        f=new Fixture(context);saved=ReplyStateGate.snapshot(f.editor,null,KEY);
        check(passed,"ordinary state cannot capture an active visual reply",saved==null);
        input=editor(context);saved=ReplyStateGate.snapshot(input,null,KEY);input.setTag(KEY,new Object());
        check(passed,"ordinary snapshot rejects a reply tag added while waiting",!saved.matches(input,null));
        f=new Fixture(context);saved=f.save();
        check(passed,"reply snapshot rejects a different editor",!saved.matches(editor(context),f.data));
        f=new Fixture(context,"n","\uD800",1L,2L);saved=f.save();set(f.data,"b","\uD801");
        check(passed,"reply fingerprints distinguish unpaired UTF16 surrogate values",!saved.matches(f.editor,f.data));
        input=editor(context);saved=ReplyStateGate.snapshot(input,null,KEY);saved.clear();
        check(passed,"cleared ordinary snapshot cannot authorize replay",!saved.active() && !saved.matches(input,null));
        f=new Fixture(context);saved=f.save();saved.clear();
        check(passed,"cleared active snapshot drops replay authorization",!saved.active() && !saved.matches(f.editor,f.data));
    }
}
