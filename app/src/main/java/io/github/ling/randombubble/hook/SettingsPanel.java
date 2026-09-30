package io.github.ling.randombubble.hook;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import io.github.ling.randombubble.core.BubbleSpec;
import io.github.ling.randombubble.store.JsonCodec;
import org.json.JSONArray;
import org.json.JSONObject;

/** In-QQ settings. Rows carry their own on/off pill so the host theme cannot hide the control. */
final class SettingsPanel {
    private static final int INK=0xFF10181C;
    private static final int CARD=0xFF1B282E;
    private static final int LINE=0xFF2C3E46;
    private static final int PAPER=0xFFE7F1F3;
    private static final int MUTED=0xFF8EA3AA;
    private static final int TEAL=0xFF2BB8A8;
    private static final int TEAL_DIM=0xFF163E3C;
    private SettingsPanel() {}
    static void show(Activity activity,HostRuntime runtime) {
        try { build(activity,runtime); }
        catch(Throwable e) { runtime.log("设置面板失败 "+e.getClass().getSimpleName()); }
    }
    private static void build(Activity activity,HostRuntime runtime) throws Exception {
        HostBridge bridge=runtime.bridge;
        JSONObject document=bridge.document();
        LinearLayout page=column(activity);
        page.setBackground(round(INK,dp(activity,22)));
        page.setPadding(dp(activity,18),dp(activity,16),dp(activity,18),dp(activity,12));
        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(page);
        Dialog dialog=new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(scroll);
        Window window=dialog.getWindow();
        if(window!=null) {
            window.setBackgroundDrawable(round(0x00000000,0));
            window.setLayout(-1,-1);
        }
        Runnable reopen=() -> { dialog.dismiss(); show(activity,runtime); };

        TextView title=label(activity,"Ling 随机气泡",22,PAPER,true);
        page.addView(title);
        page.addView(label(activity,"0.1.19  ·  "+bridge.libraryMask()+"  ·  装扮 "+runtime.equippedLabel(),12,MUTED,false));

        boolean fixed=document.optBoolean("fixed",false);
        boolean enabled=document.optBoolean("enabled",false);
        String mode=enabled?(fixed?"固定模式：每条都用列表第一项":"随机模式：每条换一种，尽量不连着重复"):"发送气泡已关闭";
        TextView banner=label(activity,mode,14,fixed?0xFFFFC48A:PAPER,true);
        banner.setBackground(round(fixed?0xFF3A2A18:TEAL_DIM,dp(activity,12)));
        banner.setPadding(dp(activity,14),dp(activity,12),dp(activity,14),dp(activity,12));
        page.addView(banner,gap(activity,12));

        heading(page,"发送");
        toggle(page,bridge,document,"enabled","启用发送气泡","关闭后发出去的还是账号装扮",reopen);
        toggle(page,bridge,document,"fixed","固定模式","打开就一直用第一种。要随机请保持关闭",reopen);
        toggle(page,bridge,document,"avoidRepeat","避免连续重复","库里至少两种时才换得开",reopen);
        toggle(page,bridge,document,"groups","用于群聊","群里点发送时换气泡",reopen);
        toggle(page,bridge,document,"privateChats","用于好友私聊","好友聊天里点发送时换气泡",reopen);
        toggle(page,bridge,document,"collect","自动收录","看到带气泡的消息就入库，不用长按收藏",reopen);

        heading(page,"气泡库");
        JSONArray bubbles=document.optJSONArray("bubbles");
        if(bubbles==null) bubbles=new JSONArray();
        page.addView(label(activity,bubbles.length()+" 种。点一下参加发送，长按删除。没有数量上限。",13,MUTED,false));
        LinearLayout actions=new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(pill(activity,"全选",true,() -> apply(activity,bridge,d -> selectAll(d,true),reopen)),weight());
        actions.addView(pill(activity,"全不选",false,() -> apply(activity,bridge,d -> selectAll(d,false),reopen)),weight());
        actions.addView(pill(activity,"清空",false,() -> new AlertDialog.Builder(activity).setTitle("清空气泡库？")
                .setMessage("删掉这个账号收录的全部气泡，并关闭发送。").setNegativeButton("取消",null)
                .setPositiveButton("清空",(d,w) -> apply(activity,bridge,doc -> {
                    doc.put("bubbles",new JSONArray()); doc.put("enabled",false);
                },reopen)).show()),weight());
        page.addView(actions,gap(activity,8));
        if(bubbles.length()==0) page.addView(label(activity,"还没有气泡。打开任意聊天滑一滑，带气泡的消息会自动进来；也可以长按后点「收藏气泡」。",14,PAPER,false));
        for(int i=0;i<bubbles.length();i++) {
            JSONObject row=bubbles.getJSONObject(i);
            BubbleSpec spec;
            try { spec=JsonCodec.decode(row); } catch(Exception e) { continue; }
            page.addView(bubbleRow(activity,bridge,runtime,spec,row.optString("name",spec.label()),row.optBoolean("selected",false),reopen),gap(activity,8));
        }

        heading(page,"诊断");
        page.addView(pill(activity,"查看并复制运行状态",false,() -> {
            String report=runtime.report();
            TextView t=label(activity,report,13,0xFF10181C,false);
            t.setTextIsSelectable(true);
            t.setPadding(dp(activity,18),dp(activity,12),dp(activity,18),dp(activity,12));
            ScrollView s=new ScrollView(activity); s.addView(t);
            new AlertDialog.Builder(activity).setTitle("运行状态").setView(s).setNegativeButton("关闭",null)
                    .setPositiveButton("复制",(d,w) -> {
                        ClipboardManager c=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if(c!=null) c.setPrimaryClip(ClipData.newPlainText("LingBubble",report));
                        Toast.makeText(activity,"已复制，不含聊天正文",Toast.LENGTH_SHORT).show();
                    }).show();
        }),gap(activity,8));
        page.addView(pill(activity,"关闭",true,dialog::dismiss),gap(activity,14));
        dialog.show();
    }
    private static View bubbleRow(Activity activity,HostBridge bridge,HostRuntime runtime,BubbleSpec spec,String name,boolean selected,Runnable reopen) {
        LinearLayout row=new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(round(selected?TEAL_DIM:CARD,dp(activity,14)));
        row.setPadding(dp(activity,14),dp(activity,12),dp(activity,14),dp(activity,12));
        LinearLayout words=column(activity);
        String title=name.equals(spec.label())?name:name;
        words.addView(label(activity,title,15,PAPER,true));
        String extra=spec.label()+(runtime.isEquipped(spec)?"  ·  当前装扮":"");
        words.addView(label(activity,extra,12,MUTED,false));
        row.addView(words,weight());
        TextView mark=label(activity,selected?"参与":"不用",13,selected?TEAL:MUTED,true);
        row.addView(mark);
        final String key=spec.key();
        row.setOnClickListener(v -> apply(activity,bridge,d -> setSelected(d,key,!selected),reopen));
        row.setOnLongClickListener(v -> {
            new AlertDialog.Builder(activity).setTitle(name).setMessage("从库里删除这个气泡？").setNegativeButton("取消",null)
                    .setPositiveButton("删除",(d,w) -> apply(activity,bridge,doc -> {
                        JSONArray a=doc.getJSONArray("bubbles"), kept=new JSONArray();
                        for(int k=0;k<a.length();k++) if(!JsonCodec.decode(a.getJSONObject(k)).key().equals(key)) kept.put(a.getJSONObject(k));
                        doc.put("bubbles",kept);
                    },reopen)).show();
            return true;
        });
        return row;
    }
    private static void setSelected(JSONObject document,String key,boolean value) throws Exception {
        JSONArray a=document.getJSONArray("bubbles");
        for(int k=0;k<a.length();k++) if(JsonCodec.decode(a.getJSONObject(k)).key().equals(key)) a.getJSONObject(k).put("selected",value);
    }
    private static void toggle(LinearLayout page,HostBridge bridge,JSONObject document,String key,String title,String hint,Runnable reopen) {
        Activity activity=(Activity)page.getContext();
        boolean on=document.optBoolean(key,false);
        LinearLayout row=new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(round(CARD,dp(activity,14)));
        row.setPadding(dp(activity,14),dp(activity,12),dp(activity,12),dp(activity,12));
        LinearLayout words=column(activity);
        words.addView(label(activity,title,15,PAPER,true));
        words.addView(label(activity,hint,12,MUTED,false));
        row.addView(words,weight());
        TextView pill=label(activity,on?"开":"关",14,on?0xFF06211E:MUTED,true);
        pill.setGravity(Gravity.CENTER);
        pill.setMinWidth(dp(activity,52));
        pill.setBackground(round(on?TEAL:LINE,dp(activity,16)));
        pill.setPadding(dp(activity,12),dp(activity,6),dp(activity,12),dp(activity,6));
        row.addView(pill);
        row.setOnClickListener(v -> apply(activity,bridge,d -> d.put(key,!on),reopen));
        page.addView(row,gap(activity,8));
    }
    private static void apply(Activity activity,HostBridge bridge,HostBridge.Edit edit,Runnable after) {
        String error=bridge.edit(edit);
        if(error!=null) Toast.makeText(activity,"保存失败："+error,Toast.LENGTH_LONG).show();
        else if(after!=null) after.run();
    }
    private static void selectAll(JSONObject document,boolean value) throws Exception {
        JSONArray a=document.getJSONArray("bubbles");
        for(int i=0;i<a.length();i++) a.getJSONObject(i).put("selected",value);
    }
    private static void heading(LinearLayout page,String text) {
        TextView t=label((Activity)page.getContext(),text,13,TEAL,true);
        t.setPadding(dp(page.getContext(),2),dp(page.getContext(),16),0,dp(page.getContext(),6));
        page.addView(t);
    }
    private static TextView pill(Activity activity,String text,boolean filled,Runnable action) {
        TextView t=label(activity,text,14,filled?0xFF06211E:PAPER,true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(filled?TEAL:CARD,dp(activity,12)));
        t.setPadding(dp(activity,8),dp(activity,12),dp(activity,8),dp(activity,12));
        t.setOnClickListener(v -> action.run());
        return t;
    }
    private static TextView label(Activity activity,String text,int sp,int color,boolean bold) {
        TextView t=new TextView(activity);
        t.setText(text); t.setTextSize(sp); t.setTextColor(color);
        if(bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    private static LinearLayout column(Context context) {
        LinearLayout layout=new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private static GradientDrawable round(int color,int radius) {
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(radius);
        return shape;
    }
    private static LinearLayout.LayoutParams gap(Context context,int top) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(context,top);
        return lp;
    }
    private static LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
        lp.leftMargin=6; lp.rightMargin=6;
        return lp;
    }
    private static int dp(Context c,int value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
}
