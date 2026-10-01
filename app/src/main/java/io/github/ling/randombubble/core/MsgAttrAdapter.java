package io.github.ling.randombubble.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** QQ 9.3.50 experimental adapter. No QQ classes are bundled or compile-time linked.
 * copy-on-write: only map -> MsgAttributeInfo -> VASMsgElement -> bubbleInfo is copied.
 * All non-bubble attributes and message elements remain untouched.
 */
public final class MsgAttrAdapter {
    private static final String PREFIX = "com.tencent.qqnt.kernel.nativeinterface.";
    private final ClassLoader loader;
    public MsgAttrAdapter(ClassLoader loader) { this.loader = loader; }
    public List<BubbleSpec> extract(Object rawMap) {
        List<BubbleSpec> out = new ArrayList<>();
        if (!(rawMap instanceof Map)) return out;
        Map<?, ?> map = (Map<?, ?>)rawMap;
        if (map.size() > 128) return out;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            try {
                if (!(e.getKey() instanceof Integer) || e.getValue() == null) continue;
                Object attr = e.getValue();
                Object vas = Reflect.get(attr, "vasMsgInfo");
                if (vas == null) continue;
                Object b = Reflect.get(vas, "bubbleInfo");
                if (b == null) continue;
                out.add(new BubbleSpec((Integer)e.getKey(),
                        Reflect.intValue(Reflect.get(attr, "attrType")),
                        Reflect.longValue(Reflect.get(attr, "attrId")),
                        Reflect.nullableInt(Reflect.get(b, "bubbleId")),
                        Reflect.nullableInt(Reflect.get(b, "subBubbleId")),
                        Reflect.nullableInt(Reflect.get(b, "bubbleDiyTextId")),
                        Reflect.nullableInt(Reflect.get(b, "canConvertToText"))));
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) { /* no viable template */ }
        }
        return out;
    }
    public HashMap<Object,Object> withBubble(Object rawMap, BubbleSpec spec) throws ReflectiveOperationException {
        return withBubble(rawMap, spec, -1L);
    }
    /** Use outgoing identity for new attributes; server acceptance still requires receiver testing. */
    public HashMap<Object,Object> withBubble(Object rawMap, BubbleSpec spec, long messageId) throws ReflectiveOperationException {
        if (rawMap != null && !(rawMap instanceof Map)) throw new IllegalArgumentException("Not an attribute map");
        Map<?,?> original = rawMap == null ? new HashMap<>() : (Map<?,?>)rawMap;
        if (original.size() > 128) throw new IllegalArgumentException("Oversized attributes");
        // The outgoing message already carries the account bubble in its own VAS slot.
        // Put the collected style there instead of refusing a different key.
        Object vasKey=null; Object vasAttr=null; int vasCount=0;
        for (Map.Entry<?,?> e : original.entrySet()) {
            if (e.getValue()!=null && Reflect.get(e.getValue(), "vasMsgInfo")!=null) {
                vasCount++; vasKey=e.getKey(); vasAttr=e.getValue();
            }
        }
        if (vasCount>1) throw new IllegalArgumentException("Different VAS key already present");
        Object targetKey = vasAttr!=null ? vasKey : Integer.valueOf(spec.attrKey);
        Object oldAttr = vasAttr!=null ? vasAttr : original.get(spec.attrKey);
        Object attr;
        if (oldAttr != null) {
            if (vasAttr==null && Reflect.intValue(Reflect.get(oldAttr, "attrType")) != spec.attrType)
                throw new IllegalArgumentException("Attribute type mismatch");
            attr = Reflect.copy(oldAttr); // Preserve current attrId, not donor identity.
        } else {
            attr = create("MsgAttributeInfo");
            Reflect.set(attr, "attrType", spec.attrType);
            Reflect.set(attr, "attrId", messageId > 0 ? messageId : spec.attrId);
        }
        Object oldVas = Reflect.get(attr, "vasMsgInfo");
        Object vas = oldVas == null ? create("VASMsgElement") : Reflect.copy(oldVas);
        Object oldBubble = Reflect.get(vas, "bubbleInfo");
        Object bubble = oldBubble == null ? create("VASMsgBubble") : Reflect.copy(oldBubble);
        Reflect.set(bubble, "bubbleId", spec.bubbleId);
        Reflect.set(bubble, "subBubbleId", spec.subBubbleId);
        Reflect.set(bubble, "bubbleDiyTextId", spec.bubbleDiyTextId);
        Reflect.set(bubble, "canConvertToText", spec.canConvertToText);
        Reflect.set(vas, "bubbleInfo", bubble);
        Reflect.set(attr, "vasMsgInfo", vas);
        HashMap<Object,Object> result = new HashMap<>(original);
        result.put(targetKey, attr);
        return result;
    }
    private Object create(String name) throws ReflectiveOperationException {
        return Reflect.newInstance(Class.forName(PREFIX + name, false, loader));
    }
    /** Strictly plain text only. Mentions, faces, replies, files, mixed messages are excluded. */
    public static String plainText(Object raw) throws ReflectiveOperationException {
        if (!(raw instanceof List)) return null;
        List<?> list = (List<?>)raw;
        if (list.isEmpty() || list.size() > 32) return null;
        StringBuilder sb = new StringBuilder();
        for (Object e : list) {
            if (e == null || Reflect.intValue(Reflect.get(e, "elementType")) != 1) return null;
            Object text = Reflect.get(e, "textElement");
            if (text == null || Reflect.intValue(Reflect.get(text, "atType")) != 0) return null;
            Object content = Reflect.get(text, "content");
            if (!(content instanceof String)) return null;
            sb.append(content);
            if (sb.length() > SendPermit.MAX_TEXT) return null;
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
