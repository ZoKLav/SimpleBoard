package com.zoeykl.simpleboard;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Byte-level BPE matching the pinned SmolLM2 tokenizer; no JNI tokenizer or text cache. */
public final class NeuralTokenizer {
    private final Map<String,Integer> vocabulary=new HashMap<>();
    private final Map<String,Integer> ranks=new HashMap<>();
    private final char[] bytes=new char[256];
    private static final Pattern DIGIT=Pattern.compile("\\p{N}");
    private static final Pattern PART=Pattern.compile("'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+",Pattern.UNICODE_CHARACTER_CLASS);
    public NeuralTokenizer(InputStream stream)throws IOException{
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(stream))){
            if(in.readInt()!=0x53425431)throw new IOException("Tokenizer format");
            int size=in.readInt();if(size!=49152)throw new IOException("Tokenizer vocabulary");
            for(int i=0;i<size;i++)vocabulary.put(read(in),i);
            int count=in.readInt();if(count<0||count>100000)throw new IOException("Tokenizer merges");
            for(int i=0;i<count;i++)ranks.put(read(in)+"\u0000"+read(in),i);
        }
        int extra=0;for(int i=0;i<256;i++){
            boolean direct=(i>=33&&i<=126)||(i>=161&&i<=172)||(i>=174);
            bytes[i]=(char)(direct?i:256+extra++);
        }
    }
    private static String read(DataInputStream in)throws IOException{int n=in.readInt();if(n<0||n>65536)throw new IOException("Tokenizer entry");byte[] b=new byte[n];in.readFully(b);return new String(b,StandardCharsets.UTF_8);}
    public long[] encode(String text,int maxTokens){
        List<Long> ids=new ArrayList<>();Matcher digits=DIGIT.matcher(text);int last=0;
        while(digits.find()){parts(text.substring(last,digits.start()),ids);parts(digits.group(),ids);last=digits.end();}
        parts(text.substring(last),ids);
        if(ids.isEmpty())ids.add(0L);
        int start=Math.max(0,ids.size()-maxTokens);long[] result=new long[ids.size()-start];for(int i=start;i<ids.size();i++)result[i-start]=ids.get(i);return result;
    }
    private void parts(String text,List<Long> ids){Matcher matcher=PART.matcher(text);while(matcher.find()){
        List<String> pieces=new ArrayList<>();for(byte b:matcher.group().getBytes(StandardCharsets.UTF_8))pieces.add(String.valueOf(bytes[b&255]));
        while(pieces.size()>1){
            int best=Integer.MAX_VALUE,at=-1;
            for(int i=0;i<pieces.size()-1;i++){Integer rank=ranks.get(pieces.get(i)+"\u0000"+pieces.get(i+1));if(rank!=null&&rank<best){best=rank;at=i;}}
            if(at<0)break;pieces.set(at,pieces.get(at)+pieces.get(at+1));pieces.remove(at+1);
        }
        for(String piece:pieces){Integer id=vocabulary.get(piece);if(id==null)throw new IllegalStateException("Unknown BPE piece");ids.add(id.longValue());}
    }}
}
