package com.zoeykl.simpleboard;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AutocorrectEngine {
    private final Context context;
    private final WordStore store;
    public AutocorrectEngine(Context c,WordStore s){context=c;store=s;}
    public String correction(String typed){
        if(!Prefs.b(context,"autocorrect",true)||typed==null)return null;
        String explicit=store.correctionFor(typed);
        if(explicit!=null)return preserveCase(typed,explicit);
        if(store.correctionRejected(typed))return null;
        if(!TypingPolicy.autoEligible(typed)||store.isKnown(typed))return null;
        List<WordStore.Candidate> candidates=store.closeCandidates(typed,3);
        if(candidates.isEmpty())return null;
        WordStore.Candidate best=candidates.get(0);
        // Only an unambiguous single edit; frequency alone cannot resolve a tie.
        if(best.distance!=1 || (candidates.size()>1&&candidates.get(1).distance==1))return null;
        int confidence=best.freq>=1000?90:80;
        if(confidence<Prefs.i(context,"autocorrect_confidence",85))return null;
        return preserveCase(typed,best.word);
    }
    public List<String> suggestions(String typed){return suggestions(typed,new String[0]);}
    public List<String> suggestions(String typed,String[] previous){
        List<String> out=new ArrayList<>();if(typed==null||typed.isEmpty())return out;
        out.add(typed); // Always allow keeping exactly what was typed.
        String explicit=store.correctionFor(typed);if(explicit!=null)add(out,preserveCase(typed,explicit));
        if(Prefs.b(context,"next_word_predictions",true))for(String word:store.predict(previous,typed,5))add(out,preserveCase(typed,word));
        if(out.size()>=3)return out;
        if(!store.isKnown(typed)&&typed.length()>=3){
            for(WordStore.Candidate c:store.closeCandidates(typed,8))if(c.distance==1)add(out,preserveCase(typed,c.word));
        }
        if(out.size()>=3)return out;
        for(WordStore.Candidate c:store.prefix(typed,8))add(out,preserveCase(typed,c.word));
        if(out.size()<3&&typed.length()>=3)for(WordStore.Candidate c:store.closeCandidates(typed,8))add(out,preserveCase(typed,c.word));
        return out;
    }
    private void add(List<String> out,String word){if(out.size()<3&&!out.contains(word))out.add(word);}
    public void learn(String word){learn(word,new String[0],false);}
    public void learn(String word,String[] previous,boolean accepted){
        if(!Prefs.b(context,"learn_words",true))return;
        store.learnWord(word,Prefs.i(context,"learning_repetitions",5),accepted);
        if(Prefs.b(context,"next_word_predictions",true))store.learnContext(previous,word,accepted);
    }
    public List<String> predict(String[] previous,boolean capitalize,boolean caps){
        List<String> out=new ArrayList<>();if(!Prefs.b(context,"next_word_predictions",true))return out;
        for(String word:store.predict(previous,"",3)){
            if(caps)word=word.toUpperCase(Locale.US);
            else if(capitalize&&!word.isEmpty())word=Character.toUpperCase(word.charAt(0))+word.substring(1);
            else if(word.equals("i")||word.startsWith("i'"))word="I"+word.substring(1);
            out.add(word);
        }
        return out;
    }
    public void reject(String word){if(Prefs.b(context,"learn_words",true))store.rejectCorrection(word);}

    private static String preserveCase(String source,String dest){
        if(source.length()>1&&source.equals(source.toUpperCase(Locale.US)))return dest.toUpperCase(Locale.US);
        if(!source.isEmpty()&&Character.isUpperCase(source.charAt(0))&&!dest.isEmpty())return Character.toUpperCase(dest.charAt(0))+dest.substring(1);
        return dest;
    }
}
