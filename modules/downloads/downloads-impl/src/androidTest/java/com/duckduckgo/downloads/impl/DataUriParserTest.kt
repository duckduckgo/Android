/*
 * Copyright (c) 2022 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.downloads.impl

import com.duckduckgo.downloads.impl.DataUriParser.ParseResult.Invalid
import com.duckduckgo.downloads.impl.DataUriParser.ParseResult.ParsedDataUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataUriParserTest {

    private lateinit var testee: DataUriParser

    @Before
    fun setup() {
        testee = DataUriParser()
    }

    @Test
    fun whenMimeTypeProvidedAsImagePngThenPngSuffixGenerated() {
        val parsed = testee.generate("data:image/png;base64,AAAA") as ParsedDataUri
        assertEquals("png", parsed.filename.fileType)
    }

    @Test
    fun whenMimeTypeProvidedAsImageJpegThenJpgSuffixGenerated() {
        val parsed = testee.generate("data:image/jpeg;base64,AAAA") as ParsedDataUri
        assertEquals("jpg", parsed.filename.fileType)
    }

    @Test
    fun whenMimeTypeProvidedAsArbitraryImageTypeThenNoSuffixGenerated() {
        val parsed = testee.generate("data:image/foo;base64,AAAA") as ParsedDataUri
        assertEquals("", parsed.filename.fileType)
    }

    @Test
    fun whenMimeTypeNotProvidedThenNoSuffixAdded() {
        val parsed = testee.generate("data:,AAAA") as ParsedDataUri
        assertEquals("", parsed.filename.fileType)
    }

    @Test
    fun whenInvalidDataUriProvidedInvalidTypeTurned() {
        val parsed = testee.generate("AAAA")
        assertTrue(parsed === Invalid)
    }

    @Test
    fun whenKnownMimeTypeProvidedAsNonImageTypeThenSuffixStillGenerated() {
        val parsed = testee.generate("data:text/plain;base64,AAAA") as ParsedDataUri
        assertEquals("txt", parsed.filename.fileType)
    }

    @Test
    fun whenMimeTypeNotProvidedThenNoSuffixAddedInToString() {
        val filename = testee.generate("data:,AAAA") as ParsedDataUri
        assertFalse(filename.toString().contains("."))
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsPngThenSuffixIsPng() {
        val parsed = testee.generate("data:text/plain;base64,iVBORw0KGgo") as ParsedDataUri
        assertEquals("png", parsed.filename.fileType)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsJfifThenSuffixIsJpg() {
        val parsed = testee.generate("data:text/plain;base64,/9j/4AAQSkZJRgABAQ") as ParsedDataUri
        assertEquals("jpg", parsed.filename.fileType)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsSvgThenSuffixIsSvg() {
        val parsed = testee.generate("data:text/plain;base64,PHN2ZyB2ZXJzaW9uPSIxLjIiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy") as ParsedDataUri
        assertEquals("svg", parsed.filename.fileType)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsGifThenSuffixIsGif() {
        val parsed = testee.generate("data:text/plain;base64,R0lGODlhAAXQAocAAP") as ParsedDataUri
        assertEquals("gif", parsed.filename.fileType)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsPdfThenSuffixIsPdf() {
        val parsed = testee.generate("data:text/plain;base64,JVBERi0xLjEKMSAwIG9iag") as ParsedDataUri
        assertEquals("pdf", parsed.filename.fileType)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsWebpThenSuffixIsWebp() {
        val parsed = testee.generate("data:text/plain;base64,UklGRs4IAABXRUJQVlA4WAo") as ParsedDataUri
        assertEquals("webp", parsed.filename.fileType)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsBpThenSuffixIsBmp() {
        val parsed = testee.generate("data:text/plain;base64,Qk1AwgEA") as ParsedDataUri
        assertEquals("bmp", parsed.filename.fileType)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsUnknownThenSuffixIsTxt() {
        val parsed = testee.generate("data:text/plain;base64,RUJQVlA4WAo") as ParsedDataUri
        assertEquals("txt", parsed.filename.fileType)
    }

    @Test
    fun whenMimeTypeIsImageJpegThenSuffixIsJpg() {
        val parsed = testee.generate("data:image/jpeg;base64,RUJQVlA4WAo") as ParsedDataUri
        assertEquals("jpg", parsed.filename.fileType)
    }
}
