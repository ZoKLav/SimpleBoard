package com.zoeykl.simpleboard;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Private model registry. A lease prevents deleting files while native recognition uses them. */
final class SpeechModels {
    static final String BUILTIN="builtin",PREFERENCE="voice_model_id";
    private static final Map<String,Integer> leases=new HashMap<>();
    static final class Entry {
        final String id,name,engine;
        final File directory;
        Entry(String id,String name,File directory){this(id,name,directory,"vosk");}
        Entry(String id,String name,File directory,String engine){this.id=id;this.name=name;this.directory=directory;this.engine=engine;}
        public String toString(){return name;}
    }
    static File root(Context context){return new File(context.getNoBackupFilesDir(),"imported-speech");}
    static synchronized List<Entry> list(Context context){
        List<Entry> result=new ArrayList<>();result.add(new Entry(BUILTIN,context.getString(R.string.bundled_english),null));
        File[] folders=root(context).listFiles();
        if(folders!=null)for(File folder:folders){
            if(!folder.getName().matches("[a-f0-9-]{36}"))continue;
            File metadata=new File(folder,"model.json");
            if(!metadata.isFile()||metadata.length()>4096)continue;
            try(InputStream stream=new FileInputStream(metadata)){
                byte[] bytes=new byte[(int)metadata.length()];int used=0,n;
                while(used<bytes.length&&(n=stream.read(bytes,used,bytes.length-used))>0)used+=n;
                JSONObject json=new JSONObject(new String(bytes,0,used,StandardCharsets.UTF_8));
                String engine=json.optString("engine","vosk");
                File model=new File(folder,engine.equals("whisper")?"model.bin":"model");
                if(engine.equals("whisper")?model.isFile():new File(model,"am/final.mdl").isFile())
                    result.add(new Entry(folder.getName(),json.getString("name"),model,engine));
            }catch(Exception ignored){}
        }
        return result;
    }
    static synchronized Entry acquire(Context context) throws IOException {
        String wanted=Prefs.s(context,PREFERENCE,BUILTIN);
        for(Entry entry:list(context))if(entry.id.equals(wanted)){
            leases.put(entry.id,leases.getOrDefault(entry.id,0)+1);return entry;
        }
        throw new IOException("Selected voice model is unavailable. Choose a model in Voice settings.");
    }
    static synchronized void release(Entry entry){
        if(entry==null)return;int n=leases.getOrDefault(entry.id,0)-1;
        if(n<=0)leases.remove(entry.id);else leases.put(entry.id,n);
    }
    static synchronized void remove(Context context,Entry entry) throws IOException {
        if(BUILTIN.equals(entry.id))throw new IOException("The bundled model cannot be removed");
        if(leases.getOrDefault(entry.id,0)>0)throw new IOException("Finish or cancel dictation before removing this model");
        if(!entry.id.matches("[a-f0-9-]{36}"))throw new IOException("Invalid model identifier");
        File directory=new File(root(context),entry.id),trash=new File(root(context),"removing-"+entry.id);
        if(directory.exists()&&!directory.renameTo(trash))throw new IOException("Could not remove this model");
        if(Prefs.s(context,PREFERENCE,BUILTIN).equals(entry.id))Prefs.p(context).edit().putString(PREFERENCE,BUILTIN).apply();
        deleteTree(trash);
    }
    static void deleteTree(File file) throws IOException {
        if(!file.exists())return;File[] children=file.listFiles();
        if(children!=null)for(File child:children)deleteTree(child);
        if(!file.delete())throw new IOException("Could not clean up model files");
    }
    private SpeechModels(){}
}
