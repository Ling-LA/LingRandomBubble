package io.github.ling.randombubble.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Single-owner, single-flight metadata queue; time is monotonic and supplied by its caller. */
public final class NameLookupQueue {
    public static final class Attempt {
        public final String owner;public final int id;
        private Attempt(String owner,int id){this.owner=owner;this.id=id;}
    }
    private static final class Entry {long due;int failures;}
    private final Map<Integer,Entry> entries=new LinkedHashMap<>();
    private String owner="";private Attempt active;private long nextAt;
    public String owner(){return owner;}
    public int size(){return entries.size();}
    public boolean inFlight(){return active!=null;}
    public void update(String account,Collection<Integer> missing) {
        if(!owner.equals(account)){owner=account;entries.clear();active=null;nextAt=0;}
        Set<Integer> ids=new LinkedHashSet<>();for(Integer id:missing)if(id!=null && id>0)ids.add(id);
        entries.keySet().retainAll(ids);for(int id:ids)if(!entries.containsKey(id))entries.put(id,new Entry());
        if(active!=null && !entries.containsKey(active.id))active=null;
    }
    public Attempt poll(long now) {
        if(active!=null || now<nextAt)return null;
        for(Map.Entry<Integer,Entry> item:entries.entrySet())if(now>=item.getValue().due){active=new Attempt(owner,item.getKey());return active;}
        return null;
    }
    public boolean finish(Attempt attempt,boolean success,long now) {
        if(attempt==null || attempt!=active || !owner.equals(attempt.owner))return false;
        active=null;Entry entry=entries.get(attempt.id);if(entry==null)return false;
        if(success)entries.remove(attempt.id);
        else{entry.failures=Math.min(4,entry.failures+1);entry.due=now+Math.min(1800000L,300000L<<(entry.failures-1));}
        nextAt=now+(success?2000L:60000L);return true;
    }
    /** Explicit retry preserves an in-flight request and the global minimum spacing. */
    public void retry(long now){for(Entry e:entries.values()){e.due=now;e.failures=0;}nextAt=Math.min(nextAt,now+2000L);}
}
