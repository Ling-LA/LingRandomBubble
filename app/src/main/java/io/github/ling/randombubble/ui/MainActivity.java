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

/** Standalone settings; QQ's embedded panel works independently. */
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
        text(io.github.ling.randombubble.BuildConfig.VERSION_NAME+" · 实机验证版\n目标：QQ 9.3.50 / 与 QFun 1.3.4 并行\n当前账号："+repo.accountLabel(),14,false);
        text("已通过 QQ 9.3.50 双端实测：账号装扮切换可被未安装模块的接收端看到。请在诊断中确认服务器结果；编号仍需具有使用权益。",14,false);
        section("账号装扮切换");
        text("计时和逐消息默认关闭，可独立开启。低频建议 1800 秒，高频建议 60 秒，可自行配置 60 至 86400 秒。逐消息会等待商城确认后发送，增加账号设置请求。",14,false);
        JSONObject decoration=repo.decorationSettings();
        text((decoration.optBoolean("automatic",false)?"计时轮换开启 · 间隔 "+decoration.optInt("seconds",1800)+" 秒":"计时轮换关闭")+" · 逐消息"+(decoration.optBoolean("perMessage",false)?"开启":"关闭"),14,false);
        button("配置手动 / 低频 / 高频切换",this::decorationDialog);
        button("停止轮换（计时 / 逐消息）",() -> {
            JSONObject settings=repo.decorationSettings();
            JSONArray ids=settings.optJSONArray("ids");
            if(ids!=null && ids.length()>0) repo.configureDecoration(ids.join(","),settings.optInt("seconds",1800),false,false,false);
            Toast.makeText(this,"已停止计时和逐消息轮换；已提交的装扮请求可能仍会完成",Toast.LENGTH_LONG).show(); render();
        });
        JSONObject j=repo.snapshot();
        section("开关");
        toggle(j,"collect","浏览时也采集","只记录样式线索；采集编号不等于账号拥有该装扮权益。");
        section("气泡库");
        text("气泡库仅作编号参考，长按可改名或删除。账号装扮轮换请在上方独立配置，服务器仍会校验使用权益。",14,false);
        JSONArray library=j.optJSONArray("bubbles");
        text((library==null?0:library.length())+" 个气泡；搜索、分页和批量勾选请打开气泡库。",14,false);
        button("打开气泡库（搜索 / 全选）",()->startActivity(new Intent(this,BubbleLibraryActivity.class)));
        section("备份与诊断");
        text("运行日志保存在 QQ 应用目录，可通过下面的诊断按钮查看并导出。诊断在配置应用重启后仍可读取。",14,false);
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
        section("运行保护");
        text("只在 QQ 前台且屏幕解锁时处理。请求失败后停止；逐消息失败保留输入，文本或会话变化取消发送。仅支持点击发送按钮的普通文字，不支持附件、Enter 或脚本消息。勾选和全选不会开启功能。",13,false);
        scroll.post(()->scroll.scrollTo(0,y));
    }
    private void decorationDialog() {
        JSONObject settings=repo.decorationSettings();
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText ids=new EditText(this); ids.setMaxLines(3); ids.setTextSize(14); ids.setHint("有权使用的气泡编号，逗号分隔");
        String selected=repo.selectedDecorationIds();
        JSONArray existing=settings.optJSONArray("ids"); if(!selected.isEmpty()) ids.setText(selected); else if(existing!=null) try { ids.setText(existing.join(",")); } catch(Exception ignored) {}
        form.addView(ids);
        CheckBox perMessage=new CheckBox(this); perMessage.setText("逐消息切换（普通文字）"); perMessage.setChecked(settings.optBoolean("perMessage",false)); form.addView(perMessage);
        android.widget.Spinner mode=new android.widget.Spinner(this);
        mode.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"手动切换","低频自动轮换","高频自动轮换"}));
        int seconds=settings.optInt("seconds",1800); mode.setSelection(settings.optBoolean("automatic")?(seconds<1800?2:1):0); form.addView(mode);
        EditText interval=new EditText(this); interval.setInputType(InputType.TYPE_CLASS_NUMBER); interval.setHint("间隔秒数：60 至 86400"); interval.setText(String.valueOf(seconds)); form.addView(interval);
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            boolean initial=true;
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id) {
                if(initial) { initial=false; return; }
                if(position==1) interval.setText("1800"); else if(position==2) interval.setText("60");
            }
        });
        TextView hint=new TextView(this); hint.setText("手动切换到第一个编号；自动模式随机轮换。逐消息可单独开启（计时选手动），至少需要两款。点击发送后等待商城确认新装扮，再继续本次发送；失败保留输入，文本或会话变化取消发送。仅支持普通文字，不支持附件、Enter 或脚本消息。设置修改整个账号，增加账号设置请求；请确认气泡使用权益。"); form.addView(hint);
        ScrollView formScroll=new ScrollView(this); formScroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("账号装扮配置").setView(formScroll).setNegativeButton("取消",null).setPositiveButton("保存并执行",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> execute(() -> {
            boolean each=perMessage.isChecked();
            repo.configureDecoration(ids.getText().toString(),Integer.parseInt(interval.getText().toString()),mode.getSelectedItemPosition()!=0,mode.getSelectedItemPosition()==0 && !each,each);
            dialog.dismiss(); Intent qq=getPackageManager().getLaunchIntentForPackage("com.tencent.mobileqq"); if(qq!=null) startActivity(qq);
        }))); dialog.show();
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
                    String body=request==EXPORT?repo.exportLibrary():"Ling 随机气泡 "+io.github.ling.randombubble.BuildConfig.VERSION_NAME+" 运行日志\n不含聊天正文、QQ号或群号。\n\n"+repo.diagnostics();
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
