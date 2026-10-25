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
import kotlin.math.min

class PocketTtsService : TextToSpeechService() {
    @Volatile private var engine: NativePocketTts? = null
    @Volatile private var loadedVoiceName: String? = null
    private var engineKey: EngineKey? = null

    override fun onCreate() {
        super.onCreate()
        ModelPackRepository.migrateLegacy(this)
        ModelPackRepository.applySystemDefaultSelection(this)
    }

    override fun onGetLanguage(): Array<String> {
        val loaded = ModelPackRepository.findVoiceByName(this, loadedVoiceName)
        val locale = loaded?.let { ModelPackRepository.voiceLocale(it.first, it.second) }
            ?: ModelPackRepository.selectedPack(this)?.locale ?: Locale.GERMANY
        return TtsLocales.legacyParts(locale)
    }

    override fun onIsLanguageAvailable(lang: String, country: String?, variant: String?): Int {
        val requested = TtsLocales.fromLegacy(lang, country.orEmpty(), variant.orEmpty())
        val matches = ModelPackRepository.list(this).filter {
            ModelPackRepository.languageMatches(it.locale, requested)
        }
        if (matches.isEmpty()) return TextToSpeech.LANG_NOT_SUPPORTED
        if (matches.none { it.voices.isNotEmpty() }) return TextToSpeech.LANG_MISSING_DATA
        if (country.isNullOrBlank()) return TextToSpeech.LANG_AVAILABLE
        return if (matches.any { it.locale.country.equals(requested.country, true) }) {
            if (!variant.isNullOrBlank() && ModelPackRepository.findVoiceByLocale(this, requested) != null)
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE else TextToSpeech.LANG_COUNTRY_AVAILABLE
        } else TextToSpeech.LANG_AVAILABLE
    }

    override fun onLoadLanguage(lang: String, country: String?, variant: String?): Int {
        val available = onIsLanguageAvailable(lang, country, variant)
        if (available >= TextToSpeech.LANG_AVAILABLE) {
            val locale = TtsLocales.fromLegacy(lang, country.orEmpty(), variant.orEmpty())
            ModelPackRepository.resolveVoice(this, null, locale)?.let {
                loadedVoiceName = ModelPackRepository.voiceName(it.first, it.second)
            }
        }
        return available
    }

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
        val locale = TtsLocales.fromLegacy(lang, country.orEmpty(), variant.orEmpty())
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
        // Android already calls this on its synthesis thread. The callback is valid
        // only until this method returns, so all audio and done/error must be sent here.
        val text = request.charSequenceText.toString()
        val locale = TtsLocales.fromLegacy(request.language.orEmpty(), request.country.orEmpty(), request.variant.orEmpty())
        val explicit = ModelPackRepository.findVoiceByName(this, request.voiceName)
        val localeVoice = ModelPackRepository.findVoiceByLocale(this, locale)
        val loaded = ModelPackRepository.findVoiceByName(this, loadedVoiceName)?.takeIf {
            ModelPackRepository.languageMatches(it.first.locale, locale)
        }
        val resolved = explicit ?: localeVoice ?: loaded ?: ModelPackRepository.resolveVoice(this, request.voiceName, locale)
        if (resolved == null) {
            Log.e(TAG, "SYNTH_FAILED no installed voice for ${locale.toLanguageTag()}")
            callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
            return
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
            return
        }
        if (callback.start(SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) {
            Log.e(TAG, "SYNTH_START_REJECTED")
            callback.error(TextToSpeech.ERROR_OUTPUT)
            return
        }
        val effects = if (request.speechRate == 100 && request.pitch == 100) null else {
            runCatching { NativeSpeechEffects(SAMPLE_RATE, request.speechRate, request.pitch) }.getOrElse {
                Log.e(TAG, "Could not initialize speech processing", it)
                callback.error(TextToSpeech.ERROR_SERVICE)
                return
            }
        }
        try {
            var totalSamples = 0L
            var androidBlocks = 0
            val maxBlockBytes = callback.maxBufferSize.coerceAtLeast(2).let { it - (it % 2) }
            fun deliver(samples: FloatArray): Boolean {
                if (samples.isEmpty()) return true
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
            val ok = tts.synthesize(text, voice.fileName, object : NativePocketTts.AudioSink {
                override fun onAudio(samples: FloatArray): Boolean = deliver(effects?.process(samples) ?: samples)
            })
            val flushed = !ok || effects == null || deliver(effects.finish())
            if (ok && flushed && totalSamples > 0) {
                val done = callback.done()
                Log.i(TAG, "SYNTH_DONE result=$done samples=$totalSamples blocks=$androidBlocks rate=${request.speechRate} pitch=${request.pitch}")
            } else {
                Log.e(TAG, "SYNTH_FAILED samples=$totalSamples blocks=$androidBlocks")
                callback.error(TextToSpeech.ERROR_SYNTHESIS)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Speech processing failed", error)
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
        } finally {
            effects?.close()
        }
    }

    private fun obtainEngine(pack: ModelPack): NativePocketTts {
        val key = EngineKey(
            pack.id,
            pack.precision,
            ModelPackRepository.temperature(this, pack),
            ModelPackRepository.lsdSteps(this, pack),
            ModelPackRepository.threads(this, pack),
            ModelPackRepository.sentencePauseMs(this, pack),
            ModelPackRepository.maxTextTokens(this, pack)
        )
        if (engine != null && engineKey == key) return engine!!
        engine?.close()
        engine = null
        engineKey = null
        Log.i(
            TAG,
            "ENGINE_LOAD pack=${pack.id} precision=${key.precision} temperature=${key.temperature} " +
                "lsd=${key.lsdSteps} threads=${key.threads} pauseMs=${key.sentencePauseMs} " +
                "maxTextTokens=${key.maxTextTokens}"
        )
        return NativePocketTts(
            pack.modelsDir.absolutePath,
            pack.voicesDir.absolutePath,
            key.precision,
            key.temperature,
            key.lsdSteps,
            key.threads,
            key.sentencePauseMs,
            key.maxTextTokens
        ).also {
            engine = it
            engineKey = key
            Log.i(TAG, "ENGINE_READY pack=${pack.id}")
        }
    }

    override fun onStop() { engine?.stop() }

    override fun onDestroy() {
        engine?.close()
        engine = null
        super.onDestroy()
    }

    private data class EngineKey(
        val packId: String,
        val precision: String,
        val temperature: Float,
        val lsdSteps: Int,
        val threads: Int,
        val sentencePauseMs: Int,
        val maxTextTokens: Int
    )

    private companion object {
        const val TAG = "PocketTTS"
        const val SAMPLE_RATE = 24_000
    }
}
