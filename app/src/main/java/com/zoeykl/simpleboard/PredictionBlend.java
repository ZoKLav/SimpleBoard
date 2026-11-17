package com.zoeykl.simpleboard;

import java.util.*;

/** Neural word log-probabilities plus a bounded, local personalization bias. */
public final class PredictionBlend {
    private PredictionBlend(){}
    public static List<String> combine(Map<String,Double> neural,Map<String,Double> personal,Set<String> blocked,String prefix,int limit){
        String p=LocalLanguageModel.normalize(prefix);Map<String,Double> scores=new HashMap<>();
        double best=Double.NEGATIVE_INFINITY;for(double score:neural.values())if(Double.isFinite(score))best=Math.max(best,score);
        if(!Double.isFinite(best))return new ArrayList<>();
        for(Map.Entry<String,Double> entry:neural.entrySet()){
            String word=entry.getKey();if(!word.startsWith(p)||blocked.contains(word)||!Double.isFinite(entry.getValue()))continue;
            scores.put(word,entry.getValue()+Math.min(4,personal.getOrDefault(word,0d)));
        }
        for(Map.Entry<String,Double> entry:personal.entrySet()){
            String word=entry.getKey();if(!word.startsWith(p)||blocked.contains(word)||entry.getValue()<1||scores.containsKey(word))continue;
            scores.put(word,best-3+Math.min(4,entry.getValue()));
        }
        return LocalLanguageModel.ranked(scores,limit);
    }
}
