package com.zoeykl.simpleboard;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read only the app's fixed subfolder after a one-time grant to the exact VoiceModels folder. */
final class SharedVoiceFolder {
    private static final String PREF="voice_shared_folder";
    private static volatile boolean busy,cancelled;
    private static volatile int status=R.string.folder_intro;
    private static volatile int done;
    static boolean busy(){return busy;}
    static int status(){return status;}
    static int count(){return done;}
    static boolean configured(Context c){return !Prefs.s(c,PREF,"").isEmpty();}
    static synchronized void cancel(){cancelled=true;}
    static synchronized void configure(Context c,Uri tree){
        if(busy)return;
        begin();Context app=c.getApplicationContext();
        new Thread(()->{
            try{
                if(!"com.android.externalstorage.documents".equals(tree.getAuthority())||!"primary:Documents/SimpleBoard/VoiceModels".equals(DocumentsContract.getTreeDocumentId(tree)))
                    throw new IOException("Select the dedicated VoiceModels folder");
                Uri models=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
                Prefs.p(app).edit().putString(PREF,models.toString()).apply();status=R.string.folder_ready;
            }catch(Exception e){status=R.string.folder_setup_error;}finally{busy=false;}
        },"SimpleBoard-folder-setup").start();
    }
    private static void begin(){busy=true;cancelled=false;done=0;status=R.string.folder_working;}
    static synchronized void scan(Context c){
        if(busy||!configured(c))return;
        begin();Context app=c.getApplicationContext();
        new Thread(()->{
            File staging=new File(SpeechModels.root(app),"whisper-staging");
            try{
                Uri folder=Uri.parse(Prefs.s(app,PREF,""));
                Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(folder,DocumentsContract.getDocumentId(folder));
                Map<String,Uri> files=new HashMap<>();
                try(Cursor cursor=app.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
                    if(cursor==null)throw new IOException("Cannot read folder");
                    while(cursor.moveToNext())for(WhisperCatalog.Model model:WhisperCatalog.MODELS)
                        if(model.file.equals(cursor.getString(1)))files.put(model.file,DocumentsContract.buildDocumentUriUsingTree(folder,cursor.getString(0)));
                }
                int found=0;
                for(WhisperCatalog.Model model:WhisperCatalog.MODELS){
                    check();Uri file=files.get(model.file);if(file==null)continue;found++;
                    File target=new File(SpeechModels.root(app),model.id());
                    if(new File(target,"model.bin").length()==model.bytes&&new File(target,"model.json").isFile())continue;
                    if(target.exists())throw new IOException("Incomplete existing model");
                    SpeechModels.deleteTree(staging);if(!staging.mkdirs())throw new IOException("Cannot prepare folder");
                    if(staging.getUsableSpace()<model.bytes+128L*1024*1024)throw new IOException("Insufficient space");
                    MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;byte[] buffer=new byte[65536];
                    status=R.string.model_verifying;
                    try(InputStream in=app.getContentResolver().openInputStream(file);FileOutputStream out=new FileOutputStream(new File(staging,"model.bin"))){
                        if(in==null)throw new IOException("Cannot open model");
                        int n;while((n=in.read(buffer))!=-1){check();total+=n;if(total>model.bytes)throw new IOException("Wrong model size");digest.update(buffer,0,n);out.write(buffer,0,n);}
                        out.getFD().sync();
                    }
                    StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.US,"%02x",b&255));
                    if(total!=model.bytes||!model.sha256.equals(hash.toString()))throw new IOException("Checksum mismatch");
                    JSONObject metadata=new JSONObject().put("name",model.label).put("engine","whisper").put("sha256",model.sha256).put("bytes",model.bytes);
                    try(FileOutputStream out=new FileOutputStream(new File(staging,"model.json"))){out.write(metadata.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
                    check();if(!staging.renameTo(target))throw new IOException("Cannot finish installation");done++;
                }
                status=found==0?R.string.folder_empty:R.string.folder_scanned;
            }catch(Exception|OutOfMemoryError e){status=cancelled?R.string.import_cancelled:R.string.folder_scan_error;}
            finally{try{SpeechModels.deleteTree(staging);}catch(IOException ignored){}busy=false;}
        },"SimpleBoard-model-scan").start();
    }
    private static void check() throws IOException {if(cancelled)throw new InterruptedIOException();}
    private SharedVoiceFolder(){}
}
