package com.zoeykl.simpleboard;

import java.util.*;
import java.util.regex.*;

/** Offline English grammar and punctuation repairs. All changes are reviewed before insertion. */
final class GrammarTool {
    static final class Result {
        final String text;
        final List<String> changes;
        Result(String text,Set<String> changes){this.text=text;this.changes=new ArrayList<>(changes);}
    }
    private static String replace(String text,String expression,String replacement,String label,Set<String> changes){
        String next=text.replaceAll(expression,replacement);if(!next.equals(text))changes.add(label);return next;
    }
    static Result check(String original){
        Set<String> changes=new LinkedHashSet<>();String text=original;
        Matcher words=Pattern.compile("[\\p{L}]+(?:['\u2019][\\p{L}]+)*").matcher(text);StringBuffer out=new StringBuffer();
        while(words.find()){
            String word=words.group(),replacement=EnglishDefaults.correction(word,true,true);
            if(replacement==null)replacement=word;
            if(Character.isUpperCase(word.charAt(0)))replacement=Character.toUpperCase(replacement.charAt(0))+replacement.substring(1);
            if(word.length()>1&&word.equals(word.toUpperCase(Locale.US)))replacement=replacement.toUpperCase(Locale.US);
            if(!replacement.equals(word))changes.add("Contractions and capitalization");
            words.appendReplacement(out,Matcher.quoteReplacement(replacement));
        }
        words.appendTail(out);text=out.toString();
        text=replace(text,"(?i)\\b(could|should|would|might|must) of\\b","$1 have","Verb phrases",changes);
        text=replace(text,"(?i)\\balot\\b","a lot","Word spacing",changes);
        text=replace(text,"(?i)\\b(the|a|an|and|to|of|in|for|with)\\s+\\1\\b","$1","Repeated words",changes);
        text=replace(text,"\\bI (?:is|are)\\b","I am","Subject and verb agreement",changes);
        text=replace(text,"(?i)\\b(you|we|they) is\\b","$1 are","Subject and verb agreement",changes);
        text=replace(text,"(?i)\\b(he|she|it) are\\b","$1 is","Subject and verb agreement",changes);
        text=replace(text,"(?i)\\b(he|she|it) (?:don't|don\u2019t)\\b","$1 doesn't","Subject and verb agreement",changes);
        text=replace(text,"(?i)\\b(you|we|they) (?:doesn't|doesn\u2019t)\\b","$1 don't","Subject and verb agreement",changes);
        text=replace(text,"(?i)\\b(he|she|it) have\\b","$1 has","Subject and verb agreement",changes);
        text=replace(text,"(?i)\\byour (welcome|right|wrong)\\b","you're $1","Common word confusions",changes);
        text=replace(text,"(?i)\\btheir (is|are|was|were)\\b","there $1","Common word confusions",changes);
        text=replace(text,"(?i)\\ban (university|uniform|unicorn|unit|user|European|one)\\b","a $1","Articles",changes);
        text=replace(text,"(?i)\\ba (hour|honor|honour|honest|heir|apple|egg|idea|orange|umbrella)\\b","an $1","Articles",changes);
        text=replace(text,"[ \\t]+([,.;:!?])","$1","Punctuation spacing",changes);
        text=replace(text,"([,;:!?])(?=[\\p{L}])","$1 ","Punctuation spacing",changes);
        text=replace(text,"(?<=[\\p{L}])\\.(?=[A-Z][a-z])",". ","Punctuation spacing",changes);
        text=replace(text,"[ \\t]{2,}"," ","Repeated spaces",changes);
        StringBuilder capitalized=new StringBuilder(text);boolean sentence=true;
        for(int i=0;i<capitalized.length();i++){
            char c=capitalized.charAt(i);
            if(sentence&&Character.isLetter(c)){capitalized.setCharAt(i,Character.toUpperCase(c));sentence=false;}
            else if(Character.isLetterOrDigit(c))sentence=false;
            if(c=='\n'||"!?".indexOf(c)>=0||c=='.'&&(i+1==capitalized.length()||Character.isWhitespace(capitalized.charAt(i+1))))sentence=true;
        }
        if(!capitalized.toString().equals(text))changes.add("Sentence capitalization");
        return new Result(capitalized.toString(),changes);
    }
    private GrammarTool(){}
}
