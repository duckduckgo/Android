package com.duckduckgo.autofill.impl.importing.takeout.webflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.Command
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.Command.PromptUserToConfirmFlowCancellation
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.HideWebPage
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.NavigatingBack
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.ShowWebPage
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
    private val mockWebFlowStepObserver: BookmarkImportWebFlowStepObserver = mock()

    private val testee = ImportGoogleBookmarksWebFlowViewModel(
        dispatchers = coroutineTestRule.testDispatcherProvider,
        reauthenticationHandler = mock(),
        autofillFeature = mock(),
        bookmarkImportProcessor = mockBookmarkImportProcessor,
        bookmarkImportConfigStore = mock(),
        takeoutWebMessageParser = mock(),
        webFlowStepObserver = mockWebFlowStepObserver,
    )

    @Test
    fun whenOnPageStartedWithTakeoutUrlThenHideWebPage() = runTest {
        testee.onPageStarted("https://takeout.google.com")
        assertEquals(HideWebPage, testee.viewState.value)
    }

    @Test
    fun whenOnPageStartedWithUppercaseTakeoutUrlThenHideWebPage() = runTest {
        testee.onPageStarted("https://TAKEOUT.GOOGLE.COM")
        assertEquals(HideWebPage, testee.viewState.value)
    }

    @Test
    fun whenOnPageStartedWithAccountsGoogleUrlThenShowWebPage() = runTest {
        testee.onPageStarted("https://accounts.google.com/signin")
        assertEquals(ShowWebPage, testee.viewState.value)
    }

    @Test
    fun whenOnPageStartedWithExampleUrlThenShowWebPage() = runTest {
        testee.onPageStarted("https://example.com/page")
        assertEquals(ShowWebPage, testee.viewState.value)
    }

    @Test
    fun whenOnPageStartedWithAccountsGoogleUrlContainingTakeoutInPathThenShowWebPage() = runTest {
        testee.onPageStarted("https://accounts.google.com/signin?continue=https://takeout.google.com")
        assertEquals(ShowWebPage, testee.viewState.value)
    }

    @Test
    fun whenOnPageStartedWithNullUrlThenViewStateRemainsUnchanged() = runTest {
        val initialState = testee.viewState.value
        testee.onPageStarted(null)
        assertEquals(initialState, testee.viewState.value)
    }

    @Test
    fun whenOnPageStartedWithMalformedUrlThenViewStateRemainsUnchanged() = runTest {
        val initialState = testee.viewState.value
        testee.onPageStarted("not-a-valid-url")
        assertEquals(initialState, testee.viewState.value)
    }

    @Test
    fun whenFirstPageLoadingThenShowWebPageState() = runTest {
        testee.firstPageLoading()
        assertEquals(ShowWebPage, testee.viewState.value)
    }

    @Test
    fun whenBackButtonPressedAndCannotGoBackThenPromptUserToConfirmFlowCancellation() = runTest {
        testee.commands.test {
            testee.onBackButtonPressed(canGoBack = false)
            val command = awaitItem()
            assertTrue(command is PromptUserToConfirmFlowCancellation)
        }
    }

    @Test
    fun whenBackButtonPressedAndCanGoBackThenNavigatingBackState() = runTest {
        testee.onBackButtonPressed(canGoBack = true)
        assertEquals(NavigatingBack, testee.viewState.value)
    }

    @Test
    fun whenDownloadDetectedWithValidZipThenProcessorCalled() = runTest {
        configureImportSuccessful()

        testee.commands.test {
            triggerDownloadDetectedWithTakeoutZip()
            awaitItem()
            verify(mockBookmarkImportProcessor).downloadAndImportFromTakeoutZipUrl(any(), any(), any())
        }
    }

    @Test
    fun whenDownloadDetectedWithValidZipButFailsToDownloadThenExitFlowAsFailureCommandEmitted() = runTest {
        configureImportFailure()

        testee.commands.test {
            triggerDownloadDetectedWithTakeoutZip()
            awaitItem() as Command.ExitFlowAsFailure
        }
    }

    @Test
    fun whenDownloadDetectedWithInvalidTypeThenExitFlowAsFailureCommandEmitted() = runTest {
        testee.commands.test {
            triggerDownloadDetectedButNotATakeoutZip()
            awaitItem() as Command.ExitFlowAsFailure
        }
    }

    @Test
    fun whenUpdateLatestStepLoginPageFromUninitializedThenSetsToFirstVisit() = runTest {
        testee.onPageStarted("https://accounts.google.com")
        assertEquals(ShowWebPage, testee.viewState.value)
    }

    @Test
    fun whenFatalWebViewErrorEncounteredThenExitFlowAsFailure() = runTest {
        whenever(mockWebFlowStepObserver.getCurrentStep()).thenReturn("some-step")
        testee.commands.test {
            testee.onFatalWebViewError()
            val command = awaitItem() as Command.ExitFlowAsFailure
            assertTrue(command.reason is UserCannotImportReason.WebViewError)
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
