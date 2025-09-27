package com.duckduckgo.autofill.impl.importing.takeout.processor

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor.*
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor.ImportResult.Error.DownloadError
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor.ImportResult.Error.ImportError
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor.ImportResult.Error.ParseError
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor.ImportResult.Success
import com.duckduckgo.autofill.impl.importing.takeout.zip.TakeoutBookmarkExtractor
import com.duckduckgo.autofill.impl.importing.takeout.zip.TakeoutBookmarkExtractor.ExtractionResult
import com.duckduckgo.autofill.impl.importing.takeout.zip.TakeoutZipDownloader
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.service.ImportSavedSitesResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TakeoutBookmarkImportProcessorTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val mockZipDownloader: TakeoutZipDownloader = mock()
    private val mockExtractor: TakeoutBookmarkExtractor = mock()
    private val mockImporter: TakeoutBookmarkImporter = mock()

    private val testee =
        TakeoutBookmarkImportProcessor(
            dispatchers = coroutineTestRule.testDispatcherProvider,
            takeoutZipDownloader = mockZipDownloader,
            bookmarkExtractor = mockExtractor,
            takeoutBookmarkImporter = mockImporter,
        )

    @Before
    fun setup() =
        runTest {
            configureDownloadSuccessful()
            configureExtractionSuccessful()
        }

    @Test
    fun whenImportSucceedsWithMultipleBookmarksThenReturnsSuccessResult() =
        runTest {
            configureImportSuccessful(multipleBookmarks())

            val result = triggerImport()

            assertTrue(result is Success)
            assertEquals(3, (result as Success).importedCount)
        }

    @Test
    fun whenImportSucceedsWithSingleBookmarkThenReturnsSuccessResult() =
        runTest {
            configureImportSuccessful(singleBookmark())

            val result = triggerImport()

            assertTrue(result is Success)
            assertEquals(1, (result as Success).importedCount)
        }

    @Test
    fun whenImportSucceedsWithEmptyListThenReturnsSuccessResult() =
        runTest {
            configureImportSuccessful(emptyBookmarkList())

            val result = triggerImport()

            assertTrue(result is Success)
            assertEquals(0, (result as Success).importedCount)
        }

    @Test
    fun whenDownloadFailsThenReturnsDownloadError() =
        runTest {
            configureDownloadFailure()

            val result = triggerImport()

            assertTrue(result is DownloadError)
        }

    @Test
    fun whenExtractionFailsThenReturnsParseError() =
        runTest {
            configureExtractionFailure()

            val result = triggerImport()

            assertTrue(result is ParseError)
        }

    @Test
    fun whenImportFailsThenReturnsImportError() =
        runTest {
            configureImportFailure()

            val result = triggerImport()

            assertTrue(result is ImportError)
        }

    private suspend fun triggerImport(): ImportResult = testee.downloadAndImportFromTakeoutZipUrl("aUrl", "aUserAgent", "aFolder")

    private suspend fun configureDownloadSuccessful() {
        val testUri = Uri.parse("file:///test/bookmarks.zip")
        whenever(mockZipDownloader.downloadZip(any(), any())).thenReturn(testUri)
    }

    private suspend fun configureDownloadFailure() {
        doAnswer { throw Exception("Download failed") }.whenever(mockZipDownloader).downloadZip(any(), any())
    }

    private suspend fun configureExtractionSuccessful() {
        val extractedUri = Uri.parse("file:///test/bookmarks.html")
        val extractionResult = ExtractionResult.Success(extractedUri)
        whenever(mockExtractor.extractBookmarksFromFile(any())).thenReturn(extractionResult)
    }

    private suspend fun configureExtractionFailure() {
        val extractionResult = ExtractionResult.Error(Exception("Extraction failed"))
        whenever(mockExtractor.extractBookmarksFromFile(any())).thenReturn(extractionResult)
    }

    private suspend fun configureImportSuccessful(bookmarks: List<SavedSite>) {
        val importResult = ImportSavedSitesResult.Success(bookmarks)
        whenever(mockImporter.importBookmarks(any(), any())).thenReturn(importResult)
    }

    private suspend fun configureImportFailure() {
        val importResult = ImportSavedSitesResult.Error(Exception("Import failed"))
        whenever(mockImporter.importBookmarks(any(), any())).thenReturn(importResult)
    }

    private fun multipleBookmarks(): List<SavedSite> =
        listOf(
            Bookmark("1", "Test Bookmark 1", "https://example1.com", lastModified = null),
            Bookmark("2", "Test Bookmark 2", "https://example2.com", lastModified = null),
            Bookmark("3", "Test Bookmark 3", "https://example3.com", lastModified = null),
        )

    private fun emptyBookmarkList(): List<SavedSite> = emptyList()

    private fun singleBookmark(): List<SavedSite> = listOf(Bookmark("1", "Only Bookmark", "https://example.com", lastModified = null))
}
