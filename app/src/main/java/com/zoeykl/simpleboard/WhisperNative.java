package com.zoeykl.simpleboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** One worker owns decoding/close; cancellation is safe from the UI thread. */
final class WhisperNative implements AutoCloseable {
    static {System.loadLibrary("simpleboard_whisper");}
    private long handle;
    WhisperNative(String path) throws IOException {handle=open(path);if(handle==0)throw new IOException("Whisper model could not load");}
    String transcribe(float[] samples,int count,String language) throws IOException {
        byte[] result=decode(handle,samples,count,language,Runtime.getRuntime().availableProcessors());
        if(result==null)return "";
        try{return new String(result,StandardCharsets.UTF_8).trim();}finally{Arrays.fill(result,(byte)0);}
    }
    synchronized void cancel(){if(handle!=0)abort(handle);}
    @Override public synchronized void close(){if(handle!=0){destroy(handle);handle=0;}}
    private static native long open(String path) throws IOException;
    private static native byte[] decode(long handle,float[] audio,int count,String language,int threads) throws IOException;
    private static native void abort(long handle);
    private static native void destroy(long handle);
}
