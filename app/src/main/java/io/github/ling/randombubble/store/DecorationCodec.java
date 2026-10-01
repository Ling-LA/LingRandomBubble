package io.github.ling.randombubble.store;

import io.github.ling.randombubble.core.DecorationSettings;
import org.json.*;
import java.util.LinkedHashSet;

/** Shared validation for standalone and embedded account controls. */
public final class DecorationCodec {
    private DecorationCodec() {}
    public static JSONObject defaults(String owner) throws JSONException {
        return new JSONObject().put("account",owner).put("ids",new JSONArray()).put("seconds",1800)
            .put("automatic",false).put("perMessage",false).put("generation","off");
    }
    public static JSONObject create(String owner,String raw,int seconds,boolean automatic,boolean manual) throws JSONException {
        return create(owner,raw,seconds,automatic,manual,false);
    }
    public static JSONObject create(String owner,String raw,int seconds,boolean automatic,boolean manual,boolean perMessage) throws JSONException {
        owner(owner); int[] ids=DecorationSettings.ids(raw); DecorationSettings.interval(seconds);
        DecorationSettings.rotationPool(ids.length,automatic,perMessage);
        JSONArray pool=new JSONArray();for(int id:ids)pool.put(id);
        JSONObject j=defaults(owner).put("ids",pool).put("seconds",seconds).put("automatic",automatic).put("perMessage",perMessage)
            .put("generation",java.util.UUID.randomUUID().toString());
        if(manual && !perMessage)j.put("command",java.util.UUID.randomUUID().toString()).put("expires",System.currentTimeMillis()+30000);
        return j;
    }
    public static JSONObject validate(String raw,String owner) throws JSONException {
        owner(owner); if(raw==null || raw.length()>262144)throw new IllegalArgumentException("装扮配置无效");
        JSONObject j=new JSONObject(raw);
        if(!(j.get("account") instanceof String) || !owner.equals(j.get("account")))throw new IllegalArgumentException("账号已变化，请重新打开设置");
        if(!(j.get("automatic") instanceof Boolean) || !(j.get("seconds") instanceof Integer))throw new IllegalArgumentException("装扮配置类型无效");
        if(!j.has("perMessage"))j.put("perMessage",false);
        else if(!(j.get("perMessage") instanceof Boolean))throw new IllegalArgumentException("每消息轮换配置类型无效");
        DecorationSettings.interval(j.getInt("seconds"));
        if(!(j.get("generation") instanceof String))throw new IllegalArgumentException("配置代次类型无效");
        String generation=j.getString("generation");if(generation.isEmpty() || generation.length()>64)throw new IllegalArgumentException("配置代次无效");
        LinkedHashSet<Integer> ids=new LinkedHashSet<>();JSONArray pool=j.getJSONArray("ids");
        for(int i=0;i<pool.length();i++) {Object value=pool.get(i);if(!(value instanceof Integer) || (Integer)value<=0 || (Integer)value>999999999)throw new IllegalArgumentException("气泡编号无效");ids.add((Integer)value);}
        DecorationSettings.rotationPool(ids.size(),j.getBoolean("automatic"),j.getBoolean("perMessage"));
        if(j.getBoolean("perMessage") && (j.has("command") || j.has("expires")))throw new IllegalArgumentException("每消息轮换不能携带手动请求");
        if(j.has("command") && (!(j.get("command") instanceof String) || ids.isEmpty() || j.getString("command").isEmpty() || j.getString("command").length()>64 || !(j.get("expires") instanceof Long)))throw new IllegalArgumentException("手动请求无效");
        return j;
    }
    public static JSONObject stopped(JSONObject current,String owner) throws JSONException {
        JSONObject j=validate(current.toString(),owner);j.put("automatic",false).put("perMessage",false).put("generation",java.util.UUID.randomUUID().toString());j.remove("command");j.remove("expires");return j;
    }
    public static void owner(String owner) {
        if(owner==null || !owner.matches("[1-9]\\d{4,12}"))throw new IllegalStateException("请等待 QQ 识别当前账号");
    }
}
