package io.github.ling.randombubble.core;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/** Weak identity set. Never retain message records/lists or trust their equals(). */
public final class IdentityWeakSet {
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
    private final Set<Key> entries = new HashSet<>();
    private static final class Key extends WeakReference<Object> {
        final int hash;
        Key(Object o, ReferenceQueue<Object> q) { super(o, q); hash = System.identityHashCode(o); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Object a = get(), b = ((Key)o).get();
            return a != null && a == b;
        }
    }
    private void prune() { Key k; while ((k = (Key)queue.poll()) != null) entries.remove(k); }
    public synchronized void add(Object o) {
        if (o == null) return;
        prune(); entries.add(new Key(o, queue));
    }
    public synchronized boolean contains(Object o) {
        if (o == null) return false;
        prune(); return entries.contains(new Key(o, null));
    }
    public synchronized int size() { prune(); return entries.size(); }
}
