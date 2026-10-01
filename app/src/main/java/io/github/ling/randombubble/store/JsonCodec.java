package io.github.ling.randombubble.store;

import io.github.ling.randombubble.core.BubbleSpec;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class JsonCodec {
    /** No product cap. The byte guard only rejects a corrupt or enormous file. */
    public static final int MAX_LIBRARY = Integer.MAX_VALUE;
    public static final int MAX_JSON_CHARS = 4_000_000;
    private JsonCodec() {}
    public static JSONObject encode(BubbleSpec b) throws JSONException {
        JSONObject j = new JSONObject();
        j.put("attrKey", b.attrKey); j.put("attrType", b.attrType); j.put("attrId", b.attrId);
        j.put("bubbleId", nullable(b.bubbleId)); j.put("subBubbleId", nullable(b.subBubbleId));
        j.put("bubbleDiyTextId", nullable(b.bubbleDiyTextId)); j.put("canConvertToText", nullable(b.canConvertToText));
        return j;
    }
    private static Object nullable(Integer n) { return n == null ? JSONObject.NULL : n; }
    private static long number(JSONObject j, String key) throws JSONException {
        Object raw = j.get(key);
        if (!(raw instanceof Integer) && !(raw instanceof Long)) throw new JSONException("Integer field required: " + key);
        return ((Number)raw).longValue();
    }
    private static int small(JSONObject j, String key) throws JSONException {
        long n = number(j, key);
        if (n < 0 || n > Integer.MAX_VALUE) throw new JSONException("Field out of range: " + key);
        return (int)n;
    }
    private static Integer optInt(JSONObject j, String key) throws JSONException {
        return !j.has(key) || j.isNull(key) ? null : small(j, key);
    }
    public static BubbleSpec decode(JSONObject j) throws JSONException {
        try {
            return new BubbleSpec(small(j,"attrKey"), small(j,"attrType"), number(j,"attrId"),
                    optInt(j,"bubbleId"), optInt(j,"subBubbleId"), optInt(j,"bubbleDiyTextId"), optInt(j,"canConvertToText"));
        } catch (IllegalArgumentException e) { throw new JSONException("Invalid bubble metadata"); }
    }
    public static JSONObject object(String s) throws JSONException {
        if (s == null || s.length() > MAX_JSON_CHARS) throw new JSONException("JSON too large/null");
        return new JSONObject(s);
    }
    public static Config config(String text) throws JSONException {
        JSONObject j = object(text);
        JSONArray a = j.getJSONArray("bubbles");
        List<BubbleSpec> selected = new ArrayList<>();
        for (int i=0; i<a.length(); i++) {
            JSONObject row = a.getJSONObject(i);
            if (row.optBoolean("selected", false)) selected.add(decode(row));
        }
        return new Config(j.optBoolean("enabled",false), j.optBoolean("collect",false),
                j.optBoolean("fixed",false), j.optBoolean("avoidRepeat",true),
                j.optBoolean("groups",true), j.optBoolean("privateChats",true), selected);
    }
    /** Older builds enabled sending and selected harvested styles without consent. */
    public static JSONObject migrateSafety(JSONObject j) throws JSONException {
        if(j.optInt("safetyVersion",0)<1) {
            j.put("enabled",false).put("collect",false).put("safetyVersion",1);
            JSONArray rows=j.getJSONArray("bubbles");
            for(int i=0;i<rows.length();i++) rows.getJSONObject(i).put("selected",false);
        }
        return j;
    }
    public static JSONObject defaults() {
        try {
            return new JSONObject().put("schema",1).put("safetyVersion",1).put("enabled",false).put("collect",false)
                    .put("fixed",false).put("avoidRepeat",true).put("groups",true)
                    .put("privateChats",true).put("bubbles",new JSONArray());
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
}
