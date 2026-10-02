package io.github.ling.randombubble.hook;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Reads QQ 9.3.50's selected-media result. Never inspects or modifies its elements. */
final class MediaSelectionGate {
    private static final String RESULT="com.tencent.mobileqq.aio.input.edit.b$i";
    enum State { UNKNOWN, EMPTY, NON_EMPTY }
    private MediaSelectionGate() {}

    static State read(Object result) {
        try {
            if(result==null || !result.getClass().getName().equals(RESULT))return State.UNKNOWN;
            Method getter=result.getClass().getDeclaredMethod("b");
            int modifiers=getter.getModifiers();
            if(!Modifier.isPublic(modifiers) || !Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers)
                || getter.getReturnType()!=List.class)return State.UNKNOWN;
            Object selected=getter.invoke(result);
            if(!(selected instanceof List))return State.UNKNOWN;
            return ((List<?>)selected).isEmpty()?State.EMPTY:State.NON_EMPTY;
        } catch(Throwable unknown) {return State.UNKNOWN;}
    }

    /** Only a successfully read empty native result can authorize a text-only delay. */
    static boolean knownEmpty(Object result) {return read(result)==State.EMPTY;}
}
