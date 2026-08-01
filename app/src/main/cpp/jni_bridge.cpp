#include <jni.h>
#include <atomic>
#include <mutex>
#include <string>

extern "C" {
void* ptt_create(const char*, const char*, const char*, const char*, float, int, int);
void ptt_destroy(void*);
void* ptt_stream_start(void*, const char*, const char*);
int ptt_stream_read(void*, float**, int*);
void ptt_stream_cancel(void*);
void ptt_stream_end(void*);
void ptt_free_audio(float*);
}

struct Engine {
    void* tts = nullptr;
    std::atomic<void*> stream{nullptr};
    std::mutex synth_mutex;
};

static Engine* engine_from(jlong value) { return reinterpret_cast<Engine*>(value); }

extern "C" JNIEXPORT jlong JNICALL
Java_org_pockettts_android_engine_NativePocketTts_nativeCreate(
        JNIEnv* env, jobject, jstring models, jstring voices, jstring precision,
        jfloat temperature, jint lsd_steps, jint threads) {
    const char* model_path = env->GetStringUTFChars(models, nullptr);
    const char* voice_path = env->GetStringUTFChars(voices, nullptr);
    const char* precision_value = env->GetStringUTFChars(precision, nullptr);
    auto* engine = new Engine();
    // The upstream C API defaults to a relative "models/tokenizer.model".
    // Android stores assets in the app-private model directory, so pass the
    // absolute tokenizer path explicitly.
    const std::string tokenizer_path = std::string(model_path) + "/tokenizer.model";
    engine->tts = ptt_create(model_path, voice_path, tokenizer_path.c_str(), precision_value,
                             temperature, lsd_steps, threads);
    env->ReleaseStringUTFChars(models, model_path);
    env->ReleaseStringUTFChars(voices, voice_path);
    env->ReleaseStringUTFChars(precision, precision_value);
    if (!engine->tts) { delete engine; return 0; }
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_pockettts_android_engine_NativePocketTts_nativeSynthesize(
        JNIEnv* env, jobject, jlong value, jstring text, jstring voice, jobject sink) {
    auto* engine = engine_from(value);
    if (!engine || !engine->tts) return JNI_FALSE;
    std::lock_guard<std::mutex> guard(engine->synth_mutex);
    const char* utf8 = env->GetStringUTFChars(text, nullptr);
    const char* voice_utf8 = env->GetStringUTFChars(voice, nullptr);
    void* stream = ptt_stream_start(engine->tts, utf8, voice_utf8);
    env->ReleaseStringUTFChars(text, utf8);
    env->ReleaseStringUTFChars(voice, voice_utf8);
    if (!stream) return JNI_FALSE;
    engine->stream.store(stream);
    jclass sink_class = env->GetObjectClass(sink);
    jmethodID on_audio = env->GetMethodID(sink_class, "onAudio", "([F)Z");
    bool success = on_audio != nullptr;
    while (success) {
        float* samples = nullptr;
        int count = 0;
        const int state = ptt_stream_read(stream, &samples, &count);
        if (state == 0) break;
        if (state < 0) { success = false; break; }
        jfloatArray chunk = env->NewFloatArray(count);
        if (!chunk) { ptt_free_audio(samples); success = false; break; }
        env->SetFloatArrayRegion(chunk, 0, count, samples);
        ptt_free_audio(samples);
        const jboolean accepted = env->CallBooleanMethod(sink, on_audio, chunk);
        env->DeleteLocalRef(chunk);
        if (env->ExceptionCheck() || !accepted) { env->ExceptionClear(); success = false; break; }
    }
    engine->stream.store(nullptr);
    ptt_stream_end(stream);
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_pockettts_android_engine_NativePocketTts_nativeStop(JNIEnv*, jobject, jlong value) {
    auto* engine = engine_from(value);
    if (engine) if (void* stream = engine->stream.load()) ptt_stream_cancel(stream);
}

extern "C" JNIEXPORT void JNICALL
Java_org_pockettts_android_engine_NativePocketTts_nativeDestroy(JNIEnv*, jobject, jlong value) {
    auto* engine = engine_from(value);
    if (!engine) return;
    if (void* stream = engine->stream.load()) ptt_stream_cancel(stream);
    std::lock_guard<std::mutex> guard(engine->synth_mutex);
    ptt_destroy(engine->tts);
    delete engine;
}
