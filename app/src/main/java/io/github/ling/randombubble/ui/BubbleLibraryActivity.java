package io.github.ling.randombubble.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import android.view.*;
import io.github.ling.randombubble.store.Repository;
import io.github.ling.randombubble.core.BubbleSpec;
import io.github.ling.randombubble.core.LibrarySearch;
import io.github.ling.randombubble.store.LibraryRows;
import io.github.ling.randombubble.store.LibraryGroup;
import io.github.ling.randombubble.store.JsonCodec;
import org.json.*;
import java.util.*;

/** Fixed controls and recycled rows: library size never pushes settings off screen. */
public final class BubbleLibraryActivity extends Activity {
    private static final int PAGE_SIZE=20;
    private Repository repo;
    private final ArrayList<JSONObject> rows=new ArrayList<>(), matches=new ArrayList<>();
    private final ArrayList<LibraryGroup> groups=new ArrayList<>();
    private int page;
    private String query="";
    private boolean selectedOnly;
    private TextView summary,pageLabel,previous,next,selectButton,clearButton;
    private final Rows adapter=new Rows();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); repo=Repository.get(this);
        if(state!=null) { query=state.getString("query",""); page=state.getInt("page"); selectedOnly=state.getBoolean("selectedOnly"); }
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BubbleUi.CANVAS);root.setFocusableInTouchMode(true);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)-> { v.setPadding(BubbleUi.dp(this,16)+insets.getSystemWindowInsetLeft(),BubbleUi.dp(this,16)+insets.getSystemWindowInsetTop(),BubbleUi.dp(this,16)+insets.getSystemWindowInsetRight(),BubbleUi.dp(this,12)+insets.getSystemWindowInsetBottom());return insets; });root.requestApplyInsets();
        BubbleUi.libraryHeader(root,"长按气泡可改名或删除",this::finish);
        EditText search=BubbleUi.search(this);search.setText(query);root.addView(search,gap(12));
        root.addView(BubbleUi.text(this,"多个关键词用空格、逗号或顿号分隔",11,BubbleUi.MUTED,false),gap(6));
        summary=BubbleUi.text(this,"",12,BubbleUi.MUTED,false);root.addView(summary,gap(10));
        LinearLayout bulk=new LinearLayout(this);root.addView(bulk,gap(8));
        selectButton=addButton(bulk,"全选全部",()->bulk(true));selectButton.setSelected(true);clearButton=addButton(bulk,"取消全选",()->bulk(false));
        CheckBox filter=BubbleUi.checkBox(this);filter.setMinHeight(BubbleUi.dp(this,44));filter.setText("只看已勾选");filter.setChecked(selectedOnly);root.addView(filter,gap(4));
        filter.setOnCheckedChangeListener((v,checked)->{selectedOnly=checked;page=0;filter();});
        FrameLayout listArea=new FrameLayout(this);LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,0,1);space.topMargin=BubbleUi.dp(this,8);root.addView(listArea,space);
        ListView list=new ListView(this);list.setAdapter(adapter);list.setDivider(new android.graphics.drawable.ColorDrawable(BubbleUi.CANVAS));list.setDividerHeight(BubbleUi.dp(this,1));
        list.setPadding(BubbleUi.dp(this,12),BubbleUi.dp(this,4),BubbleUi.dp(this,12),BubbleUi.dp(this,4));
        list.setBackground(BubbleUi.surface(this,BubbleUi.PAPER,14));list.setClipToOutline(true);listArea.addView(list,new FrameLayout.LayoutParams(-1,-1));
        TextView empty=BubbleUi.text(this,"没有找到气泡\n试试其他名称或编号",14,BubbleUi.MUTED,false);empty.setGravity(Gravity.CENTER);listArea.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
        list.setOnItemLongClickListener((p,v,index,id)-> { edit(groups.get(page*PAGE_SIZE+index));return true; });
        LinearLayout navigation=new LinearLayout(this);navigation.setGravity(Gravity.CENTER_VERTICAL);root.addView(navigation,gap(8));
        previous=addButton(navigation,"上一页",()->{page--;refresh();list.setSelection(0);});
        pageLabel=BubbleUi.text(this,"",12,BubbleUi.MUTED,false);pageLabel.setGravity(Gravity.CENTER);navigation.addView(pageLabel,new LinearLayout.LayoutParams(0,-2,1));
        next=addButton(navigation,"下一页",()->{page++;refresh();list.setSelection(0);});
        root.addView(BubbleUi.text(this,"批量选择作用于全部匹配结果，包含其他页。",11,BubbleUi.MUTED,false),gap(6));
        root.requestFocus();
        reload();
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();page=0;filter();}
            public void afterTextChanged(Editable s){}
        });
    }
    private interface Work { void run() throws Exception; }
    private LinearLayout.LayoutParams gap(int top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=BubbleUi.dp(this,top);return p;
    }
    private TextView addButton(LinearLayout parent,String label,Work action) {
        TextView button=BubbleUi.action(this,label,false,()->execute(action));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(BubbleUi.dp(this,2),0,BubbleUi.dp(this,2),0);parent.addView(button,p);return button;
    }
    private void execute(Work work) { try {work.run();} catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();} }
    private void reload() {
        rows.clear();JSONArray a=repo.snapshot().optJSONArray("bubbles");
        if(a!=null)for(int i=0;i<a.length();i++)rows.add(a.optJSONObject(i));
        filter();
    }
    private void filter() {
        matches.clear();LibrarySearch search=new LibrarySearch(query);
        for(JSONObject row:rows)if(LibraryRows.matches(row,search,selectedOnly))matches.add(row);
        groups.clear();groups.addAll(LibraryGroup.of(matches));
        page=Math.max(0,Math.min(page,Math.max(0,(groups.size()-1)/PAGE_SIZE)));refresh();
    }
    private void refresh() {
        ArrayList<LibraryGroup> all=LibraryGroup.of(rows);int selected=0;for(LibraryGroup group:all)if(group.selected()>0)selected++;
        int pages=Math.max(1,(groups.size()+PAGE_SIZE-1)/PAGE_SIZE);
        summary.setText("共 "+all.size()+" 款 · 已选 "+selected+" 款 · 匹配 "+groups.size()+" 款");
        pageLabel.setText("第 "+(page+1)+" / "+pages+" 页");
        boolean filtered=selectedOnly || !new LibrarySearch(query).isEmpty();
        selectButton.setText(filtered?"选择搜索结果":"全选全部");clearButton.setText(filtered?"取消结果勾选":"取消全选");
        for(TextView button:new TextView[]{selectButton,clearButton}){button.setEnabled(!matches.isEmpty());button.setAlpha(matches.isEmpty()?0.4f:1f);}
        previous.setEnabled(page>0);previous.setAlpha(page>0?1f:0.4f);next.setEnabled(page+1<pages);next.setAlpha(page+1<pages?1f:0.4f);adapter.notifyDataSetChanged();
    }
    private void bulk(boolean checked) throws Exception {if(matches.isEmpty())return;repo.selectKeys(LibraryRows.keys(matches),checked);reload();}
    private void edit(LibraryGroup group) {
        execute(()->{
            Set<String> targets=group.keys();String name=group.name();
            new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"改名（"+group.rows.size()+" 条记录）","删除（"+group.rows.size()+" 条记录）"},(d,which)-> {
                if(which==1)new AlertDialog.Builder(this).setTitle("删除这款气泡？").setMessage("将删除当前匹配的 "+targets.size()+" 条记录；其他搜索结果不变。").setNegativeButton("取消",null).setPositiveButton("删除",(confirm,w)->execute(()->{repo.removeKeys(targets);reload();})).show();
                else {EditText input=new EditText(this);input.setSingleLine(true);input.setText(name);
                    new AlertDialog.Builder(this).setTitle("气泡名称").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(dialog,w)->execute(()->{repo.renameKeys(targets,input.getText().toString());reload();})).show();}
            }).show();
        });
    }
    @Override protected void onSaveInstanceState(Bundle state) {state.putString("query",query);state.putInt("page",page);state.putBoolean("selectedOnly",selectedOnly);super.onSaveInstanceState(state);}
    private final class Rows extends BaseAdapter {
        public int getCount(){return Math.min(PAGE_SIZE,Math.max(0,groups.size()-page*PAGE_SIZE));}
        public Object getItem(int index){return groups.get(page*PAGE_SIZE+index);}
        public long getItemId(int index){return page*PAGE_SIZE+index;}
        public View getView(int index,View recycled,ViewGroup parent) {
            CheckBox box=recycled instanceof CheckBox?(CheckBox)recycled:BubbleUi.checkBox(BubbleLibraryActivity.this);
            LibraryGroup group=(LibraryGroup)getItem(index);box.setOnCheckedChangeListener(null);box.setOnLongClickListener(null);box.setEnabled(true);
            try {
                Set<String> targets=group.keys();box.setText(BubbleUi.rowLabel(group.name(),group.detail()));box.setChecked(group.allSelected());BubbleUi.partial(box,group.partial());
                box.setOnCheckedChangeListener((v,checked)->execute(()->{repo.selectKeys(targets,checked);reload();}));
                box.setOnLongClickListener(v->{edit(group);return true;});
            } catch(Exception e){box.setText("无效气泡条目");box.setChecked(false);BubbleUi.partial(box,false);box.setEnabled(false);}
            return box;
        }
    }
}
