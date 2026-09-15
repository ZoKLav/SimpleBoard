package com.zoeykl.simpleboard;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public final class AutocorrectEngine {
    private final Context context;
    private final WordStore store;
    public AutocorrectEngine(Context c, WordStore s){context=c;store=s;}

    public String correction(String typed){
        if(!Prefs.b(context,"autocorrect",true) || typed==null || typed.length()<2)return null;
        String explicit=store.correctionFor(typed); if(explicit!=null)return preserveCase(typed,explicit);
        if(store.isKnown(typed))return null;
        List<WordStore.Candidate> c=store.closeCandidates(typed,5); if(c.isEmpty())return null;
        WordStore.Candidate best=c.get(0); int len=typed.length();
        int confidence=100-best.distance*28-Math.abs(best.word.length()-len)*5+Math.min(10,best.freq/500);
        int threshold=Prefs.i(context,"autocorrect_confidence",70);
        if(len<=3 && best.distance>1)return null;
        if(confidence<threshold)return null;
        return preserveCase(typed,best.word);
    }

    public List<String> suggestions(String prefix){
        List<String> out=new ArrayList<>(); if(prefix==null||prefix.length()==0)return out;
        String explicit=store.correctionFor(prefix); if(explicit!=null)out.add(preserveCase(prefix,explicit));
        for(WordStore.Candidate c:store.prefix(prefix,5)){String w=preserveCase(prefix,c.word); if(!out.contains(w))out.add(w); if(out.size()>=3)break;}
        if(out.size()<3){for(WordStore.Candidate c:store.closeCandidates(prefix,5)){String w=preserveCase(prefix,c.word); if(!out.contains(w))out.add(w); if(out.size()>=3)break;}}
        return out;
    }

    public void learn(String word){if(Prefs.b(context,"learn_words",true))store.learnWord(word);}
    public void manualCorrection(String from,String to){if(from!=null&&to!=null&&!from.equalsIgnoreCase(to)){store.addCorrection(from,to);store.addWord(to);}}
    private static String preserveCase(String source,String dest){if(source.length()>0&&Character.isUpperCase(source.charAt(0))&&dest.length()>0)return Character.toUpperCase(dest.charAt(0))+dest.substring(1);return dest;}
}
