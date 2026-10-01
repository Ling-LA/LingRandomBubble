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
import io.github.ling.randombubble.store.JsonCodec;
import org.json.*;
import java.util.*;

/** Fixed controls and recycled rows: library size never pushes settings off screen. */
public final class BubbleLibraryActivity extends Activity {
    private static final int PAGE_SIZE=20;
    private Repository repo;
    private final ArrayList<JSONObject> rows=new ArrayList<>(), matches=new ArrayList<>();
    private int page;
    private String query="";
    private boolean selectedOnly;
    private TextView summary;
    private Button previous,next;
    private final Rows adapter=new Rows();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); repo=Repository.get(this);
        if(state!=null) { query=state.getString("query",""); page=state.getInt("page"); selectedOnly=state.getBoolean("selectedOnly"); }
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,12,24,12); setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)-> { v.setPadding(24+insets.getSystemWindowInsetLeft(),12+insets.getSystemWindowInsetTop(),24+insets.getSystemWindowInsetRight(),12+insets.getSystemWindowInsetBottom());return insets; }); root.requestApplyInsets();
        Button back=new Button(this); back.setText("返回设置"); back.setOnClickListener(v->finish()); root.addView(back);
        EditText search=new EditText(this); search.setSingleLine(true); search.setHint("搜索名称或气泡编号"); search.setText(query); root.addView(search);
        summary=new TextView(this); summary.setPadding(8,12,8,12); root.addView(summary);
        LinearLayout bulk=new LinearLayout(this); root.addView(bulk);
        addButton(bulk,"全选全部",()->bulk(true)); addButton(bulk,"取消全选",()->bulk(false));
        CheckBox filter=new CheckBox(this); filter.setText("只看已勾选");filter.setChecked(selectedOnly);root.addView(filter);
        filter.setOnCheckedChangeListener((v,checked)->{selectedOnly=checked;page=0;filter();});
        ListView list=new ListView(this); list.setAdapter(adapter); root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemLongClickListener((p,v,index,id)-> { edit(matches.get(page*PAGE_SIZE+index));return true; });
        LinearLayout navigation=new LinearLayout(this); root.addView(navigation);
        previous=addButton(navigation,"上一页",()->{page--;refresh();list.setSelection(0);});
        next=addButton(navigation,"下一页",()->{page++;refresh();list.setSelection(0);});
        TextView hint=new TextView(this);hint.setText("全选覆盖全部分页和搜索外条目；勾选不会开启轮换。返回设置后可将已勾选项填入轮换配置。");hint.setTextSize(12);root.addView(hint);
        reload();
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();page=0;filter();}
            public void afterTextChanged(Editable s){}
        });
    }
    private interface Work { void run() throws Exception; }
    private Button addButton(LinearLayout parent,String label,Work action) {
        Button button=new Button(this);button.setText(label);parent.addView(button,new LinearLayout.LayoutParams(0,-2,1));
        button.setOnClickListener(v->execute(action));return button;
    }
    private void execute(Work work) { try {work.run();} catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();} }
    private void reload() {
        rows.clear();JSONArray a=repo.snapshot().optJSONArray("bubbles");
        if(a!=null)for(int i=0;i<a.length();i++)rows.add(a.optJSONObject(i));
        filter();
    }
    private void filter() {
        matches.clear();String needle=query.trim().toLowerCase(Locale.ROOT);
        for(JSONObject row:rows)if(row!=null && (!selectedOnly || row.optBoolean("selected")) &&
            (row.optString("name")+" "+row.optInt("bubbleId")+" "+row.optString("subBubbleId")).toLowerCase(Locale.ROOT).contains(needle))matches.add(row);
        page=Math.max(0,Math.min(page,Math.max(0,(matches.size()-1)/PAGE_SIZE)));refresh();
    }
    private void refresh() {
        int selected=0;for(JSONObject row:rows)if(row!=null && row.optBoolean("selected"))selected++;
        int pages=Math.max(1,(matches.size()+PAGE_SIZE-1)/PAGE_SIZE);
        summary.setText("共 "+rows.size()+" 项 · 已勾选 "+selected+" 项 · 匹配 "+matches.size()+" 项\n第 "+(page+1)+" / "+pages+" 页，每页 "+PAGE_SIZE+" 项");
        previous.setEnabled(page>0);next.setEnabled(page+1<pages);adapter.notifyDataSetChanged();
    }
    private void bulk(boolean checked) throws Exception {repo.selectAll(checked);reload();}
    private void edit(JSONObject row) {
        execute(()->{
            BubbleSpec b=JsonCodec.decode(row);String name=row.optString("name",b.label());
            new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"改名","删除"},(d,which)-> {
                if(which==1)execute(()->{repo.remove(b.key());reload();});
                else {EditText input=new EditText(this);input.setSingleLine(true);input.setText(name);
                    new AlertDialog.Builder(this).setTitle("气泡名称").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(dialog,w)->execute(()->{repo.rename(b.key(),input.getText().toString());reload();})).show();}
            }).show();
        });
    }
    @Override protected void onSaveInstanceState(Bundle state) {state.putString("query",query);state.putInt("page",page);state.putBoolean("selectedOnly",selectedOnly);super.onSaveInstanceState(state);}
    private final class Rows extends BaseAdapter {
        public int getCount(){return Math.min(PAGE_SIZE,Math.max(0,matches.size()-page*PAGE_SIZE));}
        public Object getItem(int index){return matches.get(page*PAGE_SIZE+index);}
        public long getItemId(int index){return page*PAGE_SIZE+index;}
        public View getView(int index,View recycled,ViewGroup parent) {
            CheckBox box=recycled instanceof CheckBox?(CheckBox)recycled:new CheckBox(BubbleLibraryActivity.this);
            JSONObject row=(JSONObject)getItem(index);box.setOnCheckedChangeListener(null);box.setEnabled(true);box.setPadding(12,18,12,18);box.setTextSize(15);
            try {
                BubbleSpec b=JsonCodec.decode(row);box.setText(row.optString("name",b.label())+"\n"+b.label());box.setChecked(row.optBoolean("selected"));
                box.setOnCheckedChangeListener((v,checked)->execute(()->{repo.select(b.key(),checked);row.put("selected",checked);if(selectedOnly)filter();else refresh();}));
                // CheckBox consumes ListView long clicks; wire the same menu directly.
                box.setOnLongClickListener(v->{edit(row);return true;});
            } catch(Exception e){box.setText("无效气泡条目");box.setEnabled(false);}
            return box;
        }
    }
}
