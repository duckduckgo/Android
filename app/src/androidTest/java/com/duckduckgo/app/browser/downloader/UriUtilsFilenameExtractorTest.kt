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

import androidx.test.filters.SdkSuppress
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class UriUtilsFilenameExtractorTest {

    private val mockedPixel: Pixel = mock()
    private val testee: FilenameExtractor = FilenameExtractor(mockedPixel)

    @Test
    fun whenUrlEndsWithFilenameAsJpgNoMimeOrContentDispositionThenFilenameShouldBeExtracted() {
        val url = "https://example.com/realFilename.jpg"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("realFilename.jpg", extracted)
    }

    @Test
    fun whenUrlContainsFilenameButContainsAdditionalPathSegmentsThenFilenameShouldBeExtracted() {
        val url = "https://foo.example.com/path/images/b/b1/realFilename.jpg/other/stuff"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("realFilename.jpg", extracted)
    }

    @Test
    fun whenUrlContainsFilenameButContainsAdditionalPathSegmentsAndQueryParamsThenFilenameShouldBeExtracted() {
        val url = "https://foo.example.com/path/images/b/b1/realFilename.jpg/other/stuff?cb=123"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("realFilename.jpg", extracted)
    }

    @Test
    fun whenUrlContainsFilenameButContainsAdditionalPathSegmentsAndQueryParamsWhichLookLikeAFilenameThenFilenameShouldBeExtracted() {
        val url = "https://foo.example.com/path/images/b/b1/realFilename.jpg/other/stuff?cb=123.com"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("realFilename.jpg", extracted)
    }

    @Test
    fun whenUrlContainsFilenameButContentDispositionSaysOtherwiseThenExtractFromContentDisposition() {
        val url = "https://example.com/filename.jpg"
        val mimeType: String? = null
        val contentDisposition: String = "Content-Disposition: attachment; filename=fromDisposition.jpg"
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("fromDisposition.jpg", extracted)
    }

    @Test
    fun whenFilenameEndsInBinThenThatIsExtracted() {
        val url = "https://example.com/realFilename.bin"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("realFilename.bin", extracted)
    }

    @Test
    fun whenUrlContainsNoFileNameButLotsOfPathsSegmentsThenFirstSegmentNameIsUsed() {
        val url = "https://example.com/foo/bar/files"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("foo.bin", extracted)
    }

    @Test
    fun whenFilenameEndsInBinWithASlashThenThatIsExtracted() {
        val url = "https://example.com/realFilename.bin/"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("realFilename.bin", extracted)
    }

    @Test
    fun whenFilenameContainsBinThenThatIsExtracted() {
        val url = "https://example.com/realFilename.bin/foo/bar"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("realFilename.bin", extracted)
    }

    @Test
    fun whenUrlIsEmptyStringAndNoOtherDataProvidedThenDefaultNameAndBinFiletypeReturned() {
        val url = ""
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("downloadfile.bin", extracted)
    }

    @Test
    @SdkSuppress(maxSdkVersion = 21)
    fun whenUrlIsEmptyStringAndMimeTypeProvidedThenDefaultNameAndFiletypeFromMimeReturnedLollipop() {
        val url = ""
        val mimeType = "image/jpeg"
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("downloadfile.jpeg", extracted)
    }

    @Test
    @SdkSuppress(minSdkVersion = 22)
    fun whenUrlIsEmptyStringAndMimeTypeProvidedThenDefaultNameAndFiletypeFromMimeReturned() {
        val url = ""
        val mimeType = "image/jpeg"
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("downloadfile.jpg", extracted)
    }

    @Test
    fun whenUrlIsEmptyStringAndContentDispositionProvidedThenExtractFromContentDisposition() {
        val url = ""
        val mimeType: String? = null
        val contentDisposition = "Content-Disposition: attachment; filename=fromDisposition.jpg"
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("fromDisposition.jpg", extracted)
    }

    @Test
    fun whenNoFilenameAndNoPathSegmentsThenDomainNameReturned() {
        val url = "http://example.com"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("example.com", extracted)
    }

    @Test
    fun whenNoFilenameAndPathSegmentsThenPathNameBinFileIsReturned() {
        val url = "http://example.com/cat/600/400"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("cat.bin", extracted)
    }

    @Test
    fun whenNoFilenameAndPathSegmentsThenFirePixel() {
        val url = "http://example.com/cat/600/400"
        val mimeType: String? = null
        val contentDisposition: String? = null
        testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        verify(mockedPixel).fire(AppPixelName.DOWNLOAD_FILE_DEFAULT_GUESSED_NAME)
    }

    private fun buildPendingDownload(url: String, contentDisposition: String?, mimeType: String?): FileDownloader.PendingFileDownload {
        return FileDownloader.PendingFileDownload(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            subfolder = "aFolder",
            userAgent = "aUserAgent"
        )
    }

    @Test
    fun whenTwitterImageUrlThenProperUriIsReturned() {
        val url = "https://pbs.twimg.com/media/E317JKmWEAYaUuo?format=jpg&name=medium"
        val mimeType: String? = null
        val contentDisposition: String? = null
        val extracted = testee.extract(buildPendingDownload(url, contentDisposition, mimeType))
        assertEquals("cat.bin", extracted)
    }
}
