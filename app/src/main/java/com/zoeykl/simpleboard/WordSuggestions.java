package com.zoeykl.simpleboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

/** Model-independent completions and opt-in missing-space repair. Never changes editor text. */
public final class WordSuggestions {
    private final NavigableSet<String> lexicon=new TreeSet<>();
    public WordSuggestions(Reader source)throws IOException {
        try(BufferedReader reader=new BufferedReader(source)){
            String line;while((line=reader.readLine())!=null){int tab=line.indexOf('\t');String word=tab<0?line:line.substring(0,tab);
                if(TypingPolicy.learnable(word)||word.equals("a")||word.equals("i"))lexicon.add(word);
            }
        }
    }
    public List<String> complete(String input,Map<String,Integer> common,int limit){
        String typed=input.toLowerCase(Locale.US);
        if(typed.isEmpty()||typed.length()>48)return new ArrayList<>();
        Map<String,Integer> matches=new HashMap<>();
        collect(typed,0,matches);
        for(String word:common.keySet())if(word.startsWith(typed))matches.put(word,0);
        // One edit anywhere in the prefix, including an adjacent transposition.
        if(typed.length()>=3){
            for(int i=0;i<=typed.length();i++){
                String left=typed.substring(0,i),right=typed.substring(i);
                if(i<typed.length()){
                    collect(left+right.substring(1),1,matches);
                    if(i+1<typed.length())collect(left+right.charAt(1)+right.charAt(0)+right.substring(2),1,matches);
                }
                for(char c='a';c<='z';c++){
                    collect(left+c+right,1,matches);
                    if(i<typed.length())collect(left+c+right.substring(1),1,matches);
                }
            }
        }
        matches.remove(typed);
        List<String> out=new ArrayList<>(matches.keySet());
        out.sort(Comparator.<String>comparingDouble(w->matches.get(w)*10d-Math.log1p(common.getOrDefault(w,0))+.1*Math.abs(w.length()-typed.length())).thenComparing(w->w));
        return out.size()>limit?new ArrayList<>(out.subList(0,limit)):out;
    }
    private void collect(String prefix,int edits,Map<String,Integer> matches){
        int count=0;for(String word:lexicon.tailSet(prefix,true)){
            if(!word.startsWith(prefix)||count++>=24)break;
            matches.merge(word,edits,Math::min);
        }
    }
    /** Bounded segmentation of the current token using common/personal dictionary words. */
    public static String split(String input,Map<String,Integer> words){
        String text=input.toLowerCase(Locale.US);int n=text.length();
        if(n<6||n>48||words.containsKey(text))return null;
        for(int i=0;i<n;i++)if(!Character.isLetter(text.charAt(i)))return null;
        double[][] costs=new double[n+1][3];String[][] paths=new String[n+1][3];int[][] counts=new int[n+1][3];
        for(double[] row:costs)Arrays.fill(row,Double.POSITIVE_INFINITY);
        costs[0][0]=0;paths[0][0]="";
        for(int start=0;start<n;start++)for(int edits=0;edits<=2;edits++){
            if(paths[start][edits]==null||counts[start][edits]>=6)continue;
            for(Map.Entry<String,Integer> entry:words.entrySet()){
                String word=entry.getKey();if(word.length()>20||word.length()<2&&!word.equals("a")&&!word.equals("i"))continue;
                for(int length=Math.max(1,word.length()-1);length<=word.length()+1&&start+length<=n;length++){
                    int end=start+length;String part=text.substring(start,end);
                    int distance=oneEdit(part,word);if(distance>1||edits+distance>2||distance>0&&length<4)continue;
                    // Repeated-key slips are more likely than changing a letter to another word.
                    boolean repeated=false;
                    if(distance==1&&part.length()==word.length()+1)for(int k=1;k<part.length();k++){
                        if(part.charAt(k)==part.charAt(k-1)&&(part.substring(0,k)+part.substring(k+1)).equals(word)){repeated=true;break;}
                    }
                    double cost=costs[start][edits]+2+distance*(repeated?3:5)+1d/(1+Math.log1p(entry.getValue()));
                    int next=edits+distance;
                    if(cost<costs[end][next]){costs[end][next]=cost;counts[end][next]=counts[start][edits]+1;paths[end][next]=paths[start][edits].isEmpty()?word:paths[start][edits]+" "+word;}
                }
            }
        }
        int best=-1;for(int e=0;e<=2;e++)if(counts[n][e]>=2&&(best<0||costs[n][e]<costs[n][best]))best=e;
        return best<0?null:paths[n][best];
    }
    private static int oneEdit(String a,String b){
        if(a.equals(b))return 0;
        if(Math.abs(a.length()-b.length())>1)return 2;
        int i=0;while(i<a.length()&&i<b.length()&&a.charAt(i)==b.charAt(i))i++;
        if(a.length()==b.length()){
            if(a.substring(i+1).equals(b.substring(i+1)))return 1;
            return i+1<a.length()&&a.charAt(i)==b.charAt(i+1)&&a.charAt(i+1)==b.charAt(i)&&a.substring(i+2).equals(b.substring(i+2))?1:2;
        }
        return a.length()>b.length()?(a.substring(i+1).equals(b.substring(i))?1:2):(a.substring(i).equals(b.substring(i+1))?1:2);
    }
}
