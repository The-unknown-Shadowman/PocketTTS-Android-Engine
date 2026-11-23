package org.pockettts.android.engine

import java.util.Locale

/** Expand unambiguous large cardinal numbers before the model tokenizes them. */
internal object SpokenNumbers {
    // Consume whole identifiers, references and numeric expressions so that a
    // matching integer inside a date, decimal, version or filename stays intact.
    private val token = Regex("[\\p{L}\\p{N}_]+(?:[.,:/+\\-–][\\p{L}\\p{N}_]+)*")
    private val englishInteger = Regex("(?:[1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+)")
    private val germanInteger = Regex("(?:[1-9][0-9]*|[1-9][0-9]{0,2}(?:\\.[0-9]{3})+)")

    fun normalize(text: String, locale: Locale): String {
        // Use the selected model's language, never the phone's display language.
        return replaceIntegers(text, locale.language) {
            if (locale.language == "de") german(it) else english(it)
        }
    }

    internal fun replaceIntegers(text: String, language: String, spellOut: (Long) -> String): String {
        val integer = when (language) {
            "en" -> englishInteger
            "de" -> germanInteger
            else -> return text
        }
        return token.replace(text) { match ->
            val value = match.value
            val prefix = text.getOrNull(match.range.first - 1)
            if (prefix != null && prefix in ".,:/+-–") return@replace value
            if (!integer.matches(value)) return@replace value
            val digits = value.replace(",", "").replace(".", "")
            // Bound expansion and preserve leading-zero identifiers and small
            // numbers, which already read well. Avoid floating-point rounding.
            if (digits.length > 15) return@replace value
            val number = digits.toLongOrNull() ?: return@replace value
            if (number < 1000) return@replace value
            spellOut(number)
        }
    }

    private val englishSmall = listOf("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val englishTens = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private val germanSmall = listOf("", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun",
        "zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn")
    private val germanTens = listOf("", "", "zwanzig", "dreißig", "vierzig", "fünfzig", "sechzig", "siebzig", "achtzig", "neunzig")

    private fun englishSmall(number: Int): String = when {
        number < 20 -> englishSmall[number]
        number < 100 -> englishTens[number / 10] +
            if (number % 10 == 0) "" else "-" + englishSmall[number % 10]
        else -> englishSmall[number / 100] + " hundred" +
            if (number % 100 == 0) "" else " " + englishSmall(number % 100)
    }

    private fun english(number: Long): String {
        var remaining = number
        val parts = mutableListOf<String>()
        for ((scale, name) in listOf(1_000_000_000_000L to "trillion", 1_000_000_000L to "billion",
            1_000_000L to "million", 1_000L to "thousand")) {
            val count = (remaining / scale).toInt()
            if (count > 0) parts += englishSmall(count) + " " + name
            remaining %= scale
        }
        if (remaining > 0) parts += englishSmall(remaining.toInt())
        return parts.joinToString(" ")
    }

    private fun germanSmall(number: Int): String = when {
        number < 20 -> germanSmall[number]
        number < 100 -> (if (number % 10 == 0) "" else
            (if (number % 10 == 1) "ein" else germanSmall[number % 10]) + "und") + germanTens[number / 10]
        else -> (if (number / 100 == 1) "ein" else germanSmall[number / 100]) + "hundert" +
            germanSmall(number % 100)
    }

    private fun german(number: Long): String {
        var remaining = number
        val parts = mutableListOf<String>()
        for ((scale, name) in listOf(1_000_000_000_000L to "Billion", 1_000_000_000L to "Milliarde",
            1_000_000L to "Million")) {
            val count = (remaining / scale).toInt()
            if (count > 0) parts += if (count == 1) "eine $name" else germanSmall(count) + " " +
                name + (if (name == "Milliarde") "n" else "en")
            remaining %= scale
        }
        val thousands = (remaining / 1000).toInt()
        val tail = (if (thousands == 0) "" else
            (if (thousands == 1) "ein" else germanSmall(thousands)) + "tausend") +
            germanSmall((remaining % 1000).toInt())
        if (tail.isNotEmpty()) parts += tail
        return parts.joinToString(" ")
    }
}
