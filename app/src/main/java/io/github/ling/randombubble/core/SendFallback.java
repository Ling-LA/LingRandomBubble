package io.github.ling.randombubble.core;
/** Briefly bypass failed decoration attempts; never authorizes a send or retains draft data. */
public final class SendFallback {
    private String owner="",generation="";private long started=-1;
    public synchronized boolean active(String account,String config,long now){return started>=0 && owner.equals(account) && generation.equals(config) && now>=started && now-started<60000L;}
    /** True only for the first failure in this account/config cooldown; bounds notifications too. */
    public synchronized boolean fail(String account,String config,long now){
        if(active(account,config,now))return false;
        owner=account;generation=config;started=now;return true;
    }
}
