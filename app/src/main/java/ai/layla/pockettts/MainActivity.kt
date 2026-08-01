package ai.layla.pockettts

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var packSpinner: Spinner
    private lateinit var voiceSpinner: Spinner
    private lateinit var importVoiceButton: Button
    private lateinit var deletePackButton: Button
    private lateinit var temperatureInput: EditText
    private lateinit var lsdInput: EditText
    private lateinit var threadsInput: EditText
    private lateinit var statusView: TextView
    private var packs: List<ModelPack> = emptyList()
    private var visibleVoices: List<PackVoice> = emptyList()
    private var currentPack: ModelPack? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        ModelPackRepository.migrateLegacy(this)
        ModelPackRepository.applySystemDefaultSelection(this)
        if (intent?.action == TextToSpeech.Engine.ACTION_CHECK_TTS_DATA) {
            val languages = ArrayList(ModelPackRepository.list(this).map { it.languageTag }.distinct())
            val result = Intent()
                .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, languages)
                .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, arrayListOf())
            setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, result)
            finish()
            return
        }
        if (intent?.action == ACTION_SELF_TEST) {
            showSelfTest()
            return
        }
        if (intent?.action == ACTION_MULTI_VOICE_SELF_TEST) {
            showMultiVoiceSelfTest()
            return
        }
        createUi()
        refreshData()
    }

    private fun createUi() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                dp(20),
                systemBars.top + dp(20),
                dp(20),
                systemBars.bottom + dp(32)
            )
            insets
        }
        content.addView(ImageView(this).apply {
            setImageResource(R.drawable.pocket_voice_hero)
            contentDescription = getString(R.string.hero_content_description)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(Color.rgb(9, 10, 31))
            }
            clipToOutline = true
        }, matchWidth(height = 190, bottom = 20))
        content.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.app_description)
            textSize = 15f
            setPadding(0, dp(8), 0, dp(20))
        })

        content.addLabel(getString(R.string.language_pack))
        packSpinner = Spinner(this)
        content.addView(packSpinner, matchWidth())
        content.addView(Button(this).apply {
            text = getString(R.string.import_language_pack)
            setOnClickListener { openDocument(REQUEST_MODEL_PACK, "application/zip") }
        }, matchWidth())
        deletePackButton = Button(this).apply {
            text = getString(R.string.delete_language_pack)
            setOnClickListener { confirmDeletePack() }
        }
        content.addView(deletePackButton, matchWidth())

        content.addLabel(getString(R.string.voice))
        voiceSpinner = Spinner(this)
        content.addView(voiceSpinner, matchWidth())
        importVoiceButton = Button(this).apply {
            text = getString(R.string.import_reference_voice)
            setOnClickListener { openDocument(REQUEST_VOICE, "audio/wav") }
        }
        content.addView(importVoiceButton, matchWidth())

        content.addLabel(getString(R.string.generation_parameters))
        temperatureInput = addNumberField(content, getString(R.string.temperature_label), decimal = true)
        lsdInput = addNumberField(content, getString(R.string.lsd_steps_label))
        threadsInput = addNumberField(content, getString(R.string.cpu_threads_label))
        content.addView(Button(this).apply {
            text = getString(R.string.save_selection)
            setOnClickListener { saveSettings() }
        }, matchWidth())

        content.addView(Button(this).apply {
            text = getString(R.string.open_android_tts)
            setOnClickListener { openTtsSettings() }
        }, matchWidth(top = 12))
        statusView = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(20), 0, 0)
        }
        content.addView(statusView, matchWidth())
        setContentView(ScrollView(this).apply { addView(content) })

        packSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                packs.getOrNull(position)?.let {
                    ModelPackRepository.selectPack(this@MainActivity, it.id)
                    showPack(it)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val pack = currentPack ?: return
                visibleVoices.getOrNull(position)?.let { ModelPackRepository.selectVoice(this@MainActivity, pack.id, it.id) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun refreshData(message: String? = null) {
        packs = ModelPackRepository.list(this)
        val selected = ModelPackRepository.selectedPack(this)
        packSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            if (packs.isEmpty()) listOf(getString(R.string.no_language_pack)) else packs.map { it.displayName })
        val index = packs.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
        if (packs.isNotEmpty()) packSpinner.setSelection(index)
        showPack(packs.getOrNull(index))
        statusView.text = message ?: if (packs.isEmpty()) {
            getString(R.string.status_no_model)
        } else {
            getString(R.string.status_installed, packs.size)
        }
    }

    private fun showPack(pack: ModelPack?) {
        currentPack = pack
        visibleVoices = pack?.voices.orEmpty()
        voiceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            if (visibleVoices.isEmpty()) listOf(getString(R.string.no_voice)) else visibleVoices.map { it.displayName })
        val selectedVoice = pack?.let { ModelPackRepository.selectedVoiceId(this, it) }
        val voiceIndex = visibleVoices.indexOfFirst { it.id == selectedVoice }.coerceAtLeast(0)
        if (visibleVoices.isNotEmpty()) voiceSpinner.setSelection(voiceIndex)
        importVoiceButton.isEnabled = pack != null
        deletePackButton.isEnabled = pack != null
        temperatureInput.isEnabled = pack != null
        lsdInput.isEnabled = pack != null
        threadsInput.isEnabled = pack != null
        temperatureInput.setText(pack?.let { ModelPackRepository.temperature(this, it).toString() }.orEmpty())
        lsdInput.setText(pack?.let { ModelPackRepository.lsdSteps(this, it).toString() }.orEmpty())
        threadsInput.setText(pack?.let { ModelPackRepository.threads(this, it).toString() }.orEmpty())
    }

    private fun confirmDeletePack() {
        val pack = currentPack ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_pack_title)
            .setMessage(getString(R.string.delete_pack_message, pack.displayName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_language_pack) { _, _ -> deletePack(pack) }
            .show()
    }

    private fun deletePack(pack: ModelPack) {
        statusView.text = getString(R.string.status_deleting_pack, pack.displayName)
        deletePackButton.isEnabled = false
        thread(name = "pockettts-delete-pack") {
            runCatching { ModelPackRepository.deletePack(this, pack) }
                .onSuccess {
                    runOnUiThread { refreshData(getString(R.string.status_delete_success, pack.displayName)) }
                }
                .onFailure { error ->
                    Log.e(TAG, "DELETE_PACK_FAILED pack=${pack.id}", error)
                    runOnUiThread {
                        deletePackButton.isEnabled = currentPack != null
                        statusView.text = getString(R.string.status_delete_failed, error.message.orEmpty())
                    }
                }
        }
    }

    private fun saveSettings() {
        val pack = currentPack ?: return
        val temperature = temperatureInput.text.toString().replace(',', '.').toFloatOrNull()
        val lsd = lsdInput.text.toString().toIntOrNull()
        val threads = threadsInput.text.toString().toIntOrNull()
        if (temperature == null || temperature !in 0f..2f || lsd == null || lsd !in 1..8 || threads == null || threads !in 1..8) {
            Toast.makeText(this, getString(R.string.invalid_parameters), Toast.LENGTH_LONG).show()
            return
        }
        ModelPackRepository.saveParameters(this, pack, temperature, lsd, threads)
        visibleVoices.getOrNull(voiceSpinner.selectedItemPosition)?.let {
            ModelPackRepository.selectVoice(this, pack.id, it.id)
        }
        statusView.text = getString(R.string.status_saved, pack.displayName)
    }

    private fun openDocument(requestCode: Int, mime: String) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (requestCode == REQUEST_MODEL_PACK) "*/*" else mime
            if (requestCode == REQUEST_MODEL_PACK) putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
            )
            if (requestCode == REQUEST_VOICE) putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/wav", "audio/x-wav", "audio/wave"))
        }, requestCode)
    }

    private fun openTtsSettings() {
        runCatching { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            .onFailure { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    @Deprecated("Activity result API is sufficient for this framework-only activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        statusView.text = if (requestCode == REQUEST_MODEL_PACK) {
            getString(R.string.status_importing_pack)
        } else getString(R.string.status_importing_voice)
        thread(name = "pockettts-import") {
            runCatching {
                when (requestCode) {
                    REQUEST_MODEL_PACK -> ModelPackRepository.importPack(this, uri).displayName
                    REQUEST_VOICE -> {
                        val pack = currentPack ?: error("Kein Sprachpaket ausgewählt")
                        ModelPackRepository.importVoice(this, pack, uri).displayName
                    }
                    else -> return@thread
                }
            }.onSuccess { name -> runOnUiThread { refreshData(getString(R.string.status_import_success, name)) } }
                .onFailure { error ->
                    Log.e(TAG, "IMPORT_FAILED", error)
                    runOnUiThread { statusView.text = getString(R.string.status_import_failed, error.message.orEmpty()) }
                }
        }
    }

    private fun showSelfTest() {
        val status = TextView(this).apply {
            text = getString(R.string.self_test_running)
            textSize = 18f
            setPadding(dp(24), dp(36), dp(24), dp(24))
        }
        setContentView(status)
        thread(name = "pockettts-self-test") {
            runCatching { runSelfTest() }
                .onSuccess { result ->
                    Log.i(TAG, "SELF_TEST_OK file=${result.file} samples=${result.samples} chunks=${result.chunks} min=${result.min} max=${result.max}")
                    runOnUiThread {
                        status.text = getString(R.string.self_test_success, result.samples, result.samples / SAMPLE_RATE)
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "SELF_TEST_FAILED", error)
                    runOnUiThread { status.text = getString(R.string.self_test_failed, error.message.orEmpty()) }
                }
        }
    }

    private fun showMultiVoiceSelfTest() {
        val status = TextView(this).apply {
            text = getString(R.string.self_test_running)
            textSize = 18f
            setPadding(dp(24), dp(36), dp(24), dp(24))
        }
        setContentView(status)
        thread(name = "pockettts-multi-voice-test") {
            runCatching { runMultiVoiceSelfTest() }
                .onSuccess { result ->
                    Log.i(
                        TAG,
                        "MULTI_VOICE_TEST_OK pack=${result.packId} first=${result.firstVoice} " +
                            "second=${result.secondVoice} firstHash=${result.firstHash} " +
                            "secondHash=${result.secondHash} repeatHash=${result.repeatHash}"
                    )
                    runOnUiThread { status.text = getString(R.string.multi_voice_test_success) }
                }
                .onFailure { error ->
                    Log.e(TAG, "MULTI_VOICE_TEST_FAILED", error)
                    runOnUiThread { status.text = getString(R.string.self_test_failed, error.message.orEmpty()) }
                }
        }
    }

    private fun runMultiVoiceSelfTest(): MultiVoiceTestResult {
        val pack = ModelPackRepository.list(this).firstOrNull { it.voices.size >= 2 }
            ?: error(getString(R.string.error_need_two_voices))
        val first = pack.voices[0]
        val second = pack.voices[1]
        NativePocketTts(
            pack.modelsDir.absolutePath,
            pack.voicesDir.absolutePath,
            pack.precision,
            0f,
            ModelPackRepository.lsdSteps(this, pack),
            1
        ).use { tts ->
            val firstHash = synthesizeFingerprint(tts, first.fileName)
            val secondHash = synthesizeFingerprint(tts, second.fileName)
            val repeatHash = synthesizeFingerprint(tts, first.fileName)
            check(firstHash == repeatHash) { getString(R.string.error_voice_repeat_mismatch) }
            check(firstHash != secondHash) { getString(R.string.error_voices_not_distinct) }
            return MultiVoiceTestResult(pack.id, first.id, second.id, firstHash, secondHash, repeatHash)
        }
    }

    private fun synthesizeFingerprint(tts: NativePocketTts, voiceFile: String): String {
        var hash = -0x340d631b7bdddcdbL
        var samples = 0L
        check(tts.synthesize(getString(R.string.multi_voice_test_text), voiceFile, object : NativePocketTts.AudioSink {
            override fun onAudio(values: FloatArray): Boolean {
                values.forEach { value ->
                    hash = hash xor value.toBits().toLong()
                    hash *= 0x100000001b3L
                }
                samples += values.size
                return true
            }
        })) { getString(R.string.error_native_synthesis) }
        check(samples > 0) { getString(R.string.error_no_samples) }
        return java.lang.Long.toUnsignedString(hash, 16)
    }

    private fun runSelfTest(): SelfTestResult {
        val (pack, voice) = ModelPackRepository.resolveVoice(this, null) ?: error(getString(R.string.error_no_model_voice))
        val pcm = ByteArrayOutputStream()
        var samples = 0L
        var chunks = 0
        var minimum = Float.POSITIVE_INFINITY
        var maximum = Float.NEGATIVE_INFINITY
        NativePocketTts(
            pack.modelsDir.absolutePath,
            pack.voicesDir.absolutePath,
            pack.precision,
            ModelPackRepository.temperature(this, pack),
            ModelPackRepository.lsdSteps(this, pack),
            ModelPackRepository.threads(this, pack)
        ).use { tts ->
            check(tts.synthesize(getString(R.string.test_text), voice.fileName, object : NativePocketTts.AudioSink {
                override fun onAudio(values: FloatArray): Boolean {
                    chunks++
                    samples += values.size
                    val bytes = ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    values.forEach { value ->
                        minimum = minOf(minimum, value)
                        maximum = maxOf(maximum, value)
                        bytes.putShort((value.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                    }
                    pcm.write(bytes.array())
                    Log.i(TAG, "SELF_TEST_CHUNK index=$chunks samples=${values.size} total=$samples")
                    return true
                }
            })) { getString(R.string.error_native_synthesis) }
        }
        check(samples > 0) { getString(R.string.error_no_samples) }
        val output = File(filesDir, "selftest.wav")
        val audio = pcm.toByteArray()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + audio.size)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray(Charsets.US_ASCII)); putInt(audio.size)
        }.array()
        output.outputStream().use { it.write(header); it.write(audio) }
        return SelfTestResult(output.absolutePath, samples, chunks, minimum, maximum)
    }

    private fun LinearLayout.addLabel(label: String) {
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(6))
        })
    }

    private fun addNumberField(parent: LinearLayout, hint: String, decimal: Boolean = false): EditText =
        EditText(this).apply {
            parent.addView(TextView(this@MainActivity).apply {
                text = hint
                textSize = 14f
                setPadding(dp(4), dp(8), 0, 0)
            }, matchWidth())
            this.hint = hint
            inputType = if (decimal) 0x00002002 else 0x00000002
            parent.addView(this, matchWidth())
        }

    private fun matchWidth(top: Int = 0, bottom: Int = 0, height: Int? = null) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        height?.let(::dp) ?: LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class SelfTestResult(val file: String, val samples: Long, val chunks: Int, val min: Float, val max: Float)
    private data class MultiVoiceTestResult(
        val packId: String,
        val firstVoice: String,
        val secondVoice: String,
        val firstHash: String,
        val secondHash: String,
        val repeatHash: String
    )

    private companion object {
        const val TAG = "PocketTTS"
        const val ACTION_SELF_TEST = "ai.layla.pockettts.SELF_TEST"
        const val ACTION_MULTI_VOICE_SELF_TEST = "ai.layla.pockettts.MULTI_VOICE_SELF_TEST"
        const val REQUEST_MODEL_PACK = 1001
        const val REQUEST_VOICE = 1002
        const val SAMPLE_RATE = 24_000
    }
}
