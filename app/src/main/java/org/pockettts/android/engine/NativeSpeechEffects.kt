package org.pockettts.android.engine

/** Per-request streaming time-stretch and pitch, with a fixed PCM output sample rate. */
internal class NativeSpeechEffects(sampleRate: Int, ratePercent: Int, pitchPercent: Int) : AutoCloseable {
    private var handle: Long

    init {
        System.loadLibrary("pockettts_jni")
        handle = nativeCreate(sampleRate, factor(ratePercent), factor(pitchPercent))
        check(handle != 0L) { "Could not allocate speech processing stream" }
    }

    fun process(samples: FloatArray): FloatArray = nativeProcess(handle, samples, false)
    fun finish(): FloatArray = nativeProcess(handle, FloatArray(0), true)
    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(sampleRate: Int, speed: Float, pitch: Float): Long
    private external fun nativeProcess(handle: Long, samples: FloatArray, flush: Boolean): FloatArray
    private external fun nativeDestroy(handle: Long)

    companion object {
        fun factor(percent: Int): Float = if (percent <= 0) 1f else (percent / 100f).coerceIn(0.05f, 20f)
    }
}
