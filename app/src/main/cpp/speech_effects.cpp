#include <jni.h>
#include "sonic.h"

extern "C" JNIEXPORT jlong JNICALL
Java_org_pockettts_android_engine_NativeSpeechEffects_nativeCreate(
        JNIEnv*, jobject, jint sample_rate, jfloat speed, jfloat pitch) {
    sonicStream stream = sonicCreateStream(sample_rate, 1);
    if (stream) {
        sonicSetSpeed(stream, speed);
        sonicSetPitch(stream, pitch);
    }
    return reinterpret_cast<jlong>(stream);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_org_pockettts_android_engine_NativeSpeechEffects_nativeProcess(
        JNIEnv* env, jobject, jlong handle, jfloatArray samples, jboolean flush) {
    auto stream = reinterpret_cast<sonicStream>(handle);
    if (!stream) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Closed speech processor");
        return nullptr;
    }
    const jsize length = env->GetArrayLength(samples);
    int ok = 1;
    if (length) {
        jfloat* input = env->GetFloatArrayElements(samples, nullptr);
        if (!input) return nullptr;
        ok = sonicWriteFloatToStream(stream, input, length);
        env->ReleaseFloatArrayElements(samples, input, JNI_ABORT);
    }
    if (ok && flush) ok = sonicFlushStream(stream);
    if (!ok) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Speech processing allocation failed");
        return nullptr;
    }
    const int available = sonicSamplesAvailable(stream);
    jfloatArray result = env->NewFloatArray(available);
    if (!result || available == 0) return result;
    jfloat* output = env->GetFloatArrayElements(result, nullptr);
    if (!output) return nullptr;
    sonicReadFloatFromStream(stream, output, available);
    env->ReleaseFloatArrayElements(result, output, 0);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_org_pockettts_android_engine_NativeSpeechEffects_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    sonicDestroyStream(reinterpret_cast<sonicStream>(handle));
}
