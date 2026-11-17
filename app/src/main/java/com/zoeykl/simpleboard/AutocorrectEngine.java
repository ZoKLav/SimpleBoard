package com.zoeykl.simpleboard;

import android.content.Context;
import java.util.*;

public final class AutocorrectEngine {
    private final Context context;
    private WordStore store; // Owned exclusively by the suggestion worker.
    private volatile BuiltinDictionary dictionary;
    private volatile WordStore.Snapshot snapshot=WordStore.Snapshot.EMPTY;
    public AutocorrectEngine(Context c){context=c.getApplicationContext();}
    public void initialize(WordStore s){
        store=s;reload();
        try{dictionary=new BuiltinDictionary(context.getAssets().open("dictionary/english.txt"));}
        catch(java.io.IOException ignored){}
    }
    void reload(){if(store!=null)snapshot=store.snapshot();}
    /** No database, disk, native inference, or locks on the Space/punctuation path. */
    public String correction(String typed){
        if(!Prefs.b(context,"autocorrect",true)||typed==null||typed.isEmpty())return null;
        WordStore.Snapshot data=snapshot;
        String raw=typed.toLowerCase(Locale.US),lower=raw.replace('\u2019','\'');
        String explicit=data.corrections.getOrDefault(raw,data.corrections.get(lower));
        if(explicit!=null)return preserveCase(typed,explicit);
        if(data.rejected.contains(raw)||data.rejected.contains(lower)||data.blocked.contains(raw)||data.blocked.contains(lower))return null;
        String standard=EnglishDefaults.correction(typed,Prefs.b(context,"correct_contractions",true),Prefs.b(context,"correct_proper_nouns",true));
        if(standard==null&&Prefs.b(context,"correct_proper_nouns",true))standard=data.display.getOrDefault(raw,data.display.get(lower));
        if(standard!=null&&!data.blocked.contains(standard.toLowerCase(Locale.US)))return preserveCase(typed,standard);
        if(!TypingPolicy.autoEligible(typed)||data.words.containsKey(lower)||(dictionary!=null&&dictionary.contains(lower)))return null;
        Map<String,Integer> candidates=new HashMap<>();
        for(String edit:singleEdits(lower)){
            Integer frequency=data.words.get(edit);
            if(frequency!=null&&!data.blocked.contains(edit))candidates.put(edit,frequency);
        }
        if(candidates.size()!=1)return null;
        String best=candidates.keySet().iterator().next();
        if((candidates.get(best)>=1000?90:80)<Prefs.i(context,"autocorrect_confidence",85))return null;
        return preserveCase(typed,display(best));
    }
    private static Set<String> singleEdits(String word){
        Set<String> out=new HashSet<>();
        for(int i=0;i<=word.length();i++){
            String left=word.substring(0,i),right=word.substring(i);
            if(i<word.length()){
                out.add(left+right.substring(1));
                if(i+1<word.length())out.add(left+right.charAt(1)+right.charAt(0)+right.substring(2));
            }
            for(char c='a';c<='z';c++){
                out.add(left+c+right);
                if(i<word.length())out.add(left+c+right.substring(1));
            }
        }
        out.remove(word);return out;
    }
    public List<String> suggestions(String typed,String[] previous){
        List<String> out=new ArrayList<>();if(typed==null||typed.isEmpty())return out;
        out.add(typed);
        if(typed.length()>48)return out;
        String correction=correction(typed);if(correction!=null)add(out,correction);
        WordStore.Snapshot data=snapshot;
        String explicit=data.corrections.get(typed.toLowerCase(Locale.US));if(explicit!=null)add(out,preserveCase(typed,explicit));
        if(store!=null){
            if(Prefs.b(context,"next_word_predictions",true))for(String word:store.predict(previous,typed,5))add(out,preserveCase(typed,display(word)));
            for(WordStore.Candidate candidate:store.prefix(typed,8))add(out,preserveCase(typed,display(candidate.word)));
        }
        if(out.size()<3&&typed.length()>=3){
            List<String> near=new ArrayList<>();
            for(String edit:singleEdits(typed.toLowerCase(Locale.US)))if(data.words.containsKey(edit))near.add(edit);
            near.sort((a,b)->Integer.compare(data.words.get(b),data.words.get(a)));
            for(String word:near)if(!data.blocked.contains(word))add(out,preserveCase(typed,display(word)));
        }
        if(typed.length()>=6&&typed.length()<=48&&!data.words.containsKey(typed.toLowerCase(Locale.US))&&out.size()<3){
            String split=WordSuggestions.split(typed,data.words);if(split!=null)add(out,preserveCase(typed,split));
        }
        if(out.size()<3&&dictionary!=null)for(String word:dictionary.complete(typed.toLowerCase(Locale.US),24))
            if(!data.blocked.contains(word))add(out,preserveCase(typed,display(word)));
        return out;
    }
    private static void add(List<String> out,String word){if(out.size()<3&&!out.contains(word))out.add(word);}
    public void learn(String word,String[] previous,boolean accepted){
        if(store==null||!Prefs.b(context,"learn_words",true))return;
        String learned=previous.length==0?word.toLowerCase(Locale.US):word;
        store.learnWord(learned,Prefs.i(context,"learning_repetitions",5),accepted);
        if(Prefs.b(context,"next_word_predictions",true))store.learnContext(previous,word,accepted);
        reload();
    }
    public List<String> predict(String[] previous,boolean capitalize,boolean caps){
        List<String> out=new ArrayList<>();if(store==null||!Prefs.b(context,"next_word_predictions",true))return out;
        for(String word:store.predict(previous,"",3)){
            word=display(word);
            if(caps)word=word.toUpperCase(Locale.US);
            else if(capitalize&&!word.isEmpty())word=Character.toUpperCase(word.charAt(0))+word.substring(1);
            else if(word.equals("i")||word.startsWith("i'"))word="I"+word.substring(1);
            out.add(word);
        }
        return out;
    }
    public void reject(String word){if(store!=null&&Prefs.b(context,"learn_words",true)){store.rejectCorrection(word);reload();}}
    String display(String word){return snapshot.display.getOrDefault(word.toLowerCase(Locale.US),EnglishDefaults.display(word));}
    private static String preserveCase(String source,String dest){
        if(source.length()>1&&source.equals(source.toUpperCase(Locale.US)))return dest.toUpperCase(Locale.US);
        if(!source.isEmpty()&&Character.isUpperCase(source.charAt(0))&&!dest.isEmpty()&&dest.equals(dest.toLowerCase(Locale.US)))return Character.toUpperCase(dest.charAt(0))+dest.substring(1);
        return dest;
    }
}
