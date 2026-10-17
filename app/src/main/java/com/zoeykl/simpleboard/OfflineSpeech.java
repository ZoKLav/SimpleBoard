package com.zoeykl.simpleboard;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Selectable offline Vosk ASR. All audio and transcripts remain in memory, never in files or logs. */
final class OfflineSpeech implements AutoCloseable {
    interface Listener {void status(String text);void partial(String text);void result(String text);void error(String text);}
    private static final String ASSET="voice/en-us-0.22-lgraph";
    private static final int RATE=16000;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"SimpleBoard-speech"));
    private volatile Session current;
    private boolean closed;
    private static final class Session {
        final Listener listener;
        volatile boolean cancelled,finish;
        volatile AudioRecord recorder;
        volatile WhisperNative whisper;
        Session(Listener listener){this.listener=listener;}
    }
    OfflineSpeech(Context context){this.context=context.getApplicationContext();}
    void start(Listener listener){
        cancel();if(closed)return;
        Session session=new Session(listener);current=session;worker.execute(()->recognize(session));
    }
    void finish(){Session s=current;if(s!=null)s.finish=true;}
    void cancel(){
        Session s=current;current=null;
        if(s!=null){s.cancelled=true;WhisperNative nativeSession=s.whisper;if(nativeSession!=null)nativeSession.cancel();AudioRecord r=s.recorder;if(r!=null)try{r.stop();}catch(IllegalStateException ignored){}}
    }
    private void post(Session s,Runnable callback){main.post(()->{if(current==s&&!s.cancelled)callback.run();});}
    private void status(Session s,String message){post(s,()->s.listener.status(message));}
    /** Dedicated microphone reader so decoding cannot starve AudioRecord's buffer. */
    private static final class Capture implements AutoCloseable {
        final java.util.concurrent.LinkedBlockingQueue<short[]> queue=new java.util.concurrent.LinkedBlockingQueue<>(1200);
        final AudioRecord recorder;
        final Session session;
        volatile boolean done,stopped;
        volatile String failure;
        final Thread thread;
        Capture(AudioRecord recorder,Session session){
            this.recorder=recorder;this.session=session;
            thread=new Thread(this::read,"SimpleBoard-capture");thread.start();
        }
        private void read(){
            short[] buffer=new short[1600];int captured=0;
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
                while(!stopped&&!session.cancelled&&!session.finish&&captured<RATE*120){
                    int n=recorder.read(buffer,0,Math.min(buffer.length,RATE*120-captured));
                    if(stopped||session.cancelled)break;
                    if(n<0){failure="Microphone read failed ("+n+")";break;}
                    if(n==0)continue;
                    short[] chunk=Arrays.copyOf(buffer,n);captured+=n;
                    if(!queue.offer(chunk)){
                        Arrays.fill(chunk,(short)0);
                        failure="Microphone audio queue filled; try a shorter dictation.";break;
                    }
                }
            }catch(RuntimeException e){if(!stopped&&!session.cancelled)failure="Microphone capture stopped unexpectedly";}
            finally{
                Arrays.fill(buffer,(short)0);
                try{recorder.stop();}catch(IllegalStateException ignored){}
                done=true;
            }
        }
        @Override public void close(){
            stopped=true;
            try{recorder.stop();}catch(IllegalStateException ignored){}
            boolean interrupted=false;
            while(thread.isAlive())try{thread.join();}catch(InterruptedException e){interrupted=true;}
            short[] chunk;while((chunk=queue.poll())!=null)Arrays.fill(chunk,(short)0);
            if(interrupted)Thread.currentThread().interrupt();
        }
    }
    private void recognize(Session s){
        AudioRecord recorder=null;
        Capture capture=null;
        SpeechModels.Entry selected=null;
        try {
            status(s,"Preparing offline speech model…");
            selected=SpeechModels.acquire(context);
            if("whisper".equals(selected.engine)){recognizeWhisper(s,selected.directory);return;}
            File directory=selected.directory==null?install(s):selected.directory;
            if(s.cancelled)return;
            if(s.finish){post(s,()->s.listener.result(""));return;}
            int minimum=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(minimum<=0)throw new IOException("Microphone does not support 16 kHz recording");
            recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(minimum*2,RATE*2));
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Microphone could not be initialized");
            s.recorder=recorder;
            if(s.cancelled)return;
            recorder.startRecording();
            if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("Microphone did not start");
            capture=new Capture(recorder,s);
            status(s,"Listening · Loading recognition model · Up to 2 minutes");
            org.vosk.LibVosk.vosk_set_log_level(-1);
            try(Model model=new Model(directory.getAbsolutePath());Recognizer recognizer=new Recognizer(model,RATE)) {
                if(s.cancelled)return;
                status(s,s.finish?"Finishing recorded audio…":"Listening offline · Tap Done to insert");
                long lastPartial=0;
                StringBuilder complete=new StringBuilder();
                while(!s.cancelled&&(!capture.done||!capture.queue.isEmpty())) {
                    short[] audio=capture.queue.poll(100,java.util.concurrent.TimeUnit.MILLISECONDS);
                    if(audio==null)continue;
                    boolean boundary;
                    try{boundary=recognizer.acceptWaveForm(audio,audio.length);}
                    finally{Arrays.fill(audio,(short)0);}
                    if(boundary)append(complete,new JSONObject(recognizer.getResult()).optString("text"));
                    long now=SystemClock.elapsedRealtime();
                    if(boundary||now-lastPartial>=250) {
                        String part=boundary?"":new JSONObject(recognizer.getPartialResult()).optString("partial");
                        final String text=join(complete.toString(),part);
                        post(s,()->s.listener.partial(text));lastPartial=now;
                    }
                }
                if(s.cancelled)return;
                if(capture.failure!=null)throw new IOException(capture.failure);
                status(s,"Finishing offline transcript…");
                append(complete,new JSONObject(recognizer.getFinalResult()).optString("text"));
                final String text=complete.toString().trim();
                post(s,()->s.listener.result(text));
            }
        } catch(SecurityException e) {
            post(s,()->s.listener.error("Microphone permission is missing. Enable it in Voice settings."));
        } catch(OutOfMemoryError e) {
            post(s,()->s.listener.error("Not enough free memory for the speech model. Close other apps and try again."));
        } catch(Exception|LinkageError e) {
            // Do not write exceptions or transcripts to logs; present a non-content diagnostic.
            final String message=e instanceof IOException&&e.getMessage()!=null&&(e.getMessage().startsWith("Microphone")||e.getMessage().startsWith("Selected voice model"))
                ?e.getMessage():"Offline speech could not start. Check microphone access or choose the bundled model in Voice settings.";
            post(s,()->s.listener.error(message));
        } finally {
            if(capture!=null)capture.close();s.recorder=null;if(recorder!=null)recorder.release();SpeechModels.release(selected);
        }
    }
    private void recognizeWhisper(Session s,File model) throws Exception {
        float[] samples=new float[RATE*60];short[] buffer=new short[1600];AudioRecord recorder=null;
        int used=0;boolean signal=false;
        try {
            int minimum=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(minimum<=0)throw new IOException("Microphone does not support 16 kHz recording");
            recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(minimum*2,6400));
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Microphone could not be initialized");
            s.recorder=recorder;if(s.cancelled)return;
            recorder.startRecording();if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("Microphone did not start");
            status(s,context.getString(R.string.whisper_recording));
            while(!s.cancelled&&!s.finish&&used<samples.length){
                int n=recorder.read(buffer,0,Math.min(buffer.length,samples.length-used));
                if(s.cancelled)return;if(n<0)throw new IOException("Microphone read failed");
                for(int i=0;i<n;i++){if(Math.abs((int)buffer[i])>32)signal=true;samples[used++]=buffer[i]/32768f;buffer[i]=0;}
            }
            recorder.stop();recorder.release();recorder=null;s.recorder=null;
            if(s.cancelled)return;
            if(!signal||used<RATE/4){post(s,()->s.listener.result(""));return;}
            status(s,context.getString(R.string.whisper_processing));
            try(WhisperNative nativeSession=new WhisperNative(model.getAbsolutePath())){
                s.whisper=nativeSession;if(s.cancelled)return;
                String language=Prefs.s(context,"whisper_language","auto");
                if(!java.util.Arrays.asList("auto","en","zh","hi","es","ar").contains(language))language="auto";
                final String text=nativeSession.transcribe(samples,used,language);
                if(!s.cancelled)post(s,()->s.listener.result(text));
            }finally{s.whisper=null;}
        }finally{
            s.recorder=null;if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();}
            Arrays.fill(samples,0);Arrays.fill(buffer,(short)0);
        }
    }
    private File install(Session s) throws Exception {
        File root=new File(context.getNoBackupFilesDir(),"speech");
        File ready=new File(root,"en-us-0.22-lgraph"),staging=new File(root,"en-us-0.22-lgraph.pending");
        if(new File(ready,".complete").isFile())return ready;
        deleteTree(staging);deleteTree(ready);
        if(!staging.mkdirs())throw new IOException("Cannot create speech directory");
        try {
            byte[] buffer=new byte[65536];
            try(BufferedReader manifest=new BufferedReader(new InputStreamReader(context.getAssets().open("voice/files.tsv"),java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while((line=manifest.readLine())!=null) {
                    if(s.cancelled)throw new InterruptedIOException();
                    String[] fields=line.split("\t");
                    if(fields.length!=3)throw new IOException("Invalid speech manifest");
                    File target=new File(staging,fields[0]);
                    if(!target.getCanonicalPath().startsWith(staging.getCanonicalPath()+File.separator))throw new IOException("Invalid speech path");
                    target.getParentFile().mkdirs();MessageDigest hash=MessageDigest.getInstance("SHA-256");long length=0;
                    try(InputStream in=context.getAssets().open(ASSET+"/"+fields[0]);FileOutputStream out=new FileOutputStream(target)) {
                        int n;while((n=in.read(buffer))!=-1){if(s.cancelled)throw new InterruptedIOException();out.write(buffer,0,n);hash.update(buffer,0,n);length+=n;}
                        out.getFD().sync();
                    }
                    StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format(java.util.Locale.US,"%02x",b&255));
                    if(length!=Long.parseLong(fields[1])||!digest.toString().equals(fields[2]))throw new IOException("Speech model checksum mismatch");
                }
            }
            try(FileOutputStream marker=new FileOutputStream(new File(staging,".complete"))){marker.write(1);marker.getFD().sync();}
            if(!staging.renameTo(ready))throw new IOException("Cannot finish speech installation");
            return ready;
        }catch(Exception e){deleteTree(staging);throw e;}
    }
    private static String join(String a,String b){return a.isEmpty()?b:b.isEmpty()?a:a+" "+b;}
    private static void append(StringBuilder text,String part){if(!part.isEmpty()){if(text.length()>0)text.append(' ');text.append(part);}}
    private static void deleteTree(File file) throws IOException {
        if(!file.exists())return;File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);
        if(!file.delete())throw new IOException("Cannot replace incomplete speech files");
    }
    @Override public void close(){closed=true;cancel();main.removeCallbacksAndMessages(null);worker.shutdown();}
}
