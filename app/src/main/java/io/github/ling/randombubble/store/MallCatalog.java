package io.github.ling.randombubble.store;

import io.github.ling.randombubble.core.BubbleSpec;
import java.util.*;
import org.json.*;

/** Public mall metadata only: main bubble ID and official name. */
public final class MallCatalog {
    private MallCatalog() {}
    public static final class Item {
        public final int id;
        public final String name;
        public Item(int id,String name) {
            if(id<=0 || id>999999999)throw new IllegalArgumentException("bubble ID");
            this.id=id;this.name=name==null?"":name.trim();
            if(this.name.length()>128)throw new IllegalArgumentException("bubble name");
        }
    }
    public static final int MAX_CHARS=524288;
    /** Only explicitly typed item objects are accepted, never bare numbers or URL digits. */
    public static List<Item> parse(JSONObject response) throws JSONException {
        if(response==null || response.toString().length()>MAX_CHARS)throw new JSONException("mall response size");
        LinkedHashMap<Integer,Item> items=new LinkedHashMap<>();walk(response,0,new int[]{0},items);
        return new ArrayList<>(items.values());
    }
    /** Only the observed, read-only mall catalog/search endpoints. Queries are not retained. */
    public static boolean acceptsUrl(String url) {
        if(url==null || url.length()>8192)return false;
        try {
            java.net.URI uri=new java.net.URI(url);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || !"zb.vip.qq.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo()!=null || (uri.getPort()!=-1 && uri.getPort()!=443) || uri.getFragment()!=null)return false;
            String path=uri.getRawPath();
            return "/v2/kuikly-api/new-mall-first-screen".equals(path)
                || "/trpc-proxy/qqva/qc_submall_server/qc_submall_server/GetMoreListV2".equals(path)
                || "/trpc-proxy/qqva/qc_search_server/personalize_search/gxhCommSearchV2".equals(path);
        }catch(java.net.URISyntaxException e){return false;}
    }
    public static boolean httpSucceeded(Object success,Object status) {
        int code=integer(status);return integer(success)==1 && code>=200 && code<300;
    }
    public static List<Item> parseHttp(String json)throws JSONException {
        if(json==null || json.length()>MAX_CHARS)throw new JSONException("mall response size");
        JSONObject root=new JSONObject(json);Object payload;
        if(root.has("retCode")) {
            if(integer(root.opt("retCode"))!=0)return Collections.emptyList();
            JSONObject error=root.optJSONObject("error");
            if(root.has("error") && (error==null || integer(error.opt("code"))!=0))return Collections.emptyList();
            payload=root.opt("response");
        }else {
            if(integer(root.opt("code"))!=0)return Collections.emptyList();
            payload=root.opt("data");
        }
        // Parse only the successful data envelope, excluding transport and account metadata.
        if(!(payload instanceof JSONObject))return Collections.emptyList();
        return parse((JSONObject)payload);
    }
    private static int integer(Object value) {
        if(value instanceof Integer || value instanceof Long){long n=((Number)value).longValue();return n>=0 && n<=999999999?(int)n:-1;}
        if(value instanceof String && ((String)value).matches("[0-9]{1,9}"))try{return Integer.parseInt((String)value);}catch(NumberFormatException ignored){}
        return -1;
    }
    private static void walk(Object value,int depth,int[] count,LinkedHashMap<Integer,Item> items)throws JSONException {
        if(depth>12 || ++count[0]>16000 || items.size()>2048)throw new JSONException("mall response complexity");
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)walk(a.opt(i),depth+1,count,items);}
        else if(value instanceof JSONObject){
            JSONObject j=(JSONObject)value;
            if(j.has("ret") && integer(j.opt("ret"))!=0)return;
            boolean snake=j.has("app_id");
            if(integer(j.opt(snake?"app_id":"appId"))==2){
                int id=integer(j.opt(snake?"item_id":"itemId"));
                Object raw=j.opt(snake?"item_name":"itemName");if(!(raw instanceof String))raw=j.opt("name");
                String name=raw instanceof String?((String)raw).trim():"";
                if(id>0 && name.length()<=128){Item old=items.get(id);if(old==null || old.name.isEmpty())items.put(id,new Item(id,name));}
            }
            Iterator<String> keys=j.keys();while(keys.hasNext()){Object nested=j.opt(keys.next());if(nested instanceof JSONObject || nested instanceof JSONArray)walk(nested,depth+1,count,items);}
        }
    }
    public static final class Merge {
        public int added,named;
        public boolean changed(){return added>0 || named>0;}
    }
    /** Main-ID dedup also covers existing chat variants; never selects or changes controls. */
    public static Merge merge(JSONObject document,Collection<Item> items) throws JSONException {
        JSONArray rows=document.getJSONArray("bubbles");Map<Integer,List<JSONObject>> byId=new HashMap<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);int id=row.optInt("bubbleId");
            if(id>0)byId.computeIfAbsent(id,k->new ArrayList<>()).add(row);
        }
        Merge result=new Merge();
        for(Item item:items){
            if(item==null)continue;
            List<JSONObject> existing=byId.get(item.id);
            if(existing==null){
                BubbleSpec spec=new BubbleSpec(17,17,0L,item.id,0,null,0);
                JSONObject row=JsonCodec.encode(spec).put("name",spec.label()).put("selected",false);
                rows.put(row);existing=new ArrayList<>();existing.add(row);byId.put(item.id,existing);result.added++;
            }
            for(JSONObject row:existing)if(LibraryRows.mergeName(row,item.name))result.named++;
        }
        return result;
    }
}
