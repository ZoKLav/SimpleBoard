#include <jni.h>
#include <whisper.h>
#include <atomic>
#include <algorithm>
#include <new>
#include <string>

struct Session { whisper_context *ctx; std::atomic<bool> cancelled{false}; };
static void fail(JNIEnv *env,const char *message) {
    env->ThrowNew(env->FindClass("java/io/IOException"),message);
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_zoeykl_simpleboard_WhisperNative_open(JNIEnv *env,jclass,jstring path) {
    whisper_log_set([](enum ggml_log_level,const char *,void *){},nullptr);
    const char *file=env->GetStringUTFChars(path,nullptr);
    if(!file)return 0;
    auto params=whisper_context_default_params();params.use_gpu=false;
    whisper_context *ctx=nullptr;
    try {ctx=whisper_init_from_file_with_params(file,params);}
    catch(...) {env->ReleaseStringUTFChars(path,file);fail(env,"Whisper initialization failed");return 0;}
    env->ReleaseStringUTFChars(path,file);
    if(!ctx){fail(env,"Whisper model could not load");return 0;}
    Session *session=new(std::nothrow) Session;
    if(!session){whisper_free(ctx);fail(env,"Not enough memory for Whisper");return 0;}
    session->ctx=ctx;return reinterpret_cast<jlong>(session);
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_zoeykl_simpleboard_WhisperNative_decode(JNIEnv *env,jclass,jlong handle,jfloatArray audio,jint count,jstring language,jint threads) {
    auto *session=reinterpret_cast<Session *>(handle);
    if(!session||count<1||count>env->GetArrayLength(audio)){fail(env,"Invalid audio buffer");return nullptr;}
    if(session->cancelled.load())return env->NewByteArray(0);
    auto params=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    const char *lang=env->GetStringUTFChars(language,nullptr);if(!lang)return nullptr;
    params.language=lang;params.translate=false;params.no_context=true;params.no_timestamps=true;
    params.print_special=false;params.print_progress=false;params.print_realtime=false;params.print_timestamps=false;
    params.n_threads=std::max(1,std::min(4,static_cast<int>(threads)));
    params.suppress_blank=true;params.suppress_nst=true;
    params.abort_callback=[](void *data){return static_cast<Session *>(data)->cancelled.load();};
    params.abort_callback_user_data=session;
    jfloat *samples=env->GetFloatArrayElements(audio,nullptr);
    if(!samples){env->ReleaseStringUTFChars(language,lang);return nullptr;}
    int result=-1;
    try {result=whisper_full(session->ctx,params,samples,count);}
    catch(...) {
        env->ReleaseFloatArrayElements(audio,samples,JNI_ABORT);env->ReleaseStringUTFChars(language,lang);
        fail(env,"Whisper decoding could not complete");return nullptr;
    }
    env->ReleaseFloatArrayElements(audio,samples,JNI_ABORT);env->ReleaseStringUTFChars(language,lang);
    if(session->cancelled.load())return env->NewByteArray(0);
    if(result!=0){fail(env,"Whisper transcription failed");return nullptr;}
    try {
        std::string text;
        for(int i=0;i<whisper_full_n_segments(session->ctx);i++) {
            if(whisper_full_get_segment_no_speech_prob(session->ctx,i)>0.8f)continue;
            text+=whisper_full_get_segment_text(session->ctx,i);
        }
        jbyteArray bytes=env->NewByteArray(static_cast<jsize>(text.size()));
        if(bytes)env->SetByteArrayRegion(bytes,0,static_cast<jsize>(text.size()),reinterpret_cast<const jbyte *>(text.data()));
        std::fill(text.begin(),text.end(),'\0');return bytes;
    }catch(const std::bad_alloc &){fail(env,"Not enough memory for transcript");return nullptr;}
}
extern "C" JNIEXPORT void JNICALL
Java_com_zoeykl_simpleboard_WhisperNative_abort(JNIEnv *,jclass,jlong handle) {
    if(handle)reinterpret_cast<Session *>(handle)->cancelled.store(true);
}
extern "C" JNIEXPORT void JNICALL
Java_com_zoeykl_simpleboard_WhisperNative_destroy(JNIEnv *,jclass,jlong handle) {
    auto *session=reinterpret_cast<Session *>(handle);
    if(session){whisper_free(session->ctx);delete session;}
}
