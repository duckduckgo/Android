package com.duckduckgo.common.utils.extensions

import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.util.Locale

class LocaleExtensionsTest {

    @Test
    fun whenHasUnicodeLocaleExtensionThenRemoveUnicodeLocaleExtension() {
        val locale = Locale.Builder()
            .setLanguage("en")
            .setRegion("US")
            .setExtension(Locale.UNICODE_LOCALE_EXTENSION, "test")
            .build()

        assertEquals("en-US-u-test", locale.toLanguageTag())
        assertEquals("en-US", locale.toSanitizedLanguageTag())
    }

    @Test
    fun whenDoesNotHaveUnicodeLocaleExtensionThenLanguageTagIsUnchanged() {
        val locale = Locale.Builder()
            .setLanguage("en")
            .setRegion("US")
            .build()

        assertEquals("en-US", locale.toLanguageTag())
        assertEquals("en-US", locale.toSanitizedLanguageTag())
    }
}
