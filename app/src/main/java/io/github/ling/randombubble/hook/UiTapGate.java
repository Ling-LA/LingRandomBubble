package io.github.ling.randombubble.hook;

import android.app.Activity;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Observes but NEVER replaces listeners, consumes touch events, or edits the input.
 * Conservative v0.1: Chinese/English visible Send buttons; hardware Enter is excluded.
 */
final class UiTapGate {
    private final HostRuntime runtime;
    private WeakReference<View> downButton=new WeakReference<>(null);
    private WeakReference<Activity> downActivity=new WeakReference<>(null);
    private long downAt;
    private float downX,downY;
    UiTapGate(HostRuntime runtime) { this.runtime=runtime; }
    void event(Activity activity,MotionEvent event) {
        if(activity==null || event==null) return;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_POINTER_DOWN) { reset(); runtime.permit.clear(); return; }
        if(action!=MotionEvent.ACTION_DOWN && action!=MotionEvent.ACTION_UP) return;
        try {
            if(action==MotionEvent.ACTION_DOWN) {
                reset(); runtime.permit.clear();
                if(!runtime.bridge.config.enabled || !runtime.versionSupported || event.getPointerCount()!=1) return;
                View button=hitSend(activity.getWindow().getDecorView(),(int)event.getRawX(),(int)event.getRawY());
                if(button==null) return;
                downButton=new WeakReference<>(button); downActivity=new WeakReference<>(activity);
                downAt=SystemClock.uptimeMillis(); downX=event.getRawX(); downY=event.getRawY();
            } else {
                View button=downButton.get(); Activity started=downActivity.get(); long time=SystemClock.uptimeMillis()-downAt;
                reset();
                if(button==null || started!=activity || time<0 || time>700 || event.getPointerCount()!=1) return;
                float density=activity.getResources().getDisplayMetrics().density;
                if(Math.hypot(event.getRawX()-downX,event.getRawY()-downY)>32*density) return;
                if(hitSend(activity.getWindow().getDecorView(),(int)event.getRawX(),(int)event.getRawY())!=button) return;
                List<EditText> editors=new ArrayList<>(); int[] budget={1200};
                findEditors(activity.getWindow().getDecorView(),editors,budget,0);
                if(editors.size()!=1) return;
                String text=editors.get(0).getText().toString();
                runtime.arm(text);
            }
        } catch(Throwable e) { reset(); runtime.permit.clear(); runtime.setError("发送点击观察："+e.getClass().getSimpleName()); }
    }
    void reset() { downButton.clear(); downActivity.clear(); downAt=0; }
    private static boolean inside(View v,int x,int y) {
        Rect r=new Rect(); return v.isShown() && v.getGlobalVisibleRect(r) && r.contains(x,y);
    }
    private static View hitSend(View root,int x,int y) {
        List<View> path=new ArrayList<>(); int[] budget={1200};
        if(!hitPath(root,x,y,path,budget,0)) return null;
        View labeled=null;
        // Only consider the actual touched branch; no search of unrelated siblings.
        // QQ's clickable ancestor is often the full-width input bar. The compact 发送 label
        // on that same path is still the button the user pressed.
        for(int i=path.size()-1;i>=0;i--) {
            View v=path.get(i);
            boolean marked=(v instanceof TextView && sendLabel(((TextView)v).getText())) || sendLabel(v.getContentDescription());
            if(marked && compact(v)) labeled=v;
            if(labeled!=null && v.isClickable() && v.isEnabled()) return compact(v) ? v : labeled;
        }
        return labeled!=null && labeled.isClickable() ? labeled : null;
    }
    private static boolean compact(View v) {
        float d=v.getResources().getDisplayMetrics().density;
        return v.getHeight()<=180*d && v.getWidth()<=320*d;
    }
    private static boolean sendLabel(CharSequence s) {
        if(s==null) return false;
        String t=s.toString().trim(); return t.equals("发送") || t.equalsIgnoreCase("send");
    }
    private static boolean hitPath(View v,int x,int y,List<View> path,int[] budget,int depth) {
        if(depth>32 || --budget[0]<0 || !inside(v,x,y)) return false;
        path.add(v);
        if(v instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)v;
            for(int i=group.getChildCount()-1;i>=0;i--) {
                View child=group.getChildAt(i);
                if(hitPath(child,x,y,path,budget,depth+1)) break;
            }
        }
        return true;
    }
    private static void findEditors(View v,List<EditText> out,int[] budget,int depth) {
        if(depth>32 || --budget[0]<0 || !v.isShown()) return;
        if(v instanceof EditText) {
            for(Class<?> c=v.getClass();c!=null;c=c.getSuperclass())
                if(c.getName().equals("com.tencent.mobileqq.aio.input.edit.AIOEditText")) { out.add((EditText)v); break; }
        }
        if(v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) findEditors(g.getChildAt(i),out,budget,depth+1);
        }
    }
}
