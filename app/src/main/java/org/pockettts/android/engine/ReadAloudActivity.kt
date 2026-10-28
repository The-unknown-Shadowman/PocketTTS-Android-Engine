package org.pockettts.android.engine

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputFilter
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.UUID

/** Receives plain text without following links, opening attachments or speaking automatically. */
class ReadAloudActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var play: Button
    private lateinit var stop: Button
    private lateinit var status: TextView
    private var tts: TextToSpeech? = null
    private var ready = false
    private var destroyed = false
    private var activeUtterance: String? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val maxLength = TextToSpeech.getMaxSpeechInputLength()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(dp(20), bars.top + dp(20), dp(20), bars.bottom + dp(20))
            insets
        }
        content.addView(TextView(this).apply { text = getString(R.string.read_aloud); textSize = 24f })
        content.addView(TextView(this).apply { text = getString(R.string.read_aloud_help) })
        input = EditText(this).apply {
            id = R.id.read_aloud_text
            hint = getString(R.string.read_aloud_text)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 4
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
            // Keep shared text out of automatic view-state persistence.
            isSaveEnabled = false
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        content.addView(input, LinearLayout.LayoutParams(-1, -2))
        play = Button(this).apply {
            id = R.id.read_aloud_play
            text = getString(R.string.read_aloud_play)
            isEnabled = false
            setOnClickListener { speak() }
        }
        content.addView(play)
        stop = Button(this).apply {
            id = R.id.read_aloud_stop
            text = getString(R.string.read_aloud_stop)
            isEnabled = false
            setOnClickListener { stopReading() }
        }
        content.addView(stop)
        content.addView(Button(this).apply {
            text = getString(R.string.read_aloud_configure)
            setOnClickListener { startActivity(Intent(this@ReadAloudActivity, MainActivity::class.java)) }
        })
        status = TextView(this).apply { id = R.id.read_aloud_status }
        content.addView(status)
        setContentView(ScrollView(this).apply { addView(content) })

        val shared = if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            runCatching { intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() }.getOrNull()
        } else null
        if (shared != null && shared.length > maxLength) {
            status.text = getString(R.string.read_aloud_too_long, maxLength)
        } else {
            input.setText(shared.orEmpty())
        }
        // Post the callback so the constructor has assigned tts before it is used.
        val handler = Handler(Looper.getMainLooper())
        tts = TextToSpeech(this, { result -> handler.post {
            if (!destroyed) {
                ready = result == TextToSpeech.SUCCESS
                play.isEnabled = ready
                if (!ready) status.setText(R.string.read_aloud_failed)
            }
        } }, packageName)
        tts?.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = complete(utteranceId, R.string.read_aloud_done)
            @Deprecated("Required by Android's TTS listener")
            override fun onError(utteranceId: String?) = complete(utteranceId, R.string.read_aloud_failed)
            override fun onError(utteranceId: String?, errorCode: Int) =
                complete(utteranceId, R.string.read_aloud_failed)
        })
    }

    private fun speak() {
        val client = tts ?: return
        if (!ready) return
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            status.setText(R.string.read_aloud_text)
            return
        }
        val pack = ModelPackRepository.selectedPack(this)
        val voice = pack?.voices?.firstOrNull { it.id == ModelPackRepository.selectedVoiceId(this, pack) }
        if (pack == null || voice == null) {
            status.setText(R.string.error_no_model_voice)
            return
        }
        val selected = client.voices?.firstOrNull { it.name == ModelPackRepository.voiceName(pack, voice) }
        if (selected == null || client.setVoice(selected) != TextToSpeech.SUCCESS) {
            status.setText(R.string.read_aloud_failed)
            return
        }
        val id = UUID.randomUUID().toString()
        activeUtterance = id
        play.isEnabled = false
        stop.isEnabled = true
        status.setText(R.string.read_aloud_reading)
        if (client.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id) != TextToSpeech.SUCCESS) {
            complete(id, R.string.read_aloud_failed)
        }
    }

    private fun complete(id: String?, message: Int) = runOnUiThread {
        if (!destroyed && activeUtterance != null && activeUtterance == id) {
            activeUtterance = null
            play.isEnabled = ready
            stop.isEnabled = false
            status.setText(message)
        }
    }

    private fun stopReading() {
        activeUtterance = null
        tts?.stop()
        play.isEnabled = ready
        stop.isEnabled = false
        status.setText(R.string.read_aloud_stopped)
    }

    override fun onStop() {
        if (activeUtterance != null) stopReading()
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
