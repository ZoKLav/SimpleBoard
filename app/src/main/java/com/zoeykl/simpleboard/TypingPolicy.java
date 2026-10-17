package com.zoeykl.simpleboard;

/** Pure typing rules, also exercised without an Android runtime. */
public final class TypingPolicy {
    private TypingPolicy() {}
    public static boolean learnable(String s){
        if(s==null||s.length()<2||s.length()>40)return false;
        for(int i=0;i<s.length();i++){char c=s.charAt(i);if(!Character.isLetter(c)&&c!='\'')return false;}
        return Character.isLetter(s.charAt(0))&&Character.isLetter(s.charAt(s.length()-1));
    }
    public static boolean autoEligible(String s){
        return learnable(s)&&s.length()>=4&&s.equals(s.toLowerCase(java.util.Locale.US));
    }
    public static int distance(String a,String b){
        int[][] d=new int[a.length()+1][b.length()+1];
        for(int i=0;i<=a.length();i++)d[i][0]=i;
        for(int j=0;j<=b.length();j++)d[0][j]=j;
        for(int i=1;i<=a.length();i++)for(int j=1;j<=b.length();j++){
            d[i][j]=Math.min(Math.min(d[i-1][j]+1,d[i][j-1]+1),d[i-1][j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
            if(i>1&&j>1&&a.charAt(i-1)==b.charAt(j-2)&&a.charAt(i-2)==b.charAt(j-1))d[i][j]=Math.min(d[i][j],d[i-2][j-2]+1);
        }
        return d[a.length()][b.length()];
    }
}
