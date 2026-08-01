package org.pockettts.android.engine

import android.content.Context
import android.net.Uri
import android.os.LocaleList
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

internal data class PackVoice(
    val id: String,
    val displayName: String,
    val fileName: String
)

internal data class ModelPack(
    val id: String,
    val displayName: String,
    val languageTag: String,
    val precision: String,
    val defaultTemperature: Float,
    val defaultLsdSteps: Int,
    val defaultThreads: Int,
    val voices: List<PackVoice>,
    val root: File
) {
    val locale: Locale get() = Locale.forLanguageTag(languageTag)
    val modelsDir: File get() = File(root, "models")
    val voicesDir: File get() = File(root, "voices")
    val manifestFile: File get() = File(root, "manifest.json")
}

internal object ModelPackRepository {
    private const val FORMAT_VERSION = 1
    private const val PREFS = "pockettts_pack_settings"
    private const val SYSTEM_DEFAULT_V1 = "selection.system_default.v1"
    private val sharedModels = listOf("mimi_encoder.onnx", "text_conditioner.onnx", "tokenizer.model")

    fun packsRoot(context: Context): File = File(context.filesDir, "model-packs").apply { mkdirs() }

    fun migrateLegacy(context: Context) {
        val legacy = File(context.filesDir, "pockettts")
        if (!File(legacy, "models/flow_lm_main.onnx").isFile) return
        val target = File(packsRoot(context), "german-fp32")
        if (File(target, "manifest.json").isFile) return
        if (!target.exists() && !legacy.renameTo(target)) return
        writeManifest(
            ModelPack(
                id = "german-fp32",
                displayName = "Deutsch (FP32)",
                languageTag = "de-DE",
                precision = "fp32",
                defaultTemperature = 0.7f,
                defaultLsdSteps = 1,
                defaultThreads = 2,
                voices = emptyList(),
                root = target
            )
        )
    }

    fun list(context: Context): List<ModelPack> {
        migrateLegacy(context)
        return packsRoot(context).listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .mapNotNull { runCatching { readManifest(it) }.getOrNull() }
            .filter(::isValid)
            .sortedBy { it.displayName.lowercase(Locale.ROOT) }
    }

    fun find(context: Context, id: String): ModelPack? = list(context).firstOrNull { it.id == id }

    fun applySystemDefaultSelection(context: Context) {
        val preferences = prefs(context)
        if (preferences.getBoolean(SYSTEM_DEFAULT_V1, false)) return
        val preferred = preferredSystemPack(list(context))
        preferences.edit()
            .putBoolean(SYSTEM_DEFAULT_V1, true)
            .also { editor -> preferred?.let { editor.putString("selected_pack", it.id) } }
            .apply()
    }

    fun selectedPack(context: Context): ModelPack? {
        val packs = list(context)
        val selected = prefs(context).getString("selected_pack", null)
        return packs.firstOrNull { it.id == selected } ?: preferredSystemPack(packs)
    }

    fun selectPack(context: Context, id: String) {
        prefs(context).edit().putString("selected_pack", id).apply()
    }

    fun selectedVoiceId(context: Context, pack: ModelPack): String? {
        val selected = prefs(context).getString("voice.${pack.id}", null)
        return pack.voices.firstOrNull { it.id == selected }?.id ?: pack.voices.firstOrNull()?.id
    }

    fun selectVoice(context: Context, packId: String, voiceId: String) {
        prefs(context).edit().putString("voice.$packId", voiceId).apply()
    }

    fun temperature(context: Context, pack: ModelPack): Float =
        prefs(context).getFloat("temperature.${pack.id}", pack.defaultTemperature)

    fun lsdSteps(context: Context, pack: ModelPack): Int =
        prefs(context).getInt("lsd.${pack.id}", pack.defaultLsdSteps)

    fun threads(context: Context, pack: ModelPack): Int =
        prefs(context).getInt("threads.${pack.id}", pack.defaultThreads)

    fun saveParameters(context: Context, pack: ModelPack, temperature: Float, lsdSteps: Int, threads: Int) {
        prefs(context).edit()
            .putFloat("temperature.${pack.id}", temperature.coerceIn(0f, 2f))
            .putInt("lsd.${pack.id}", lsdSteps.coerceIn(1, 8))
            .putInt("threads.${pack.id}", threads.coerceIn(1, 8))
            .apply()
    }

    fun importPack(context: Context, uri: Uri): ModelPack {
        val staging = File(packsRoot(context), ".import-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { context.getString(R.string.error_open_pack) }
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val target = File(staging, entry.name)
                        require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                            context.getString(R.string.error_invalid_pack_path)
                        }
                        if (entry.isDirectory) target.mkdirs() else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                    }
                }
            }
            val pack = readManifest(staging)
            require(isValid(pack)) { context.getString(R.string.error_incomplete_pack) }
            val destination = File(packsRoot(context), pack.id)
            val backup = File(packsRoot(context), ".backup-${pack.id}-${System.currentTimeMillis()}")
            if (destination.exists()) require(destination.renameTo(backup)) { context.getString(R.string.error_backup_pack) }
            if (!staging.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                error(context.getString(R.string.error_activate_pack))
            }
            backup.deleteRecursively()
            val installed = readManifest(destination)
            selectPack(context, installed.id)
            return installed
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    fun importVoice(context: Context, pack: ModelPack, uri: Uri): ModelPack {
        val displayName = queryDisplayName(context, uri).substringBeforeLast('.').ifBlank { context.getString(R.string.new_voice) }
        val idBase = displayName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "voice" }
        var id = idBase
        var suffix = 2
        while (pack.voices.any { it.id == id }) id = "$idBase-${suffix++}"
        val targetName = "$id.wav"
        val target = File(pack.voicesDir.apply { mkdirs() }, targetName)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { context.getString(R.string.error_open_wav) }
            FileOutputStream(target).use { input.copyTo(it) }
        }
        try {
            require(isWaveFile(target)) { context.getString(R.string.error_invalid_wav) }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
        val updated = pack.copy(voices = pack.voices + PackVoice(id, displayName, targetName))
        writeManifest(updated)
        selectVoice(context, pack.id, id)
        return updated
    }

    fun deletePack(context: Context, pack: ModelPack) {
        val root = packsRoot(context).canonicalFile
        val target = pack.root.canonicalFile
        require(target.parentFile == root && target.name == pack.id) {
            context.getString(R.string.error_delete_pack)
        }
        if (!target.exists()) return

        val trash = File(root, ".delete-${pack.id}-${UUID.randomUUID()}")
        require(target.renameTo(trash)) { context.getString(R.string.error_delete_pack) }
        if (!trash.deleteRecursively() || trash.exists()) {
            throw IllegalStateException(context.getString(R.string.error_delete_pack))
        }

        val preferences = prefs(context)
        val editor = preferences.edit()
            .remove("voice.${pack.id}")
            .remove("temperature.${pack.id}")
            .remove("lsd.${pack.id}")
            .remove("threads.${pack.id}")
        if (preferences.getString("selected_pack", null) == pack.id) {
            val replacement = preferredSystemPack(list(context))
            if (replacement == null) editor.remove("selected_pack")
            else editor.putString("selected_pack", replacement.id)
        }
        editor.apply()
    }

    fun voiceName(pack: ModelPack, voice: PackVoice): String = "${pack.id}::${voice.id}"

    fun voiceLocale(pack: ModelPack, voice: PackVoice): Locale {
        // Some TTS clients preserve only the locale and discard Voice.name.
        // Give every voice a stable, valid BCP-47 variant so those clients can
        // still address two voices of the same language independently.
        val variant = String.format(
            Locale.ROOT,
            "V%07X",
            "${pack.id}::${voice.id}".hashCode() and 0x0FFFFFFF
        )
        return Locale.Builder().setLocale(pack.locale).setVariant(variant).build()
    }

    fun findVoiceByName(context: Context, name: String?): Pair<ModelPack, PackVoice>? {
        if (name.isNullOrBlank() || "::" !in name) return null
        val (packId, voiceId) = name.split("::", limit = 2)
        val pack = list(context).firstOrNull { it.id == packId } ?: return null
        val voice = pack.voices.firstOrNull { it.id == voiceId } ?: return null
        return pack to voice
    }

    fun languageMatches(packLocale: Locale, requested: Locale): Boolean {
        if (requested.language.isBlank()) return true
        val requestedLanguage = requested.language.lowercase(Locale.ROOT)
        val aliases = buildSet {
            add(packLocale.language.lowercase(Locale.ROOT))
            runCatching { add(packLocale.isO3Language.lowercase(Locale.ROOT)) }
        }
        return requestedLanguage in aliases
    }

    fun findVoiceByLocale(context: Context, locale: Locale): Pair<ModelPack, PackVoice>? {
        if (locale.variant.isBlank()) return null
        list(context).filter { languageMatches(it.locale, locale) }.forEach { pack ->
            pack.voices.firstOrNull {
                voiceLocale(pack, it).variant.equals(locale.variant, true)
            }?.let { return pack to it }
        }
        return null
    }

    fun resolveVoice(context: Context, name: String?, locale: Locale? = null): Pair<ModelPack, PackVoice>? {
        val packs = list(context)
        findVoiceByName(context, name)?.let { return it }
        val matching = packs.filter { pack ->
            locale == null || languageMatches(pack.locale, locale)
        }
        if (locale != null) findVoiceByLocale(context, locale)?.let { return it }
        val candidates = if (matching.isNotEmpty()) matching else {
            packs.filter { it.locale.language.equals("en", true) }.ifEmpty { packs }
        }
        val pack = candidates.firstOrNull { it.id == selectedPack(context)?.id }
            ?: preferredSystemPack(candidates)
        val voice = pack?.voices?.firstOrNull { it.id == selectedVoiceId(context, pack) } ?: pack?.voices?.firstOrNull()
        return if (pack != null && voice != null) pack to voice else null
    }

    private fun readManifest(root: File): ModelPack {
        val json = JSONObject(File(root, "manifest.json").readText())
        require(json.optInt("format", 0) == FORMAT_VERSION) { "Nicht unterstütztes Paketformat" }
        val voiceArray = json.optJSONArray("voices") ?: JSONArray()
        val voices = (0 until voiceArray.length()).map { index ->
            val item = voiceArray.getJSONObject(index)
            PackVoice(item.getString("id"), item.getString("name"), item.getString("file"))
        }
        return ModelPack(
            id = sanitizeId(json.getString("id")),
            displayName = json.getString("name"),
            languageTag = json.getString("languageTag"),
            precision = json.optString("precision", "fp32").lowercase(Locale.ROOT),
            defaultTemperature = json.optDouble("temperature", 0.7).toFloat(),
            defaultLsdSteps = json.optInt("lsdSteps", 1),
            defaultThreads = json.optInt("threads", 2),
            voices = voices,
            root = root
        )
    }

    private fun writeManifest(pack: ModelPack) {
        pack.root.mkdirs()
        val voices = JSONArray().apply {
            pack.voices.forEach { voice ->
                put(JSONObject().put("id", voice.id).put("name", voice.displayName).put("file", voice.fileName))
            }
        }
        JSONObject()
            .put("format", FORMAT_VERSION)
            .put("id", pack.id)
            .put("name", pack.displayName)
            .put("languageTag", pack.languageTag)
            .put("precision", pack.precision)
            .put("temperature", pack.defaultTemperature.toDouble())
            .put("lsdSteps", pack.defaultLsdSteps)
            .put("threads", pack.defaultThreads)
            .put("voices", voices)
            .toString(2)
            .also { pack.manifestFile.writeText(it) }
    }

    private fun preferredSystemPack(packs: List<ModelPack>): ModelPack? {
        val locales = LocaleList.getDefault()
        for (index in 0 until locales.size()) {
            val locale = locales[index]
            packs.firstOrNull { languageMatches(it.locale, locale) }?.let { return it }
        }
        return packs.firstOrNull { it.locale.language.equals("en", true) } ?: packs.firstOrNull()
    }

    private fun isValid(pack: ModelPack): Boolean {
        if (pack.precision !in setOf("fp32", "int8")) return false
        val suffix = if (pack.precision == "int8") "_int8" else ""
        val precisionModels = listOf("flow_lm_flow$suffix.onnx", "flow_lm_main$suffix.onnx", "mimi_decoder$suffix.onnx")
        return pack.id.isNotBlank() && pack.locale.language.isNotBlank() &&
            (sharedModels + precisionModels).all { File(pack.modelsDir, it).isFile } &&
            pack.voices.all { File(pack.voicesDir, it.fileName).isFile }
    }

    private fun isWaveFile(file: File): Boolean {
        if (file.length() < 44) return false
        RandomAccessFile(file, "r").use { input ->
            val riff = ByteArray(4)
            input.readFully(riff)
            input.seek(8)
            val wave = ByteArray(4)
            input.readFully(wave)
            return riff.contentEquals("RIFF".toByteArray(Charsets.US_ASCII)) &&
                wave.contentEquals("WAVE".toByteArray(Charsets.US_ASCII))
        }
    }

    private fun sanitizeId(raw: String): String {
        val id = raw.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]+"), "-").trim('-')
        require(id.isNotBlank() && id == raw) { "Ungültige Paket-ID" }
        return id
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: context.getString(R.string.new_voice)
        }
        return uri.lastPathSegment ?: context.getString(R.string.new_voice)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
