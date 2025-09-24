package com.duckduckgo.autofill.impl.importing.takeout.processor

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.service.ImportSavedSitesResult
import com.duckduckgo.savedsites.api.service.SavedSitesImporter
import com.duckduckgo.savedsites.api.service.SavedSitesImporter.ImportFolder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TakeoutBookmarkImporterTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockSavedSitesImporter = mock<SavedSitesImporter>()

    private val successfulResultNoneImported = ImportSavedSitesResult.Success(emptyList())
    private val successfulResultWithImports = ImportSavedSitesResult.Success(
        listOf(
            SavedSite.Bookmark("1", "Title 1", "http://example1.com", lastModified = "2023-01-01"),
            SavedSite.Bookmark("2", "Title 2", "http://example2.com", lastModified = "2023-01-01"),
        ),
    )
    private val failedResult = ImportSavedSitesResult.Error(Exception())

    private val testFolder = ImportFolder.Folder("Test Folder")

    private val testee = RealTakeoutBookmarkImporter(
        savedSitesImporter = mockSavedSitesImporter,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenImportingToRootThenCallsSavedSitesImporterWithRoot() = runTest {
        configureSuccessfulResult()
        testee.importBookmarks(loadHtmlFile("valid_chrome_bookmarks_netscape"), ImportFolder.Root)
        verify(mockSavedSitesImporter).import(any(), eq(ImportFolder.Root))
    }

    @Test
    fun whenImportingToFolderThenCallsSavedSitesImporterWithFolder() = runTest {
        configureSuccessfulResult()
        testee.importBookmarks(loadHtmlFile("valid_chrome_bookmarks_netscape"), testFolder)
        verify(mockSavedSitesImporter).import(any(), eq(testFolder))
    }

    @Test
    fun whenImportSucceedsWithMultipleImportsThenReturnsSuccessResult() = runTest {
        configureResult(successfulResultWithImports)

        val result = testee.importBookmarks(loadHtmlFile("valid_chrome_bookmarks_netscape"), ImportFolder.Root)

        assertTrue(result is ImportSavedSitesResult.Success)
        assertEquals(2, (result as ImportSavedSitesResult.Success).savedSites.size)
    }

    @Test
    fun whenImportSucceedsWithNoImportsThenReturnsSuccessResult() = runTest {
        configureResult(successfulResultNoneImported)

        val result = testee.importBookmarks(loadHtmlFile("valid_chrome_bookmarks_netscape"), ImportFolder.Root)

        assertTrue(result is ImportSavedSitesResult.Success)
        assertEquals(0, (result as ImportSavedSitesResult.Success).savedSites.size)
    }

    @Test
    fun whenImportFailsThenReturnsErrorResult() = runTest {
        configureResult(failedResult)

        val result = testee.importBookmarks(loadHtmlFile("valid_chrome_bookmarks_netscape"), ImportFolder.Root)

        assertTrue(result is ImportSavedSitesResult.Error)
    }

    @Test
    fun whenSavedSitesImporterThrowsExceptionThenReturnsErrorResult() = runTest {
        whenever(mockSavedSitesImporter.import(any(), any())).thenThrow(RuntimeException("Unexpected error"))
        val result = testee.importBookmarks(loadHtmlFile("valid_chrome_bookmarks_netscape"), ImportFolder.Root)
        assertTrue(result is ImportSavedSitesResult.Error)
    }

    private suspend fun configureResult(result: ImportSavedSitesResult) {
        whenever(mockSavedSitesImporter.import(any(), any())).thenReturn(result)
    }

    private suspend fun configureSuccessfulResult() {
        whenever(mockSavedSitesImporter.import(any(), any())).thenReturn(successfulResultNoneImported)
    }

    private fun loadHtmlFile(filename: String): String {
        return FileUtilities.loadText(
            TakeoutBookmarkImporterTest::class.java.classLoader!!,
            "html/$filename.html",
        )
    }
}
