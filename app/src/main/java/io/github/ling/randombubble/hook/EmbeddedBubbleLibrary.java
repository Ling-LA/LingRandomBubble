package io.github.ling.randombubble.hook;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import io.github.ling.randombubble.core.BubbleSpec;
import io.github.ling.randombubble.store.JsonCodec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Account-local paginated manager built solely from host-context Android views. */
final class EmbeddedBubbleLibrary {
    private static final int PAGE_SIZE=20;
    /** In-memory only; a recent batch can be undone after reopening this account's dialog. */
    private static final Map<String,Set<String>> undoSelections=new LinkedHashMap<String,Set<String>>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Set<String>> entry) { return size()>8; }
    };
    private final Activity activity;
    private final HostBridge bridge;
    private final String owner;
    private final ArrayList<JSONObject> rows=new ArrayList<>(), matches=new ArrayList<>();
    private final Rows adapter=new Rows();
    private Dialog dialog;
    private TextView summary,previous,next,filterButton,undoButton;
    private ListView list;
    private int page;
    private String query="";
    private boolean selectedOnly;

    private EmbeddedBubbleLibrary(Activity activity,HostRuntime runtime) {
        this.activity=activity; bridge=runtime.bridge; owner=bridge.accountIdentity();
    }
    static void show(Activity activity,HostRuntime runtime,Runnable after) {
        if(activity.isFinishing() || activity.isDestroyed()) return;
        try { new EmbeddedBubbleLibrary(activity,runtime).build(after); }
        catch(Throwable e) { runtime.log("内嵌气泡库失败 "+e.getClass().getSimpleName()); SettingsPanel.error(activity,"无法打开气泡库"); after.run(); }
    }
    private void build(Runnable after) {
        LinearLayout root=SettingsPanel.page(activity);
        dialog=SettingsPanel.dialog(activity,root);
        dialog.setOnDismissListener(d -> { if(!activity.isFinishing() && !activity.isDestroyed()) after.run(); });
        root.addView(SettingsPanel.label(activity,"气泡库",22,SettingsPanel.PAPER,true));
        root.addView(SettingsPanel.label(activity,bridge.libraryMask()+" · 长按条目改名或删除",12,SettingsPanel.MUTED,false));
        EditText search=new EditText(activity); search.setSingleLine(true); search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setHint("搜索名称或气泡编号"); search.setTextSize(15); search.setTextColor(SettingsPanel.PAPER); search.setHintTextColor(SettingsPanel.MUTED);
        root.addView(search,SettingsPanel.gap(activity,8));
        summary=SettingsPanel.label(activity,"",13,SettingsPanel.MUTED,false); root.addView(summary,SettingsPanel.gap(activity,6));
        LinearLayout bulk=new LinearLayout(activity); root.addView(bulk,SettingsPanel.gap(activity,8));
        addButton(bulk,"全选全部",() -> bulk(true)); addButton(bulk,"取消全选",() -> bulk(false));
        LinearLayout selectionTools=new LinearLayout(activity); root.addView(selectionTools,SettingsPanel.gap(activity,6));
        filterButton=addButton(selectionTools,"只看已勾选：关",() -> { selectedOnly=!selectedOnly; page=0; filter(); list.setSelection(0); });
        undoButton=addButton(selectionTools,"撤销批量选择",this::undoBulk);
        list=new ListView(activity); list.setAdapter(adapter); list.setDividerHeight(SettingsPanel.dp(activity,4));
        list.setBackgroundColor(SettingsPanel.INK); root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemLongClickListener((parent,view,index,id) -> { edit((JSONObject)adapter.getItem(index)); return true; });
        LinearLayout navigation=new LinearLayout(activity); root.addView(navigation,SettingsPanel.gap(activity,6));
        previous=addButton(navigation,"上一页",() -> { page--; refresh(); list.setSelection(0); });
        next=addButton(navigation,"下一页",() -> { page++; refresh(); list.setSelection(0); });
        root.addView(SettingsPanel.label(activity,"全选覆盖全部分页和搜索外条目。勾选不会开启轮换；账号装扮仍需商城使用权益。",12,SettingsPanel.MUTED,false),SettingsPanel.gap(activity,8));
        root.addView(SettingsPanel.pill(activity,"返回设置",true,dialog::dismiss),SettingsPanel.gap(activity,8));
        reload();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { query=s.toString(); page=0; filter(); list.setSelection(0); }
            public void afterTextChanged(Editable s) {}
        });
        SettingsPanel.showDialog(dialog);
    }
    private TextView addButton(LinearLayout parent,String label,Runnable action) {
        TextView button=SettingsPanel.pill(activity,label,false,action);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.setMargins(SettingsPanel.dp(activity,2),0,SettingsPanel.dp(activity,2),0);
        parent.addView(button,lp); return button;
    }
    private boolean currentAccount() {
        if(owner.equals(bridge.accountIdentity())) return true;
        SettingsPanel.error(activity,"账号已切换，请重新打开面板"); dialog.dismiss(); return false;
    }
    private boolean mutate(HostBridge.Edit edit) {
        if(!currentAccount()) return false;
        String failure=bridge.editForAccount(owner,edit);
        if(failure!=null) { SettingsPanel.error(activity,failure); return false; }
        reload(); return true;
    }
    private void reload() {
        if(!currentAccount()) return;
        rows.clear(); JSONArray a=bridge.document().optJSONArray("bubbles");
        if(a!=null) for(int i=0;i<a.length();i++) { JSONObject row=a.optJSONObject(i); if(row!=null) rows.add(row); }
        filter();
    }
    private void filter() {
        matches.clear(); String needle=query.trim().toLowerCase(Locale.ROOT);
        for(JSONObject row:rows) if((!selectedOnly || row.optBoolean("selected")) &&
            (row.optString("name")+" "+row.optString("bubbleId")+" "+row.optString("subBubbleId")).toLowerCase(Locale.ROOT).contains(needle)) matches.add(row);
        page=Math.max(0,Math.min(page,Math.max(0,(matches.size()-1)/PAGE_SIZE))); refresh();
    }
    private void refresh() {
        int selected=0; for(JSONObject row:rows) if(row.optBoolean("selected")) selected++;
        int pages=Math.max(1,(matches.size()+PAGE_SIZE-1)/PAGE_SIZE);
        summary.setText("共 "+rows.size()+" 项 · 已勾选 "+selected+" 项 · 匹配 "+matches.size()+" 项\n第 "+(page+1)+" / "+pages+" 页，每页 "+PAGE_SIZE+" 项");
        previous.setEnabled(page>0); previous.setAlpha(page>0?1f:0.4f); next.setEnabled(page+1<pages); next.setAlpha(page+1<pages?1f:0.4f);
        filterButton.setText("只看已勾选："+(selectedOnly?"开":"关")); updateUndoButton(); adapter.notifyDataSetChanged();
    }
    private void bulk(boolean checked) {
        Set<String> before=new HashSet<>();
        if(mutate(document -> {
            JSONArray a=document.getJSONArray("bubbles");
            for(int i=0;i<a.length();i++) {
                JSONObject row=a.getJSONObject(i);
                if(row.optBoolean("selected")) before.add(JsonCodec.decode(row).key());
                row.put("selected",checked);
            }
            if(!checked) document.put("enabled",false);
        })) {
            synchronized(undoSelections) { undoSelections.put(owner,before); }
            updateUndoButton();
        }
    }
    private void updateUndoButton() {
        boolean available;
        synchronized(undoSelections) { available=undoSelections.containsKey(owner); }
        undoButton.setEnabled(available); undoButton.setAlpha(available?1f:0.4f);
    }
    private void undoBulk() {
        final Set<String> saved;
        synchronized(undoSelections) { saved=undoSelections.get(owner); }
        if(saved==null) return;
        if(mutate(document -> {
            JSONArray a=document.getJSONArray("bubbles");
            for(int i=0;i<a.length();i++) {
                JSONObject row=a.getJSONObject(i); row.put("selected",saved.contains(JsonCodec.decode(row).key()));
            }
            // Restoring selection never enables the abandoned send path or decoration rotation.
            document.put("enabled",false);
        })) {
            synchronized(undoSelections) { if(undoSelections.get(owner)==saved) undoSelections.remove(owner); }
            updateUndoButton();
        }
    }
    private void edit(JSONObject row) {
        if(!currentAccount()) return;
        try {
            BubbleSpec b=JsonCodec.decode(row); String name=row.optString("name",b.label());
            SettingsPanel.alert(activity).setTitle(name).setItems(new String[]{"改名","删除"},(d,which) -> {
                if(which==1) mutate(document -> {
                    JSONArray a=document.getJSONArray("bubbles"), kept=new JSONArray(); boolean selected=false;
                    for(int i=0;i<a.length();i++) if(!JsonCodec.decode(a.getJSONObject(i)).key().equals(b.key())) {
                        JSONObject item=a.getJSONObject(i); kept.put(item); selected|=item.optBoolean("selected");
                    }
                    document.put("bubbles",kept); if(!selected) document.put("enabled",false);
                });
                else rename(b,name);
            }).show();
        } catch(Exception e) { SettingsPanel.error(activity,"无效气泡条目"); }
    }
    private void rename(BubbleSpec b,String name) {
        EditText input=new EditText(activity); input.setSingleLine(true); input.setTextColor(0xFF10181C); input.setText(name); input.setSelection(input.length());
        android.app.AlertDialog rename=SettingsPanel.alert(activity).setTitle("气泡名称（最多 48 字）").setView(input)
            .setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        rename.setOnShowListener(d -> rename.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value=input.getText().toString().trim();
            if(value.isEmpty() || value.length()>48) { input.setError("名称需为 1～48 字"); return; }
            if(!currentAccount()) { rename.dismiss(); return; }
            String failure=bridge.editForAccount(owner,document -> {
                JSONArray a=document.getJSONArray("bubbles");
                for(int i=0;i<a.length();i++) if(JsonCodec.decode(a.getJSONObject(i)).key().equals(b.key())) a.getJSONObject(i).put("name",value);
            });
            if(failure!=null) SettingsPanel.error(activity,failure); else { rename.dismiss(); reload(); }
        })); rename.show();
    }
    private final class Rows extends BaseAdapter {
        public int getCount() { return Math.min(PAGE_SIZE,Math.max(0,matches.size()-page*PAGE_SIZE)); }
        public Object getItem(int index) { return matches.get(page*PAGE_SIZE+index); }
        public long getItemId(int index) { return page*PAGE_SIZE+index; }
        public View getView(int index,View recycled,ViewGroup parent) {
            CheckBox box=recycled instanceof CheckBox?(CheckBox)recycled:new CheckBox(activity);
            JSONObject row=(JSONObject)getItem(index); box.setOnCheckedChangeListener(null); box.setOnLongClickListener(null); box.setEnabled(true);
            box.setTextColor(SettingsPanel.PAPER); box.setTextSize(15); box.setPadding(SettingsPanel.dp(activity,10),SettingsPanel.dp(activity,12),SettingsPanel.dp(activity,10),SettingsPanel.dp(activity,12));
            box.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{SettingsPanel.TEAL,SettingsPanel.MUTED}));
            boolean checked=row.optBoolean("selected"); box.setBackground(SettingsPanel.round(checked?SettingsPanel.TEAL_DIM:SettingsPanel.CARD,SettingsPanel.dp(activity,10)));
            try {
                BubbleSpec b=JsonCodec.decode(row); box.setText(row.optString("name",b.label())+"\n"+b.label()); box.setChecked(checked);
                box.setOnCheckedChangeListener((v,value) -> mutate(document -> {
                    JSONArray a=document.getJSONArray("bubbles"); boolean any=false;
                    for(int i=0;i<a.length();i++) {
                        JSONObject item=a.getJSONObject(i); if(JsonCodec.decode(item).key().equals(b.key())) item.put("selected",value);
                        any|=item.optBoolean("selected");
                    }
                    if(!any) document.put("enabled",false);
                }));
                box.setOnLongClickListener(v -> { edit(row); return true; });
            } catch(Exception e) { box.setText("无效气泡条目"); box.setChecked(false); box.setEnabled(false); }
            return box;
        }
    }
}
