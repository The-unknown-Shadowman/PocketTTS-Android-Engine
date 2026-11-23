package org.pockettts.android.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SpokenNumbersTest {
    private fun expand(text: String, language: String = "en") =
        SpokenNumbers.replaceIntegers(text, language) { "[$it]" }

    @Test fun groupedAndPlainEnglishIntegersKeepTheirValue() {
        assertEquals("There were [500000] men and [1234567] people.",
            expand("There were 500,000 men and 1,234,567 people."))
        assertEquals("[500000]. [1000]!", expand("500000. 1000!"))
    }

    @Test fun groupingFollowsTheModelLanguage() {
        assertEquals("Es waren [500000] und [1234567].",
            expand("Es waren 500.000 und 1.234.567.", "de"))
        assertEquals("500.000 and 1.234", expand("500.000 and 1.234"))
        assertEquals("500,000 und 1,234", expand("500,000 und 1,234", "de"))
        assertEquals("500,000", expand("500,000", "fr"))
    }

    @Test fun identifiersDecimalsDatesAndReferencesStayIntact() {
        val text = "64 125 000500 0500,000 abc500000 500000th 500000.wav " +
            "3:500000 500000:3 2026-11-23 23/11/2026 1.2.3 192.168.1.43 " +
            "500,000.25 500000.25 .500000 +500000 -500000 500000–600000"
        assertEquals(text, expand(text))
        assertEquals("500.000,25 und 500000,25", expand("500.000,25 und 500000,25", "de"))
    }

    @Test fun malformedGroupingIsNotPartiallyConverted() {
        val text = "12,34,567 500,00 500,000, 1,0000 500.000.000,25"
        assertEquals("12,34,567 500,00 [500000], 1,0000 500.000.000,25", expand(text))
    }

    @Test fun expansionIsBoundedAndDoesNotRoundLargeIntegers() {
        assertEquals("[999999999999999] 1000000000000000 99999999999999999999999999",
            expand("999999999999999 1000000000000000 99999999999999999999999999"))
    }

    @Test fun wordsAndWhitespaceArePreserved() {
        assertEquals("Five hundred thousand.\n\t[500000] people", expand("Five hundred thousand.\n\t500,000 people"))
    }

    @Test fun englishNumbersAreSpelledExactlyWithoutFloatingPoint() {
        assertEquals("five hundred thousand", SpokenNumbers.normalize("500,000", Locale.US))
        assertEquals("one million two hundred thirty-four thousand five hundred sixty-seven",
            SpokenNumbers.normalize("1,234,567", Locale.US))
        assertEquals("one thousand one", SpokenNumbers.normalize("1001", Locale.UK))
        assertEquals("one billion", SpokenNumbers.normalize("1,000,000,000", Locale.US))
        assertEquals("nine hundred ninety-nine trillion nine hundred ninety-nine billion " +
            "nine hundred ninety-nine million nine hundred ninety-nine thousand nine hundred ninety-nine",
            SpokenNumbers.normalize("999999999999999", Locale.US))
    }

    @Test fun germanNumbersUseTheCorrectCompoundWordsAndScale() {
        val cases = mapOf("500.000" to "fünfhunderttausend", "1001" to "eintausendeins",
            "121.321" to "einhunderteinundzwanzigtausenddreihunderteinundzwanzig",
            "1.000.000" to "eine Million", "2.000.000" to "zwei Millionen",
            "1.000.000.000" to "eine Milliarde", "2.000.000.000" to "zwei Milliarden",
            "1.000.000.000.000" to "eine Billion", "2.000.000.000.000" to "zwei Billionen")
        for ((input, expected) in cases) assertEquals(expected, SpokenNumbers.normalize(input, Locale.GERMANY))
    }
}
