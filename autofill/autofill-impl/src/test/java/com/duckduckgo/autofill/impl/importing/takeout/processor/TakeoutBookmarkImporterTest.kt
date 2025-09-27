package com.duckduckgo.autofill.impl.importing.takeout.processor

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.service.ImportSavedSitesResult
import com.duckduckgo.savedsites.api.service.SavedSitesImporter
import com.duckduckgo.savedsites.api.service.SavedSitesImporter.ImportFolder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@RunWith(AndroidJUnit4::class)
class TakeoutBookmarkImporterTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockSavedSitesImporter = mock<SavedSitesImporter>()

    private val successfulResultNoneImported = ImportSavedSitesResult.Success(emptyList())
    private val successfulResultWithImports =
        ImportSavedSitesResult.Success(
            listOf(
                SavedSite.Bookmark("1", "Title 1", "http://example1.com", lastModified = "2023-01-01"),
                SavedSite.Bookmark("2", "Title 2", "http://example2.com", lastModified = "2023-01-01"),
            ),
        )
    private val failedResult = ImportSavedSitesResult.Error(Exception())

    private val testFolder = ImportFolder.Folder("Test Folder")

    private val testee =
        RealTakeoutBookmarkImporter(
            savedSitesImporter = mockSavedSitesImporter,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )

    @Test
    fun whenImportingToRootThenCallsSavedSitesImporterWithRoot() =
        runTest {
            configureSuccessfulResult()
            val tempFileUri = createTempFileWithContent(loadHtmlFile("valid_chrome_bookmarks_netscape"))

            testee.importBookmarks(tempFileUri, ImportFolder.Root)

            verify(mockSavedSitesImporter).import(any(), eq(ImportFolder.Root))
        }

    @Test
    fun whenImportingToRootThenCallsSavedSitesImporterWithTempFile() =
        runTest {
            configureSuccessfulResult()
            val tempFileUri = createTempFileWithContent(loadHtmlFile("valid_chrome_bookmarks_netscape"))

            testee.importBookmarks(tempFileUri, ImportFolder.Root)

            verify(mockSavedSitesImporter).import(eq(tempFileUri), any())
        }

    @Test
    fun whenImportingToFolderThenCallsSavedSitesImporterWithFolder() =
        runTest {
            configureSuccessfulResult()
            val tempFileUri = createTempFileWithContent(loadHtmlFile("valid_chrome_bookmarks_netscape"))

            testee.importBookmarks(tempFileUri, testFolder)

            verify(mockSavedSitesImporter).import(any(), eq(testFolder))
        }

    @Test
    fun whenImportingToFolderThenCallsSavedSitesImporterWithTempFile() =
        runTest {
            configureSuccessfulResult()
            val tempFileUri = createTempFileWithContent(loadHtmlFile("valid_chrome_bookmarks_netscape"))

            testee.importBookmarks(tempFileUri, testFolder)

            verify(mockSavedSitesImporter).import(eq(tempFileUri), any())
        }

    @Test
    fun whenImportSucceedsWithMultipleImportsThenReturnsSuccessResult() =
        runTest {
            configureResult(successfulResultWithImports)
            val tempFileUri = createTempFileWithContent(loadHtmlFile("valid_chrome_bookmarks_netscape"))

            val result = testee.importBookmarks(tempFileUri, ImportFolder.Root)

            assertTrue(result is ImportSavedSitesResult.Success)
            assertEquals(2, (result as ImportSavedSitesResult.Success).savedSites.size)
        }

    @Test
    fun whenImportSucceedsWithNoImportsThenReturnsSuccessResult() =
        runTest {
            configureResult(successfulResultNoneImported)
            val tempFileUri = createTempFileWithContent(loadHtmlFile("valid_chrome_bookmarks_netscape"))

            val result = testee.importBookmarks(tempFileUri, ImportFolder.Root)

            assertTrue(result is ImportSavedSitesResult.Success)
            assertEquals(0, (result as ImportSavedSitesResult.Success).savedSites.size)
        }

    @Test
    fun whenImportFailsThenReturnsErrorResult() =
        runTest {
            configureResult(failedResult)
            val tempFileUri = createTempFileWithContent(loadHtmlFile("valid_chrome_bookmarks_netscape"))

            val result = testee.importBookmarks(tempFileUri, ImportFolder.Root)

            assertTrue(result is ImportSavedSitesResult.Error)
        }

    @Test
    fun whenSavedSitesImporterThrowsExceptionThenReturnsErrorResult() =
        runTest {
            whenever(mockSavedSitesImporter.import(any(), any())).thenThrow(RuntimeException("Unexpected error"))
            val tempFileUri = createTempFileWithContent(loadHtmlFile("valid_chrome_bookmarks_netscape"))

            val result = testee.importBookmarks(tempFileUri, ImportFolder.Root)

            assertTrue(result is ImportSavedSitesResult.Error)
        }

    @Test
    fun whenImportSucceedsThenTempFileIsDeleted() =
        runTest {
            configureSuccessfulResult()
            val tempFileUri = createTempFileWithContent(loadHtmlFile("valid_chrome_bookmarks_netscape"))
            val tempFile = File(tempFileUri.path!!)

            assertTrue("Temp file should exist before import", tempFile.exists())
            testee.importBookmarks(tempFileUri, ImportFolder.Root)
            assertFalse("Temp file should be deleted after import", tempFile.exists())
        }

    @Test
    fun whenImportFailsThenTempFileIsStillDeleted() =
        runTest {
            configureResult(failedResult)
            val tempFileUri = createTempFileWithContent(loadHtmlFile("valid_chrome_bookmarks_netscape"))
            val tempFile = File(tempFileUri.path!!)

            assertTrue("Temp file should exist before import", tempFile.exists())
            testee.importBookmarks(tempFileUri, ImportFolder.Root)
            assertFalse("Temp file should be deleted even after failed import", tempFile.exists())
        }

    @Test
    fun whenImportThrowsExceptionThenTempFileIsStillDeleted() =
        runTest {
            whenever(mockSavedSitesImporter.import(any(), any())).thenThrow(RuntimeException("Unexpected error"))
            val tempFileUri = createTempFileWithContent(loadHtmlFile("valid_chrome_bookmarks_netscape"))
            val tempFile = File(tempFileUri.path!!)

            assertTrue("Temp file should exist before import", tempFile.exists())
            testee.importBookmarks(tempFileUri, ImportFolder.Root)
            assertFalse("Temp file should be deleted even when exception is thrown", tempFile.exists())
        }

    private suspend fun configureResult(result: ImportSavedSitesResult) {
        whenever(mockSavedSitesImporter.import(any(), any())).thenReturn(result)
    }

    private suspend fun configureSuccessfulResult() {
        whenever(mockSavedSitesImporter.import(any(), any())).thenReturn(successfulResultNoneImported)
    }

    private fun loadHtmlFile(filename: String): String =
        FileUtilities.loadText(
            TakeoutBookmarkImporterTest::class.java.classLoader!!,
            "html/$filename.html",
        )

    private fun createTempFileWithContent(content: String): Uri {
        val tempFile = File.createTempFile("test_bookmarks", ".html").also { it.writeText(content) }
        return Uri.fromFile(tempFile)
    }
}
