package io.github.ling.randombubble.hook;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import io.github.ling.randombubble.BuildConfig;
import org.json.JSONArray;
import org.json.JSONObject;

/** The entire settings flow uses QQ's Activity; no installed module Activity is required. */
final class SettingsPanel {
    static final int INK=0xFF10181C, CARD=0xFF1B282E, LINE=0xFF2C3E46;
    static final int PAPER=0xFFE7F1F3, MUTED=0xFF8EA3AA, TEAL=0xFF2BB8A8, TEAL_DIM=0xFF163E3C;
    private SettingsPanel() {}
    static void show(Activity activity,HostRuntime runtime) {
        if(activity.isFinishing() || activity.isDestroyed()) return;
        try { build(activity,runtime); }
        catch(Throwable e) { runtime.log("设置面板失败 "+e.getClass().getSimpleName()); }
    }
    private static void build(Activity activity,HostRuntime runtime) throws Exception {
        HostBridge bridge=runtime.bridge;
        final String owner=bridge.accountIdentity();
        JSONObject document=bridge.document();
        LinearLayout page=page(activity);
        ScrollView scroll=new ScrollView(activity); scroll.setFillViewport(true); scroll.addView(page);
        Dialog dialog=dialog(activity,scroll);
        Runnable reopen=() -> { dialog.dismiss(); show(activity,runtime); };
        page.addView(label(activity,"Ling 随机气泡",22,PAPER,true));
        page.addView(label(activity,BuildConfig.VERSION_NAME+"  ·  "+bridge.libraryMask()+"  ·  装扮 "+runtime.equippedLabel(),12,MUTED,false));
        page.addView(label(activity,"在 QQ 内管理气泡库和账号装扮，集成加载也可使用。只切换已获得使用权益的气泡。",13,MUTED,false),gap(activity,8));
        heading(page,"账号装扮切换");
        JSONObject settings=bridge.decorationSettings();
        String state=settings.optBoolean("automatic",false)?"计时轮换开启 · 间隔 "+settings.optInt("seconds",1800)+" 秒":"计时轮换关闭";
        state+=" · 逐消息"+(settings.optBoolean("perMessage",false)?"开启":"关闭");
        page.addView(label(activity,state,14,PAPER,false));
        page.addView(label(activity,"通过商城正常接口修改整账号装扮。计时与逐消息可独立开启；普通文字、QQ 自带小表情及引用回复文字的逐消息发送会等待服务器确认，引用内 QQ 原生 @ 随引用处理。",13,MUTED,false),gap(activity,6));
        page.addView(pill(activity,"配置手动 / 低频 / 高频切换",true,() -> {
            dialog.dismiss(); EmbeddedDecorationPanel.show(activity,runtime,() -> show(activity,runtime));
        }),gap(activity,8));
        page.addView(pill(activity,"停止轮换（计时 / 逐消息）",false,() -> {
            String error=bridge.stopDecorationForAccount(owner);
            if(error!=null) error(activity,error);
            else { Toast.makeText(activity,"已停止计时和逐消息轮换；已提交的装扮请求可能仍会完成",Toast.LENGTH_LONG).show(); reopen.run(); }
        }),gap(activity,8));
        heading(page,"采集");
        toggle(page,bridge,owner,document,"collect","自动收录","仅记录样式编号，不获得装扮权益",reopen);
        heading(page,"气泡库");
        JSONArray bubbles=document.optJSONArray("bubbles");
        int count=bubbles==null?0:bubbles.length(), selected=0;
        if(bubbles!=null) for(int i=0;i<bubbles.length();i++) {
            JSONObject row=bubbles.optJSONObject(i); if(row!=null && row.optBoolean("selected")) selected++;
        }
        page.addView(label(activity,count+" 项 · 已勾选 "+selected+" 项",14,PAPER,false));
        page.addView(label(activity,"搜索、每页 20 项、全选全部、取消全选；长按改名或删除。勾选不会自动开启轮换。",13,MUTED,false),gap(activity,6));
        page.addView(pill(activity,"打开气泡库（搜索 / 全选）",true,() -> {
            dialog.dismiss(); EmbeddedBubbleLibrary.show(activity,runtime,() -> show(activity,runtime));
        }),gap(activity,8));
        heading(page,"诊断");
        page.addView(pill(activity,"查看并复制运行状态",false,() -> {
            String report=runtime.report();
            TextView t=label(activity,report,13,0xFF10181C,false);
            t.setTextIsSelectable(true); t.setPadding(dp(activity,18),dp(activity,12),dp(activity,18),dp(activity,12));
            ScrollView s=new ScrollView(activity); s.addView(t);
            alert(activity).setTitle("运行状态").setView(s).setNegativeButton("关闭",null)
                    .setPositiveButton("复制",(d,w) -> {
                        ClipboardManager c=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if(c!=null) c.setPrimaryClip(ClipData.newPlainText("LingBubble",report));
                        Toast.makeText(activity,"已复制，不含聊天正文",Toast.LENGTH_SHORT).show();
                    }).show();
        }),gap(activity,8));
        page.addView(pill(activity,"关闭",true,dialog::dismiss),gap(activity,14));
        showDialog(dialog);
    }
    private static void toggle(LinearLayout page,HostBridge bridge,String owner,JSONObject document,String key,String title,String hint,Runnable reopen) {
        Activity activity=(Activity)page.getContext();
        boolean on=document.optBoolean(key,false);
        LinearLayout row=new LinearLayout(activity); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(round(CARD,dp(activity,14))); row.setPadding(dp(activity,14),dp(activity,12),dp(activity,12),dp(activity,12));
        LinearLayout words=column(activity); words.addView(label(activity,title,15,PAPER,true)); words.addView(label(activity,hint,12,MUTED,false));
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView mark=label(activity,on?"开":"关",14,on?0xFF06211E:MUTED,true); mark.setGravity(Gravity.CENTER); mark.setMinWidth(dp(activity,52));
        mark.setBackground(round(on?TEAL:LINE,dp(activity,16))); mark.setPadding(dp(activity,12),dp(activity,6),dp(activity,12),dp(activity,6)); row.addView(mark);
        row.setOnClickListener(v -> { String failure=bridge.editForAccount(owner,d -> d.put(key,!on)); if(failure!=null) error(activity,failure); else reopen.run(); });
        page.addView(row,gap(activity,8));
    }
    static void error(Activity activity,String message) { Toast.makeText(activity,"保存失败："+message,Toast.LENGTH_LONG).show(); }
    static AlertDialog.Builder alert(Activity activity) {
        return new AlertDialog.Builder(new ContextThemeWrapper(activity,android.R.style.Theme_DeviceDefault_Light_Dialog_Alert));
    }
    static LinearLayout page(Activity activity) {
        LinearLayout p=column(activity); p.setBackground(round(INK,dp(activity,18)));
        p.setPadding(dp(activity,18),dp(activity,16),dp(activity,18),dp(activity,12)); return p;
    }
    static Dialog dialog(Activity activity,android.view.View body) {
        Dialog dialog=new Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); dialog.setContentView(body);
        Window window=dialog.getWindow();
        if(window!=null) {
            window.setBackgroundDrawable(round(0x00000000,0));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }
    static void showDialog(Dialog dialog) {
        dialog.show(); Window window=dialog.getWindow(); if(window!=null) window.setLayout(-1,-1);
    }
    static void heading(LinearLayout page,String text) {
        TextView t=label((Activity)page.getContext(),text,13,TEAL,true); t.setPadding(dp(page.getContext(),2),dp(page.getContext(),16),0,dp(page.getContext(),6)); page.addView(t);
    }
    static TextView pill(Activity activity,String text,boolean filled,Runnable action) {
        TextView t=label(activity,text,14,filled?0xFF06211E:PAPER,true); t.setGravity(Gravity.CENTER);
        t.setBackground(round(filled?TEAL:CARD,dp(activity,12))); t.setPadding(dp(activity,8),dp(activity,12),dp(activity,8),dp(activity,12));
        t.setOnClickListener(v -> action.run()); return t;
    }
    static TextView label(Activity activity,String text,int sp,int color,boolean bold) {
        TextView t=new TextView(activity); t.setText(text); t.setTextSize(sp); t.setTextColor(color); if(bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t;
    }
    static LinearLayout column(Context context) { LinearLayout layout=new LinearLayout(context); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    static GradientDrawable round(int color,int radius) { GradientDrawable shape=new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(radius); return shape; }
    static LinearLayout.LayoutParams gap(Context context,int top) { LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(context,top); return lp; }
    static int dp(Context c,int value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
}
