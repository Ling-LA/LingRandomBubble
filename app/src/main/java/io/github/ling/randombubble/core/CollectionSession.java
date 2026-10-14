package io.github.ling.randombubble.core;

/** A response belongs to one uninterrupted collection session on one account. */
public final class CollectionSession {
    public static final class Ticket {
        public final String owner;
        private final long epoch;
        private Ticket(String owner,long epoch){this.owner=owner;this.epoch=epoch;}
    }
    private String owner="";
    private boolean enabled;
    private long epoch;
    public synchronized void update(String next,boolean collecting) {
        if(next==null)next="";
        if(!next.equals(owner) || collecting!=enabled){epoch++;owner=next;enabled=collecting;}
    }
    public synchronized Ticket ticket(){return enabled && owner.matches("[1-9]\\d{4,12}")?new Ticket(owner,epoch):null;}
    public synchronized boolean accepts(Ticket ticket){return ticket!=null && enabled && ticket.epoch==epoch && owner.equals(ticket.owner);}
}
