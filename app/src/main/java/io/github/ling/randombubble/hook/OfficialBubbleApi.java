package io.github.ling.randombubble.hook;

import android.content.Context;
import android.content.SharedPreferences;
import de.robv.android.xposed.*;
import org.json.*;
import java.lang.reflect.*;
import java.util.Map;

/** QQ 9.3.50's official mall protocol. Authentication stays inside QQ's network SDK. */
final class OfficialBubbleApi {
    static final String SET="MQUpdateSvc_com_qq_vip_zb.web.OidbSvcTrpcJsapiTcp.0x942d_0";
    static final String DETAIL="MQUpdateSvc_com_qq_vip_zb.web.OidbSvcTrpcJsapiTcp.0x9716_0";
    interface Reply { void done(JSONObject value,Throwable error); }
    private final Context context;
    private final ClassLoader loader;
    private final HostRuntime runtime;
    private SharedPreferences prefs;
    private final ThreadLocal<Boolean> internal=new ThreadLocal<>();
    private SharedPreferences prefs(){if(prefs==null)prefs=context.getSharedPreferences("LingBubble-official-mall",Context.MODE_PRIVATE);if(prefs==null)throw new IllegalStateException("storage unavailable");return prefs;}
    OfficialBubbleApi(Context c,ClassLoader l,HostRuntime r) {
        context=c;loader=l;runtime=r;
    }
    String account() throws Throwable {
        Object mobile=XposedHelpers.getStaticObjectField(Class.forName("mqq.app.MobileQQ",false,loader),"sMobileQQ");
        Object app=XposedHelpers.callMethod(mobile,"peekAppRuntime");
        return app==null?"":(String)XposedHelpers.callMethod(app,"getCurrentAccountUin");
    }
    void installLearner() throws Throwable {
        Class<?> platform=Class.forName("com.tencent.mobileqq.ntcompose.export.modules.QQKuiklyPlatformApi",false,loader);
        XposedBridge.hookAllMethods(platform,"call",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    if(Boolean.TRUE.equals(internal.get()) || p.args.length!=3 || !"sendPbRequest".equals(p.args[0]) || !(p.args[1] instanceof Object[]))return;
                    Object[] args=(Object[])p.args[1];
                    if(args.length<5 || !(SET.equals(args[0]) || DETAIL.equals(args[0])) || !(args[1] instanceof byte[]) || p.args[2]==null)return;
                    Object envelope=Class.forName("com.tencent.mobileqq.WebSsoBody$WebSsoRequestBody",false,loader).getConstructor().newInstance();
                    XposedHelpers.callMethod(envelope,"mergeFrom",args[1]);
                    String owner=account();if(owner.isEmpty())return;
                    JSONObject protocol=protocol(envelope,((Number)args[3]).intValue());
                    if(DETAIL.equals(args[0])) {
                        JSONObject detail=decodeRequest((byte[])args[1]);
                        if(detail.getInt("appId")==2 && detail.has("qua")) {
                            JSONObject filter=new JSONObject(),source=detail.getJSONObject("filter");
                            for(String key:new String[]{"needAuth","needLike","needDesigner","needUsing"})filter.put(key,source.get(key));
                            JSONObject safe=new JSONObject().put("appId",detail.get("appId")).put("itemId",detail.get("itemId"))
                                .put("filter",filter).put("qua",detail.getString("qua"));
                            prefs().edit().putString("detail."+owner,safe.toString()).commit();
                        }
                        runtime.log("商城详情参数类型：appId "+detail.opt("appId").getClass().getSimpleName()+"，itemId "+detail.opt("itemId").getClass().getSimpleName()+"，traceId存在="+(args[2] instanceof String && !((String)args[2]).isEmpty()));
                        prefs().edit().putString("protocol."+owner,protocol.toString()).commit();return;
                    }
                    JSONObject request=decodeRequest((byte[])args[1]);
                    JSONObject item=request.getJSONObject("stUniBusinessItem"),common=request.getJSONObject("commonParam");
                    int id=item.getInt("itemid");
                    if(item.getInt("appid")!=2 || id<=0 || id>999999999)return;
                    // Retain only the known official bubble fields. Never retain login cookies/keys.
                    JSONObject template=new JSONObject().put("stBubble",new JSONObject().put("deltype",request.getJSONObject("stBubble").getInt("deltype")))
                        .put("stUniBusinessItem",new JSONObject().put("appid",2).put("itemid",id));
                    JSONObject safe=new JSONObject();for(String key:new String[]{"source","iOpplat","sClientIp","sClientVer","setUpItemSign"})if(common.has(key))safe.put(key,common.get(key));
                    template.put("commonParam",safe);
                    Object callback=p.args[2];Class<?> fun=Class.forName("kotlin.jvm.functions.Function1",false,loader);
                    p.args[2]=Proxy.newProxyInstance(loader,new Class<?>[]{fun},(proxy,method,a)-> {
                        if(method.getName().equals("invoke") && a!=null && a.length==1 && a[0] instanceof Object[]) {
                            try {
                                Object[] result=(Object[])a[0];
                                if(result.length==3 && result[0] instanceof Number && ((Number)result[0]).longValue()==0 && result[2] instanceof byte[] && owner.equals(account())) {
                                    JSONObject response=decodeResponse((byte[])result[2]);
                                    if(response.has("ret") && response.getInt("ret")==0) {
                                        SharedPreferences.Editor edit=prefs().edit().putString("item."+owner+"."+id,template.toString()).putString("protocol."+owner,protocol.toString());
                                        boolean signed=hasSignature(safe);
                                        runtime.log("商城设置标记类型 "+(safe.opt("setUpItemSign")==null?"null":safe.opt("setUpItemSign").getClass().getSimpleName()));
                                        if(!signed)edit.putString("base."+owner,template.toString());
                                        if(edit.commit())runtime.log("已学习商城正常设置参数 "+id+"；逐款签名="+signed);
                                    }
                                }
                            }catch(Throwable e){runtime.log("商城参数学习失败 "+e.getClass().getSimpleName());}
                        }
                        try{return method.invoke(callback,a);}catch(InvocationTargetException e){throw e.getCause();}
                    });
                }catch(Throwable e){runtime.log("商城参数观察失败 "+e.getClass().getSimpleName());}
            }
        });
    }
    private JSONObject decodeRequest(byte[] bytes) throws Throwable {
        if(bytes.length>32768)throw new IllegalArgumentException("request size");
        Object body=Class.forName("com.tencent.mobileqq.WebSsoBody$WebSsoRequestBody",false,loader).getConstructor().newInstance();
        XposedHelpers.callMethod(body,"mergeFrom",bytes);
        return new JSONObject((String)XposedHelpers.callMethod(XposedHelpers.getObjectField(body,"data"),"get"));
    }
    private JSONObject decodeResponse(byte[] bytes) throws Throwable {
        if(bytes.length>65536)throw new IllegalArgumentException("response size");
        Object body=Class.forName("com.tencent.mobileqq.WebSsoBody$WebSsoResponseBody",false,loader).getConstructor().newInstance();
        XposedHelpers.callMethod(body,"mergeFrom",bytes);
        if(!Integer.valueOf(0).equals(XposedHelpers.callMethod(XposedHelpers.getObjectField(body,"ret"),"get")))throw new IllegalStateException("SSO rejected");
        return new JSONObject((String)XposedHelpers.callMethod(XposedHelpers.getObjectField(body,"data"),"get"));
    }
    private boolean hasSignature(JSONObject common) {
        Object value=common.opt("setUpItemSign");
        return value instanceof String && !((String)value).isEmpty() && !value.equals("0") && !value.equals("1") && !value.equals("true") && !value.equals("false");
    }
    JSONObject template(String owner,int id) throws Throwable {
        String raw=prefs().getString("item."+owner+"."+id,null);
        if(raw==null)raw=prefs().getString("base."+owner,null);
        if(raw==null)for(Map.Entry<String,?> entry:prefs().getAll().entrySet()) {
            if(entry.getKey().startsWith("item."+owner+".") && entry.getValue() instanceof String) {
                JSONObject candidate=new JSONObject((String)entry.getValue());
                raw=(String)entry.getValue();break;
            }
        }
        if(raw==null)throw new IllegalStateException("请先在商城正常设置一次气泡以初始化接口");
        JSONObject j=new JSONObject(raw),item=j.getJSONObject("stUniBusinessItem");
        // Keep the successful normal common parameters intact. Entitlement preflight and
        // the server validate every new target; never generate, strip or bypass signatures.
        item.put("itemid",id);return j;
    }
    void detail(int id,Reply cb) {
        try {
            String raw=prefs().getString("detail."+account(),null);
            if(raw==null)throw new IllegalStateException("normal detail not initialized");
            JSONObject request=new JSONObject(raw);
            Object original=request.get("itemId");request.put("itemId",original instanceof String?String.valueOf(id):id);
            request(DETAIL,request,cb);
        }catch(Throwable e){cb.done(null,e);}
    }
    boolean authorized(JSONObject j,int id) throws Throwable {
        JSONObject base=j.getJSONObject("baseInfo"),auth=j.getJSONObject("authInfo"),mall=j.getJSONObject("mallInfo");
        runtime.log("商城权益字段类型：authret "+auth.opt("authret").getClass().getSimpleName()+"，payForbidden "+mall.opt("payForbidden").getClass().getSimpleName());
        return base.getInt("appId")==2 && base.getInt("itemId")==id && auth.has("authret") && auth.getInt("authret")==0
            && mall.has("payForbidden") && !io.github.ling.randombubble.core.ServerFlags.value(mall.get("payForbidden"));
    }
    void set(JSONObject body,Reply cb){request(SET,body,cb);}
    private int number(Object object,String field) {return ((Number)XposedHelpers.callMethod(XposedHelpers.getObjectField(object,field),"get")).intValue();}
    private JSONObject protocol(Object envelope,int loginMode) throws Throwable {
        JSONObject j=new JSONObject().put("version",number(envelope,"version")).put("type",number(envelope,"type")).put("loginMode",loginMode);
        Object login=XposedHelpers.getObjectField(envelope,"login_sig");
        if(login==null)throw new IllegalStateException("login envelope missing");
        j.put("loginType",number(login,"uint32_type")).put("loginAppid",number(login,"uint32_appid"));
        Object micro=XposedHelpers.callMethod(XposedHelpers.getObjectField(login,"bytes_sig"),"get");
        byte[] actual=(byte[])XposedHelpers.callMethod(micro,"toByteArray");
        boolean sdk=actual.length==0 && j.getInt("version")==0 && j.getInt("type")==0
            && j.getInt("loginType")==0 && j.getInt("loginAppid")==0;
        java.util.Arrays.fill(actual,(byte)0);
        if(!sdk)throw new IllegalStateException("unsupported normal SDK envelope");
        j.put("provider","sdk");
        return j;
    }
    private void request(String cmd,JSONObject data,Reply cb) {
        java.util.concurrent.atomic.AtomicBoolean delivered=new java.util.concurrent.atomic.AtomicBoolean();
        Reply single=(value,error)->{
            if(!delivered.compareAndSet(false,true))return;
            try{cb.done(value,error);}catch(Throwable e){runtime.setError("商城回调处理异常："+e.getClass().getSimpleName());}
        };
        try {
            String owner=account();String raw=prefs().getString("protocol."+owner,null);
            if(raw==null)throw new IllegalStateException("normal protocol not initialized");
            JSONObject protocol=new JSONObject(raw);
            Class<?> platform=Class.forName("com.tencent.mobileqq.ntcompose.export.modules.QQKuiklyPlatformApi",false,loader);
            Object api=platform.getConstructor().newInstance();
            Object network=XposedHelpers.newInstance(Class.forName("com.tencent.mobileqq.ntcompose.export.modules.QQKuiklyPlatformApi$b",false,loader),context,Integer.valueOf(System.identityHashCode(api)));
            XposedHelpers.setObjectField(api,"i",XposedHelpers.newInstance(Class.forName("kotlin.InitializedLazyImpl",false,loader),network));
            Object body=Class.forName("com.tencent.mobileqq.WebSsoBody$WebSsoRequestBody",false,loader).getConstructor().newInstance();
            if(!"sdk".equals(protocol.getString("provider")) || protocol.getInt("version")!=0
                || protocol.getInt("type")!=0 || protocol.getInt("loginType")!=0
                || protocol.getInt("loginAppid")!=0)throw new IllegalStateException("unsupported normal SDK envelope");
            // The verified normal request contains only data. QQ supplies authentication.
            XposedHelpers.callMethod(XposedHelpers.getObjectField(body,"data"),"set",data.toString());
            byte[] encoded=(byte[])XposedHelpers.callMethod(body,"toByteArray");
            Class<?> fun=Class.forName("kotlin.jvm.functions.Function1",false,loader);
            Object callback=Proxy.newProxyInstance(loader,new Class<?>[]{fun},(proxy,method,args)-> {
                if(method.getName().equals("invoke")) {
                    try {
                        if(args==null || args.length!=1 || !(args[0] instanceof Object[]))throw new IllegalStateException("response shape");
                        Object[] result=(Object[])args[0];
                        if(result.length!=3 || !(result[0] instanceof Number) || ((Number)result[0]).longValue()!=0 || !(result[2] instanceof byte[])) {
                            if(result.length>0 && result[0] instanceof Number)runtime.log("商城请求被拒绝，服务端码 "+result[0]);
                            if(result.length>2 && result[2] instanceof byte[]) {
                                try {
                                    JSONObject rejection=decodeResponse((byte[])result[2]);
                                    String message=rejection.optString("ErrorInfo");java.util.ArrayList<String> categories=new java.util.ArrayList<>();
                                    for(String category:new String[]{"鉴权","登录","签名","权限","频率","参数","账号","authentication","signature","ticket","expired","invalid","permission","water","key","salt","limit","uin"})if(message.toLowerCase(java.util.Locale.ROOT).contains(category.toLowerCase(java.util.Locale.ROOT)))categories.add(category);
                                    runtime.log("商城拒绝类别 "+categories);
                                }catch(Throwable ignored){}
                            }
                            throw new IllegalStateException("SSO rejected");
                        }
                        if(!owner.equals(account()))throw new IllegalStateException("account changed");
                        single.done(decodeResponse((byte[])result[2]),null);
                    }catch(Throwable e){single.done(null,e);}
                    return XposedHelpers.getStaticObjectField(Class.forName("kotlin.Unit",false,loader),"INSTANCE");
                }
                if(method.getName().equals("toString"))return "LingBubble official callback";
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==args[0];return null;
            });
            Object[] fields={cmd,encoded,"",protocol.getInt("loginMode"),new JSONObject().put("timeout",15).toString()};
            internal.set(true);
            try {XposedHelpers.callMethod(api,"call","sendPbRequest",(Object)fields,callback);}finally{internal.remove();}
        }catch(Throwable e){single.done(null,e);}
    }
}
