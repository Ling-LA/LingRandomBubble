package io.github.ling.randombubble.hook;

import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

/** Binds the shared automatic name queue to this panel; closing UI never loses queued work. */
final class BubbleNameIndexer {
    private final HostRuntime runtime;private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable update;private boolean stopped;private long revision;
    BubbleNameIndexer(Activity activity,Dialog dialog,HostRuntime runtime,String owner,TextView button,Runnable refreshed) {
        this.runtime=runtime;revision=runtime.bridge.libraryRevision();
        update=new Runnable(){public void run(){
            if(stopped || activity.isFinishing() || activity.isDestroyed())return;
            if(!owner.equals(runtime.bridge.accountIdentity())){stop();button.setText("账号已切换，请重新打开");return;}
            if(dialog.isShowing() && runtime.currentActivity()==activity){
                button.setText(runtime.bridge.nameStatus());long next=runtime.bridge.libraryRevision();
                if(next!=revision){revision=next;refreshed.run();}
            }
            main.postDelayed(this,1500);
        }};button.setText(runtime.bridge.nameStatus());main.postDelayed(update,1500);
    }
    void toggle(){runtime.bridge.toggleNames();}
    void stop(){stopped=true;main.removeCallbacks(update);}
}
