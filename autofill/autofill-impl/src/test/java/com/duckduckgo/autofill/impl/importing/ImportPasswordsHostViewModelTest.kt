/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing

import android.app.Activity
import app.cash.turbine.test
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.impl.importing.ImportPasswordsHostViewModel.Command
import com.duckduckgo.autofill.impl.importing.credentialtransfer.CredentialExchangeImportHandler
import com.duckduckgo.autofill.impl.importing.credentialtransfer.CredentialExchangeImportOutcome
import com.duckduckgo.autofill.impl.importing.credentialtransfer.CredentialExchangePasswordImporter
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.ErrorParsingCsv
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.WebViewCrash
import com.duckduckgo.autofill.impl.importing.wideevents.CredentialImportWideEvent
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ImportPasswordsHostViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val credentialExchangePasswordImporter: CredentialExchangePasswordImporter = mock()
    private val credentialExchangeImportHandler: CredentialExchangeImportHandler = mock()
    private val credentialImportWideEvent: CredentialImportWideEvent = mock()
    private val passwordImportExperimentMetrics: PasswordImportExperimentMetrics = mock()

    private val testee = ImportPasswordsHostViewModel(
        credentialExchangePasswordImporter = credentialExchangePasswordImporter,
        credentialExchangeImportHandler = credentialExchangeImportHandler,
        credentialImportWideEvent = credentialImportWideEvent,
        passwordImportExperimentMetrics = passwordImportExperimentMetrics,
    )

    @Test
    fun whenCredentialExchangeSupportedThenImportStartsWithCredentialExchange() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)

        testee.commands().test {
            testee.onCreated(SOURCE, isRestored = false)

            assertEquals(Command.StartCredentialExchange, awaitItem())
        }
        verify(credentialImportWideEvent).onImportStarted(SOURCE, usesCredentialExchange = true)
        verify(passwordImportExperimentMetrics).fireImportStartedMetric()
    }

    @Test
    fun whenCredentialExchangeNotSupportedThenImportStartsWithWebFlow() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(false)

        testee.commands().test {
            testee.onCreated(SOURCE, isRestored = false)

            assertEquals(Command.StartWebFlow, awaitItem())
        }
        verify(credentialImportWideEvent).onImportStarted(SOURCE, usesCredentialExchange = false)
        verifyNoInteractions(passwordImportExperimentMetrics)
    }

    @Test
    fun whenActivityRecreatedDuringWebFlowThenImportIsNotStartedAgain() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(false)

        testee.commands().test {
            testee.onCreated(SOURCE, isRestored = false)
            assertEquals(Command.StartWebFlow, awaitItem())

            testee.onCreated(SOURCE, isRestored = true)
            advanceUntilIdle()

            expectNoEvents()
        }
        verify(credentialImportWideEvent, times(1)).onImportStarted(any(), any())
    }

    @Test
    fun whenActivityRecreatedWhileCredentialExchangeRunsThenImportFinishesAsCancelled() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)

        testee.commands().test {
            testee.onCreated(SOURCE, isRestored = false)
            assertEquals(Command.StartCredentialExchange, awaitItem())

            testee.onCreated(SOURCE, isRestored = true)

            assertEquals(Command.Finish(ImportPasswordsHostResult.UserCancelled), awaitItem())
        }
        verify(credentialImportWideEvent, times(1)).onImportStarted(any(), any())
    }

    @Test
    fun whenRestoredAfterProcessDeathThenImportFinishesAsCancelledWithoutStartingAgain() = runTest {
        testee.commands().test {
            testee.onCreated(SOURCE, isRestored = true)

            assertEquals(Command.Finish(ImportPasswordsHostResult.UserCancelled), awaitItem())
        }
        verify(credentialImportWideEvent, never()).onImportStarted(any(), any())
    }

    @Test
    fun whenCredentialExchangeImportedThenImportFinishesWithSuccess() = runTest {
        givenExchangeHandledAs(CredentialExchangeImportOutcome.Imported)

        testee.commands().test {
            testee.onCredentialExchangeResult(EXCHANGE_SUCCESS, SOURCE)

            assertEquals(Command.Finish(ImportPasswordsHostResult.Success), awaitItem())
        }
        verify(credentialExchangeImportHandler).handle(EXCHANGE_SUCCESS, SOURCE)
        verifyNoInteractions(passwordImportExperimentMetrics)
    }

    @Test
    fun whenCredentialExchangeCancelledThenImportFinishesAsCancelled() = runTest {
        givenExchangeHandledAs(CredentialExchangeImportOutcome.UserCancelled)

        testee.commands().test {
            testee.onCredentialExchangeResult(CredentialExchangeResult.Cancelled, SOURCE)

            assertEquals(Command.Finish(ImportPasswordsHostResult.UserCancelled), awaitItem())
        }
        verify(passwordImportExperimentMetrics).fireImportCancelledMetric()
    }

    @Test
    fun whenCredentialExchangeFailsWithoutFallbackThenImportFinishesWithPermanentError() = runTest {
        givenExchangeHandledAs(CredentialExchangeImportOutcome.Failed)

        testee.commands().test {
            testee.onCredentialExchangeResult(EXCHANGE_FAILURE, SOURCE)

            assertEquals(Command.Finish(ImportPasswordsHostResult.PermanentError), awaitItem())
        }
        verify(passwordImportExperimentMetrics).fireImportFailedMetric()
    }

    @Test
    fun whenCredentialExchangeFallsBackThenWebFlowStartsWithinTheSameImport() = runTest {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(true)
        givenExchangeHandledAs(CredentialExchangeImportOutcome.FallBackToWebFlow)

        testee.commands().test {
            testee.onCreated(SOURCE, isRestored = false)
            assertEquals(Command.StartCredentialExchange, awaitItem())

            testee.onCredentialExchangeResult(EXCHANGE_FAILURE, SOURCE)

            assertEquals(Command.StartWebFlow, awaitItem())
        }
        verify(credentialImportWideEvent, times(1)).onImportStarted(any(), any())
        verify(passwordImportExperimentMetrics, never()).fireImportFailedMetric()
        verify(passwordImportExperimentMetrics, never()).fireImportCancelledMetric()
    }

    @Test
    fun whenWebFlowResultNotOkThenImportFinishesAsCancelledAndNothingRecorded() = runTest {
        testee.commands().test {
            testee.onWebFlowFinished(Activity.RESULT_CANCELED, null)

            assertEquals(Command.Finish(ImportPasswordsHostResult.UserCancelled), awaitItem())
        }
        verifyNoInteractions(credentialImportWideEvent)
    }

    @Test
    fun whenWebFlowSucceededThenImportFinishesWithSuccessAndIsRecorded() = runTest {
        testee.commands().test {
            testee.onWebFlowFinished(Activity.RESULT_OK, ImportGooglePasswordResult.Success)

            assertEquals(Command.Finish(ImportPasswordsHostResult.Success), awaitItem())
        }
        verify(credentialImportWideEvent).onWebFlowSucceeded()
    }

    @Test
    fun whenWebFlowCancelledThenImportFinishesAsCancelledAndIsRecorded() = runTest {
        testee.commands().test {
            testee.onWebFlowFinished(Activity.RESULT_OK, ImportGooglePasswordResult.UserCancelled(stage = "stage"))

            assertEquals(Command.Finish(ImportPasswordsHostResult.UserCancelled), awaitItem())
        }
        verify(credentialImportWideEvent).onWebFlowCancelled()
    }

    @Test
    fun whenWebViewCrashedThenImportFinishesWithTransientErrorAndIsRecorded() = runTest {
        testee.commands().test {
            testee.onWebFlowFinished(Activity.RESULT_OK, ImportGooglePasswordResult.Error(WebViewCrash))

            assertEquals(Command.Finish(ImportPasswordsHostResult.TransientError), awaitItem())
        }
        verify(credentialImportWideEvent).onWebFlowFailed(WebViewCrash)
    }

    @Test
    fun whenCsvCouldNotBeParsedThenImportFinishesWithPermanentErrorAndIsRecorded() = runTest {
        testee.commands().test {
            testee.onWebFlowFinished(Activity.RESULT_OK, ImportGooglePasswordResult.Error(ErrorParsingCsv))

            assertEquals(Command.Finish(ImportPasswordsHostResult.PermanentError), awaitItem())
        }
        verify(credentialImportWideEvent).onWebFlowFailed(ErrorParsingCsv)
    }

    @Test
    fun whenWebFlowReturnedNoDetailsThenImportFinishesWithTransientErrorAndNothingRecorded() = runTest {
        testee.commands().test {
            testee.onWebFlowFinished(Activity.RESULT_OK, null)

            assertEquals(Command.Finish(ImportPasswordsHostResult.TransientError), awaitItem())
        }
        verifyNoInteractions(credentialImportWideEvent)
    }

    private suspend fun givenExchangeHandledAs(outcome: CredentialExchangeImportOutcome) {
        whenever(credentialExchangeImportHandler.handle(any(), any())).thenReturn(outcome)
    }

    private companion object {
        val SOURCE = AutofillImportLaunchSource.Onboarding
        val EXCHANGE_SUCCESS = CredentialExchangeResult.Success(emptyList(), exporterPackageName = null)
        val EXCHANGE_FAILURE = CredentialExchangeResult.Failure(CredentialExchangeFailure.NO_EXPORTER_AVAILABLE)
    }
}
