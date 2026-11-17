package com.zoeykl.simpleboard;

import java.io.*;
import java.util.*;

/** Sorted public-domain word list. Loaded on the worker, never scanned on a keypress. */
final class BuiltinDictionary {
    private final String[] words;
    BuiltinDictionary(InputStream source)throws IOException{
        List<String> entries=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(source,java.nio.charset.StandardCharsets.UTF_8))){
            String line;while((line=reader.readLine())!=null)if(!line.isEmpty())entries.add(line);
        }
        words=entries.toArray(new String[0]);Arrays.sort(words);
    }
    boolean contains(String word){return Arrays.binarySearch(words,word)>=0;}
    List<String> complete(String prefix,int limit){
        List<String> out=new ArrayList<>();int index=Arrays.binarySearch(words,prefix);if(index<0)index=-index-1;
        while(index<words.length&&out.size()<limit&&words[index].startsWith(prefix)){
            String word=words[index++];if(!word.equals(prefix))out.add(word);
        }
        return out;
    }
}
