package com.duckduckgo.networkprotection.impl.settings.geoswitching

import org.junit.Assert.assertEquals
import org.junit.Test

class CountryUtilsTest {

    @Test
    fun testEmojiForCountryCode() {
        assertEquals("🇬🇧", getEmojiForCountryCode("gb"))
        assertEquals("🇬🇧", getEmojiForCountryCode("GB"))

        assertEquals("🇫🇷", getEmojiForCountryCode("fr"))
        assertEquals("🇫🇷", getEmojiForCountryCode("FR"))

        assertEquals("🇨🇦", getEmojiForCountryCode("ca"))
        assertEquals("🇨🇦", getEmojiForCountryCode("CA"))

        assertEquals("🇺🇸", getEmojiForCountryCode("us"))
        assertEquals("🇺🇸", getEmojiForCountryCode("US"))

        assertEquals("🇩🇪", getEmojiForCountryCode("de"))
        assertEquals("🇩🇪", getEmojiForCountryCode("DE"))

        assertEquals("🇪🇸", getEmojiForCountryCode("es"))
        assertEquals("🇪🇸", getEmojiForCountryCode("ES"))

        assertEquals("🇳🇱", getEmojiForCountryCode("nl"))
        assertEquals("🇳🇱", getEmojiForCountryCode("NL"))

        assertEquals("🏳️", getEmojiForCountryCode(""))
    }
}
