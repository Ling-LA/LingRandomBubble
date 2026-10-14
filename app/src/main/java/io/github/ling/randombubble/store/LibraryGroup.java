package io.github.ling.randombubble.store;

import io.github.ling.randombubble.core.BubbleSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/** Presentation grouping only. Original templates remain intact and searchable. */
public final class LibraryGroup {
    public final List<JSONObject> rows=new ArrayList<>();
    public final int bubbleId;
    private LibraryGroup(int id){bubbleId=id;}
    public static ArrayList<LibraryGroup> of(Collection<JSONObject> rows) {
        LinkedHashMap<String,LibraryGroup> groups=new LinkedHashMap<>();
        for(JSONObject row:rows)if(row!=null) {
            int id=row.optInt("bubbleId");String key="bubble:"+id;
            if(id<=0)try{key="record:"+JsonCodec.decode(row).key();}catch(Exception e){key="invalid:"+row.toString();}
            LibraryGroup group=groups.get(key);if(group==null){group=new LibraryGroup(id);groups.put(key,group);}group.rows.add(row);
        }
        return new ArrayList<>(groups.values());
    }
    public int selected(){int n=0;for(JSONObject row:rows)if(row.optBoolean("selected"))n++;return n;}
    public boolean allSelected(){return !rows.isEmpty() && selected()==rows.size();}
    public boolean partial(){int n=selected();return n>0 && n<rows.size();}
    public Set<String> keys() throws JSONException{return LibraryRows.keys(rows);}
    public String name() throws JSONException {
        String fallback="";
        for(JSONObject row:rows) {
            BubbleSpec spec=JsonCodec.decode(row);String raw=row.optString("name","").trim();
            if(!raw.isEmpty() && !raw.equals(spec.label()))return raw;
            String label=LibraryRows.displayName(row);if(fallback.isEmpty() || !LibraryRows.officialName(row).isEmpty())fallback=label;
        }
        return fallback;
    }
    public String detail() throws JSONException {
        String label=rows.size()==1?JsonCodec.decode(rows.get(0)).label():"编号 "+bubbleId+" · 已合并 "+rows.size()+" 条记录";
        return partial()?label+" · 部分勾选 "+selected()+"/"+rows.size():label;
    }
}
