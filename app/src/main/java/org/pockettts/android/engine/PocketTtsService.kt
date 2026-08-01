package org.pockettts.android.engine

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.min

class PocketTtsService : TextToSpeechService() {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var engine: NativePocketTts? = null
    @Volatile private var loadedVoiceName: String? = null
    private var engineKey: EngineKey? = null

    override fun onCreate() {
        super.onCreate()
        ModelPackRepository.migrateLegacy(this)
        ModelPackRepository.applySystemDefaultSelection(this)
    }

    override fun onGetLanguage(): Array<String> {
        val locale = ModelPackRepository.selectedPack(this)?.locale ?: Locale.GERMANY
        return arrayOf(locale.language, locale.country, locale.variant)
    }

    override fun onIsLanguageAvailable(lang: String, country: String?, variant: String?): Int {
        val requested = Locale(lang, country.orEmpty(), variant.orEmpty())
        val matches = ModelPackRepository.list(this).filter {
            ModelPackRepository.languageMatches(it.locale, requested)
        }
        if (matches.isEmpty()) return TextToSpeech.LANG_NOT_SUPPORTED
        if (country.isNullOrBlank()) return TextToSpeech.LANG_AVAILABLE
        return if (matches.any { it.locale.country.equals(country, true) }) {
            TextToSpeech.LANG_COUNTRY_AVAILABLE
        } else TextToSpeech.LANG_AVAILABLE
    }

    override fun onLoadLanguage(lang: String, country: String?, variant: String?): Int =
        onIsLanguageAvailable(lang, country, variant)

    override fun onGetVoices(): MutableList<Voice> = ModelPackRepository.list(this).flatMap { pack ->
        pack.voices.map { voice ->
            Voice(
                ModelPackRepository.voiceName(pack, voice),
                ModelPackRepository.voiceLocale(pack, voice),
                Voice.QUALITY_HIGH,
                Voice.LATENCY_NORMAL,
                false,
                emptySet()
            )
        }
    }.toMutableList()

    override fun onGetDefaultVoiceNameFor(lang: String, country: String?, variant: String?): String? {
        if (onIsLanguageAvailable(lang, country, variant) < TextToSpeech.LANG_AVAILABLE) return null
        val locale = Locale(lang, country.orEmpty(), variant.orEmpty())
        val resolved = ModelPackRepository.resolveVoice(this, null, locale) ?: return null
        return ModelPackRepository.voiceName(resolved.first, resolved.second)
    }

    override fun onIsValidVoiceName(voiceName: String): Int =
        if (ModelPackRepository.findVoiceByName(this, voiceName) != null) {
            TextToSpeech.SUCCESS
        } else {
            TextToSpeech.ERROR
        }

    override fun onLoadVoice(voiceName: String): Int {
        val resolved = ModelPackRepository.findVoiceByName(this, voiceName) ?: return TextToSpeech.ERROR
        loadedVoiceName = voiceName
        Log.i(TAG, "VOICE_LOADED pack=${resolved.first.id} voice=${resolved.second.id} name=$voiceName")
        return TextToSpeech.SUCCESS
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        executor.execute {
            val text = request.charSequenceText.toString()
            val locale = Locale(request.language.orEmpty(), request.country.orEmpty(), request.variant.orEmpty())
            val explicit = ModelPackRepository.findVoiceByName(this, request.voiceName)
            val localeVoice = ModelPackRepository.findVoiceByLocale(this, locale)
            val loaded = ModelPackRepository.findVoiceByName(this, loadedVoiceName)?.takeIf {
                ModelPackRepository.languageMatches(it.first.locale, locale)
            }
            val resolved = explicit ?: localeVoice ?: loaded ?: ModelPackRepository.resolveVoice(this, request.voiceName, locale)
            if (resolved == null) {
                Log.e(TAG, "SYNTH_FAILED no installed voice for ${locale.toLanguageTag()}")
                callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
                return@execute
            }
            val (pack, voice) = resolved
            Log.i(
                TAG,
                "SYNTH_START pack=${pack.id} voice=${voice.id} requestVoice=${request.voiceName} " +
                    "loadedVoice=$loadedVoiceName locale=${request.language}-${request.country}-${request.variant} " +
                    "chars=${text.length} maxBuffer=${callback.maxBufferSize}"
            )
            val tts = runCatching { obtainEngine(pack) }.getOrElse {
                Log.e(TAG, "Pocket TTS model was unavailable for synthesis", it)
                callback.error(TextToSpeech.ERROR_SERVICE)
                return@execute
            }
            if (callback.start(SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) {
                Log.e(TAG, "SYNTH_START_REJECTED")
                callback.error(TextToSpeech.ERROR_OUTPUT)
                return@execute
            }
            var totalSamples = 0L
            var androidBlocks = 0
            val maxBlockBytes = callback.maxBufferSize.coerceAtLeast(2).let { it - (it % 2) }
            val ok = tts.synthesize(text, voice.fileName, object : NativePocketTts.AudioSink {
                override fun onAudio(samples: FloatArray): Boolean {
                    val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    samples.forEach { pcm.putShort((it.coerceIn(-1f, 1f) * 32767f).toInt().toShort()) }
                    val bytes = pcm.array()
                    var offset = 0
                    while (offset < bytes.size) {
                        val length = min(maxBlockBytes, bytes.size - offset)
                        if (callback.audioAvailable(bytes, offset, length) != TextToSpeech.SUCCESS) {
                            Log.e(TAG, "SYNTH_AUDIO_REJECTED block=$androidBlocks offset=$offset length=$length")
                            return false
                        }
                        offset += length
                        androidBlocks++
                    }
                    totalSamples += samples.size
                    Log.i(TAG, "SYNTH_AUDIO samples=${samples.size} total=$totalSamples blocks=$androidBlocks")
                    return true
                }
            })
            if (ok && totalSamples > 0) {
                val done = callback.done()
                Log.i(TAG, "SYNTH_DONE result=$done samples=$totalSamples blocks=$androidBlocks")
            } else {
                Log.e(TAG, "SYNTH_FAILED samples=$totalSamples blocks=$androidBlocks")
                callback.error(TextToSpeech.ERROR_SYNTHESIS)
            }
        }
    }

    private fun obtainEngine(pack: ModelPack): NativePocketTts {
        val key = EngineKey(
            pack.id,
            pack.precision,
            ModelPackRepository.temperature(this, pack),
            ModelPackRepository.lsdSteps(this, pack),
            ModelPackRepository.threads(this, pack)
        )
        if (engine != null && engineKey == key) return engine!!
        engine?.close()
        engine = null
        engineKey = null
        Log.i(TAG, "ENGINE_LOAD pack=${pack.id} precision=${key.precision} temperature=${key.temperature} lsd=${key.lsdSteps} threads=${key.threads}")
        return NativePocketTts(
            pack.modelsDir.absolutePath,
            pack.voicesDir.absolutePath,
            key.precision,
            key.temperature,
            key.lsdSteps,
            key.threads
        ).also {
            engine = it
            engineKey = key
            Log.i(TAG, "ENGINE_READY pack=${pack.id}")
        }
    }

    override fun onStop() { engine?.stop() }

    override fun onDestroy() {
        executor.shutdownNow()
        engine?.close()
        engine = null
        super.onDestroy()
    }

    private data class EngineKey(
        val packId: String,
        val precision: String,
        val temperature: Float,
        val lsdSteps: Int,
        val threads: Int
    )

    private companion object {
        const val TAG = "PocketTTS"
        const val SAMPLE_RATE = 24_000
    }
}
