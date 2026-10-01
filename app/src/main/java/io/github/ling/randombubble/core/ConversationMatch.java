package io.github.ling.randombubble.core;

/** Deduplicates wrapper observations only when their actual conversation identity agrees. */
public final class ConversationMatch<T> {
    private T first;
    private Object fragment,root;
    private int type;
    private String peer,guild;
    private boolean ambiguous;
    public void add(T candidate,Object fragment,Object root,int type,String peer,String guild) {
        if(candidate==null || fragment==null || root==null || peer==null || guild==null) {ambiguous=true;return;}
        if(first==null) {
            first=candidate;this.fragment=fragment;this.root=root;this.type=type;this.peer=peer;this.guild=guild;
        } else if(!same(this.fragment,this.root,this.type,this.peer,this.guild,fragment,root,type,peer,guild))ambiguous=true;
    }
    /** Returning the first equivalent observation never chooses between distinct conversations. */
    public T unique() {return ambiguous?null:first;}
    public static boolean same(Object fragment,Object root,int type,String peer,String guild,
                               Object otherFragment,Object otherRoot,int otherType,String otherPeer,String otherGuild) {
        return fragment!=null && root!=null && peer!=null && guild!=null
            && otherFragment!=null && otherRoot!=null && otherPeer!=null && otherGuild!=null
            && fragment==otherFragment && root==otherRoot && type==otherType
            && peer.equals(otherPeer) && guild.equals(otherGuild);
    }
}
