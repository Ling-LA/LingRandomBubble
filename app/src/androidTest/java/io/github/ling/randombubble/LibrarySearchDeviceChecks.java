package io.github.ling.randombubble;

import io.github.ling.randombubble.core.BubbleSpec;
import io.github.ling.randombubble.core.LibrarySearch;
import io.github.ling.randombubble.store.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;
import org.json.*;

final class LibrarySearchDeviceChecks {
    static void check(Consumer<String> passed,String name,boolean value){if(!value)throw new AssertionError(name);passed.accept(name);}
    static JSONObject row(int id,String name,boolean selected) throws Exception {
        return JsonCodec.encode(new BubbleSpec(17,17,0L,id,0,null,0)).put("name",name).put("selected",selected);
    }
    static void run(Consumer<String> passed) throws Exception {
        JSONObject document=JsonCodec.defaults();JSONArray rows=document.getJSONArray("bubbles");
        ArrayList<JSONObject> matches=new ArrayList<>();
        for(int i=0;i<63;i++){
            JSONObject r=row(1000+i,i<43?"小猫"+i:"小狗"+i,i==62);rows.put(r);
            if(LibraryRows.matches(r,new LibrarySearch("猫"),false))matches.add(r);
        }
        check(passed,"Chinese names filter 43 results across three pages",matches.size()==43);
        LibraryRows.select(document,LibraryRows.keys(matches),true);
        check(passed,"select results includes every page",JsonCodec.config(document.toString()).selected.size()==44);
        check(passed,"select results preserves outside selected row",rows.getJSONObject(62).getBoolean("selected"));
        check(passed,"select results leaves outside unselected row unchanged",!rows.getJSONObject(61).getBoolean("selected"));
        LibraryRows.select(document,LibraryRows.keys(matches),false);
        check(passed,"clear results only clears matched rows",JsonCodec.config(document.toString()).selected.size()==1 && rows.getJSONObject(62).getBoolean("selected"));
        String before=document.toString();LibraryRows.select(document,Collections.emptySet(),true);
        check(passed,"zero results select is a no-op",before.equals(document.toString()));
        check(passed,"selected-only filter excludes unselected matches",!LibraryRows.matches(rows.getJSONObject(0),new LibrarySearch("猫"),true));
        check(passed,"selected-only filter includes selected matches",LibraryRows.matches(rows.getJSONObject(62),new LibrarySearch("猫 狗"),true));
        JSONObject legacy=row(2173887,new BubbleSpec(17,17,0L,2173887,0,null,0).label(),true);
        String key=JsonCodec.decode(legacy).key();LibraryRows.mergeName(legacy,"肥嘟嘟小初");
        check(passed,"legacy numeric label displays real mall name",LibraryRows.displayName(legacy).equals("肥嘟嘟小初"));
        check(passed,"legacy row searchable by mall name",LibraryRows.matches(legacy,new LibrarySearch("小初"),false));
        check(passed,"name enrichment preserves style and selection",key.equals(JsonCodec.decode(legacy).key()) && legacy.getBoolean("selected"));
        legacy.put("name","我的最爱");LibraryRows.mergeName(legacy,"肥嘟嘟小初新版");
        check(passed,"enrichment preserves custom rename",LibraryRows.displayName(legacy).equals("我的最爱"));
        check(passed,"custom name stays searchable",LibraryRows.matches(legacy,new LibrarySearch("最爱"),false));
        check(passed,"official name remains searchable after rename",LibraryRows.matches(legacy,new LibrarySearch("小初"),false));
        JSONObject copy=LibraryRows.copyNames(legacy,JsonCodec.encode(JsonCodec.decode(legacy)));
        check(passed,"export import carries both names",copy.getString("name").equals("我的最爱") && LibraryRows.officialName(copy).equals("肥嘟嘟小初新版"));
        check(passed,"duplicate name update is a no-op",!LibraryRows.mergeName(copy,"肥嘟嘟小初新版"));
        check(passed,"empty name cannot erase cached name",!LibraryRows.mergeName(copy," ") && !LibraryRows.officialName(copy).isEmpty());
        JSONObject detail=new JSONObject().put("baseInfo",new JSONObject().put("appId",2).put("itemId",2173887).put("name","肥嘟嘟小初"));
        check(passed,"official detail accepts exact bubble identity",LibraryRows.detailName(detail,2173887).equals("肥嘟嘟小初"));
        check(passed,"official detail rejects mismatched identity",LibraryRows.detailName(detail,2153379).isEmpty());
        detail.getJSONObject("baseInfo").put("appId",3);
        check(passed,"official detail rejects another product type",LibraryRows.detailName(detail,2173887).isEmpty());
        detail.getJSONObject("baseInfo").put("appId",2).put("name",12);
        check(passed,"official detail rejects non-string names",LibraryRows.detailName(detail,2173887).isEmpty());
        check(passed,"malformed empty and null detail safe",LibraryRows.detailName(null,1).isEmpty() && LibraryRows.detailName(new JSONObject(),1).isEmpty());
        JSONObject first=row(10,"同名气泡",true);
        JSONObject attrVariant=JsonCodec.encode(new BubbleSpec(17,17,99L,10,0,null,0)).put("name","气泡 10 / 子气泡 0").put("selected",false);
        JSONObject subVariant=JsonCodec.encode(new BubbleSpec(17,17,0L,10,3,null,0)).put("name","另一别名").put("selected",false);
        JSONObject another=row(11,"同名气泡",true);
        ArrayList<JSONObject> originals=new ArrayList<>(java.util.Arrays.asList(first,attrVariant,subVariant,another));
        String originalsBefore=originals.toString();ArrayList<LibraryGroup> groups=LibraryGroup.of(originals);
        check(passed,"grouping collapses capture attributes and sub-bubbles of the same main ID",groups.size()==2 && groups.get(0).rows.size()==3);
        check(passed,"same name with different main IDs remains distinct",groups.get(0).bubbleId==10 && groups.get(1).bubbleId==11);
        check(passed,"display grouping never deletes or mutates records",originalsBefore.equals(originals.toString()));
        check(passed,"partial selection is explicit",groups.get(0).partial() && !groups.get(0).allSelected() && groups.get(0).selected()==1);
        check(passed,"custom name is retained as group title",groups.get(0).name().equals("同名气泡"));
        check(passed,"group keys retain every original template identity",groups.get(0).keys().size()==3);
        JSONObject grouped=JsonCodec.defaults().put("bubbles",new JSONArray(originals));
        LibraryRows.select(grouped,groups.get(0).keys(),true);
        check(passed,"group selection covers hidden variants and preserves another group",groups.get(0).allSelected() && another.getBoolean("selected"));
        ArrayList<JSONObject> subset=new ArrayList<>();for(JSONObject r:originals)if(LibraryRows.matches(r,new LibrarySearch("另一别名"),false))subset.add(r);
        LibraryRows.select(grouped,LibraryGroup.of(subset).get(0).keys(),false);
        check(passed,"filtered group action preserves nonmatching variants of same bubble",first.getBoolean("selected") && attrVariant.getBoolean("selected") && !subVariant.getBoolean("selected"));
        JSONObject zero=JsonCodec.encode(new BubbleSpec(17,17,0L,0,7,null,0));
        JSONObject missing=JsonCodec.encode(new BubbleSpec(17,17,0L,null,8,null,0));
        check(passed,"absent or zero main IDs never collapse unrelated records",LibraryGroup.of(java.util.Arrays.asList(zero,missing)).size()==2);
        check(passed,"empty grouped results remain empty",LibraryGroup.of(Collections.emptyList()).isEmpty());
    }
}
