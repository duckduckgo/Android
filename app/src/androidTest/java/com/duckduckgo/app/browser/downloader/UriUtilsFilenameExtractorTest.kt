/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class UriUtilsFilenameExtractorTest {

    private val testee: FilenameExtractor = FilenameExtractor()

    @Test
    fun whenUrlEndsWithFilenameAsJpgNoMimeOrContentDispositionThenFilenameShouldBeExtracted() {
        val url = "https://example.com/realFilename.jpg"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(url, contentDisposition, mimeType)
        assertEquals("realFilename.jpg", extracted)
    }

    @Test
    fun whenUrlContainsFilenameButContainsAdditionalPathSegmentsThenFilenameShouldBeExtracted() {
        val url = "https://foo.example.com/path/images/b/b1/realFilename.jpg/other/stuff"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(url, contentDisposition, mimeType)
        assertEquals("realFilename.jpg", extracted)
    }

    @Test
    fun whenUrlContainsFilenameButContainsAdditionalPathSegmentsAndQueryParamsThenFilenameShouldBeExtracted() {
        val url = "https://foo.example.com/path/images/b/b1/realFilename.jpg/other/stuff?cb=123"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(url, contentDisposition, mimeType)
        assertEquals("realFilename.jpg", extracted)
    }

    @Test
    fun whenUrlContainsFilenameButContainsAdditionalPathSegmentsAndQueryParamsWhichLookLikeAFilenameThenFilenameShouldBeExtracted() {
        val url = "https://foo.example.com/path/images/b/b1/realFilename.jpg/other/stuff?cb=123.com"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(url, contentDisposition, mimeType)
        assertEquals("realFilename.jpg", extracted)
    }

    @Test
    fun whenUrlContainsFilenameButContentDispositionSaysOtherwiseThenExtractFromContentDisposition() {
        val url = "https://example.com/filename.jpg"
        val mimeType: String? = null
        val contentDisposition: String? = "Content-Disposition: attachment; filename=fromDisposition.jpg"
        val extracted = testee.extract(url, contentDisposition, mimeType)
        assertEquals("fromDisposition.jpg", extracted)
    }

    @Test
    fun whenFilenameEndsInBinThenThatIsExtracted() {
        val url = "https://example.com/realFilename.bin"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(url, contentDisposition, mimeType)
        assertEquals("realFilename.bin", extracted)
    }

    @Test
    fun whenUrlContainsNoFileNameButLotsOfPathsSegmentsThenFirstSegmentNameIsUsed() {
        val url = "https://example.com/foo/bar/files"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(url, contentDisposition, mimeType)
        assertEquals("foo", extracted)
    }

    @Test
    fun whenFilenameEndsInBinWithASlashThenThatIsExtracted() {
        val url = "https://example.com/realFilename.bin/"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(url, contentDisposition, mimeType)
        assertEquals("realFilename.bin", extracted)
    }

    @Test
    fun whenFilenameContainsBinThenThatIsExtracted() {
        val url = "https://example.com/realFilename.bin/foo/bar"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(url, contentDisposition, mimeType)
        assertEquals("realFilename.bin", extracted)
    }

    @Test
    fun whenUrlIsEmptyStringAndNoOtherDataProvidedThenDefaultNameAndBinFiletypeReturned() {
        val url = ""
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(url, contentDisposition, mimeType)
        assertEquals("downloadfile", extracted)
    }

    @Test
    fun whenUrlIsEmptyStringAndMimeTypeProvidedThenDefaultNameAndFiletypeFromMimeReturned() {
        val url = ""
        val mimeType: String? = "image/jpeg"
        val contentDisposition: String? = null
        val extracted = testee.extract(url, contentDisposition, mimeType)
        assertEquals("downloadfile.jpg", extracted)
    }

    @Test
    fun whenUrlIsEmptyStringAndContentDispositionProvidedThenExtractFromContentDisposition() {
        val url = ""
        val mimeType: String? = null
        val contentDisposition: String? = "Content-Disposition: attachment; filename=fromDisposition.jpg"
        val extracted = testee.extract(url, contentDisposition, mimeType)
        assertEquals("fromDisposition.jpg", extracted)
    }

    @Test
    fun whenNoFilenameAndNoPathSegmentsThenDomainNameReturned() {
        val url = "http://example.com"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(url, contentDisposition, mimeType)
        assertEquals("example.com", extracted)
    }
}
