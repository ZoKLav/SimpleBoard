package com.zoeykl.simpleboard;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Main-thread coordinator: one cancellable worker, immediate fallback, no persisted editor text. */
public final class NeuralPredictions implements AutoCloseable {
    public interface Listener {void ready(Map<String,Double> scores);}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>(),r->{Thread t=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"SimpleBoard-neural");t.setDaemon(true);return t;});
    private volatile NeuralEngine engine;
    private volatile long generation;
    private volatile boolean closed;
    private boolean unavailable;
    private int timeouts;
    private String requested,cachedContext;
    private Map<String,Double> cached;
    private Listener listener;
    private Future<?> task;
    private Runnable pending,release;
    public NeuralPredictions(Context c){context=c.getApplicationContext();}
    private boolean permitted(){
        ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        return !closed&&!unavailable&&timeouts<3&&Build.SUPPORTED_64_BIT_ABIS.length>0&&(manager==null||!manager.isLowRamDevice())&&Prefs.b(context,"neural_predictions",true);
    }
    public void request(String text,Listener target){
        if(!permitted()||text.isEmpty())return;
        if(release!=null){main.removeCallbacks(release);release=null;}
        listener=target;
        if(text.equals(cachedContext)&&cached!=null){target.ready(new HashMap<>(cached));return;}
        if(text.equals(requested))return;
        cancelTask();requested=text;cachedContext=null;cached=null;
        final long ticket=++generation;
        pending=()->{pending=null;task=worker.submit(()->run(ticket,text));};
        main.postDelayed(pending,600);
    }
    private void run(long ticket,String text){
        if(closed||ticket!=generation)return;
        try{
            if(engine==null){
                status("Loading local neural model");
                ByteBuffer model;
                try(AssetFileDescriptor file=context.getAssets().openFd("neural/model.onnx");FileInputStream stream=new FileInputStream(file.getFileDescriptor())){
                    model=stream.getChannel().map(FileChannel.MapMode.READ_ONLY,file.getStartOffset(),file.getLength());
                }
                engine=new NeuralEngine(model,context.getAssets().open("neural/tokenizer.bin"),context.getAssets().open("neural/lexicon.tsv"),context.getAssets().open("neural/boundaries.txt"));
            }
            if(closed||ticket!=generation)return;
            engine.beginRequest();if(ticket!=generation){engine.cancel();return;}
            final NeuralEngine active=engine;AtomicBoolean expired=new AtomicBoolean(false);
            Runnable timeout=()->{if(ticket==generation&&!closed){expired.set(true);active.cancel();}};
            main.postDelayed(timeout,750);
            Map<String,Double> result=null;
            try{result=active.predict(text);}catch(Exception failure){if(!expired.get()&&ticket==generation)throw failure;}
            finally{main.removeCallbacks(timeout);}
            final Map<String,Double> scores=result;
            main.post(()->{
                if(closed||ticket!=generation)return;
                if(expired.get()){timeouts++;status(timeouts>=3?"Lightweight fallback: neural prediction timed out repeatedly":"Lightweight fallback: neural prediction exceeded time budget");return;}
                if(scores==null||scores.isEmpty())return;
                cachedContext=text;cached=new HashMap<>(scores);status("Neural predictions ready (offline)");
                if(listener!=null)listener.ready(new HashMap<>(cached));
            });
        }catch(Exception|LinkageError|OutOfMemoryError failure){
            dispose();main.post(()->{if(!closed&&ticket==generation){unavailable=true;status("Lightweight fallback: neural model could not run on this device");}});
        }
    }
    private void status(String text){if(!text.equals(Prefs.s(context,"neural_status","")))Prefs.p(context).edit().putString("neural_status",text).apply();}
    private void cancelTask(){
        if(pending!=null){main.removeCallbacks(pending);pending=null;}
        NeuralEngine current=engine;if(current!=null)current.cancel();
        if(task!=null){task.cancel(false);task=null;worker.purge();}
    }
    /** Yield native work to typing, retaining only an already-computed context result. */
    public void typing(){generation++;cancelTask();requested=null;listener=null;}
    /** Clears editor text and results immediately; cancelling native work is cooperative. */
    public void reset(){generation++;cancelTask();requested=null;cachedContext=null;cached=null;listener=null;}
    public void idle(){reset();if(release!=null)main.removeCallbacks(release);final long ticket=generation;release=()->{if(!closed&&ticket==generation)worker.execute(()->{if(ticket==generation)dispose();});};main.postDelayed(release,30000);}
    public void suspendForVoice(Runnable ready){
        reset();if(release!=null){main.removeCallbacks(release);release=null;}
        if(!closed)worker.execute(()->{dispose();main.post(()->{if(!closed)ready.run();});});
    }
    private void dispose(){NeuralEngine old=engine;engine=null;if(old!=null)try{old.close();}catch(Exception ignored){}}
    @Override public void close(){reset();closed=true;main.removeCallbacksAndMessages(null);worker.execute(this::dispose);worker.shutdown();}
}
