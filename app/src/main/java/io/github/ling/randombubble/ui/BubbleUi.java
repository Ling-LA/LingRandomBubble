package io.github.ling.randombubble.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Resource-free widgets shared by the standalone app and QQ's embedded screens. */
public final class BubbleUi {
    public static final int CANVAS=0xFFF6FAF7, PAPER=0xFFFFFFFF, INK=0xFF243F35;
    public static final int MUTED=0xFF5E756A, MINT=0xFF35745F, SOFT=0xFFE3F1E9;
    private BubbleUi() {}
    public static int dp(Context context,int value) { return Math.round(value*context.getResources().getDisplayMetrics().density); }
    public static GradientDrawable surface(Context context,int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(context,radius));return d;
    }
    public static TextView text(Context context,String value,int size,int color,boolean bold) {
        TextView v=new TextView(context);v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return v;
    }
    public static TextView action(Context context,String label,boolean primary,Runnable run) {
        TextView v=new TextView(context) {
            @Override public CharSequence getAccessibilityClassName() { return "android.widget.Button"; }
        };
        v.setText(label);v.setTextSize(13);v.setTextColor(primary?PAPER:INK);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        v.setGravity(Gravity.CENTER);v.setMinHeight(dp(context,44));v.setPadding(dp(context,8),dp(context,8),dp(context,8),dp(context,8));
        StateListDrawable base=new StateListDrawable();base.addState(new int[]{android.R.attr.state_selected},surface(context,SOFT,12));
        base.addState(new int[]{},surface(context,primary?MINT:PAPER,12));
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2435745F),base,null));
        v.setFocusable(true);v.setOnClickListener(view->run.run());return v;
    }
    public static void input(EditText input) {
        Context c=input.getContext();input.setTextSize(15);input.setTextColor(INK);input.setHintTextColor(MUTED);
        input.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));input.setMinHeight(dp(c,48));
        input.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));input.setBackgroundTintList(null);
        StateListDrawable states=new StateListDrawable();
        GradientDrawable focus=surface(c,PAPER,14);focus.setStroke(dp(c,1),MINT);
        GradientDrawable normal=surface(c,PAPER,14);normal.setStroke(dp(c,1),SOFT);
        states.addState(new int[]{android.R.attr.state_focused},focus);states.addState(new int[]{},normal);input.setBackground(states);
    }
    public static EditText search(Context context) {
        EditText v=new EditText(new ContextThemeWrapper(context,android.R.style.Theme_Material_Light));
        v.setSingleLine(true);v.setInputType(android.text.InputType.TYPE_CLASS_TEXT);input(v);
        v.setHint("搜索气泡名称或编号");v.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        Symbol lens=new Symbol(dp(context,18),false);lens.setBounds(0,0,lens.getIntrinsicWidth(),lens.getIntrinsicHeight());
        v.setCompoundDrawablesRelative(lens,null,null,null);v.setCompoundDrawablePadding(dp(context,10));return v;
    }
    public static CheckBox checkBox(Context context) {
        CheckBox box=new CheckBox(new ContextThemeWrapper(context,android.R.style.Theme_Material_Light));
        // QQ can supply oversized themed button drawables. Own the drawable and its intrinsic bounds.
        box.setButtonTintList(null);box.setButtonDrawable(new SquareCheck(dp(context,20)));box.setBackgroundTintList(null);
        box.setTextColor(INK);box.setTextSize(15);box.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        box.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);box.setMinWidth(0);box.setMinHeight(dp(context,60));
        box.setPadding(dp(context,12),dp(context,8),dp(context,12),dp(context,8));box.setCompoundDrawablePadding(dp(context,12));
        box.setBackground(new RippleDrawable(ColorStateList.valueOf(SOFT),surface(context,PAPER,10),null));return box;
    }
    public static void partial(CheckBox box,boolean mixed) {
        box.setActivated(mixed);if(android.os.Build.VERSION.SDK_INT>=30)box.setStateDescription(mixed?"部分勾选":null);
    }
    public static CharSequence rowLabel(String name,String detail) {
        SpannableString text=new SpannableString(name+"\n"+detail);int split=name.length()+1;
        text.setSpan(new StyleSpan(Typeface.BOLD),0,name.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(0.8f),split,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(MUTED),split,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return text;
    }
    public static void libraryHeader(LinearLayout root,String subtitle,Runnable back) {
        Context c=root.getContext();LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView emblem=new ImageView(c);emblem.setImageDrawable(new Symbol(dp(c,38),true));emblem.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(dp(c,38),dp(c,38));icon.setMarginEnd(dp(c,10));row.addView(emblem,icon);
        LinearLayout words=new LinearLayout(c);words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(c,"气泡库",22,INK,true));words.addView(text(c,subtitle,11,MUTED,false));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(action(c,"返回设置",false,back),new LinearLayout.LayoutParams(dp(c,76),-2));root.addView(row);
    }
    /** Native CheckBox semantics, with a 20dp rounded square independent of the host theme. */
    private static final class SquareCheck extends Drawable {
        private final int size;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path tick=new Path();
        private boolean checked,mixed,enabled=true;private int alpha=255;
        SquareCheck(int size){this.size=size;}
        @Override public int getIntrinsicWidth(){return size;}
        @Override public int getIntrinsicHeight(){return size;}
        @Override public boolean isStateful(){return true;}
        @Override protected boolean onStateChange(int[] state){
            boolean next=false,partial=false,active=false;for(int item:state){if(item==android.R.attr.state_checked)next=true;if(item==android.R.attr.state_enabled)active=true;if(item==android.R.attr.state_activated)partial=true;}
            boolean changed=next!=checked||active!=enabled||mixed!=partial;checked=next;enabled=active;mixed=partial;if(changed)invalidateSelf();return changed;
        }
        @Override public void draw(Canvas canvas){
            canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/20f,getBounds().height()/20f);
            paint.setColor(checked||mixed?MINT:MUTED);paint.setAlpha(enabled?alpha:alpha/2);paint.setStyle(checked||mixed?Paint.Style.FILL:Paint.Style.STROKE);paint.setStrokeWidth(1.4f);
            canvas.drawRoundRect(1,1,19,19,4,4,paint);
            if(checked||mixed){paint.setColor(PAPER);paint.setAlpha(enabled?alpha:alpha/2);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
                tick.reset();if(mixed){tick.moveTo(5.5f,10);tick.lineTo(14.5f,10);}else{tick.moveTo(5.2f,10);tick.lineTo(8.4f,13.2f);tick.lineTo(14.7f,6.8f);}canvas.drawPath(tick,paint);}
            canvas.restore();
        }
        @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class Symbol extends Drawable {
        private final int size;private final boolean bubble;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Symbol(int size,boolean bubble){this.size=size;this.bubble=bubble;}
        @Override public int getIntrinsicWidth(){return size;}
        @Override public int getIntrinsicHeight(){return size;}
        @Override public void draw(Canvas c){
            c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/38f,getBounds().height()/38f);
            p.setStyle(Paint.Style.FILL);p.setColor(bubble?SOFT:MUTED);
            if(bubble){c.drawRoundRect(0,0,38,38,13,13,p);p.setColor(MINT);c.drawRoundRect(8,9,30,26,6,6,p);
                Path tail=new Path();tail.moveTo(13,24);tail.lineTo(11,30);tail.lineTo(19,25);c.drawPath(tail,p);p.setColor(PAPER);
                for(int x:new int[]{14,19,24})c.drawCircle(x,17.5f,1.2f,p);
            }else{p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setStrokeCap(Paint.Cap.ROUND);c.drawCircle(16,16,10,p);c.drawLine(24,24,33,33,p);}
            c.restore();
        }
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
