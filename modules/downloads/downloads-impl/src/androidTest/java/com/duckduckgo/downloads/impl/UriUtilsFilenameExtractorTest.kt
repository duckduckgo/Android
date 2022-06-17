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

package com.duckduckgo.downloads.impl

import androidx.test.filters.SdkSuppress
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.downloads.impl.pixels.DownloadsPixelName
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UriUtilsFilenameExtractorTest {

    private val mockedPixel: Pixel = mock()
    private val testee: FilenameExtractor = FilenameExtractor(mockedPixel)

    @Test
    fun whenUrlEndsWithFilenameAsJpgNoMimeOrContentDispositionThenFilenameShouldBeExtracted() {
        val url = "https://example.com/realFilename.jpg"
        val mimeType: String? = null
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted
        assertEquals("realFilename.jpg", extractionResult.filename)
    }

    @Test
    fun whenUrlContainsFilenameButContainsAdditionalPathSegmentsThenFilenameShouldBeExtracted() {
        val url = "https://foo.example.com/path/images/b/b1/realFilename.jpg/other/stuff"
        val mimeType: String? = null
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted
        assertEquals("realFilename.jpg", extractionResult.filename)
    }

    @Test
    fun whenUrlContainsFilenameButContainsAdditionalPathSegmentsAndQueryParamsThenFilenameShouldBeExtracted() {
        val url = "https://foo.example.com/path/images/b/b1/realFilename.jpg/other/stuff?cb=123"
        val mimeType: String? = null
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted
        assertEquals("realFilename.jpg", extractionResult.filename)
    }

    @Test
    fun whenUrlContainsFilenameButContainsPathSegmentsWhichLookLikeAFilenameThenFilenameShouldBeExtracted() {
        val url = "https://foo.example.com/path/dotted.path/b/b1/realFilename.jpg"
        val mimeType: String? = null
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted
        assertEquals("realFilename.jpg", extractionResult.filename)
    }

    @Test
    @SdkSuppress(minSdkVersion = 22)
    fun whenUrlContainsAmbiguousFilenameButContainsPathSegmentsWhichLookLikeAFilenameAndMimeTypeProvidedThenFilenameShouldBeExtracted() {
        val url = "https://foo.example.com/path/dotted.path/b/b1/realFilename"
        val mimeType = "image/jpeg"
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted
        assertEquals("realFilename.jpg", extractionResult.filename)
    }

    @Test
    fun whenUrlContainsFilenameAndContainsMultiplePathSegmentsAndMimeTypeProvidedThenFilenameShouldBeExtracted() {
        val url = "https://foo.example.com/path/images/b/b1/realFilename.jpg"
        val mimeType = "image/jpeg"
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted
        assertEquals("realFilename.jpg", extractionResult.filename)
    }

    @Test
    fun whenUrlContainsFilenameButContainsAdditionalPathSegmentsAndQueryParamsWhichLookLikeAFilenameThenFilenameShouldBeExtracted() {
        val url = "https://foo.example.com/path/images/b/b1/realFilename.jpg/other/stuff?cb=123.com"
        val mimeType: String? = null
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted
        assertEquals("realFilename.jpg", extractionResult.filename)
    }

    @Test
    fun whenUrlContainsFilenameButContentDispositionSaysOtherwiseThenExtractFromContentDisposition() {
        val url = "https://example.com/filename.jpg"
        val mimeType: String? = null
        val contentDisposition = "Content-Disposition: attachment; filename=fromDisposition.jpg"

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted
        assertEquals("fromDisposition.jpg", extractionResult.filename)
    }

    @Test
    fun whenFilenameEndsInBinThenThatIsExtracted() {
        val url = "https://example.com/realFilename.bin"
        val mimeType: String? = null
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted

        assertEquals("realFilename.bin", extractionResult.filename)
    }

    @Test
    fun whenUrlContainsNoFileNameButLotsOfPathsSegmentsThenFirstSegmentNameIsUsed() {
        val url = "https://example.com/foo/bar/files"
        val mimeType: String? = null
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Guess)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Guess
        assertEquals("foo", extractionResult.bestGuess)
    }

    @Test
    fun whenFilenameEndsInBinWithASlashThenThatIsExtracted() {
        val url = "https://example.com/realFilename.bin/"
        val mimeType: String? = null
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted

        assertEquals("realFilename.bin", extractionResult.filename)
    }

    @Test
    fun whenFilenameContainsBinThenThatIsExtracted() {
        val url = "https://example.com/realFilename.bin/foo/bar"
        val mimeType: String? = null
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted

        assertEquals("realFilename.bin", extractionResult.filename)
    }

    @Test
    fun whenUrlIsEmptyStringAndNoOtherDataProvidedThenDefaultNameFiletypeReturned() {
        val url = ""
        val mimeType: String? = null
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Guess)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Guess

        assertEquals("downloadfile", extractionResult.bestGuess)
    }

    @Test
    @SdkSuppress(maxSdkVersion = 21)
    fun whenUrlIsEmptyStringAndMimeTypeProvidedThenDefaultNameAndFiletypeFromMimeReturnedLollipop() {
        val url = ""
        val mimeType = "image/jpeg"
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Guess)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Guess

        assertEquals("downloadfile.jpeg", extractionResult.bestGuess)
    }

    @Test
    @SdkSuppress(minSdkVersion = 22)
    fun whenUrlIsEmptyStringAndMimeTypeProvidedThenDefaultNameAndFiletypeFromMimeReturned() {
        val url = ""
        val mimeType = "image/jpeg"
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted

        assertEquals("downloadfile.jpg", extractionResult.filename)
    }

    @Test
    fun whenUrlIsEmptyStringAndContentDispositionProvidedThenExtractFromContentDisposition() {
        val url = ""
        val mimeType: String? = null
        val contentDisposition = "Content-Disposition: attachment; filename=fromDisposition.jpg"

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted

        assertEquals("fromDisposition.jpg", extractionResult.filename)
    }

    @Test
    fun whenNoFilenameAndNoPathSegmentsThenDomainNameReturned() {
        val url = "http://example.com"
        val mimeType: String? = null
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted

        assertEquals("example.com", extractionResult.filename)
    }

    @Test
    fun whenNoFilenameAndPathSegmentsThenPathNameFileIsReturned() {
        val url = "http://example.com/cat/600/400"
        val mimeType: String? = null
        val contentDisposition: String? = null

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Guess)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Guess

        assertEquals("cat", extractionResult.bestGuess)
    }

    @Test
    fun whenNoFilenameAndPathSegmentsThenFirePixel() {
        val url = "http://example.com/cat/600/400"
        val mimeType: String? = null
        val contentDisposition: String? = null

        testee.extract(buildPendingDownload(url, contentDisposition, mimeType))

        verify(mockedPixel).fire(DownloadsPixelName.DOWNLOAD_FILE_DEFAULT_GUESSED_NAME)
    }

    @Test
    fun whenUrlContainsFilenameAndMimeTypeIsTextXPython3AndContentDispositionIsEmptyThenFilenameIsReturned() {
        val url = """
            https://ddg-name-test-ubsgiobgibsdgsbklsdjgm.netlify.app/uploads/qwertyuiopasdfghjklzxcvbnm1234567890qwertyuiopasdfghjklzxcvbnm/bat.py
        """.trimIndent()
        val mimeType = "text/x-python3; charset=UTF-8"
        val contentDisposition = ""

        val extractionResult = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertTrue(extractionResult is FilenameExtractor.FilenameExtractionResult.Extracted)

        extractionResult as FilenameExtractor.FilenameExtractionResult.Extracted

        assertEquals("bat.py", extractionResult.filename)
    }

    private fun buildPendingDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?
    ): PendingFileDownload {
        return PendingFileDownload(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            subfolder = "aFolder",
            userAgent = "aUserAgent"
        )
    }
}
