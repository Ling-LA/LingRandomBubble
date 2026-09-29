package io.github.ling.randombubble.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import io.github.ling.randombubble.core.BubbleSpec;
import io.github.ling.randombubble.store.JsonCodec;
import io.github.ling.randombubble.store.Repository;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native, standalone settings. No widgets or long-press entries are inserted into QQ. */
public final class MainActivity extends Activity {
    private static final int EXPORT=1,IMPORT=2,EXPORT_LOG=3;
    private Repository repo;
    private LinearLayout content;
    private ScrollView scroll;
    private interface Work { void run() throws Exception; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); repo=Repository.get(this); render();
    }
    @Override protected void onResume() { super.onResume(); if(repo!=null) render(); }
    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private TextView text(String value,int size,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(25,40,58));
        t.setPadding(0,dp(6),0,dp(6)); if(bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        content.addView(t,new LinearLayout.LayoutParams(-1,-2)); return t;
    }
    private void section(String name) { TextView t=text(name,19,true); t.setPadding(0,dp(23),0,dp(8)); }
    private void button(String name,Work action) {
        Button b=new Button(this); b.setAllCaps(false); b.setText(name);
        b.setOnClickListener(v -> execute(action)); content.addView(b,new LinearLayout.LayoutParams(-1,-2));
    }
    private void execute(Work action) {
        try { action.run(); } catch(Exception e) {
            String m=e.getMessage(); Toast.makeText(this,m==null?e.getClass().getSimpleName():m,Toast.LENGTH_LONG).show();
        }
    }
    private void toggle(JSONObject j,String key,String label,String hint) {
        Switch s=new Switch(this); s.setText(label); s.setTextSize(16); s.setPadding(0,dp(12),0,dp(8));
        s.setChecked(j.optBoolean(key,false)); content.addView(s,new LinearLayout.LayoutParams(-1,-2));
        text(hint,13,false);
        s.setOnCheckedChangeListener((v,checked) -> {
            try { repo.setFlag(key,checked); }
            catch(Exception e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); render(); }
        });
    }
    private void render() {
        int y=scroll==null?0:scroll.getScrollY();
        scroll=new ScrollView(this); scroll.setFillViewport(true);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(22),dp(18),dp(22),dp(30));
        scroll.addView(content); setContentView(scroll);
        // API 35 edge-to-edge: keep controls clear of system bars without AndroidX.
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets;
        });
        scroll.requestApplyInsets();
        text("Ling 随机气泡",28,true);
        text("0.1.12 · 实验版\n目标：QQ 9.3.50 / 与 QFun 1.3.4 并行\n当前账号："+repo.accountLabel(),14,false);
        text("在 QQ 里长按别人的消息，点「收藏气泡」。之后自己点发送，会把收藏的气泡参数写进这条新消息。请用另一台 QQ 确认对方能看到。",14,false);
        JSONObject j=repo.snapshot();
        section("开关");
        toggle(j,"enabled","启用发送气泡","收藏后会自动打开。只处理点发送按钮的新普通文字；不处理键盘回车、自动回复、复读或转发。");
        toggle(j,"collect","浏览时也采集","可选。打开后滚动到有气泡的消息会入库，但默认不参与发送。长按菜单的「收藏气泡」会直接参与发送。");
        toggle(j,"fixed","固定模式","开启：使用列表中第一个勾选项。关闭：从所有勾选项随机选择。");
        toggle(j,"avoidRepeat","避免连续重复","随机模式下至少勾选两种不同气泡才有意义；只影响本次进程中的正常新消息。");
        toggle(j,"groups","用于群聊","仍需正常点击发送；QFun 的 +1 原样放行。");
        toggle(j,"privateChats","用于好友私聊","陌生人临时会话、频道等不在首版范围。");
        section("气泡库");
        text("勾选参与随机的气泡。关闭固定模式时，每条新消息从勾选项里随机，并尽量不连续重复。长按库内条目可改名或删除。不修改 QFun 的 +1。",14,false);
        button("刷新气泡库与状态",this::render);
        try {
            JSONArray a=j.getJSONArray("bubbles");
            text(a.length()+" / "+JsonCodec.MAX_LIBRARY+" 个气泡",13,false);
            if(a.length()==0) text("暂无气泡。到 QQ 长按一条有气泡的消息，点「收藏气泡」。",15,false);
            for(int i=0;i<a.length();i++) addBubbleRow(a.getJSONObject(i));
        } catch(Exception e) { text("读取气泡库失败："+e.getClass().getSimpleName(),14,false); }
        section("备份与诊断");
        text("运行日志保存在手机的 Download/LingRandomBubble-log.txt。打开 QQ 时不再弹出提示。也可以点下面的按钮导出。",14,false);
        button("导出气泡库 JSON",() -> {
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json").putExtra(Intent.EXTRA_TITLE,"LingRandomBubble-library.json");
            startActivityForResult(intent,EXPORT);
        });
        button("导入气泡库 JSON",() -> {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
            startActivityForResult(intent,IMPORT);
        });
        button("查看 / 复制诊断",this::showDiagnostics);
        button("导出运行日志",() -> {
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/plain").putExtra(Intent.EXTRA_TITLE,"LingRandomBubble-log.txt");
            startActivityForResult(intent,EXPORT_LOG);
        });
        button("清空气泡库并关闭功能",() -> new AlertDialog.Builder(this).setTitle("清空气泡库？")
                .setMessage("将关闭发送及采集开关。不会删除 QQ 的消息，也不会修改 QFun 配置。")
                .setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->execute(()->{repo.clearLibrary();render();})).show());
        section("严格回避规则（不可关闭）");
        text("① 转发路径不改发送参数。\n② 已读取的原消息对象直接跳过。\n③ 识别到 QFun / 复读调用栈直接跳过。\n④ 只有正常点“发送”的一次性匹配许可才能改气泡。\n⑤ 接口缺失、版本不符、配置读取失败时，保留原消息。",14,false);
        text("默认不开启。不包含联网、广告、遥测或登录功能；不会获取 Cookie、密码或支付信息。QQ 模块仍可能触发账号风控，本项目不承诺零风险。",13,false);
        scroll.post(()->scroll.scrollTo(0,y));
    }
    private void addBubbleRow(JSONObject row) throws Exception {
        BubbleSpec b=JsonCodec.decode(row); String name=row.optString("name",b.label());
        CheckBox box=new CheckBox(this); box.setText(name+"\n"+b.label()); box.setTextSize(14);
        box.setPadding(0,dp(10),0,dp(10)); box.setChecked(row.optBoolean("selected",false));
        content.addView(box,new LinearLayout.LayoutParams(-1,-2));
        box.setOnCheckedChangeListener((v,checked) -> execute(()->{repo.select(b.key(),checked);render();}));
        box.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"改名","删除"},(d,which) -> {
                if(which==0) rename(b,name);
                else execute(()->{repo.remove(b.key());render();});
            }).show(); return true;
        });
    }
    private void rename(BubbleSpec b,String old) {
        EditText input=new EditText(this); input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT); input.setText(old);
        new AlertDialog.Builder(this).setTitle("气泡名称（最多 48 字）").setView(input).setNegativeButton("取消",null)
            .setPositiveButton("保存",(d,w)->execute(()->{repo.rename(b.key(),input.getText().toString());render();})).show();
    }
    private void showDiagnostics() {
        String report=repo.diagnostics(); TextView t=new TextView(this); t.setText(report); t.setTextIsSelectable(true);
        t.setTextSize(14); t.setPadding(dp(20),dp(12),dp(20),dp(12)); ScrollView s=new ScrollView(this); s.addView(t);
        new AlertDialog.Builder(this).setTitle("QQ 进程诊断").setView(s).setNegativeButton("关闭",null)
                .setPositiveButton("复制",(d,w)->{
                    ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    c.setPrimaryClip(ClipData.newPlainText("LingBubble diagnostics",report));
                    Toast.makeText(this,"已复制诊断，不含聊天正文",Toast.LENGTH_SHORT).show();
                }).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        if(request!=EXPORT && request!=IMPORT && request!=EXPORT_LOG) return;
        // Document providers may be slow: never block the UI during file IO.
        new Thread(()->{
            try {
                String message;
                if(request==EXPORT || request==EXPORT_LOG) {
                    String body=request==EXPORT?repo.exportLibrary():"Ling 随机气泡 0.1.12 运行日志\n不含聊天正文、QQ号或群号。\n\n"+repo.diagnostics();
                    try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
                        if(out==null) throw new IllegalStateException("无法打开输出文件");
                        out.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                    message=request==EXPORT?"已导出气泡库":"已导出运行日志";
                } else {
                    String json;
                    try(InputStream in=getContentResolver().openInputStream(uri); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                        if(in==null) throw new IllegalStateException("无法打开输入文件");
                        byte[] buffer=new byte[4096]; int n;
                        while((n=in.read(buffer))!=-1) {
                            if(out.size()+n>JsonCodec.MAX_JSON_CHARS) throw new IllegalArgumentException("文件超过 256 KB");
                            out.write(buffer,0,n);
                        }
                        json=new String(out.toByteArray(),StandardCharsets.UTF_8);
                    }
                    message="新增 "+repo.importLibrary(json)+" 个气泡；发送开关已关闭，请重新选择并测试";
                }
                final String done=message;
                runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){Toast.makeText(this,done,Toast.LENGTH_LONG).show();render();}});
            } catch(Exception e) {
                final String error="文件操作失败："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
                runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())Toast.makeText(this,error,Toast.LENGTH_LONG).show();});
            }
        },"LingBubble-Documents").start();
    }
}
