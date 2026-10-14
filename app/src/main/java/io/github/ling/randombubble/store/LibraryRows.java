package io.github.ling.randombubble.store;

import io.github.ling.randombubble.core.BubbleSpec;
import io.github.ling.randombubble.core.LibrarySearch;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Display/search metadata never changes style IDs or rotation configuration. */
public final class LibraryRows {
    private LibraryRows() {}
    public static String officialName(JSONObject row) {
        Object raw=row.opt("officialName");
        return raw instanceof String && ((String)raw).trim().length()<=128?((String)raw).trim():"";
    }
    public static String displayName(JSONObject row) throws JSONException {
        String name=row.isNull("name")?"":row.optString("name","").trim();BubbleSpec b=JsonCodec.decode(row);
        if(name.isEmpty() || name.equals(b.label())) {
            String official=officialName(row);if(!official.isEmpty())return official;
            return b.label();
        }
        return name;
    }
    public static boolean matches(JSONObject row,LibrarySearch query,boolean selectedOnly) {
        return row!=null && (!selectedOnly || row.optBoolean("selected")) && query.matches(
            row.isNull("name")?"":row.optString("name"),officialName(row),row.isNull("bubbleId")?"":row.optString("bubbleId"),
            row.isNull("subBubbleId")?"":row.optString("subBubbleId"));
    }
    /** Snapshot every filtered row, including pages not on screen. */
    public static Set<String> keys(Collection<JSONObject> rows) throws JSONException {
        Set<String> keys=new HashSet<>();for(JSONObject row:rows)keys.add(JsonCodec.decode(row).key());return keys;
    }
    public static void select(JSONObject document,Set<String> keys,boolean value) throws JSONException {
        JSONArray rows=document.getJSONArray("bubbles");boolean any=false;
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);
            if(keys.contains(JsonCodec.decode(row).key()))row.put("selected",value);
            any|=row.optBoolean("selected");
        }
        if(!any)document.put("enabled",false);
    }
    public static boolean mergeName(JSONObject row,String name) throws JSONException {
        if(name==null || name.trim().isEmpty() || name.trim().length()>128)return false;
        name=name.trim();if(name.equals(officialName(row)))return false;
        row.put("officialName",name);return true;
    }
    /** Details are accepted only for the requested bubble, never another app/item. */
    public static String detailName(JSONObject detail,int id) {
        if(detail==null)return "";JSONObject base=detail.optJSONObject("baseInfo");
        if(base==null || base.optInt("appId")!=2 || base.optInt("itemId")!=id || id<=0)return "";
        Object value=base.opt("name");if(!(value instanceof String))value=base.opt("itemName");
        if(!(value instanceof String))return "";
        String name=((String)value).trim();return name.length()<=128?name:"";
    }
    public static JSONObject copyNames(JSONObject source,JSONObject target) throws JSONException {
        String name=source.optString("name","");if(!name.isEmpty())target.put("name",name.substring(0,Math.min(48,name.length())));
        mergeName(target,officialName(source));return target;
    }
}
