package io.github.ling.randombubble.hook;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

/** Decoration settings stay in the current QQ account's local configuration. */
final class EmbeddedDecorationPanel {
    private EmbeddedDecorationPanel() {}
    static void show(Activity activity,HostRuntime runtime,Runnable after) {
        if(activity.isFinishing() || activity.isDestroyed()) return;
        try { build(activity,runtime,after); }
        catch(Throwable e) { runtime.log("内嵌装扮配置失败 "+e.getClass().getSimpleName()); SettingsPanel.error(activity,"无法打开装扮配置"); after.run(); }
    }
    private static void build(Activity activity,HostRuntime runtime,Runnable after) throws Exception {
        HostBridge bridge=runtime.bridge; String owner=bridge.accountIdentity(); JSONObject settings=bridge.decorationSettings();
        LinearLayout form=SettingsPanel.page(activity); ScrollView scroll=new ScrollView(activity); scroll.setFillViewport(true); scroll.addView(form);
        Dialog dialog=SettingsPanel.dialog(activity,scroll);
        dialog.setOnDismissListener(d -> { if(!activity.isFinishing() && !activity.isDestroyed()) after.run(); });
        form.addView(SettingsPanel.label(activity,"账号装扮配置",22,SettingsPanel.PAPER,true));
        form.addView(SettingsPanel.label(activity,bridge.libraryMask()+" · 影响整个账号的后续消息",12,SettingsPanel.MUTED,false));
        SettingsPanel.heading(form,"有权使用的气泡编号");
        EditText ids=input(activity,"气泡编号，逗号分隔",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        ids.setMinLines(2); ids.setMaxLines(3); JSONArray existing=settings.optJSONArray("ids");
        String saved=existing==null?"":existing.join(","); ids.setText(saved.isEmpty()?bridge.selectedDecorationIds():saved); form.addView(ids);
        form.addView(SettingsPanel.pill(activity,"填入气泡库已勾选编号",false,() -> {
            if(!owner.equals(bridge.accountIdentity())) { SettingsPanel.error(activity,"账号已切换，请重新打开配置"); dialog.dismiss(); return; }
            String selected=bridge.selectedDecorationIds();
            if(selected.isEmpty()) Toast.makeText(activity,"请先在气泡库勾选有权使用的气泡",Toast.LENGTH_LONG).show();
            else ids.setText(selected);
        }),SettingsPanel.gap(activity,8));
        CheckBox perMessage=new CheckBox(activity); perMessage.setText("逐消息切换（普通文字）"); perMessage.setTextSize(15); perMessage.setTextColor(SettingsPanel.PAPER);
        perMessage.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{SettingsPanel.TEAL,SettingsPanel.MUTED}));
        perMessage.setChecked(settings.optBoolean("perMessage",false)); form.addView(perMessage,SettingsPanel.gap(activity,12));
        form.addView(SettingsPanel.label(activity,"可单独开启：计时模式选手动即可。需要至少两款气泡。点击发送后会等待商城确认新装扮，再继续这一次发送。",13,SettingsPanel.MUTED,false),SettingsPanel.gap(activity,6));
        SettingsPanel.heading(form,"切换模式");
        int seconds=settings.optInt("seconds",1800);
        int[] mode={settings.optBoolean("automatic",false)?(seconds<1800?2:1):0};
        EditText interval=input(activity,"间隔秒数：60 至 86400",InputType.TYPE_CLASS_NUMBER); interval.setSingleLine(true); interval.setText(String.valueOf(seconds));
        LinearLayout modes=new LinearLayout(activity); form.addView(modes);
        TextView[] options=new TextView[3]; String[] titles={"手动","低频","高频"};
        Runnable update=() -> {
            for(int i=0;i<options.length;i++) { boolean on=mode[0]==i; options[i].setTextColor(on?0xFF06211E:SettingsPanel.PAPER); options[i].setBackground(SettingsPanel.round(on?SettingsPanel.TEAL:SettingsPanel.CARD,SettingsPanel.dp(activity,12))); }
        };
        for(int i=0;i<options.length;i++) {
            final int selected=i;
            options[i]=SettingsPanel.pill(activity,titles[i],false,() -> {
                mode[0]=selected; if(selected==1) interval.setText("1800"); else if(selected==2) interval.setText("60"); update.run();
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.setMargins(SettingsPanel.dp(activity,2),0,SettingsPanel.dp(activity,2),0); modes.addView(options[i],lp);
        }
        update.run(); SettingsPanel.heading(form,"间隔秒数"); form.addView(interval);
        form.addView(SettingsPanel.label(activity,"低频预设 1800 秒，高频预设 60 秒，可自行修改为 60～86400 秒。手动切换到第一个编号；自动随机轮换不同款式，至少需要两款。",13,SettingsPanel.MUTED,false),SettingsPanel.gap(activity,8));
        form.addView(SettingsPanel.label(activity,"逐消息失败保留输入；等待期间文本或会话变化会取消发送。仅支持点击发送按钮的普通文字，不支持附件、Enter 或脚本消息。",13,SettingsPanel.MUTED,false),SettingsPanel.gap(activity,8));
        form.addView(SettingsPanel.label(activity,"仅在 QQ 前台且屏幕解锁时执行。全选或收录不代表拥有使用权益。高频及逐消息会增加账号设置请求，建议优先使用低频。",13,SettingsPanel.MUTED,false),SettingsPanel.gap(activity,8));
        TextView failure=SettingsPanel.label(activity,"",13,0xFFFFB9AD,false); failure.setVisibility(View.GONE); form.addView(failure,SettingsPanel.gap(activity,8));
        form.addView(SettingsPanel.pill(activity,"保存并执行",true,() -> {
            if(!owner.equals(bridge.accountIdentity())) { SettingsPanel.error(activity,"账号已切换，请重新打开配置"); dialog.dismiss(); return; }
            final int value;
            try { value=Integer.parseInt(interval.getText().toString().trim()); }
            catch(NumberFormatException e) { failure.setText("请输入 60～86400 之间的整数秒数"); failure.setVisibility(View.VISIBLE); interval.requestFocus(); return; }
            boolean each=perMessage.isChecked();
            String error=bridge.configureDecorationForAccount(owner,ids.getText().toString(),value,mode[0]!=0,mode[0]==0 && !each,each);
            if(error!=null) { failure.setText(error); failure.setVisibility(View.VISIBLE); return; }
            String message=each?(mode[0]==0?"已启用逐消息切换；点击发送时先确认新装扮":"已保存计时和逐消息切换，计时间隔 "+value+" 秒"):
                (mode[0]==0?"已提交手动切换，请查看诊断确认服务器结果":"已保存自动轮换，间隔 "+value+" 秒；请查看诊断确认服务器结果");
            Toast.makeText(activity,message,Toast.LENGTH_LONG).show(); dialog.dismiss();
        }),SettingsPanel.gap(activity,14));
        form.addView(SettingsPanel.pill(activity,"取消",false,dialog::dismiss),SettingsPanel.gap(activity,8));
        SettingsPanel.showDialog(dialog);
    }
    private static EditText input(Activity activity,String hint,int type) {
        EditText input=new EditText(activity); input.setTextSize(15); input.setTextColor(SettingsPanel.PAPER); input.setHintTextColor(SettingsPanel.MUTED); input.setHint(hint); input.setInputType(type); return input;
    }
}
