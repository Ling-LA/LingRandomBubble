package io.github.ling.randombubble.hook;

import de.robv.android.xposed.*;
import io.github.ling.randombubble.core.CollectionSession;
import io.github.ling.randombubble.store.MallCatalog;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Observes only normal mall responses. Never requests, sets, purchases or changes a QQ result. */
final class MallCollector {
    private MallCollector() {}
    static void watchHttp(XC_MethodHook.MethodHookParam p,ClassLoader loader,HostRuntime runtime,String owner)throws Throwable {
        if(!runtime.versionSupported || p.args.length!=3 || !"httpRequest".equals(p.args[0])
                || !(p.args[1] instanceof String) || p.args[2]==null)return;
        String request=(String)p.args[1];if(request.length()>32768)return;
        if(!MallCatalog.acceptsUrl(new JSONObject(request).optString("url")))return;
        CollectionSession.Ticket ticket=runtime.bridge.collectionTicket(owner);if(ticket==null)return;
        Object original=p.args[2];Class<?> function=Class.forName("kotlin.jvm.functions.Function1",false,loader);
        AtomicBoolean observed=new AtomicBoolean();
        p.args[2]=Proxy.newProxyInstance(loader,new Class<?>[]{function},(proxy,method,args)->{
            try {
                if(method.getName().equals("invoke") && args!=null && args.length==1 && args[0] instanceof java.util.Map && observed.compareAndSet(false,true)){
                    java.util.Map<?,?> reply=(java.util.Map<?,?>)args[0];Object data=reply.get("data");
                    if(MallCatalog.httpSucceeded(reply.get("success"),reply.get("statusCode"))
                            && data instanceof String && ((String)data).length()<=MallCatalog.MAX_CHARS){
                        final String json=(String)data;runtime.bridge.mallResponse(ticket,()->MallCatalog.parseHttp(json));
                    }
                }
            }catch(Throwable e){runtime.log("商城收录观察暂缓 "+e.getClass().getSimpleName());}
            try{return method.invoke(original,args);}catch(InvocationTargetException e){throw e.getCause();}
        });
    }
    static void watch(XC_MethodHook.MethodHookParam p,ClassLoader loader,HostRuntime runtime,String owner) throws Throwable {
        if(!runtime.versionSupported || p.args.length!=3 || p.args[2]==null)return;
        if("sendWebSSORequest".equals(p.args[0]) && p.args[1] instanceof String){
            String request=(String)p.args[1];if(request.length()>32768)return;
            JSONObject j=new JSONObject(request);
            if(!"MQUpdateSvc_com_qq_vip.web.OidbSvcTrpcJsapiTcp.0x9d23_0".equals(j.optString("cmd")))return;
            CollectionSession.Ticket token=runtime.bridge.collectionTicket(owner);if(token==null)return;
            Object original=p.args[2];Class<?> function=Class.forName("kotlin.jvm.functions.Function1",false,loader);
            AtomicBoolean observed=new AtomicBoolean();
            p.args[2]=Proxy.newProxyInstance(loader,new Class<?>[]{function},(proxy,method,args)->{
                try {
                    if(method.getName().equals("invoke") && args!=null && args.length==1 && args[0] instanceof java.util.Map && observed.compareAndSet(false,true)){
                        java.util.Map<?,?> reply=(java.util.Map<?,?>)args[0];Object code=reply.get("errCode"),data=reply.get("data");
                        if(code instanceof Number && ((Number)code).longValue()==0 && data instanceof String && ((String)data).length()<=MallCatalog.MAX_CHARS){
                            final String json=(String)data;runtime.bridge.mallResponse(token,()->MallCatalog.parse(new JSONObject(json)));
                        }
                    }
                }catch(Throwable e){runtime.log("商城收录观察暂缓 "+e.getClass().getSimpleName());}
                try{return method.invoke(original,args);}catch(InvocationTargetException e){throw e.getCause();}
            });return;
        }
        if(!"sendPbRequest".equals(p.args[0]) || !(p.args[1] instanceof Object[]))return;
        Object[] fields=(Object[])p.args[1];
        if(fields.length<2 || !(fields[0] instanceof String) || !(fields[1] instanceof byte[]))return;
        String command=(String)fields[0];
        if(!command.equals(OfficialBubbleApi.DETAIL) && !command.equals("MQUpdateSvc_com_qq_vip_zb.web.OidbSvcTrpcJsapiTcp.0x97a2_0"))return;
        CollectionSession.Ticket ticket=runtime.bridge.collectionTicket(owner);if(ticket==null)return;
        Object original=p.args[2];Class<?> function=Class.forName("kotlin.jvm.functions.Function1",false,loader);
        AtomicBoolean observed=new AtomicBoolean();
        p.args[2]=Proxy.newProxyInstance(loader,new Class<?>[]{function},(proxy,method,args)->{
            try {
                if(method.getName().equals("invoke") && args!=null && args.length==1 && args[0] instanceof Object[] && observed.compareAndSet(false,true)){
                    Object[] reply=(Object[])args[0];
                    if(reply.length==3 && reply[0] instanceof Number && ((Number)reply[0]).longValue()==0 && reply[2] instanceof byte[]){
                        byte[] raw=(byte[])reply[2];
                        if(raw.length>0 && raw.length<=MallCatalog.MAX_CHARS){
                            final byte[] bytes=raw.clone();
                            runtime.bridge.mallResponse(ticket,()->{
                                try {
                                    Object body=Class.forName("com.tencent.mobileqq.WebSsoBody$WebSsoResponseBody",false,loader).getConstructor().newInstance();
                                    XposedHelpers.callMethod(body,"mergeFrom",bytes);
                                    if(!Integer.valueOf(0).equals(XposedHelpers.callMethod(XposedHelpers.getObjectField(body,"ret"),"get")))throw new JSONException("mall rejected");
                                    String data=(String)XposedHelpers.callMethod(XposedHelpers.getObjectField(body,"data"),"get");
                                    if(data.length()>MallCatalog.MAX_CHARS)throw new JSONException("mall size");
                                    return MallCatalog.parse(new JSONObject(data));
                                }catch(Exception e){throw e;}catch(Throwable e){throw new IllegalStateException("mall decoding",e);}
                            });
                        }
                    }
                }
            }catch(Throwable e){runtime.log("商城收录观察暂缓 "+e.getClass().getSimpleName());}
            try{return method.invoke(original,args);}catch(InvocationTargetException e){throw e.getCause();}
        });
    }
}
