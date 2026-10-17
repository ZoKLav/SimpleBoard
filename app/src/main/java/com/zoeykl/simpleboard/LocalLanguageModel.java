package com.zoeykl.simpleboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small offline backoff model. No message history, networking, or external model files. */
public final class LocalLanguageModel {
    public static final String START="<s>";
    private static final double HALF_LIFE_MS=30d*24*60*60*1000;
    private static final Map<String,Map<String,Integer>> BASE=new HashMap<>();
    static {
        // Original hand-written starter examples, not text extracted from private messages.
        String[] phrases={
            "i am on my way", "i will be there soon", "i will let you know", "i will call you later",
            "i will see you tomorrow", "i can do that", "i can help you", "i have a question",
            "i have to go", "i need to check", "i need some help", "i want to see you",
            "i think that is good", "i think so", "i don't know", "i don't think so", "i don't have time",
            "i would like to", "i love you", "i miss you", "i just got home", "i was going to",
            "i'm on my way", "i'm going to be late", "i'm at home", "i'm at work", "i'm not sure",
            "i'm sorry about that", "i'm looking forward to it", "i'll be there soon", "i'll let you know",
            "i'll call you later", "i'll see you soon", "i've been thinking about it",
            "you are welcome", "you can do it", "you have to see this", "you're welcome",
            "we can do that", "we should go", "we will be there", "we need to talk", "we are on our way",
            "can you help me", "can you send me the address", "can you call me", "can we meet tomorrow",
            "could you please send it", "would you like to", "do you want to", "do you have time",
            "did you get my message", "did you see that", "are you on your way", "are you free tonight",
            "are you at home", "are you okay", "what are you doing", "what do you think",
            "what time is it", "what time works for you", "where are you", "where do you want to meet",
            "when will you be home", "when are you coming", "how are you doing", "how was your day",
            "how much is it", "how long will it take", "let me know", "let me check", "let me see",
            "let's do it", "let's meet tomorrow", "thank you so much", "thank you for your help",
            "thanks for letting me know", "thanks for the update", "thanks so much", "thanks again",
            "good morning", "good afternoon", "good evening", "good night", "have a good day",
            "have a great weekend", "have a safe trip", "see you soon", "see you later", "see you tomorrow",
            "talk to you later", "sounds good to me", "that sounds good", "that makes sense",
            "that's a good idea", "that's okay", "that's great", "it's all good", "it's okay",
            "no problem", "no worries", "not right now", "not yet", "of course", "on my way",
            "be right back", "be there soon", "please let me know", "please send me the address",
            "sorry about that", "sorry i'm late", "happy birthday", "happy to help", "hello how are you",
            "hey how is it going", "okay see you soon", "yes that works", "yes please", "yes i can",
            "there is a problem", "there are a few things", "this is the one", "this looks good",
            "it was nice to see you", "it will be fine", "is there anything else", "is that okay",
            "at the moment", "in the morning", "in a few minutes", "for the first time", "as soon as possible"
        };
        for(String phrase:phrases){List<String> previous=new ArrayList<>();for(String word:phrase.split(" ")){
            for(String key:keys(previous.toArray(new String[0])))BASE.computeIfAbsent(key,k->new HashMap<>()).merge(word,1,Integer::sum);
            previous.add(word);if(previous.size()>2)previous.remove(0);
        }}
    }
    public static java.util.Set<String> vocabulary(){java.util.Set<String> words=new java.util.TreeSet<>();for(Map<String,Integer> options:BASE.values())words.addAll(options.keySet());return words;}
    private LocalLanguageModel(){}
    public static String normalize(String word){return word==null?"":word.toLowerCase(Locale.US).replace('\u2019','\'');}
    public static boolean token(String word){return "i".equals(word)||"a".equals(word)||TypingPolicy.learnable(word);}
    /** Only the final two adjacent words; punctuation, digits and line breaks stop context. */
    public static String[] context(String before,String current){
        if(before==null)return new String[0];
        String text=before;
        if(current!=null&&!current.isEmpty()&&text.endsWith(current))text=text.substring(0,text.length()-current.length());
        List<String> result=new ArrayList<>();int end=text.length();
        while(end>0&&result.size()<2){
            while(end>0&&(text.charAt(end-1)==' '||text.charAt(end-1)=='\t'))end--;
            if(end==0)break;
            int start=end;while(start>0&&!Character.isWhitespace(text.charAt(start-1)))start--;
            String word=normalize(text.substring(start,end));if(!token(word))break;
            result.add(0,word);end=start;
            if(end>0&&(text.charAt(end-1)=='\n'||text.charAt(end-1)=='\r'))break;
        }
        return result.toArray(new String[0]);
    }
    public static String[] keys(String[] previous){
        if(previous.length==0)return new String[]{START};
        String last=normalize(previous[previous.length-1]);
        if(previous.length==1)return new String[]{last};
        return new String[]{last,normalize(previous[previous.length-2])+" "+last};
    }
    public static double decay(double score,long updated,long now){
        if(updated<=0)return score; // Existing frequency counts retain their initial weight during migration.
        return score*Math.pow(0.5,Math.max(0d,(double)now-updated-7d*24*60*60*1000)/HALF_LIFE_MS);
    }
    public static Map<String,Double> base(String[] previous,String prefix){
        Map<String,Double> scores=new HashMap<>();String p=normalize(prefix);
        for(String key:keys(previous)){
            Map<String,Integer> options=BASE.get(key);if(options==null)continue;
            double weight=key.indexOf(' ')>=0?2.0:1.0;
            for(Map.Entry<String,Integer> e:options.entrySet())if(e.getKey().startsWith(p))scores.merge(e.getKey(),weight*Math.log1p(e.getValue()),Double::sum);
        }
        return scores;
    }
    public static List<String> ranked(Map<String,Double> scores,int limit){
        List<String> words=new ArrayList<>(scores.keySet());words.sort((a,b)->{int n=Double.compare(scores.get(b),scores.get(a));return n!=0?n:a.compareTo(b);});
        return words.size()>limit?new ArrayList<>(words.subList(0,limit)):words;
    }
}
