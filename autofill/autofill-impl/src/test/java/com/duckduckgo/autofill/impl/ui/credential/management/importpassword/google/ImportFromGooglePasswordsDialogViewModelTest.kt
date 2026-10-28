package com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google

import android.content.Context
import android.view.View
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.api.AutofillImportLaunchSource.InBrowserPromo
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin
import com.duckduckgo.autofill.impl.importing.AutofillImportRedesignFeature
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.Finished
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.InProgress
import com.duckduckgo.autofill.impl.importing.capability.ImportGooglePasswordsCapabilityChecker
import com.duckduckgo.autofill.impl.importing.credentialtransfer.CredentialExchangeImportResult
import com.duckduckgo.autofill.impl.importing.credentialtransfer.CredentialExchangePasswordImporter
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.ErrorParsingCsv
import com.duckduckgo.autofill.impl.importing.wideevents.CredentialImportWideEvent
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
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.promptscoordinator.api.PromptExposureReporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.milliseconds

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
    private val credentialImportWideEvent: CredentialImportWideEvent = mock()
    private val importRedesignFeature = FakeFeatureToggleFactory.create(AutofillImportRedesignFeature::class.java)
    private val promotionPlugins = mutableListOf<PasswordsImportScreenPromotionPlugin>()
    private val testee = ImportFromGooglePasswordsDialogViewModel(
        credentialImporter = credentialImporter,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        importPasswordsPixelSender = importPasswordsPixelSender,
        autofillStore = autofillStore,
        promptExposureReporter = promptExposureReporter,
        credentialExchangePasswordImporter = credentialExchangePasswordImporter,
        webViewCapabilityChecker = webViewCapabilityChecker,
        credentialImportWideEvent = credentialImportWideEvent,
        importRedesignFeature = importRedesignFeature,
        importScreenPromotionPlugins = object : PluginPoint<PasswordsImportScreenPromotionPlugin> {
            override fun getPlugins() = promotionPlugins
        },
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
        testee.onWebFlowFinishedWithError(ErrorParsingCsv)
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
            testee.onImportButtonClicked(TEST_SOURCE)
            assertEquals(Command.StartCredentialExchange, awaitItem())
        }
    }

    @Test
    fun whenImportClickedTwiceDuringCredentialExchangeThenOnlyOneExchangeStarted() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)
        showPreImportPrompt()

        testee.commands().test {
            testee.onImportButtonClicked(TEST_SOURCE)
            testee.onImportButtonClicked(TEST_SOURCE)
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
            testee.onImportButtonClicked(TEST_SOURCE)
            assertEquals(Command.StartCredentialExchange, awaitItem())
            testee.onCredentialExchangeFinished(CredentialExchangeResult.Cancelled, TEST_SOURCE, canShowPreImportDialog = true)
            testee.onImportButtonClicked(TEST_SOURCE)
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
            testee.onImportButtonClicked(TEST_SOURCE)
            assertEquals(Command.StartWebFlow, awaitItem())
        }
    }

    @Test
    fun whenDirectImportRequestedAndCredentialExchangeSupportedThenCredentialExchangeStarted() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)

        testee.commands().test {
            testee.onDirectImportRequested(TEST_SOURCE)
            assertEquals(Command.StartCredentialExchange, awaitItem())
        }
    }

    @Test
    fun whenDirectImportRequestedAndCredentialExchangeNotSupportedThenWebFlowStarted() = runTest {
        testee.commands().test {
            testee.onDirectImportRequested(TEST_SOURCE)
            assertEquals(Command.StartWebFlow, awaitItem())
        }
    }

    @Test
    fun whenImportFinishedWithRedesignThenImportingKeptUntilPromoDecided() = runTest {
        val decision = CompletableDeferred<Boolean>()
        val plugin = FakePromotionPlugin { decision.await() }
        promotionPlugins += plugin
        configureImportFinished(savedCredentials = 10, numberSkipped = 2)
        showRedesignedPreImportPrompt()

        testee.onImportFlowFinishedSuccessfully()
        runCurrent()

        testee.viewState.value.assertIsImporting()
        assertEquals(listOf(PasswordsImportScreenPromotionPlugin.ImportResult(successCount = 10, skippedCount = 2)), plugin.requests)

        decision.complete(true)
        runCurrent()

        assertEquals(ImportSuccess(finished(savedCredentials = 10, numberSkipped = 2), promo = plugin), testee.viewState.value.viewMode)
    }

    @Test
    fun whenSeveralPromosEligibleThenFirstEligibleOneInSuccess() = runTest {
        val skipped = FakePromotionPlugin { false }
        val first = FakePromotionPlugin { true }
        val second = FakePromotionPlugin { true }
        promotionPlugins += listOf(skipped, first, second)
        configureImportFinished(savedCredentials = 10, numberSkipped = 0)
        showRedesignedPreImportPrompt()

        testee.onImportFlowFinishedSuccessfully()
        runCurrent()

        assertEquals(ImportSuccess(finished(savedCredentials = 10, numberSkipped = 0), promo = first), testee.viewState.value.viewMode)
        assertTrue(second.requests.isEmpty())
    }

    @Test
    fun whenNoPromoEligibleThenImportSuccessWithoutPromo() = runTest {
        promotionPlugins += FakePromotionPlugin { false }
        configureImportFinished(savedCredentials = 10, numberSkipped = 0)
        showRedesignedPreImportPrompt()

        testee.onImportFlowFinishedSuccessfully()
        runCurrent()

        assertEquals(ImportSuccess(finished(savedCredentials = 10, numberSkipped = 0), promo = null), testee.viewState.value.viewMode)
    }

    @Test
    fun whenPromoNotDecidedInTimeThenImportSuccessWithoutPromo() = runTest {
        promotionPlugins += FakePromotionPlugin { awaitCancellation() }
        configureImportFinished(savedCredentials = 10, numberSkipped = 0)
        showRedesignedPreImportPrompt()

        testee.onImportFlowFinishedSuccessfully()
        runCurrent()
        testee.viewState.value.assertIsImporting()

        advanceTimeBy(ImportFromGooglePasswordsDialogViewModel.PROMO_TIMEOUT + 1.milliseconds)

        assertEquals(ImportSuccess(finished(savedCredentials = 10, numberSkipped = 0), promo = null), testee.viewState.value.viewMode)
    }

    @Test
    fun whenImportFinishedWithoutRedesignThenPromosNotAsked() = runTest {
        val plugin = FakePromotionPlugin { true }
        promotionPlugins += plugin
        configureImportFinished(savedCredentials = 10, numberSkipped = 0)
        showPreImportPrompt()

        testee.onImportFlowFinishedSuccessfully()
        runCurrent()

        assertEquals(ImportSuccess(finished(savedCredentials = 10, numberSkipped = 0), promo = null), testee.viewState.value.viewMode)
        assertTrue(plugin.requests.isEmpty())
    }

    @Test
    fun whenInitialUiDeterminedAndRedesignEnabledThenViewStateHasRedesignEnabled() = runTest {
        importRedesignFeature.self().setRawStoredState(State(enable = true))

        testee.determineInitialUi(TEST_SOURCE, canShowPreImportDialog = true)
        advanceUntilIdle()

        assertTrue(testee.viewState.value.isRedesignEnabled)
    }

    @Test
    fun whenInitialUiDeterminedAndRedesignDisabledThenViewStateHasRedesignDisabled() = runTest {
        importRedesignFeature.self().setRawStoredState(State(enable = false))

        testee.determineInitialUi(TEST_SOURCE, canShowPreImportDialog = true)
        advanceUntilIdle()

        assertFalse(testee.viewState.value.isRedesignEnabled)
    }

    @Test
    fun whenInitialUiDeterminedThenRedesignResolvedBeforeFirstViewShown() = runTest {
        importRedesignFeature.self().setRawStoredState(State(enable = true))

        testee.viewState.test {
            awaitItem().assertIsDeterminingFirstViewToShow()
            testee.determineInitialUi(TEST_SOURCE, canShowPreImportDialog = true)

            val redesignResolved = awaitItem()
            assertTrue(redesignResolved.isRedesignEnabled)
            assertEquals(DeterminingFirstView, redesignResolved.viewMode)

            val firstView = awaitItem()
            assertTrue(firstView.isRedesignEnabled)
            firstView.assertIsPreImport()
        }
    }

    @Test
    fun whenInitialUiDeterminedWithPreImportDialogThenPreImportShown() = runTest {
        testee.determineInitialUi(TEST_SOURCE, canShowPreImportDialog = true)
        advanceUntilIdle()

        testee.viewState.value.assertIsPreImport()
    }

    @Test
    fun whenInitialUiDeterminedWithoutPreImportDialogThenWebFlowStarted() = runTest {
        testee.commands().test {
            testee.determineInitialUi(TEST_SOURCE, canShowPreImportDialog = false)
            assertEquals(Command.StartWebFlow, awaitItem())
        }
        testee.viewState.value.assertIsDeterminingFirstViewToShow()
    }

    @Test
    fun whenCredentialExchangeSucceedsThenCredentialsImported() = runTest {
        val credentials = listOf(LoginCredentials(domain = "example.com", username = "u", password = "p"))
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(EXCHANGE_SUCCESS))
            .thenReturn(CredentialExchangeImportResult.Success(credentials, originalCount = 3, exporterPackageName = EXPORTER))

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
        verify(credentialImportWideEvent).onCredentialExchangeFailed(CredentialExchangeFailure.NO_EXPORTER_AVAILABLE, null)
        verify(credentialImportWideEvent, never()).onCredentialExchangeFellBackToWebFlow(any(), anyOrNull())
    }

    @Test
    fun whenImportClickedWithCredentialExchangeThenWideEventStartedWithCredentialExchange() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)
        showPreImportPrompt()

        testee.onImportButtonClicked(TEST_SOURCE)
        advanceUntilIdle()

        verify(credentialImportWideEvent).onImportStarted(TEST_SOURCE, usesCredentialExchange = true)
    }

    @Test
    fun whenImportClickedWithoutCredentialExchangeThenWideEventStartedWithWebFlow() = runTest {
        showPreImportPrompt()

        testee.onImportButtonClicked(TEST_SOURCE)
        advanceUntilIdle()

        verify(credentialImportWideEvent).onImportStarted(TEST_SOURCE, usesCredentialExchange = false)
    }

    @Test
    fun whenImportClickedTwiceDuringCredentialExchangeThenWideEventStartedOnce() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)
        showPreImportPrompt()

        testee.onImportButtonClicked(TEST_SOURCE)
        testee.onImportButtonClicked(TEST_SOURCE)
        advanceUntilIdle()

        verify(credentialImportWideEvent, times(1)).onImportStarted(any(), any())
    }

    @Test
    fun whenDirectImportRequestedThenWideEventStarted() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)

        testee.onDirectImportRequested(TEST_SOURCE)
        advanceUntilIdle()

        verify(credentialImportWideEvent).onImportStarted(TEST_SOURCE, usesCredentialExchange = true)
    }

    @Test
    fun whenCredentialExchangeSucceedsThenWideEventGetsExporterAfterImportStarts() = runTest {
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(EXCHANGE_SUCCESS))
            .thenReturn(CredentialExchangeImportResult.Success(emptyList(), originalCount = 0, exporterPackageName = EXPORTER))

        testee.onCredentialExchangeFinished(EXCHANGE_SUCCESS, TEST_SOURCE, canShowPreImportDialog = true)
        advanceUntilIdle()

        inOrder(credentialImporter, credentialImportWideEvent) {
            verify(credentialImporter).import(emptyList(), 0, TEST_SOURCE)
            verify(credentialImportWideEvent).onCredentialExchangeSucceeded(EXPORTER)
        }
    }

    @Test
    fun whenCredentialExchangeCancelledThenWideEventCancelled() = runTest {
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(CredentialExchangeResult.Cancelled))
            .thenReturn(CredentialExchangeImportResult.Cancelled)

        testee.onCredentialExchangeFinished(CredentialExchangeResult.Cancelled, TEST_SOURCE, canShowPreImportDialog = true)
        advanceUntilIdle()

        verify(credentialImportWideEvent).onCredentialExchangeCancelled()
    }

    @Test
    fun whenCredentialExchangeFailsThenWideEventRecordsFallback() = runTest {
        val failure = CredentialExchangeResult.Failure(CredentialExchangeFailure.MALFORMED_PAYLOAD)
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(failure))
            .thenReturn(CredentialExchangeImportResult.Failure(CredentialExchangeFailure.MALFORMED_PAYLOAD, EXPORTER))

        testee.onCredentialExchangeFinished(failure, TEST_SOURCE, canShowPreImportDialog = true)
        advanceUntilIdle()

        verify(credentialImportWideEvent).onCredentialExchangeFellBackToWebFlow(CredentialExchangeFailure.MALFORMED_PAYLOAD, EXPORTER)
    }

    @Test
    fun whenWebFlowFinishedSuccessfullyThenWideEventRecordsWebFlowSuccess() = runTest {
        testee.onWebFlowFinishedSuccessfully()
        advanceUntilIdle()

        verify(credentialImportWideEvent).onWebFlowSucceeded()
    }

    @Test
    fun whenWebFlowCancelledThenWideEventGetsStage() = runTest {
        testee.onWebFlowCancelled(canShowPreImportDialog = true)
        advanceUntilIdle()

        verify(credentialImportWideEvent).onWebFlowCancelled()
    }

    @Test
    fun whenWebFlowCancelledWithoutPreImportDialogThenFlowTerminated() = runTest {
        testee.onWebFlowCancelled(canShowPreImportDialog = false)

        assertTrue(testee.viewState.value.viewMode is ViewMode.FlowTerminated)
    }

    @Test
    fun whenWebFlowFailedThenWideEventGetsReason() = runTest {
        testee.onWebFlowFinishedWithError(ErrorParsingCsv)
        advanceUntilIdle()

        verify(credentialImportWideEvent).onWebFlowFailed(ErrorParsingCsv)
    }

    private fun TestScope.showPreImportPrompt() {
        testee.shouldShowInitialInstructionalPrompt(importSource = TEST_SOURCE)
        advanceUntilIdle()
    }

    private fun TestScope.showRedesignedPreImportPrompt() {
        importRedesignFeature.self().setRawStoredState(State(enable = true))
        testee.determineInitialUi(TEST_SOURCE, canShowPreImportDialog = true)
        advanceUntilIdle()
    }

    private fun configureImportInProgress() {
        whenever(credentialImporter.getImportStatus()).thenReturn(listOf(InProgress).asFlow())
    }

    private fun configureImportFinished(
        savedCredentials: Int,
        numberSkipped: Int,
    ) {
        whenever(credentialImporter.getImportStatus()).thenReturn(listOf(InProgress, finished(savedCredentials, numberSkipped)).asFlow())
    }

    private fun finished(
        savedCredentials: Int,
        numberSkipped: Int,
    ) = Finished(savedCredentials = savedCredentials, numberSkipped = numberSkipped, source = TEST_SOURCE)

    private class FakePromotionPlugin(private val eligible: suspend () -> Boolean) : PasswordsImportScreenPromotionPlugin {
        val requests = mutableListOf<PasswordsImportScreenPromotionPlugin.ImportResult>()

        override suspend fun isEligible(data: PasswordsImportScreenPromotionPlugin.ImportResult): Boolean {
            requests += data
            return eligible()
        }

        override fun createView(
            context: Context,
            callback: PasswordsImportScreenPromotionPlugin.Callback,
        ): View = error("the ViewModel never builds the card")
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
        private const val EXPORTER = "com.example.exporter"
    }
}
