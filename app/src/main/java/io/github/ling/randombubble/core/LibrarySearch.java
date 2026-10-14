package io.github.ling.randombubble.core;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** One query shared by both library screens. Keywords are literal OR matches. */
public final class LibrarySearch {
    private final String[] words;
    public LibrarySearch(String query) {
        Set<String> unique=new LinkedHashSet<>();
        for(String word:normalize(query).split("[\\s\\p{Z},，、;；]+")) if(!word.isEmpty())unique.add(word);
        words=unique.toArray(new String[0]);
    }
    public boolean isEmpty(){return words.length==0;}
    public boolean matches(String... fields) {
        if(isEmpty())return true;
        for(String field:fields) {
            String value=normalize(field);
            for(String word:words)if(value.contains(word))return true;
        }
        return false;
    }
    private static String normalize(String text) {
        return Normalizer.normalize(text==null?"":text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
