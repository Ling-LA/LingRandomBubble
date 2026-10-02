package io.github.ling.randombubble.hook;

import android.graphics.drawable.Drawable;
import android.text.method.MovementMethod;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.Arrays;

/** Read-only QQ 9.3.50 reply-state checks. Never renders, clears or edits a reply. */
final class ReplyStateGate {
    private static final String DATA="com.tencent.mobileqq.aio.input.l";
    private static final String TAG="com.tencent.mobileqq.aio.reply.d";
    private static final String MOVEMENT="com.tencent.mobileqq.aio.reply.a";
    private ReplyStateGate() {}

    /** The caller must pass null only after a known GetReplyData result reports no reply. */
    static Snapshot snapshot(EditText editor,Object logicalReply) throws Exception {
        if(editor==null)return null;
        return read(editor,logicalReply,replyKey(editor),true);
    }

    /** Isolated Android fixtures supply a known key without inventing QQ resources. */
    static Snapshot snapshot(EditText editor,Object logicalReply,int knownTagKey) throws Exception {
        return read(editor,logicalReply,knownTagKey,false);
    }

    private static int replyKey(EditText editor) {
        return editor.getResources().getIdentifier("gja","id","com.tencent.mobileqq");
    }

    private static Snapshot read(EditText editor,Object data,int key,boolean verifyKey) throws Exception {
        if(editor==null || key==0)return null;
        Object tag=editor.getTag(key);
        MovementMethod movement=editor.getMovementMethod();
        Drawable[] absolute=editor.getCompoundDrawables(),relative=editor.getCompoundDrawablesRelative();
        if(data==null) {
            if(tag!=null || !drawables(absolute,null) || !drawables(relative,null))return null;
            return new Snapshot(editor,key,verifyKey,null,null,movement,null,null,null,0L,0L,null,null,null);
        }
        if(!exact(data,DATA) || !exact(tag,TAG) || !exact(movement,MOVEMENT))return null;
        String nickname=(String)immutableField(data,"a",String.class).get(data);
        String quoted=(String)immutableField(data,"b",String.class).get(data);
        long sequence=immutableField(data,"c",long.class).getLong(data),messageId=immutableField(data,"d",long.class).getLong(data);
        Object previewView=field(tag,"f",TextView.class).get(tag);
        Drawable top=(Drawable)field(tag,"e",Drawable.class).get(tag);
        CharSequence preview=(CharSequence)field(tag,"h",CharSequence.class).get(tag);
        // This field is QQ's internally rendered preview TextView, not the input EditText.
        if(nickname==null || quoted==null || preview==null || previewView==null || previewView.getClass()!=TextView.class || top==null
            || !drawables(absolute,top) || !drawables(relative,top))return null;
        // Identifiers may be zero or signed; preserve their exact values, never guess their semantics.
        return new Snapshot(editor,key,verifyKey,data,tag,movement,top,previewView,preview,sequence,messageId,
            fingerprint(nickname),fingerprint(quoted),fingerprint(preview));
    }

    private static boolean exact(Object value,String name) {
        return value!=null && value.getClass().getName().equals(name);
    }
    private static Field field(Object object,String name,Class<?> type) throws Exception {
        Field field=object.getClass().getDeclaredField(name);
        if(field.getType()!=type)throw new IllegalArgumentException("Unknown reply schema");
        field.setAccessible(true);return field;
    }
    private static Field immutableField(Object object,String name,Class<?> type) throws Exception {
        Field field=field(object,name,type);
        if(!Modifier.isPrivate(field.getModifiers()) || !Modifier.isFinal(field.getModifiers()))throw new IllegalArgumentException("Unknown reply data schema");
        return field;
    }
    private static boolean drawables(Drawable[] values,Drawable top) {
        return values!=null && values.length==4 && values[0]==null && values[1]==top && values[2]==null && values[3]==null;
    }
    private static byte[] fingerprint(CharSequence text) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        // Preserve raw UTF-16, including unpaired surrogates, without encoding replacement.
        int length=text.length();
        for(int i=0;i<length;i++) {char value=text.charAt(i);digest.update((byte)(value>>>8));digest.update((byte)value);}
        if(text.length()!=length)throw new IllegalArgumentException("Reply preview changed while reading");
        return digest.digest();
    }

    static final class Snapshot {
        private final WeakReference<EditText> editor;
        private final WeakReference<Object> data,tag,movement,top,previewView,preview;
        private final int key;
        private final boolean verifyKey,active,movementPresent;
        private long sequence,messageId;
        private byte[] nicknameHash,quotedHash,previewHash;
        private boolean cleared;

        private Snapshot(EditText editor,int key,boolean verifyKey,Object data,Object tag,Object movement,Object top,Object previewView,Object preview,
            long sequence,long messageId,byte[] nicknameHash,byte[] quotedHash,byte[] previewHash) {
            this.editor=new WeakReference<>(editor);this.key=key;this.verifyKey=verifyKey;active=data!=null;
            this.data=new WeakReference<>(data);this.tag=new WeakReference<>(tag);this.movement=new WeakReference<>(movement);
            this.top=new WeakReference<>(top);this.previewView=new WeakReference<>(previewView);this.preview=new WeakReference<>(preview);movementPresent=movement!=null;
            this.sequence=sequence;this.messageId=messageId;this.nicknameHash=nicknameHash;this.quotedHash=quotedHash;this.previewHash=previewHash;
        }

        synchronized boolean active() {return !cleared && active;}

        synchronized boolean matches(EditText input,Object logicalReply) {
            if(cleared || input==null || editor.get()!=input || (movementPresent && movement.get()==null))return false;
            if(active && (data.get()==null || tag.get()==null || top.get()==null || previewView.get()==null || preview.get()==null))return false;
            Snapshot now=null;
            try {
                if(verifyKey && replyKey(input)!=key)return false;
                now=read(input,logicalReply,key,verifyKey);
                if(now==null || active!=now.active || data.get()!=now.data.get() || tag.get()!=now.tag.get()
                    || movement.get()!=now.movement.get() || top.get()!=now.top.get() || previewView.get()!=now.previewView.get() || preview.get()!=now.preview.get())return false;
                return !active || (sequence==now.sequence && messageId==now.messageId
                    && MessageDigest.isEqual(nicknameHash,now.nicknameHash) && MessageDigest.isEqual(quotedHash,now.quotedHash)
                    && MessageDigest.isEqual(previewHash,now.previewHash));
            }catch(Exception ignored) {return false;}
            finally {if(now!=null)now.clear();}
        }

        synchronized void clear() {
            cleared=true;sequence=0L;messageId=0L;
            if(nicknameHash!=null)Arrays.fill(nicknameHash,(byte)0);
            if(quotedHash!=null)Arrays.fill(quotedHash,(byte)0);
            if(previewHash!=null)Arrays.fill(previewHash,(byte)0);
            nicknameHash=null;quotedHash=null;previewHash=null;
            editor.clear();data.clear();tag.clear();movement.clear();top.clear();previewView.clear();preview.clear();
        }
    }
}
