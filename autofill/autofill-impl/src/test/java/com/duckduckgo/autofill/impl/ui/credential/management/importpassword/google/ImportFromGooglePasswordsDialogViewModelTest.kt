package com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.api.AutofillImportLaunchSource.InBrowserPromo
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.Finished
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.InProgress
import com.duckduckgo.autofill.impl.importing.capability.ImportGooglePasswordsCapabilityChecker
import com.duckduckgo.autofill.impl.importing.credentialtransfer.CredentialExchangeImportResult
import com.duckduckgo.autofill.impl.importing.credentialtransfer.CredentialExchangePasswordImporter
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.ImportPasswordsPixelSender
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.Command
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.DeterminingFirstView
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.ImportSuccess
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.Importing
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.PreImport
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewState
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult
import com.duckduckgo.promptscoordinator.api.PromptExposureReporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ImportFromGooglePasswordsDialogViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val importPasswordsPixelSender: ImportPasswordsPixelSender = mock()

    private val credentialImporter: CredentialImporter = mock()
    private val autofillStore: InternalAutofillStore = mock()
    private val promptExposureReporter: PromptExposureReporter = mock()
    private val credentialExchangePasswordImporter: CredentialExchangePasswordImporter = mock()
    private val webViewCapabilityChecker: ImportGooglePasswordsCapabilityChecker = mock()
    private val testee = ImportFromGooglePasswordsDialogViewModel(
        credentialImporter = credentialImporter,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        importPasswordsPixelSender = importPasswordsPixelSender,
        autofillStore = autofillStore,
        promptExposureReporter = promptExposureReporter,
        credentialExchangePasswordImporter = credentialExchangePasswordImporter,
        webViewCapabilityChecker = webViewCapabilityChecker,
    )

    @Before
    fun setup() = runTest {
        whenever(credentialImporter.getImportStatus()).thenReturn(emptyFlow())
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(false)
        whenever(webViewCapabilityChecker.webViewCapableOfImporting()).thenReturn(true)
    }

    @Test
    fun whenInBrowserPromoShownThenPromptExposureIsReported() {
        testee.shouldShowInitialInstructionalPrompt(importSource = AutofillImportLaunchSource.InBrowserPromo)

        verify(promptExposureReporter).reportPromptShown("import_passwords_google")
    }

    @Test
    fun whenImportDialogOpenedFromAnyOtherSourceThenPromptExposureIsNotReported() {
        AutofillImportLaunchSource.entries
            .filter { it != AutofillImportLaunchSource.InBrowserPromo }
            .forEach { testee.shouldShowInitialInstructionalPrompt(importSource = it) }

        verifyNoInteractions(promptExposureReporter)
    }

    @Test
    fun whenParsingErrorOnImportThenViewModeUpdatedToError() = runTest {
        testee.onImportFlowFinishedWithError()
        testee.viewState.test {
            assertTrue(awaitItem().viewMode is ViewMode.ImportError)
        }
    }

    @Test
    fun whenSuccessfulImportThenViewModeUpdatedToInProgress() = runTest {
        configureImportInProgress()
        showPreImportPrompt()
        testee.onImportFlowFinishedSuccessfully()
        testee.viewState.test {
            awaitImportInProgress()
        }
    }

    @Test
    fun whenSuccessfulImportFlowThenImportFinishesNothingImportedThenViewModeUpdatedToResults() = runTest {
        configureImportFinished(savedCredentials = 0, numberSkipped = 0)
        showPreImportPrompt()
        testee.onImportFlowFinishedSuccessfully()
        testee.viewState.test {
            awaitImportSuccess()
        }
    }

    @Test
    fun whenSuccessfulImportFlowThenImportFinishesCredentialsImportedNoDuplicatesThenViewModeUpdatedToResults() = runTest {
        configureImportFinished(savedCredentials = 10, numberSkipped = 0)
        showPreImportPrompt()
        testee.onImportFlowFinishedSuccessfully()
        testee.viewState.test {
            val result = awaitImportSuccess()
            assertEquals(10, result.importResult.savedCredentials)
            assertEquals(0, result.importResult.numberSkipped)
        }
    }

    @Test
    fun whenSuccessfulImportFlowThenImportFinishesOnlyDuplicatesThenViewModeUpdatedToResults() = runTest {
        configureImportFinished(savedCredentials = 0, numberSkipped = 2)
        showPreImportPrompt()
        testee.onImportFlowFinishedSuccessfully()
        testee.viewState.test {
            val result = awaitImportSuccess()
            assertEquals(0, result.importResult.savedCredentials)
            assertEquals(2, result.importResult.numberSkipped)
        }
    }

    @Test
    fun whenSuccessfulImportNoUpdatesThenThenViewModeFirstInitialisedToPreImport() = runTest {
        showPreImportPrompt()
        testee.onImportFlowFinishedSuccessfully()
        testee.viewState.test {
            awaitItem().assertIsPreImport()
        }
    }

    @Test
    fun whenFirstCreatedPreImportNotRequiredThenViewModeFirstInitialisedToDeterminingView() = runTest {
        testee.viewState.test {
            awaitItem().assertIsDeterminingFirstViewToShow()
        }
    }

    @Test
    fun whenFirstCreatedPreImportRequiredThenViewModeFirstInitialisedToPreImportView() = runTest {
        showPreImportPrompt()
        testee.viewState.test {
            awaitItem().assertIsPreImport()
        }
    }

    @Test
    fun whenInBrowserPromoDismissedThenPixelSent() = runTest {
        testee.onInBrowserPromoDismissed()
        advanceUntilIdle()
        verify(importPasswordsPixelSender).onUserCancelledImportPasswordsDialog(InBrowserPromo)
    }

    @Test
    fun whenInBrowserPromoDismissedThenDeclineRecorded() = runTest {
        testee.onInBrowserPromoDismissed()
        advanceUntilIdle()
        verify(autofillStore).hasDeclinedInBrowserPasswordImportPromo = true
    }

    @Test
    fun whenCredentialExchangeSupportedThenPreImportUsesCredentialExchange() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)
        showPreImportPrompt()
        assertTrue(testee.viewState.value.usesCredentialExchange)
    }

    @Test
    fun whenCredentialExchangeNotSupportedThenPreImportUsesWebFlow() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(false)
        showPreImportPrompt()
        assertFalse(testee.viewState.value.usesCredentialExchange)
    }

    @Test
    fun whenImportClickedWithCredentialExchangeThenCredentialExchangeStarted() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)
        showPreImportPrompt()

        testee.commands().test {
            testee.onImportButtonClicked()
            assertEquals(Command.StartCredentialExchange, awaitItem())
        }
    }

    @Test
    fun whenImportClickedTwiceDuringCredentialExchangeThenOnlyOneExchangeStarted() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)
        showPreImportPrompt()

        testee.commands().test {
            testee.onImportButtonClicked()
            testee.onImportButtonClicked()
            assertEquals(Command.StartCredentialExchange, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun whenCredentialExchangeFinishedThenImportCanStartAgain() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(CredentialExchangeResult.Cancelled))
            .thenReturn(CredentialExchangeImportResult.Cancelled)
        showPreImportPrompt()

        testee.commands().test {
            testee.onImportButtonClicked()
            assertEquals(Command.StartCredentialExchange, awaitItem())
            testee.onCredentialExchangeFinished(CredentialExchangeResult.Cancelled, TEST_SOURCE, canShowPreImportDialog = true)
            testee.onImportButtonClicked()
            assertEquals(Command.StartCredentialExchange, awaitItem())
        }
    }

    @Test
    fun whenImportProgressesThenCredentialExchangeChoiceKept() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)
        configureImportInProgress()
        showPreImportPrompt()

        testee.onImportFlowFinishedSuccessfully()
        advanceUntilIdle()

        testee.viewState.value.assertIsImporting()
        assertTrue(testee.viewState.value.usesCredentialExchange)
    }

    @Test
    fun whenImportClickedWithoutCredentialExchangeThenWebFlowStarted() = runTest {
        showPreImportPrompt()

        testee.commands().test {
            testee.onImportButtonClicked()
            assertEquals(Command.StartWebFlow, awaitItem())
        }
    }

    @Test
    fun whenDirectImportRequestedAndCredentialExchangeSupportedThenCredentialExchangeStarted() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)

        testee.commands().test {
            testee.onDirectImportRequested()
            assertEquals(Command.StartCredentialExchange, awaitItem())
        }
    }

    @Test
    fun whenDirectImportRequestedAndCredentialExchangeNotSupportedThenWebFlowStarted() = runTest {
        testee.commands().test {
            testee.onDirectImportRequested()
            assertEquals(Command.StartWebFlow, awaitItem())
        }
    }

    @Test
    fun whenCredentialExchangeSucceedsThenCredentialsImported() = runTest {
        val credentials = listOf(LoginCredentials(domain = "example.com", username = "u", password = "p"))
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(EXCHANGE_SUCCESS))
            .thenReturn(CredentialExchangeImportResult.Success(credentials, originalCount = 3))

        testee.onCredentialExchangeFinished(EXCHANGE_SUCCESS, TEST_SOURCE, canShowPreImportDialog = true)
        advanceUntilIdle()

        verify(credentialImporter).import(credentials, 3, TEST_SOURCE)
    }

    @Test
    fun whenCredentialExchangeCancelledWithPreImportDialogThenDialogStays() = runTest {
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(CredentialExchangeResult.Cancelled))
            .thenReturn(CredentialExchangeImportResult.Cancelled)
        showPreImportPrompt()

        testee.onCredentialExchangeFinished(CredentialExchangeResult.Cancelled, TEST_SOURCE, canShowPreImportDialog = true)
        advanceUntilIdle()

        testee.viewState.value.assertIsPreImport()
    }

    @Test
    fun whenCredentialExchangeCancelledWithoutPreImportDialogThenFlowTerminated() = runTest {
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(CredentialExchangeResult.Cancelled))
            .thenReturn(CredentialExchangeImportResult.Cancelled)

        testee.onCredentialExchangeFinished(CredentialExchangeResult.Cancelled, TEST_SOURCE, canShowPreImportDialog = false)
        advanceUntilIdle()

        assertTrue(testee.viewState.value.viewMode is ViewMode.FlowTerminated)
    }

    @Test
    fun whenCredentialExchangeFailsThenWebFlowStartedAndNothingImported() = runTest {
        val failure = CredentialExchangeResult.Failure(CredentialExchangeFailure.NO_EXPORTER_AVAILABLE)
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(failure))
            .thenReturn(CredentialExchangeImportResult.Failure(CredentialExchangeFailure.NO_EXPORTER_AVAILABLE))

        testee.commands().test {
            testee.onCredentialExchangeFinished(failure, TEST_SOURCE, canShowPreImportDialog = true)
            assertEquals(Command.StartWebFlow, awaitItem())
        }
        verify(credentialImporter, never()).import(any(), any(), any())
    }

    @Test
    fun whenCredentialExchangeFailsAndWebViewCannotImportThenErrorShownAndNoWebFlow() = runTest {
        whenever(webViewCapabilityChecker.webViewCapableOfImporting()).thenReturn(false)
        val failure = CredentialExchangeResult.Failure(CredentialExchangeFailure.NO_EXPORTER_AVAILABLE)
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(failure))
            .thenReturn(CredentialExchangeImportResult.Failure(CredentialExchangeFailure.NO_EXPORTER_AVAILABLE))

        testee.commands().test {
            testee.onCredentialExchangeFinished(failure, TEST_SOURCE, canShowPreImportDialog = true)
            advanceUntilIdle()
            expectNoEvents()
        }
        assertTrue(testee.viewState.value.viewMode is ViewMode.ImportError)
    }

    private fun TestScope.showPreImportPrompt() {
        testee.shouldShowInitialInstructionalPrompt(importSource = TEST_SOURCE)
        advanceUntilIdle()
    }

    private fun configureImportInProgress() {
        whenever(credentialImporter.getImportStatus()).thenReturn(listOf(InProgress).asFlow())
    }

    private fun configureImportFinished(
        savedCredentials: Int,
        numberSkipped: Int,
    ) {
        whenever(credentialImporter.getImportStatus()).thenReturn(
            listOf(
                InProgress,
                Finished(savedCredentials = savedCredentials, numberSkipped = numberSkipped, source = TEST_SOURCE),
            ).asFlow(),
        )
    }

    private suspend fun TurbineTestContext<ViewState>.awaitImportSuccess(): ImportSuccess {
        awaitItem().assertIsPreImport()
        awaitItem().assertIsImporting()
        return awaitItem().viewMode as ImportSuccess
    }

    private suspend fun TurbineTestContext<ViewState>.awaitImportInProgress(): Importing {
        awaitItem().assertIsPreImport()
        return awaitItem().viewMode as Importing
    }

    private fun ViewState.assertIsPreImport() {
        assertTrue(viewMode is PreImport)
    }

    private fun ViewState.assertIsImporting() {
        assertTrue(viewMode is Importing)
    }

    private fun ViewState.assertIsDeterminingFirstViewToShow() {
        assertTrue(viewMode is DeterminingFirstView)
    }

    companion object {
        private val TEST_SOURCE = AutofillImportLaunchSource.PasswordManagementEmptyState
        private val EXCHANGE_SUCCESS = CredentialExchangeResult.Success(emptyList(), exporterPackageName = null)
    }
}
