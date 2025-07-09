package com.duckduckgo.sync.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncodingExtensionTest {

    /**
     * For reference, here are example strings and their base-64 encoded forms (regular and URL-safe versions)
     *
     * String | Base64 | Base64 URL Safe
     * noSPECIALchars3 | bm9TUEVDSUFMY2hhcnMz | bm9TUEVDSUFMY2hhcnMz  (no modifications)
     * 1paddingNeeded | MXBhZGRpbmdOZWVkZWQ= | MXBhZGRpbmdOZWVkZWQ  (padding stripped)
     * 2 padding needed | MiBwYWRkaW5nIG5lZWRlZA== | MiBwYWRkaW5nIG5lZWRlZA  (double padding stripped)
     * AA> | QUE+ | QUE-  (plus replaced with minus)
     * AA? | QUE/ | QUE_  (forward slash replaced with underscore)
     */

    @Test
    fun whenEmptyStringInThenEmptyStringOut() {
        val input = ""
        val expectedOutput = ""
        assertEquals(expectedOutput, input.applyUrlSafetyFromB64())
    }

    @Test
    fun whenNoSpecialCharactersThenEncodedStringIsUnchanged() {
        val input = "noSPECIALchars3"
        val expectedOutput = "bm9TUEVDSUFMY2hhcnMz"
        val normalB64Encoding = input.encodeB64()
        assertEquals(expectedOutput, normalB64Encoding)
        assertEquals(expectedOutput, normalB64Encoding.applyUrlSafetyFromB64())
    }

    @Test
    fun whenHasSinglePaddingCharacterThenPaddingIsTrimmed() {
        val input = "1paddingNeeded"
        val expectedOutput = "MXBhZGRpbmdOZWVkZWQ"
        val normalB64Encoding = input.encodeB64()
        assertEquals("$expectedOutput=", normalB64Encoding)
        assertEquals(expectedOutput, normalB64Encoding.applyUrlSafetyFromB64())
    }

    @Test
    fun whenHasDoublePaddingCharactersThenPaddingIsTrimmed() {
        val input = "2 padding needed"
        val expectedOutput = "MiBwYWRkaW5nIG5lZWRlZA"
        val normalB64Encoding = input.encodeB64()
        assertEquals("$expectedOutput==", normalB64Encoding)
        assertEquals(expectedOutput, normalB64Encoding.applyUrlSafetyFromB64())
    }

    @Test
    fun whenInputContainsAPlusThenReplacedWithMinus() {
        val input = "AA>"
        val expectedOutput = "QUE-"
        assertEquals(expectedOutput, input.encodeB64().applyUrlSafetyFromB64())
    }

    @Test
    fun whenInputContainsAForwardSlashThenReplacedWithUnderscore() {
        val input = "AA?"
        assertEquals("QUE_", input.encodeB64().applyUrlSafetyFromB64())
    }

    @Test
    fun whenDecodedFormContainsAnUnderscoreThenReplacedWithForwardSlash() {
        assertEquals("AA?", "QUE_".removeUrlSafetyToRestoreB64().decodeB64())
    }

    @Test
    fun whenDecodedFormContainsAMinusThenReplacedWithPlus() {
        assertEquals("AA>", "QUE+".removeUrlSafetyToRestoreB64().decodeB64())
    }
}
