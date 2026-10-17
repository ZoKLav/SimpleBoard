package com.zoeykl.simpleboard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WordStore extends SQLiteOpenHelper {
    private static final String DB="words.db";
    private static final int VERSION=3;
    public WordStore(Context c){ this(c,DB); }
    private final Context app;
    private final String legacyName;
    private long lastContextPrune;
    private static final Object MIGRATION_LOCK=new Object();
    WordStore(Context c,String name){
        super(c,name+".encrypted",LearningKey.load(c,name+".encrypted"),null,VERSION,0,
            (db,error) -> {throw new android.database.sqlite.SQLiteException("Encrypted learning database cannot be opened; preserved for recovery");},null,false);
        app=c.getApplicationContext();legacyName=name;
    }
    @Override public void onConfigure(SQLiteDatabase db){
        super.onConfigure(db);
        db.execSQL("PRAGMA temp_store=MEMORY");
        // secure_delete returns a result row, even when assigning its value.
        // SQLCipher rejects result-producing statements passed to execSQL().
        try(Cursor result=db.rawQuery("PRAGMA secure_delete=ON",null)) {
            // Moving the cursor executes the query; merely creating it is lazy.
            if(!result.moveToFirst()||result.getInt(0)!=1)
                throw new android.database.sqlite.SQLiteException("Unable to enable secure deletion");
        }
    }
    @Override public void onOpen(SQLiteDatabase db){
        super.onOpen(db);
        synchronized(MIGRATION_LOCK){migratePlaintext(db);}
    }
    private void migratePlaintext(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS migration_state(id INTEGER PRIMARY KEY)");
        boolean complete;
        try(Cursor marker=db.rawQuery("SELECT id FROM migration_state WHERE id=1",null)){complete=marker.moveToFirst();}
        java.io.File old=app.getDatabasePath(legacyName);
        if(!complete){
            db.beginTransaction();
            try {
                if(old.exists()) {
                    try(android.database.sqlite.SQLiteDatabase source=android.database.sqlite.SQLiteDatabase.openDatabase(old.getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY)) {
                        for(String table:new String[]{"words","corrections","observations","usage","contexts","rejections"}) {
                            try(Cursor present=source.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?",new String[]{table})) {
                                if(!present.moveToFirst())continue;
                            }
                            try(Cursor rows=source.rawQuery("SELECT * FROM "+table,null)) {
                                String[] columns=rows.getColumnNames();
                                while(rows.moveToNext()) {
                                    ContentValues values=new ContentValues();
                                    for(int i=0;i<columns.length;i++)switch(rows.getType(i)) {
                                        case Cursor.FIELD_TYPE_NULL:values.putNull(columns[i]);break;
                                        case Cursor.FIELD_TYPE_INTEGER:values.put(columns[i],rows.getLong(i));break;
                                        case Cursor.FIELD_TYPE_FLOAT:values.put(columns[i],rows.getDouble(i));break;
                                        case Cursor.FIELD_TYPE_BLOB:values.put(columns[i],rows.getBlob(i));break;
                                        default:values.put(columns[i],rows.getString(i));
                                    }
                                    if(table.equals("usage")&&!values.containsKey("score")) {
                                        values.put("score",values.getAsInteger("hits"));values.put("updated",System.currentTimeMillis());
                                    }
                                    if(db.insertWithOnConflict(table,null,values,SQLiteDatabase.CONFLICT_REPLACE)<0)
                                        throw new android.database.sqlite.SQLiteException("Unable to migrate local learning");
                                }
                            }
                        }
                    }
                }
                db.execSQL("INSERT INTO migration_state(id) VALUES(1)");
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }
        // Delete the legacy database and its journal only after the encrypted transaction commits.
        // The durable marker makes retrying after process death safe without reimporting stale counts.
        if((old.exists()||new java.io.File(old+"-wal").exists()||new java.io.File(old+"-shm").exists()||new java.io.File(old+"-journal").exists())&&!app.deleteDatabase(legacyName))
            throw new IllegalStateException("Encrypted migration finished, but old learning data could not be removed");
    }

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE words(word TEXT PRIMARY KEY COLLATE NOCASE, freq INTEGER NOT NULL DEFAULT 1, user INTEGER NOT NULL DEFAULT 0, blocked INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE corrections(bad TEXT PRIMARY KEY COLLATE NOCASE, good TEXT NOT NULL, hits INTEGER NOT NULL DEFAULT 1)");
        createLearning(db);
        createAdaptive(db);
        seed(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){
        if(oldV<2)createLearning(db);
        else if(oldV<3){
            db.execSQL("ALTER TABLE usage ADD COLUMN score REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE usage ADD COLUMN updated INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE observations ADD COLUMN accepted INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE usage SET score=hits,updated=?",new Object[]{System.currentTimeMillis()});
        }
        if(oldV<3){createAdaptive(db);seed(db);}
    }
    private void createLearning(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS observations(word TEXT PRIMARY KEY COLLATE NOCASE, hits INTEGER NOT NULL DEFAULT 1, accepted INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS usage(word TEXT PRIMARY KEY COLLATE NOCASE, hits INTEGER NOT NULL DEFAULT 1, score REAL NOT NULL DEFAULT 0, updated INTEGER NOT NULL DEFAULT 0)");
    }
    private void createAdaptive(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS contexts(context TEXT NOT NULL, word TEXT NOT NULL, score REAL NOT NULL, updated INTEGER NOT NULL, PRIMARY KEY(context,word))");
        db.execSQL("CREATE TABLE IF NOT EXISTS rejections(bad TEXT PRIMARY KEY COLLATE NOCASE, hits INTEGER NOT NULL DEFAULT 1, updated INTEGER NOT NULL)");
    }
    public void clearPersonalData(){
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{db.delete("words",null,null);db.delete("corrections",null,null);db.delete("observations",null,null);db.delete("usage",null,null);db.delete("contexts",null,null);db.delete("rejections",null,null);seed(db);db.setTransactionSuccessful();}
        finally{db.endTransaction();}
    }
    private void count(SQLiteDatabase db,String table,String word){
        db.execSQL("INSERT OR IGNORE INTO "+table+"(word,hits) VALUES(?,0)",new Object[]{word});
        db.execSQL("UPDATE "+table+" SET hits=MIN(hits+1,10000) WHERE word=?",new Object[]{word});
    }


    private void seed(SQLiteDatabase db){
        String[] common=("the of and to a in is you that it he was for on are as with his they I at be this have from or one had by word but not what all were we when your can said there use an each which she do how their if will up other about out many then them these so some her would make like him into time has look two more write go see number no way could people my than first water been call who oil its now find long down day did get come made may part over new sound take only little work know place year live me back give most very after thing our just name good sentence man think say great where help through much before line right too mean old any same tell boy follow came want show also around form three small set put end does another well large must big even such because turn here why ask went men read need land different home us move try kind hand picture again change off play spell air away animal house point page letter mother answer found study still learn should America world high every near add food between own below country plant last school father keep tree never start city earth eye light thought head under story saw left don't few while along might close something seem next hard open example begin life always those both paper together got group often run important until children side feet car mile night walk white sea began grow took river four carry state once book hear stop without second later miss idea enough eat face watch far Indian real almost let above girl sometimes mountain cut young talk soon list song being leave family it's body music color stand sun questions fish area mark dog horse birds problem complete room knew since ever piece told usually didn't friends easy heard order red door sure become top ship across today during short better best however low hours black products happened whole measure remember early waves reached listen wind rock space covered fast several hold himself toward five step morning passed vowel true hundred against pattern numeral table north slowly money map farm pulled draw voice seen cold cried plan notice south sing war ground fall king town I'll unit figure certain field travel wood fire upon done English road half ten fly gave box finally wait correct oh quickly person became shown minutes strong verb stars front feel fact inches street decided contain course surface produce building ocean class note nothing rest carefully scientists inside wheels stay green known island week less machine base ago stood plane system behind ran round boat game force brought understand warm common bring explain dry though language shape deep thousands yes clear equation yet government filled heat full hot check object am rule among noun power cannot able six size dark ball material special heavy fine pair circle include built can't matter square syllables perhaps bill felt suddenly test direction center farmers ready anything divided general energy subject Europe moon region return believe dance members picked simple cells paint mind love cause rain exercise eggs train blue wish drop developed window difference distance heart sit sum summer wall forest probably legs sat main winter wide written length reason kept interest arms brother race present beautiful store job edge past sign record finished discovered wild happy beside gone sky glass million west lay weather root instruments meet third months paragraph raised represent soft whether clothes flowers shall teacher held describe drive cross speak solve appear metal son either ice sleep village factors result jumped snow ride care floor hill pushed baby buy century outside everything tall already instead phrase soil bed copy free hope spring case laughed nation quite type themselves temperature bright lead everyone method section lake iron within dictionary hair age amount scale pounds although per broken moment tiny possible gold milk quiet natural lot stone act build middle speed count consonant someone sail rolled bear wonder smiled angle fraction Africa killed melody bottom trip hole poor let's fight surprise French died beat exactly remain dress cat couldn't fingers row least catch climbed wrote shouted continued itself else plains gas England burning design joined foot law ears grass you're grew skin valley cents key president brown trouble cool cloud lost sent symbols wear bad save experiment engine alone drawing east choose single touch information express mouth yard equal decimal yourself control practice report straight rise statement stick party seeds suppose woman coast bank period wire pay clean visit bit whose received garden please strange caught fell team God captain direct ring serve child desert increase history cost maybe business separate break uncle hunting flow lady students human art feeling supply corner electric insects crops tone hit sand doctor provide thus won't cook bones tail board modern compound mine wasn't fit addition belong safe soldiers guess silent trade rather compare crowd poem enjoy elements indicate except expect flat seven interesting sense string blow famous value wings movement pole exciting branches thick blood lie spot bell fun loud consider suggested thin position entered fruit tied rich dollars send sight chief Japanese stream planets rhythm eight science major observe tube necessary weight meat lifted process process army hat property particular swim terms current park sell shoulder industry wash block spread cattle wife sharp company radio we'll action capital factories settled yellow isn't southern truck fair printed wouldn't ahead chance born level triangle molecules France repeated column western church sister oxygen plural various agreed opposite wrong chart prepared pretty solution fresh shop suffix especially shoes actually nose afraid dead sugar adjective fig office huge gun similar death score forward stretched experience rose allow fear workers Washington Greek women bought led march northern create British difficult match win doesn't steel total deal determine evening Norway rope cotton apple details entire corn substances smell tools conditions cows track arrived located sir seat division effect underline view silent success company subtract event particular deal swim terms opposite wife shoe shoulder spread arrange camp invent cotton born determine quart nine noise level chance gather shop stretch throw shine property column molecule select wrong gray repeat require broad prepare salt nose plural anger claim continent oxygen sugar death pretty skill women season solution magnet silver thank branch match suffix especially fig afraid huge sister steel discuss forward similar guide experience score apple bought pitch coat mass card band rope slip win dream evening condition feed tool total basic smell valley nor double seat continue block chart hat sell success action capital maybe nice send woman current practice straight allow quite enough").split(" ");
        String[] everyday=("hello hey hi thanks thank okay ok sorry welcome goodbye morning tonight tomorrow yesterday later weekend please awesome amazing really definitely probably actually absolutely literally honestly hopefully unfortunately congratulations birthday appointment meeting message text email phone keyboard typing autocorrect suggestion suggestions settings privacy offline online local microphone permission voice speech cancel delete remove reset update download install application app password address website internet browser computer laptop screen button tap swipe cursor clipboard copied paste undo again backspace return space period comma question exclamation quote apostrophe contraction contractions groceries dinner lunch breakfast coffee tea restaurant hungry tired busy available ready soon already almost usually sometimes always never maybe sure yeah yep nope nah hello friend friends partner husband wife family mom dad sister brother kids baby everyone somebody nobody anybody somewhere nowhere anywhere everywhere anything nothing everything something myself yourself ourselves themselves itself herself himself can't don't didn't won't wouldn't couldn't shouldn't haven't hasn't hadn't isn't aren't wasn't weren't doesn't you're they're we're we've you've they've that's there's here's what's who's how's where's when's why's let's I'll you'll we'll they'll wasn't hasn't doesn't wouldn't shouldn't couldn't I'm I've I'd I'll minutes seconds tomorrow afternoon evening calendar reminder location directions driving walking running working waiting coming going getting doing having looking feeling talking thinking trying watching listening reading writing learning studying shopping cooking cleaning sleeping sitting standing bringing taking making finding keeping leaving calling texting sending received receiving receiving appointment scheduled reschedule confirm confirmation important urgent emergency safe safety home house street apartment building office school hospital station store market parking vehicle car truck bike train bus airport flight travel arrived arriving arrival departure delayed early late today tomorrow yesterday project document file folder photo picture video music audio image camera microphone battery charging charger connection connected disconnected Bluetooth wifi signal network mobile notification notifications volume brightness dark light mode portrait landscape language English Spanish French German Italian Portuguese Japanese Korean Chinese Arabic Russian accent accents punctuation capitalize lowercase uppercase dictionary vocabulary personalized learning frequency repeat repeated mistyped typo typos correction corrections completion prediction predictions useful usability simple private secure protected blocked allowed enabled disabled true false option options select selection selected edit edited editing save saved saving search searching found help helpful support supported unavailable available error errors failed successful success test testing tested build version release debug signed unsigned code source development developer software hardware system device devices Android Google board phone tablet Samsung Pixel Zoey").split(" ");
        java.util.List<String> all=new java.util.ArrayList<>(java.util.Arrays.asList(common));
        all.addAll(java.util.Arrays.asList(everyday));
        all.addAll(LocalLanguageModel.vocabulary());
        db.beginTransaction();
        try{
            int f=5000;
            for(String w:all){ if(w.length()==0)continue; ContentValues v=new ContentValues(); v.put("word",w.toLowerCase(Locale.US)); v.put("freq",Math.max(1,f--)); db.insertWithOnConflict("words",null,v,SQLiteDatabase.CONFLICT_IGNORE); }
            db.setTransactionSuccessful();
        }finally{ db.endTransaction(); }
    }

    public boolean isKnown(String word){
        Cursor c=getReadableDatabase().rawQuery("SELECT blocked FROM words WHERE word=?",new String[]{norm(word)});
        try{return c.moveToFirst() && c.getInt(0)==0;}finally{c.close();}
    }
    public void learnWord(String word,int threshold){learnWord(word,threshold,false);}
    public void learnWord(String word,int threshold,boolean accepted){
        String w=norm(word); if(!TypingPolicy.learnable(w))return;
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            Cursor existing=db.rawQuery("SELECT blocked FROM words WHERE word=?",new String[]{w});
            boolean known=false,blocked=false;
            try{if(existing.moveToFirst()){known=true;blocked=existing.getInt(0)!=0;}}finally{existing.close();}
            if(blocked)return;
            if(known){recordUsage(db,w,accepted?2:1);}
            else {
                count(db,"observations",w);
                if(accepted)db.execSQL("UPDATE observations SET accepted=accepted+1 WHERE word=?",new Object[]{w});
                Cursor c=db.rawQuery("SELECT hits,accepted FROM observations WHERE word=?",new String[]{w});
                try{if(c.moveToFirst()&&c.getInt(0)>=Math.max(3,c.getInt(1)>=2?(int)Math.ceil(threshold*0.6):threshold)){addWord(w);recordUsage(db,w,accepted?2:1);db.delete("observations","word=?",new String[]{w});}}finally{c.close();}
                db.execSQL("DELETE FROM observations WHERE rowid NOT IN (SELECT rowid FROM observations ORDER BY rowid DESC LIMIT 2000)");
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void addWord(String word){
        String w=norm(word);if(w.length()==0)return;SQLiteDatabase db=getWritableDatabase();Cursor c=db.rawQuery("SELECT freq FROM words WHERE word=?",new String[]{w});
        try{ContentValues v=new ContentValues();v.put("user",1);v.put("blocked",0);if(c.moveToFirst()){v.put("freq",Math.max(100,c.getInt(0)));db.update("words",v,"word=?",new String[]{w});}else{v.put("word",w);v.put("freq",100);db.insert("words",null,v);}}finally{c.close();}
    }
    public void deleteWord(String word){ blockWord(word); }
    public void blockWord(String word){ String w=norm(word);if(w.length()==0)return;SQLiteDatabase db=getWritableDatabase();db.delete("observations","word=?",new String[]{w});db.delete("usage","word=?",new String[]{w});db.delete("contexts","word=? OR context=? OR context LIKE ? OR context LIKE ?",new String[]{w,w,w+" %","% "+w});ContentValues v=new ContentValues();v.put("blocked",1);v.put("user",1);int n=db.update("words",v,"word=?",new String[]{w});if(n==0){v.put("word",w);v.put("freq",1);db.insert("words",null,v);} }

    public void addCorrection(String bad,String good){
        String b=norm(bad),g=good==null?"":good.trim();if(b.length()==0||g.length()==0)return;SQLiteDatabase db=getWritableDatabase();Cursor c=db.rawQuery("SELECT hits FROM corrections WHERE bad=?",new String[]{b});
        try{ContentValues v=new ContentValues();v.put("good",g);if(c.moveToFirst()){v.put("hits",c.getInt(0)+1);db.update("corrections",v,"bad=?",new String[]{b});}else{v.put("bad",b);v.put("hits",1);db.insert("corrections",null,v);}}finally{c.close();}
    }
    public String correctionFor(String bad){ Cursor c=getReadableDatabase().rawQuery("SELECT good FROM corrections WHERE bad=?",new String[]{norm(bad)}); try{return c.moveToFirst()?c.getString(0):null;}finally{c.close();} }
    public void deleteCorrection(String bad){ getWritableDatabase().delete("corrections","bad=?",new String[]{norm(bad)}); }

    public List<Candidate> prefix(String prefix,int limit){
        String p=norm(prefix);List<Candidate> out=new ArrayList<>();if(p.isEmpty())return out;
        Cursor c=getReadableDatabase().rawQuery("SELECT w.word,w.freq,COALESCE(u.score,0),COALESCE(u.updated,0) FROM words w LEFT JOIN usage u ON u.word=w.word WHERE w.blocked=0 AND substr(w.word,1,length(?))=?",new String[]{p,p});
        final java.util.Map<String,Double> scores=new java.util.HashMap<>();long now=System.currentTimeMillis();
        try{while(c.moveToNext()){String word=c.getString(0);out.add(new Candidate(word,c.getInt(1),0));scores.put(word,4*Math.log1p(LocalLanguageModel.decay(c.getDouble(2),c.getLong(3),now))+c.getInt(1)/5000d);}}finally{c.close();}
        out.sort((a,b)->{int n=Double.compare(scores.get(b.word),scores.get(a.word));return n!=0?n:a.word.compareTo(b.word);});
        return out.size()>limit?new ArrayList<>(out.subList(0,limit)):out;
    }
    private void recordUsage(SQLiteDatabase db,String word,double weight){
        long now=System.currentTimeMillis();double score=0;int hits=0;
        Cursor c=db.rawQuery("SELECT hits,score,updated FROM usage WHERE word=?",new String[]{word});
        try{if(c.moveToFirst()){hits=c.getInt(0);score=LocalLanguageModel.decay(c.getDouble(1),c.getLong(2),now);}}finally{c.close();}
        ContentValues values=new ContentValues();values.put("word",word);values.put("hits",Math.min(10000,hits+1));values.put("score",Math.min(1000,score+weight));values.put("updated",now);
        db.insertWithOnConflict("usage",null,values,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public void learnContext(String[] previous,String word,boolean accepted){
        String target=norm(word);if(!LocalLanguageModel.token(target)||!isKnown(target))return;
        for(String part:previous)if(!isKnown(part)||!LocalLanguageModel.token(part))return;
        SQLiteDatabase db=getWritableDatabase();long now=System.currentTimeMillis();
        db.beginTransaction();try{
            for(String key:LocalLanguageModel.keys(previous)){
                Cursor c=db.rawQuery("SELECT score,updated FROM contexts WHERE context=? AND word=?",new String[]{key,target});double score=0;
                try{if(c.moveToFirst())score=LocalLanguageModel.decay(c.getDouble(0),c.getLong(1),now);}finally{c.close();}
                ContentValues values=new ContentValues();values.put("context",key);values.put("word",target);values.put("score",Math.min(1000,score+(accepted?2:1)));values.put("updated",now);
                db.insertWithOnConflict("contexts",null,values,SQLiteDatabase.CONFLICT_REPLACE);
            }
            if(now-lastContextPrune>60L*60*1000){
                db.execSQL("DELETE FROM contexts WHERE updated<? AND rowid NOT IN (SELECT rowid FROM contexts ORDER BY updated DESC,rowid DESC LIMIT 6000)",new Object[]{now-7L*24*60*60*1000});
                lastContextPrune=now;
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public List<String> predict(String[] previous,String prefix,int limit){
        java.util.Map<String,Double> scores=LocalLanguageModel.base(previous,prefix);
        long now=System.currentTimeMillis();String p=norm(prefix);
        for(String key:LocalLanguageModel.keys(previous)){
            Cursor c=getReadableDatabase().rawQuery("SELECT c.word,c.score,c.updated FROM contexts c JOIN words w ON w.word=c.word WHERE c.context=? AND w.blocked=0 AND substr(c.word,1,length(?))=?",new String[]{key,p,p});
            try{while(c.moveToNext()){
                double score=LocalLanguageModel.decay(c.getDouble(1),c.getLong(2),now);
                if(score>=0.1)scores.merge(c.getString(0),(key.indexOf(' ')>=0?8d:4d)*Math.log1p(score),Double::sum);
            }}finally{c.close();}
        }
        for(String word:new ArrayList<>(scores.keySet()))if(!isKnown(word))scores.remove(word);
        if(scores.size()<limit){
            // Back off to personal vocabulary only to fill empty slots; never displace contextual matches.
            java.util.Map<String,Double> fallback=new java.util.HashMap<>();
            Cursor c=getReadableDatabase().rawQuery("SELECT u.word,u.score,u.updated FROM usage u JOIN words w ON w.word=u.word WHERE w.blocked=0 AND substr(u.word,1,length(?))=? ORDER BY u.score DESC LIMIT 100",new String[]{p,p});
            try{while(c.moveToNext())fallback.put(c.getString(0),LocalLanguageModel.decay(c.getDouble(1),c.getLong(2),now));}finally{c.close();}
            for(String word:LocalLanguageModel.ranked(fallback,100)){
                if(scores.size()>=limit)break;
                if(!scores.containsKey(word))scores.put(word,0.01+0.001*Math.log1p(fallback.get(word)));
            }
            for(String word:new String[]{"and","the","to"}){
                if(scores.size()>=limit)break;
                if(word.startsWith(p)&&isKnown(word)&&!scores.containsKey(word))scores.put(word,0.001);
            }
        }
        return LocalLanguageModel.ranked(scores,limit);
    }
    public java.util.Set<String> blockedWords(){
        java.util.Set<String> out=new java.util.HashSet<>();Cursor c=getReadableDatabase().rawQuery("SELECT word FROM words WHERE blocked=1",null);
        try{while(c.moveToNext())out.add(c.getString(0));}finally{c.close();}return out;
    }
    public java.util.Map<String,Double> personalBias(String[] previous,String prefix){
        java.util.Map<String,Double> out=new java.util.HashMap<>();String p=norm(prefix);long now=System.currentTimeMillis();
        for(String key:LocalLanguageModel.keys(previous)){
            Cursor c=getReadableDatabase().rawQuery("SELECT c.word,c.score,c.updated FROM contexts c JOIN words w ON w.word=c.word WHERE c.context=? AND w.blocked=0 AND substr(c.word,1,length(?))=?",new String[]{key,p,p});
            try{while(c.moveToNext())out.merge(c.getString(0),(key.indexOf(' ')>=0?1.2:0.7)*Math.log1p(LocalLanguageModel.decay(c.getDouble(1),c.getLong(2),now)),Double::sum);}finally{c.close();}
        }
        Cursor c=getReadableDatabase().rawQuery("SELECT u.word,u.score,u.updated FROM usage u JOIN words w ON w.word=u.word WHERE w.blocked=0 AND substr(u.word,1,length(?))=? ORDER BY u.score DESC LIMIT 100",new String[]{p,p});
        try{while(c.moveToNext())out.merge(c.getString(0),0.25*Math.log1p(LocalLanguageModel.decay(c.getDouble(1),c.getLong(2),now)),Double::sum);}finally{c.close();}
        return out;
    }
    public void rejectCorrection(String word){
        String w=norm(word);if(!LocalLanguageModel.token(w))return;long now=System.currentTimeMillis();int hits=0;
        Cursor c=getReadableDatabase().rawQuery("SELECT hits,updated FROM rejections WHERE bad=?",new String[]{w});
        try{if(c.moveToFirst()&&now-c.getLong(1)<30L*24*60*60*1000)hits=c.getInt(0);}finally{c.close();}
        ContentValues v=new ContentValues();v.put("bad",w);v.put("hits",Math.min(100,hits+1));v.put("updated",now);
        getWritableDatabase().insertWithOnConflict("rejections",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        getWritableDatabase().execSQL("DELETE FROM rejections WHERE rowid NOT IN (SELECT rowid FROM rejections ORDER BY updated DESC,rowid DESC LIMIT 1000)");
    }
    public boolean correctionRejected(String word){
        Cursor c=getReadableDatabase().rawQuery("SELECT hits,updated FROM rejections WHERE bad=?",new String[]{norm(word)});
        try{return c.moveToFirst()&&c.getInt(0)>=2&&System.currentTimeMillis()-c.getLong(1)<30L*24*60*60*1000;}finally{c.close();}
    }
    public List<Candidate> closeCandidates(String input,int limit){
        List<Candidate> out=new ArrayList<>(); String w=norm(input); if(w.length()==0)return out;
        int lo=Math.max(1,w.length()-2),hi=w.length()+2;
        Cursor c=getReadableDatabase().rawQuery("SELECT word,freq FROM words WHERE blocked=0 AND length(word) BETWEEN ? AND ? ORDER BY freq DESC",new String[]{String.valueOf(lo),String.valueOf(hi)});
        try{while(c.moveToNext()){String x=c.getString(0); int d=lev(w,x); if(d<=2)out.add(new Candidate(x,c.getInt(1),d));}}finally{c.close();}
        java.util.Collections.sort(out,(a,b)->{if(a.distance!=b.distance)return a.distance-b.distance; return b.freq-a.freq;});
        if(out.size()>limit)return new ArrayList<>(out.subList(0,limit)); return out;
    }
    public List<String> learned(int limit){ List<String> out=new ArrayList<>(); Cursor c=getReadableDatabase().rawQuery("SELECT word||'  ('||freq||')' FROM words WHERE user=1 AND blocked=0 ORDER BY freq DESC,word LIMIT "+limit,null); try{while(c.moveToNext())out.add(c.getString(0));}finally{c.close();} return out; }
    public List<String> corrections(int limit){ List<String> out=new ArrayList<>(); Cursor c=getReadableDatabase().rawQuery("SELECT bad||'  →  '||good FROM corrections ORDER BY hits DESC,bad LIMIT "+limit,null); try{while(c.moveToNext())out.add(c.getString(0));}finally{c.close();} return out; }

    public static final class Candidate { public final String word; public final int freq,distance; Candidate(String w,int f,int d){word=w;freq=f;distance=d;} }
    private static String norm(String s){return s==null?"":s.trim().toLowerCase(Locale.US);}
    static int lev(String a,String b){return TypingPolicy.distance(a,b);}
}
