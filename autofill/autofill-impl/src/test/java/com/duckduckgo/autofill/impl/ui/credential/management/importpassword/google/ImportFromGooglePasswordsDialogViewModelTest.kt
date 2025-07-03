package com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.duckduckgo.autofill.impl.importing.AutofillImportLaunchSource
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.Finished
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.InProgress
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.ErrorParsingCsv
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.ImportPasswordsPixelSender
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.DeterminingFirstView
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.ImportSuccess
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.Importing
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.PreImport
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewState
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ImportFromGooglePasswordsDialogViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val importPasswordsPixelSender: ImportPasswordsPixelSender = mock()

    private val credentialImporter: CredentialImporter = mock()
    private val testee = ImportFromGooglePasswordsDialogViewModel(
        credentialImporter = credentialImporter,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        importPasswordsPixelSender = importPasswordsPixelSender,
    )

    @Before
    fun setup() = runTest {
        whenever(credentialImporter.getImportStatus()).thenReturn(emptyFlow())
    }

    @Test
    fun whenParsingErrorOnImportThenViewModeUpdatedToError() = runTest {
        testee.onImportFlowFinishedWithError(reason = ErrorParsingCsv, importSource = TEST_SOURCE)
        testee.viewState.test {
            assertTrue(awaitItem().viewMode is ViewMode.ImportError)
        }
    }

    @Test
    fun whenSuccessfulImportThenViewModeUpdatedToInProgress() = runTest {
        configureImportInProgress()
        testee.shouldShowInitialInstructionalPrompt(importSource = TEST_SOURCE)
        testee.onImportFlowFinishedSuccessfully(importSource = TEST_SOURCE)
        testee.viewState.test {
            awaitImportInProgress()
        }
    }

    @Test
    fun whenSuccessfulImportFlowThenImportFinishesNothingImportedThenViewModeUpdatedToResults() = runTest {
        configureImportFinished(savedCredentials = 0, numberSkipped = 0)
        testee.shouldShowInitialInstructionalPrompt(importSource = TEST_SOURCE)
        testee.onImportFlowFinishedSuccessfully(importSource = TEST_SOURCE)
        testee.viewState.test {
            awaitImportSuccess()
        }
    }

    @Test
    fun whenSuccessfulImportFlowThenImportFinishesCredentialsImportedNoDuplicatesThenViewModeUpdatedToResults() = runTest {
        configureImportFinished(savedCredentials = 10, numberSkipped = 0)
        testee.shouldShowInitialInstructionalPrompt(importSource = TEST_SOURCE)
        testee.onImportFlowFinishedSuccessfully(importSource = TEST_SOURCE)
        testee.viewState.test {
            val result = awaitImportSuccess()
            assertEquals(10, result.importResult.savedCredentials)
            assertEquals(0, result.importResult.numberSkipped)
        }
    }

    @Test
    fun whenSuccessfulImportFlowThenImportFinishesOnlyDuplicatesThenViewModeUpdatedToResults() = runTest {
        configureImportFinished(savedCredentials = 0, numberSkipped = 2)
        testee.shouldShowInitialInstructionalPrompt(importSource = TEST_SOURCE)
        testee.onImportFlowFinishedSuccessfully(importSource = TEST_SOURCE)
        testee.viewState.test {
            val result = awaitImportSuccess()
            assertEquals(0, result.importResult.savedCredentials)
            assertEquals(2, result.importResult.numberSkipped)
        }
    }

    @Test
    fun whenSuccessfulImportNoUpdatesThenThenViewModeFirstInitialisedToPreImport() = runTest {
        testee.shouldShowInitialInstructionalPrompt(importSource = TEST_SOURCE)
        testee.onImportFlowFinishedSuccessfully(importSource = TEST_SOURCE)
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
        testee.shouldShowInitialInstructionalPrompt(importSource = TEST_SOURCE)
        testee.viewState.test {
            awaitItem().assertIsPreImport()
        }
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
                Finished(savedCredentials = savedCredentials, numberSkipped = numberSkipped),
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
    }
}
