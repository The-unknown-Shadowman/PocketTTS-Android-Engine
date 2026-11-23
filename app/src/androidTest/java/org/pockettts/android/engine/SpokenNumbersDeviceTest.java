package org.pockettts.android.engine;

import java.util.Locale;

/** Verify number preparation in the installed Android application as well. */
final class SpokenNumbersDeviceTest {
    static void run() {
        check(Locale.US, "There were 500,000 men.", "There were five hundred thousand men.");
        check(Locale.UK, "500000", "five hundred thousand");
        check(Locale.GERMANY, "Es waren 500.000 Menschen.", "Es waren fünfhunderttausend Menschen.");
        check(Locale.GERMANY, "500000", "fünfhunderttausend");
        check(Locale.US, "1,000,000,000", "one billion");
        check(Locale.GERMANY, "1.000.000.000", "eine Milliarde");
        String unchanged = "64 125 000500 500000th v500000 500000.wav 13:500000 " +
                "192.168.1.43 2026-11-23 500000.25 500,000.25 .500000";
        check(Locale.US, unchanged, unchanged);
        check(Locale.GERMANY, "500,000 und 500.000,25", "500,000 und 500.000,25");
        check(Locale.FRANCE, "500,000", "500,000");
        check(Locale.US, "Five hundred thousand men.", "Five hundred thousand men.");
    }

    private static void check(Locale locale, String input, String expected) {
        String actual = SpokenNumbers.INSTANCE.normalize(input, locale);
        if (!expected.equals(actual)) {
            throw new AssertionError(locale + ": " + input + " -> " + actual + "; expected " + expected);
        }
    }
}
