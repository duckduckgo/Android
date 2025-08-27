package com.duckduckgo.autofill.impl.importing.gpm.webflow

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.importing.takeout.zip.BookmarkZipEntryContentReader
import com.duckduckgo.autofill.impl.importing.takeout.zip.TakeoutBookmarkExtractor
import com.duckduckgo.autofill.impl.importing.takeout.zip.TakeoutBookmarkExtractor.ExtractionResult.Success
import com.duckduckgo.autofill.impl.importing.takeout.zip.TakeoutZipBookmarkExtractor
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TakeoutZipBookmarkExtractorTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockContext = mock<Context>()
    private val mockUri = mock<Uri>()
    private val zipEntryContentReader = BookmarkZipEntryContentReader()
    private val testee = TakeoutZipBookmarkExtractor(
        context = mockContext,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        zipEntryContentReader = zipEntryContentReader,
    )

    @Before
    fun setup() {
        whenever(mockContext.contentResolver).thenReturn(mock())
    }

    @Test
    fun whenValidZipWithBookmarksHtmlThenExtractionSucceeds() = runTest {
        val bookmarkContent = loadHtmlFile("valid_chrome_bookmarks_netscape")
        val zipData = createZipWithEntry("Takeout/Chrome/Bookmarks.html", bookmarkContent)
        mockFileUri(mockUri, zipData)

        val result = testee.extractBookmarksHtml(mockUri)

        assertTrue(result is Success)
        assertEquals(bookmarkContent, (result as Success).bookmarkHtmlContent)
    }

    @Test
    fun whenZipContainsMultipleEntriesButOnlyOneBookmarkThenCorrectFileExtracted() = runTest {
        val bookmarkContent = loadHtmlFile("valid_chrome_bookmarks_netscape")
        val zipData = createZipWithMultipleEntries(
            mapOf(
                "Takeout/Gmail/contacts.csv" to "email data",
                "Takeout/Chrome/History" to "history data",
                "Takeout/Chrome/Bookmarks.html" to bookmarkContent,
                "Takeout/YouTube/subscriptions.json" to "youtube data",
            ),
        )
        mockFileUri(mockUri, zipData)

        val result = testee.extractBookmarksHtml(mockUri)

        assertTrue(result is Success)
        assertEquals(bookmarkContent, (result as Success).bookmarkHtmlContent)
    }

    @Test
    fun whenBookmarksFileFoundButContentInvalidThenExtractionFails() = runTest {
        val invalidContent = loadHtmlFile("invalid_bookmark_content")
        val zipData = createZipWithEntry("Takeout/Chrome/Bookmarks.html", invalidContent)
        mockFileUri(mockUri, zipData)

        val result = testee.extractBookmarksHtml(mockUri)

        assertTrue(result is TakeoutBookmarkExtractor.ExtractionResult.Error)
    }

    @Test
    fun whenZipDoesNotContainBookmarksFileThenExtractionFails() = runTest {
        val zipData = createZipWithEntry("Takeout/Gmail/contacts.csv", "email data")
        mockFileUri(mockUri, zipData)

        val result = testee.extractBookmarksHtml(mockUri)

        assertTrue(result is TakeoutBookmarkExtractor.ExtractionResult.Error)
    }

    @Test
    fun whenEmptyZipThenExtractionFails() = runTest {
        val emptyZipData = createEmptyZip()
        mockFileUri(mockUri, emptyZipData)

        val result = testee.extractBookmarksHtml(mockUri)

        assertTrue(result is TakeoutBookmarkExtractor.ExtractionResult.Error)
    }

    @Test
    fun whenFileCannotBeOpenedThenExtractionFails() = runTest {
        whenever(mockContext.contentResolver.openInputStream(mockUri)).thenReturn(null)

        val result = testee.extractBookmarksHtml(mockUri)

        assertTrue(result is TakeoutBookmarkExtractor.ExtractionResult.Error)
    }

    @Test
    fun whenBookmarkHtmlHasNetscapeHeaderThenValidationPasses() = runTest {
        val content = loadHtmlFile("valid_chrome_bookmarks_netscape")
        val zipData = createZipWithEntry("Takeout/Chrome/Bookmarks.html", content)
        mockFileUri(mockUri, zipData)

        val result = testee.extractBookmarksHtml(mockUri)

        assertTrue(result is Success)
    }

    @Test
    fun whenBookmarkHtmlHasBookmarkTitleThenValidationPasses() = runTest {
        val content = loadHtmlFile("valid_chrome_bookmarks_title_only")
        val zipData = createZipWithEntry("Takeout/Chrome/Bookmarks.html", content)
        mockFileUri(mockUri, zipData)

        val result = testee.extractBookmarksHtml(mockUri)

        assertTrue(result is Success)
    }

    @Test
    fun whenBookmarkHtmlContainsMixedValidAndInvalidBookmarksThenValidationStillPasses() = runTest {
        val content = loadHtmlFile("mixed_valid_invalid_bookmarks")
        val zipData = createZipWithEntry("Takeout/Chrome/Bookmarks.html", content)
        mockFileUri(mockUri, zipData)

        val result = testee.extractBookmarksHtml(mockUri)

        // Should still pass validation because it has valid bookmark structure
        assertTrue(result is Success)
        assertEquals(content, (result as Success).bookmarkHtmlContent)
    }

    private fun loadHtmlFile(filename: String): String {
        return FileUtilities.loadText(
            TakeoutZipBookmarkExtractorTest::class.java.classLoader!!,
            "html/$filename.html",
        )
    }

    private fun createZipWithEntry(entryName: String, content: String): ByteArray {
        return createZipWithMultipleEntries(mapOf(entryName to content))
    }

    private fun createZipWithMultipleEntries(entries: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, content) ->
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun createEmptyZip(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { /* empty zip */ }
        return baos.toByteArray()
    }

    private fun mockFileUri(uri: Uri, zipData: ByteArray) {
        val inputStream: InputStream = ByteArrayInputStream(zipData)
        whenever(mockContext.contentResolver.openInputStream(uri)).thenReturn(inputStream)
    }
}
