package org.pockettts.android.engine

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class TtsLocalesTest {
    @Test fun metadataUsesAndroidLegacyFormat() {
        assertEquals("deu-DEU", TtsLocales.legacyId(Locale.GERMANY))
        assertEquals("eng-USA", TtsLocales.legacyId(Locale.US))
        assertEquals("eng", TtsLocales.legacyId(Locale.ENGLISH))
    }

    @Test fun androidLanguageAndCountryRoundTrip() {
        for (locale in listOf(Locale.GERMANY, Locale.US, Locale.ITALY, Locale("pt", "PT"), Locale("es", "ES"))) {
            val parts = TtsLocales.legacyParts(locale)
            assertEquals(locale, TtsLocales.fromLegacy(parts[0], parts[1], parts[2]))
        }
    }

    @Test fun iso2AndIso3RequestsResolveToSameLocale() {
        assertEquals(Locale.GERMANY, TtsLocales.fromLegacy("de", "DE"))
        assertEquals(Locale.GERMANY, TtsLocales.fromLegacy("deu", "DEU"))
        assertEquals(Locale.US, TtsLocales.fromLegacy("eng", "USA"))
    }

    @Test fun perVoiceVariantsArePreserved() {
        val locale = Locale.Builder().setLanguage("de").setRegion("DE").setVariant("V1234567").build()
        assertEquals("deu-DEU-V1234567", TtsLocales.legacyId(locale))
        assertEquals(locale, TtsLocales.fromLegacy("deu", "DEU", "V1234567"))
        assertEquals(listOf("deu-DEU", "deu-DEU-V1234567"),
            TtsLocales.availableIds(listOf(Locale.GERMANY, locale, Locale.GERMANY)))
    }
}
