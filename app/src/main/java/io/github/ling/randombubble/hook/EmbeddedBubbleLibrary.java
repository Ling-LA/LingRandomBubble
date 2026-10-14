package io.github.ling.randombubble.hook;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import io.github.ling.randombubble.ui.BubbleUi;
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
import io.github.ling.randombubble.core.LibrarySearch;
import io.github.ling.randombubble.store.LibraryRows;
import io.github.ling.randombubble.store.LibraryGroup;
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
    private final ArrayList<LibraryGroup> groups=new ArrayList<>();
    private Dialog dialog;
    private TextView summary,pageLabel,previous,next,filterButton,undoButton,selectButton,clearButton;
    private final HostRuntime runtime;
    private BubbleNameIndexer names;
    private ListView list;
    private int page;
    private String query="";
    private boolean selectedOnly;

    private EmbeddedBubbleLibrary(Activity activity,HostRuntime runtime) {
        this.activity=activity; this.runtime=runtime; bridge=runtime.bridge; owner=bridge.accountIdentity();
    }
    static void show(Activity activity,HostRuntime runtime,Runnable after) {
        if(activity.isFinishing() || activity.isDestroyed()) return;
        try { new EmbeddedBubbleLibrary(activity,runtime).build(after); }
        catch(Throwable e) { runtime.log("内嵌气泡库失败 "+e.getClass().getSimpleName()); SettingsPanel.error(activity,"无法打开气泡库"); after.run(); }
    }
    private void build(Runnable after) {
        LinearLayout root=SettingsPanel.page(activity);
        dialog=SettingsPanel.dialog(activity,root);
        dialog.setOnDismissListener(d -> { if(names!=null)names.stop(); if(!activity.isFinishing() && !activity.isDestroyed()) after.run(); });
        root.setFocusableInTouchMode(true);
        BubbleUi.libraryHeader(root,bridge.libraryMask()+" · 长按管理",dialog::dismiss);
        EditText search=BubbleUi.search(activity);
        root.addView(search,SettingsPanel.gap(activity,12));
        root.addView(SettingsPanel.label(activity,"多个关键词用空格、逗号或顿号分隔",11,SettingsPanel.MUTED,false),SettingsPanel.gap(activity,6));
        summary=SettingsPanel.label(activity,"",12,SettingsPanel.MUTED,false); root.addView(summary,SettingsPanel.gap(activity,10));
        LinearLayout bulk=new LinearLayout(activity); root.addView(bulk,SettingsPanel.gap(activity,8));
        selectButton=addButton(bulk,"全选全部",() -> bulk(true)); selectButton.setSelected(true);
        clearButton=addButton(bulk,"取消全选",() -> bulk(false));
        LinearLayout selectionTools=new LinearLayout(activity); root.addView(selectionTools,SettingsPanel.gap(activity,4));
        filterButton=addButton(selectionTools,"只看已勾选：关",() -> { selectedOnly=!selectedOnly; page=0; filter(); list.setSelection(0); });
        undoButton=addButton(selectionTools,"撤销批量选择",this::undoBulk);
        TextView nameButton=SettingsPanel.pill(activity,"补全商城名称（可暂停）",false,() -> names.toggle());
        nameButton.setTextColor(SettingsPanel.TEAL);root.addView(nameButton,SettingsPanel.gap(activity,4));
        names=new BubbleNameIndexer(activity,dialog,runtime,owner,nameButton,this::reload);
        FrameLayout listArea=new FrameLayout(activity);
        LinearLayout.LayoutParams listSpace=new LinearLayout.LayoutParams(-1,0,1);listSpace.topMargin=SettingsPanel.dp(activity,8);root.addView(listArea,listSpace);
        list=new ListView(activity); list.setAdapter(adapter);list.setDivider(new ColorDrawable(BubbleUi.CANVAS));list.setDividerHeight(SettingsPanel.dp(activity,1));
        list.setPadding(SettingsPanel.dp(activity,12),SettingsPanel.dp(activity,4),SettingsPanel.dp(activity,12),SettingsPanel.dp(activity,4));
        list.setBackground(SettingsPanel.round(SettingsPanel.CARD,SettingsPanel.dp(activity,14)));list.setClipToOutline(true);
        listArea.addView(list,new FrameLayout.LayoutParams(-1,-1));
        TextView empty=SettingsPanel.label(activity,"没有找到气泡\n试试其他名称或编号",14,SettingsPanel.MUTED,false);empty.setGravity(Gravity.CENTER);listArea.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
        list.setOnItemLongClickListener((parent,view,index,id) -> { edit((LibraryGroup)adapter.getItem(index)); return true; });
        LinearLayout navigation=new LinearLayout(activity);navigation.setGravity(Gravity.CENTER_VERTICAL);root.addView(navigation,SettingsPanel.gap(activity,8));
        previous=addButton(navigation,"上一页",() -> { page--; refresh(); list.setSelection(0); });
        pageLabel=SettingsPanel.label(activity,"",12,SettingsPanel.MUTED,false);pageLabel.setGravity(Gravity.CENTER);navigation.addView(pageLabel,new LinearLayout.LayoutParams(0,-2,1));
        next=addButton(navigation,"下一页",() -> { page++; refresh(); list.setSelection(0); });
        root.addView(SettingsPanel.label(activity,"批量选择作用于全部匹配结果，包含其他页。",11,SettingsPanel.MUTED,false),SettingsPanel.gap(activity,6));
        reload();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { query=s.toString(); page=0; filter(); list.setSelection(0); }
            public void afterTextChanged(Editable s) {}
        });
        root.requestFocus();SettingsPanel.showDialog(dialog);
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
        matches.clear(); LibrarySearch search=new LibrarySearch(query);
        for(JSONObject row:rows) if(LibraryRows.matches(row,search,selectedOnly)) matches.add(row);
        groups.clear();groups.addAll(LibraryGroup.of(matches));
        page=Math.max(0,Math.min(page,Math.max(0,(groups.size()-1)/PAGE_SIZE))); refresh();
    }
    private void refresh() {
        ArrayList<LibraryGroup> all=LibraryGroup.of(rows);int selected=0;for(LibraryGroup group:all)if(group.selected()>0)selected++;
        int pages=Math.max(1,(groups.size()+PAGE_SIZE-1)/PAGE_SIZE);
        summary.setText("共 "+all.size()+" 款 · 已选 "+selected+" 款 · 匹配 "+groups.size()+" 款");
        pageLabel.setText("第 "+(page+1)+" / "+pages+" 页");
        previous.setEnabled(page>0); previous.setAlpha(page>0?1f:0.4f); next.setEnabled(page+1<pages); next.setAlpha(page+1<pages?1f:0.4f);
        boolean filtered=selectedOnly || !new LibrarySearch(query).isEmpty();
        selectButton.setText(filtered?"选择搜索结果":"全选全部");clearButton.setText(filtered?"取消结果勾选":"取消全选");
        for(TextView button:new TextView[]{selectButton,clearButton}){button.setEnabled(!matches.isEmpty());button.setAlpha(matches.isEmpty()?0.4f:1f);}
        filterButton.setText("只看已勾选："+(selectedOnly?"开":"关"));filterButton.setSelected(selectedOnly); updateUndoButton(); adapter.notifyDataSetChanged();
    }
    private void bulk(boolean checked) {
        if(matches.isEmpty())return;
        final Set<String> targets;
        try {targets=LibraryRows.keys(matches);}catch(Exception e){SettingsPanel.error(activity,"无效气泡条目");return;}
        Set<String> before=new HashSet<>();
        if(mutate(document -> {
            JSONArray a=document.getJSONArray("bubbles");
            for(int i=0;i<a.length();i++) {
                JSONObject row=a.getJSONObject(i);
                if(row.optBoolean("selected")) before.add(JsonCodec.decode(row).key());
                if(targets.contains(JsonCodec.decode(row).key()))row.put("selected",checked);
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
    private void edit(LibraryGroup group) {
        if(!currentAccount()) return;
        try {
            Set<String> targets=group.keys();String name=group.name();
            SettingsPanel.alert(activity).setTitle(name).setItems(new String[]{"改名（"+group.rows.size()+" 条记录）","删除（"+group.rows.size()+" 条记录）"},(d,which) -> {
                if(which==1) SettingsPanel.alert(activity).setTitle("删除这款气泡？").setMessage("将删除当前匹配的 "+targets.size()+" 条记录；其他搜索结果不变。").setNegativeButton("取消",null).setPositiveButton("删除",(confirm,w) -> mutate(document -> {
                    JSONArray a=document.getJSONArray("bubbles"), kept=new JSONArray(); boolean selected=false;
                    for(int i=0;i<a.length();i++) if(!targets.contains(JsonCodec.decode(a.getJSONObject(i)).key())) {
                        JSONObject item=a.getJSONObject(i); kept.put(item); selected|=item.optBoolean("selected");
                    }
                    document.put("bubbles",kept); if(!selected) document.put("enabled",false);
                })).show();
                else rename(targets,name);
            }).show();
        } catch(Exception e) { SettingsPanel.error(activity,"无效气泡条目"); }
    }
    private void rename(Set<String> targets,String name) {
        EditText input=new EditText(activity); BubbleUi.input(input);input.setSingleLine(true); input.setText(name); input.setSelection(input.length());
        android.app.AlertDialog rename=SettingsPanel.alert(activity).setTitle("气泡名称（最多 48 字）").setView(input)
            .setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        rename.setOnShowListener(d -> rename.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value=input.getText().toString().trim();
            if(value.isEmpty() || value.length()>48) { input.setError("名称需为 1～48 字"); return; }
            if(!currentAccount()) { rename.dismiss(); return; }
            String failure=bridge.editForAccount(owner,document -> {
                JSONArray a=document.getJSONArray("bubbles");
                for(int i=0;i<a.length();i++) if(targets.contains(JsonCodec.decode(a.getJSONObject(i)).key())) a.getJSONObject(i).put("name",value);
            });
            if(failure!=null) SettingsPanel.error(activity,failure); else { rename.dismiss(); reload(); }
        })); rename.show();
    }
    private final class Rows extends BaseAdapter {
        public int getCount() { return Math.min(PAGE_SIZE,Math.max(0,groups.size()-page*PAGE_SIZE)); }
        public Object getItem(int index) { return groups.get(page*PAGE_SIZE+index); }
        public long getItemId(int index) { return page*PAGE_SIZE+index; }
        public View getView(int index,View recycled,ViewGroup parent) {
            CheckBox box=recycled instanceof CheckBox?(CheckBox)recycled:BubbleUi.checkBox(activity);
            LibraryGroup group=(LibraryGroup)getItem(index); box.setOnCheckedChangeListener(null); box.setOnLongClickListener(null); box.setEnabled(true);
            boolean checked=group.allSelected();
            try {
                Set<String> targets=group.keys();box.setText(BubbleUi.rowLabel(group.name(),group.detail()));box.setChecked(checked);BubbleUi.partial(box,group.partial());
                box.setOnCheckedChangeListener((v,value) -> mutate(document -> LibraryRows.select(document,targets,value)));
                box.setOnLongClickListener(v -> { edit(group); return true; });
            } catch(Exception e) { box.setText("无效气泡条目"); box.setChecked(false);BubbleUi.partial(box,false); box.setEnabled(false); }
            return box;
        }
    }
}
