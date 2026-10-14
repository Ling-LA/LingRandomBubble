package io.github.ling.randombubble;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;
import io.github.ling.randombubble.store.*;
import io.github.ling.randombubble.core.BubbleSpec;
public final class MallCollectionDeviceChecks {
 private static void check(Consumer<String> out,String name,boolean ok){if(!ok)throw new AssertionError(name);out.accept(name);}
 private static JSONObject item(Object app,Object id,String name)throws Exception{return new JSONObject().put("baseInfo",new JSONObject().put("appId",app).put("itemId",id).put("itemName",name));}
 public static void run(Consumer<String> out)throws Exception{
  JSONObject response=new JSONObject().put("items",new JSONArray().put(item(2,101,"商城花花")).put(item(2,101,"商城花花")).put(item(4,102,"其他装扮")).put(item(2,103,"小猫")));
  List<MallCatalog.Item> items=MallCatalog.parse(response);
  check(out,"mall list filters non-bubbles and deduplicates main IDs",items.size()==2 && items.get(0).id==101 && items.get(1).id==103);
  check(out,"mall detail keeps the actual item name",MallCatalog.parse(item(2,201,"指定气泡")).get(0).name.equals("指定气泡"));
  check(out,"mall parser rejects missing or untyped IDs",MallCatalog.parse(new JSONObject().put("items",new JSONArray().put(new JSONObject().put("itemId",201)).put(item(2,0,"默认")).put(item(2,1.2,"坏编号")).put(item(true,102,"坏类型")).put(item(2,"1e2","坏字符串")))).isEmpty());
  check(out,"mall parser accepts integer text without coercing floats",MallCatalog.parse(item("2","201","字符串编号")).size()==1);
  check(out,"mall parser rejects failed business responses",MallCatalog.parse(new JSONObject().put("ret",1).put("items",response.getJSONArray("items"))).isEmpty());
  check(out,"mall parser ignores identifiers embedded in URLs and descriptions",MallCatalog.parse(new JSONObject().put("url","https://example.invalid/bubble/999").put("description","{itemId:2}")).isEmpty());
  JSONObject deep=new JSONObject(),cursor=deep;for(int i=0;i<16;i++){JSONObject next=new JSONObject();cursor.put("child",next);cursor=next;}
  boolean failed=false;try{MallCatalog.parse(deep);}catch(JSONException expected){failed=true;}check(out,"mall parser bounds nested response complexity",failed);
  JSONObject snake=new JSONObject().put("series",new JSONArray().put(new JSONObject().put("items",new JSONArray().put(new JSONObject().put("base_info",new JSONObject().put("app_id",2).put("item_id",209).put("item_name","商城列表"))))));
  check(out,"mall WebSSO snake-case base fields collect only typed bubble items",MallCatalog.parse(snake).size()==1 && MallCatalog.parse(snake).get(0).name.equals("商城列表"));
  JSONObject flat=new JSONObject().put("appId",2).put("itemId",301).put("name","商城搜索款");
  JSONObject search=new JSONObject().put("retCode",0).put("error",new JSONObject().put("code",0)).put("response",new JSONObject().put("results",new JSONArray().put(flat).put(new JSONObject().put("appId",4).put("itemId",302).put("name","挂件"))));
  check(out,"mall HTTP search collects flat bubble results with names",MallCatalog.parseHttp(search.toString()).size()==1 && MallCatalog.parseHttp(search.toString()).get(0).name.equals("商城搜索款"));
  JSONObject home=new JSONObject().put("code",0).put("data",new JSONObject().put("recommendModuleList",new JSONArray().put(new JSONObject().put("newItems",new JSONArray().put(new JSONObject().put("itemDetail",flat))))));
  check(out,"mall HTTP homepage unwraps itemDetail lists",MallCatalog.parseHttp(home.toString()).get(0).id==301);
  JSONObject page=new JSONObject().put("retCode",0).put("response",new JSONObject().put("itemList",new JSONArray().put(new JSONObject().put("item",flat))));
  check(out,"mall HTTP pagination unwraps item lists",MallCatalog.parseHttp(page.toString()).get(0).id==301);
  check(out,"mall HTTP rejects failed response codes",MallCatalog.parseHttp(new JSONObject(search.toString()).put("retCode",1).toString()).isEmpty() && MallCatalog.parseHttp(new JSONObject(home.toString()).put("code",5).toString()).isEmpty());
  check(out,"mall HTTP rejects error envelopes even with success return code",MallCatalog.parseHttp(new JSONObject(search.toString()).put("error",new JSONObject().put("code",7)).toString()).isEmpty());
  check(out,"mall HTTP rejects missing explicit success envelope",MallCatalog.parseHttp(new JSONObject().put("response",search.getJSONObject("response")).toString()).isEmpty());
  check(out,"mall HTTP requires transport success and 2xx status",MallCatalog.httpSucceeded(1,200) && !MallCatalog.httpSucceeded(0,200) && !MallCatalog.httpSucceeded(1,500) && !MallCatalog.httpSucceeded(true,200) && !MallCatalog.httpSucceeded(1,200.5));
  String base="https://zb.vip.qq.com",path="/trpc-proxy/qqva/qc_search_server/personalize_search/gxhCommSearchV2";
  check(out,"mall HTTP accepts observed catalog and search URLs",MallCatalog.acceptsUrl(base+path+"?query=test") && MallCatalog.acceptsUrl(base+"/v2/kuikly-api/new-mall-first-screen") && MallCatalog.acceptsUrl(base+"/trpc-proxy/qqva/qc_submall_server/qc_submall_server/GetMoreListV2"));
  check(out,"mall HTTP excludes unrelated hosts and search history",!MallCatalog.acceptsUrl("https://example.invalid"+path) && !MallCatalog.acceptsUrl("https://zb.vip.qq.com.example.invalid"+path) && !MallCatalog.acceptsUrl(base+"/trpc-proxy/qqva/qc_search_server/personalize_search/readMySearchHistoryAndHot"));
  check(out,"mall HTTP excludes spoofed credentials and invalid schemes",!MallCatalog.acceptsUrl("http://zb.vip.qq.com"+path) && !MallCatalog.acceptsUrl("https://user@zb.vip.qq.com"+path) && !MallCatalog.acceptsUrl(base+":444"+path) && !MallCatalog.acceptsUrl(base+path+"#fragment"));
  boolean oversized=false;try{MallCatalog.parseHttp(new String(new char[MallCatalog.MAX_CHARS+1]));}catch(JSONException expected){oversized=true;}
  check(out,"mall HTTP rejects oversized data before parsing",oversized);
  boolean malformed=false;try{MallCatalog.parseHttp("{truncated");}catch(JSONException expected){malformed=true;}
  check(out,"malformed mall JSON cannot produce a partial harvest",malformed);
  JSONObject document=JsonCodec.defaults().put("collect",true);JSONObject custom=JsonCodec.encode(new BubbleSpec(17,17,77L,101,8,null,0)).put("name","我的名字").put("selected",true);
  document.getJSONArray("bubbles").put(custom);MallCatalog.Merge merge=MallCatalog.merge(document,items);
  check(out,"mall harvest deduplicates existing chat variants",merge.added==1 && document.getJSONArray("bubbles").length()==2);
  check(out,"mall harvest preserves custom name selected flag and original style",custom.getString("name").equals("我的名字") && custom.getBoolean("selected") && custom.getInt("subBubbleId")==8 && custom.getLong("attrId")==77);
  check(out,"mall harvest saves official names for immediate name search",LibraryRows.officialName(custom).equals("商城花花") && LibraryRows.matches(custom,new io.github.ling.randombubble.core.LibrarySearch("商城花花"),false));
  JSONObject added=document.getJSONArray("bubbles").getJSONObject(1);
  check(out,"mall harvest does not select new styles or enable rotation",!added.getBoolean("selected") && !document.getBoolean("enabled") && !document.optBoolean("automatic") && !document.optBoolean("perMessage") && document.getBoolean("collect"));
  String before=document.toString();MallCatalog.Merge same=MallCatalog.merge(document,items);
  check(out,"revisiting mall results is an unchanged merge",!same.changed() && before.equals(document.toString()));
  MallCatalog.merge(document,Arrays.asList(new MallCatalog.Item(101,"")));
  check(out,"missing mall name never erases an existing name",LibraryRows.officialName(custom).equals("商城花花"));
  MallCatalog.merge(document,Arrays.asList(new MallCatalog.Item(999,"商城花花")));
  check(out,"same mall name with another ID is a separate style",document.getJSONArray("bubbles").length()==3);
 }
}
