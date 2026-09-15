package com.zoeykl.simpleboard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WordStore extends SQLiteOpenHelper {
    private static final String DB="words.db";
    private static final int VERSION=1;
    public WordStore(Context c){ super(c,DB,null,VERSION); }

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE words(word TEXT PRIMARY KEY COLLATE NOCASE, freq INTEGER NOT NULL DEFAULT 1, user INTEGER NOT NULL DEFAULT 0, blocked INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE corrections(bad TEXT PRIMARY KEY COLLATE NOCASE, good TEXT NOT NULL, hits INTEGER NOT NULL DEFAULT 1)");
        seed(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){}

    private void seed(SQLiteDatabase db){
        String[] common=("the of and to a in is you that it he was for on are as with his they I at be this have from or one had by word but not what all were we when your can said there use an each which she do how their if will up other about out many then them these so some her would make like him into time has look two more write go see number no way could people my than first water been call who oil its now find long down day did get come made may part over new sound take only little work know place year live me back give most very after thing our just name good sentence man think say great where help through much before line right too mean old any same tell boy follow came want show also around form three small set put end does another well large must big even such because turn here why ask went men read need land different home us move try kind hand picture again change off play spell air away animal house point page letter mother answer found study still learn should America world high every near add food between own below country plant last school father keep tree never start city earth eye light thought head under story saw left don't few while along might close something seem next hard open example begin life always those both paper together got group often run important until children side feet car mile night walk white sea began grow took river four carry state once book hear stop without second later miss idea enough eat face watch far Indian real almost let above girl sometimes mountain cut young talk soon list song being leave family it's body music color stand sun questions fish area mark dog horse birds problem complete room knew since ever piece told usually didn't friends easy heard order red door sure become top ship across today during short better best however low hours black products happened whole measure remember early waves reached listen wind rock space covered fast several hold himself toward five step morning passed vowel true hundred against pattern numeral table north slowly money map farm pulled draw voice seen cold cried plan notice south sing war ground fall king town I'll unit figure certain field travel wood fire upon done English road half ten fly gave box finally wait correct oh quickly person became shown minutes strong verb stars front feel fact inches street decided contain course surface produce building ocean class note nothing rest carefully scientists inside wheels stay green known island week less machine base ago stood plane system behind ran round boat game force brought understand warm common bring explain dry though language shape deep thousands yes clear equation yet government filled heat full hot check object am rule among noun power cannot able six size dark ball material special heavy fine pair circle include built can't matter square syllables perhaps bill felt suddenly test direction center farmers ready anything divided general energy subject Europe moon region return believe dance members picked simple cells paint mind love cause rain exercise eggs train blue wish drop developed window difference distance heart sit sum summer wall forest probably legs sat main winter wide written length reason kept interest arms brother race present beautiful store job edge past sign record finished discovered wild happy beside gone sky glass million west lay weather root instruments meet third months paragraph raised represent soft whether clothes flowers shall teacher held describe drive cross speak solve appear metal son either ice sleep village factors result jumped snow ride care floor hill pushed baby buy century outside everything tall already instead phrase soil bed copy free hope spring case laughed nation quite type themselves temperature bright lead everyone method section lake iron within dictionary hair age amount scale pounds although per broken moment tiny possible gold milk quiet natural lot stone act build middle speed count consonant someone sail rolled bear wonder smiled angle fraction Africa killed melody bottom trip hole poor let's fight surprise French died beat exactly remain dress cat couldn't fingers row least catch climbed wrote shouted continued itself else plains gas England burning design joined foot law ears grass you're grew skin valley cents key president brown trouble cool cloud lost sent symbols wear bad save experiment engine alone drawing east choose single touch information express mouth yard equal decimal yourself control practice report straight rise statement stick party seeds suppose woman coast bank period wire pay clean visit bit whose received garden please strange caught fell team God captain direct ring serve child desert increase history cost maybe business separate break uncle hunting flow lady students human art feeling supply corner electric insects crops tone hit sand doctor provide thus won't cook bones tail board modern compound mine wasn't fit addition belong safe soldiers guess silent trade rather compare crowd poem enjoy elements indicate except expect flat seven interesting sense string blow famous value wings movement pole exciting branches thick blood lie spot bell fun loud consider suggested thin position entered fruit tied rich dollars send sight chief Japanese stream planets rhythm eight science major observe tube necessary weight meat lifted process process army hat property particular swim terms current park sell shoulder industry wash block spread cattle wife sharp company radio we'll action capital factories settled yellow isn't southern truck fair printed wouldn't ahead chance born level triangle molecules France repeated column western church sister oxygen plural various agreed opposite wrong chart prepared pretty solution fresh shop suffix especially shoes actually nose afraid dead sugar adjective fig office huge gun similar death score forward stretched experience rose allow fear workers Washington Greek women bought led march northern create British difficult match win doesn't steel total deal determine evening Norway rope cotton apple details entire corn substances smell tools conditions cows track arrived located sir seat division effect underline view silent success company subtract event particular deal swim terms opposite wife shoe shoulder spread arrange camp invent cotton born determine quart nine noise level chance gather shop stretch throw shine property column molecule select wrong gray repeat require broad prepare salt nose plural anger claim continent oxygen sugar death pretty skill women season solution magnet silver thank branch match suffix especially fig afraid huge sister steel discuss forward similar guide experience score apple bought pitch coat mass card band rope slip win dream evening condition feed tool total basic smell valley nor double seat continue block chart hat sell success action capital maybe nice send woman current practice straight allow quite enough").split(" ");
        db.beginTransaction();
        try{
            int f=5000;
            for(String w:common){ if(w.length()==0)continue; ContentValues v=new ContentValues(); v.put("word",w.toLowerCase(Locale.US)); v.put("freq",Math.max(1,f--)); db.insertWithOnConflict("words",null,v,SQLiteDatabase.CONFLICT_IGNORE); }
            db.setTransactionSuccessful();
        }finally{ db.endTransaction(); }
    }

    public boolean isKnown(String word){
        Cursor c=getReadableDatabase().rawQuery("SELECT blocked FROM words WHERE word=?",new String[]{norm(word)});
        try{return c.moveToFirst() && c.getInt(0)==0;}finally{c.close();}
    }
    public void learnWord(String word){
        String w=norm(word); if(w.length()<2)return;
        SQLiteDatabase db=getWritableDatabase();
        Cursor c=db.rawQuery("SELECT freq FROM words WHERE word=?",new String[]{w});
        try{
            if(c.moveToFirst()){ContentValues v=new ContentValues();v.put("freq",c.getInt(0)+1);v.put("user",1);v.put("blocked",0);db.update("words",v,"word=?",new String[]{w});}
            else{ContentValues v=new ContentValues();v.put("word",w);v.put("freq",2);v.put("user",1);v.put("blocked",0);db.insert("words",null,v);}
        }finally{c.close();}
    }
    public void addWord(String word){
        String w=norm(word);if(w.length()==0)return;SQLiteDatabase db=getWritableDatabase();Cursor c=db.rawQuery("SELECT freq FROM words WHERE word=?",new String[]{w});
        try{ContentValues v=new ContentValues();v.put("user",1);v.put("blocked",0);if(c.moveToFirst()){v.put("freq",Math.max(100,c.getInt(0)));db.update("words",v,"word=?",new String[]{w});}else{v.put("word",w);v.put("freq",100);db.insert("words",null,v);}}finally{c.close();}
    }
    public void deleteWord(String word){ getWritableDatabase().delete("words","word=?",new String[]{norm(word)}); }
    public void blockWord(String word){ String w=norm(word);if(w.length()==0)return;SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("blocked",1);v.put("user",1);int n=db.update("words",v,"word=?",new String[]{w});if(n==0){v.put("word",w);v.put("freq",1);db.insert("words",null,v);} }

    public void addCorrection(String bad,String good){
        String b=norm(bad),g=good==null?"":good.trim();if(b.length()==0||g.length()==0)return;SQLiteDatabase db=getWritableDatabase();Cursor c=db.rawQuery("SELECT hits FROM corrections WHERE bad=?",new String[]{b});
        try{ContentValues v=new ContentValues();v.put("good",g);if(c.moveToFirst()){v.put("hits",c.getInt(0)+1);db.update("corrections",v,"bad=?",new String[]{b});}else{v.put("bad",b);v.put("hits",1);db.insert("corrections",null,v);}}finally{c.close();}
    }
    public String correctionFor(String bad){ Cursor c=getReadableDatabase().rawQuery("SELECT good FROM corrections WHERE bad=?",new String[]{norm(bad)}); try{return c.moveToFirst()?c.getString(0):null;}finally{c.close();} }
    public void deleteCorrection(String bad){ getWritableDatabase().delete("corrections","bad=?",new String[]{norm(bad)}); }

    public List<Candidate> prefix(String prefix,int limit){
        List<Candidate> out=new ArrayList<>(); String p=norm(prefix); if(p.length()==0)return out;
        Cursor c=getReadableDatabase().rawQuery("SELECT word,freq FROM words WHERE blocked=0 AND word LIKE ? ORDER BY user DESC,freq DESC LIMIT "+Math.max(1,limit),new String[]{p+"%"});
        try{while(c.moveToNext())out.add(new Candidate(c.getString(0),c.getInt(1),0));}finally{c.close();} return out;
    }
    public List<Candidate> closeCandidates(String input,int limit){
        List<Candidate> out=new ArrayList<>(); String w=norm(input); if(w.length()==0)return out;
        int lo=Math.max(1,w.length()-2),hi=w.length()+2;
        String first=w.substring(0,1);
        Cursor c=getReadableDatabase().rawQuery("SELECT word,freq FROM words WHERE blocked=0 AND length(word) BETWEEN ? AND ? AND (substr(word,1,1)=? OR length(?)<=3) ORDER BY freq DESC LIMIT 500",new String[]{String.valueOf(lo),String.valueOf(hi),first,w});
        try{while(c.moveToNext()){String x=c.getString(0); int d=lev(w,x); if(d<=2)out.add(new Candidate(x,c.getInt(1),d));}}finally{c.close();}
        java.util.Collections.sort(out,(a,b)->{if(a.distance!=b.distance)return a.distance-b.distance; return b.freq-a.freq;});
        if(out.size()>limit)return new ArrayList<>(out.subList(0,limit)); return out;
    }
    public List<String> learned(int limit){ List<String> out=new ArrayList<>(); Cursor c=getReadableDatabase().rawQuery("SELECT word||'  ('||freq||')' FROM words WHERE user=1 ORDER BY freq DESC,word LIMIT "+limit,null); try{while(c.moveToNext())out.add(c.getString(0));}finally{c.close();} return out; }
    public List<String> corrections(int limit){ List<String> out=new ArrayList<>(); Cursor c=getReadableDatabase().rawQuery("SELECT bad||'  →  '||good FROM corrections ORDER BY hits DESC,bad LIMIT "+limit,null); try{while(c.moveToNext())out.add(c.getString(0));}finally{c.close();} return out; }

    public static final class Candidate { public final String word; public final int freq,distance; Candidate(String w,int f,int d){word=w;freq=f;distance=d;} }
    private static String norm(String s){return s==null?"":s.trim().toLowerCase(Locale.US);}
    static int lev(String a,String b){int[] p=new int[b.length()+1],q=new int[b.length()+1]; for(int j=0;j<=b.length();j++)p[j]=j; for(int i=1;i<=a.length();i++){q[0]=i; for(int j=1;j<=b.length();j++){int cost=a.charAt(i-1)==b.charAt(j-1)?0:1;q[j]=Math.min(Math.min(q[j-1]+1,p[j]+1),p[j-1]+cost);}int[]t=p;p=q;q=t;}return p[b.length()];}
}
