package ai.layla.pockettts

/** Thin lifetime-safe JNI wrapper around the streaming PocketTTS C API. */
internal class NativePocketTts(
    modelsDir: String,
    voicesDir: String,
    precision: String,
    temperature: Float,
    lsdSteps: Int,
    threads: Int
) : AutoCloseable {
    private var handle: Long = 0

    init {
        System.loadLibrary("pockettts_jni")
        handle = nativeCreate(modelsDir, voicesDir, precision, temperature, lsdSteps, threads)
        check(handle != 0L) { "Pocket-TTS-Modell konnte nicht geladen werden." }
    }

    fun synthesize(text: String, voiceFile: String, sink: AudioSink): Boolean =
        nativeSynthesize(handle, text, voiceFile, sink)
    fun stop() { if (handle != 0L) nativeStop(handle) }
    override fun close() { if (handle != 0L) nativeDestroy(handle).also { handle = 0 } }

    interface AudioSink { fun onAudio(samples: FloatArray): Boolean }

    private external fun nativeCreate(
        modelsDir: String,
        voicesDir: String,
        precision: String,
        temperature: Float,
        lsdSteps: Int,
        threads: Int
    ): Long
    private external fun nativeSynthesize(handle: Long, text: String, voiceFile: String, sink: AudioSink): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeDestroy(handle: Long)
}
