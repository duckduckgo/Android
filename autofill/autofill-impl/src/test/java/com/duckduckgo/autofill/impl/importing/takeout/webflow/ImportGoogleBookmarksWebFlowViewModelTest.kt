package com.duckduckgo.autofill.impl.importing.takeout.webflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.Command
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.NavigatingBack
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.ShowWebPage
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.UserCancelledImportFlow
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ImportGoogleBookmarksWebFlowViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockBookmarkImportProcessor: BookmarkImportProcessor = mock()

    private val testee =
        ImportGoogleBookmarksWebFlowViewModel(
            dispatchers = coroutineTestRule.testDispatcherProvider,
            reauthenticationHandler = mock(),
            autofillFeature = mock(),
            bookmarkImportProcessor = mockBookmarkImportProcessor,
            bookmarkImportConfigStore = mock(),
        )

    @Test
    fun whenFirstPageLoadingThenShowWebPageState() =
        runTest {
            testee.firstPageLoading()
            assertEquals(ShowWebPage, testee.viewState.value)
        }

    @Test
    fun whenBackButtonPressedAndCannotGoBackThenUserCancelledState() =
        runTest {
            testee.onBackButtonPressed(canGoBack = false)
            assertTrue(testee.viewState.value is UserCancelledImportFlow)
        }

    @Test
    fun whenBackButtonPressedAndCanGoBackThenNavigatingBackState() =
        runTest {
            testee.onBackButtonPressed(canGoBack = true)
            assertEquals(NavigatingBack, testee.viewState.value)
        }

    @Test
    fun whenDownloadDetectedWithValidZipThenProcessorCalled() =
        runTest {
            configureImportSuccessful()

            testee.commands.test {
                triggerDownloadDetectedWithTakeoutZip()
                awaitItem()
                verify(mockBookmarkImportProcessor).downloadAndImportFromTakeoutZipUrl(any(), any(), any())
            }
        }

    @Test
    fun whenDownloadDetectedWithValidZipButFailsToDownloadThenExitFlowAsFailureCommandEmitted() =
        runTest {
            configureImportFailure()

            testee.commands.test {
                triggerDownloadDetectedWithTakeoutZip()
                awaitItem() as Command.ExitFlowAsFailure
            }
        }

    @Test
    fun whenDownloadDetectedWithInvalidTypeThenExitFlowAsFailureCommandEmitted() =
        runTest {
            testee.commands.test {
                triggerDownloadDetectedButNotATakeoutZip()
                awaitItem() as Command.ExitFlowAsFailure
            }
        }

    private fun triggerDownloadDetectedWithTakeoutZip() {
        testee.onDownloadDetected(
            url = "https://example.com/valid-file.zip",
            userAgent = "Mozilla/5.0",
            contentDisposition = null,
            mimeType = "application/zip",
            folderName = "a folder name",
        )
    }

    private fun triggerDownloadDetectedButNotATakeoutZip() {
        testee.onDownloadDetected(
            url = "https://example.com/image.jpg",
            userAgent = "Mozilla/5.0",
            contentDisposition = null,
            mimeType = "image/jpeg",
            folderName = "a folder name",
        )
    }

    private suspend fun configureImportSuccessful() {
        whenever(mockBookmarkImportProcessor.downloadAndImportFromTakeoutZipUrl(any(), any(), any()))
            .thenReturn(BookmarkImportProcessor.ImportResult.Success(10))
    }

    private suspend fun configureImportFailure() {
        whenever(mockBookmarkImportProcessor.downloadAndImportFromTakeoutZipUrl(any(), any(), any()))
            .thenReturn(BookmarkImportProcessor.ImportResult.Error.DownloadError)
    }
}
