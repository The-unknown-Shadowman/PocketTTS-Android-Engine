package org.pockettts.android.engine

import java.util.Locale

/** Android's legacy TTS intents use ISO-639-2 / ISO-3166-3, not BCP-47 tags. */
internal object TtsLocales {
    private val languages by lazy {
        Locale.getISOLanguages().associateBy { Locale(it).isO3Language.lowercase(Locale.ROOT) }
    }
    private val countries by lazy {
        Locale.getISOCountries().associateBy { Locale("", it).isO3Country.uppercase(Locale.ROOT) }
    }

    fun fromLegacy(language: String, country: String = "", variant: String = ""): Locale {
        val lang = language.lowercase(Locale.ROOT).let { languages[it] ?: it }
        val region = country.uppercase(Locale.ROOT).let { countries[it] ?: it }
        return Locale(lang, region, variant)
    }

    fun legacyParts(locale: Locale): Array<String> = arrayOf(
        runCatching { locale.isO3Language }.getOrDefault(locale.language),
        runCatching { locale.isO3Country }.getOrDefault(locale.country),
        locale.variant
    )

    fun legacyId(locale: Locale): String = legacyParts(locale).toList()
        .dropLastWhile { it.isBlank() }.joinToString("-")

    fun availableIds(locales: List<Locale>): ArrayList<String> =
        ArrayList(locales.map(::legacyId).filter { it.isNotBlank() }.distinct())
}
