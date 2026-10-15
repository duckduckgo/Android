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

package com.duckduckgo.autofill.impl.importing.wideevents

import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.impl.engagement.store.DefaultAutofillEngagementBucketing
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.ErrorParsingCsv
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.WebViewCrash
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class RealCredentialImportWideEventTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val wideEventClient: WideEventClient = mock()
    private val importStatus = MutableSharedFlow<ImportResult>(replay = 1)
    private val credentialImporter: CredentialImporter = mock { on { getImportStatus() } doReturn importStatus }

    private val testee = RealCredentialImportWideEvent(
        wideEventClient = wideEventClient,
        engagementBucketing = DefaultAutofillEngagementBucketing(),
        credentialImporter = credentialImporter,
        appCoroutineScope = coroutineRule.testScope,
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    @Before
    fun setup() = runTest {
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(emptyList()))
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any())).thenReturn(Result.success(FLOW_ID))
    }

    @Test
    fun whenImportStartedWithCredentialExchangeThenFlowStartedWithCredentialExchange() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = true)
        advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = "credential-import",
            flowEntryPoint = "password_management_empty_state",
            metadata = mapOf(
                "initial_flow" to "credential_exchange",
                "fallback" to "false",
                "last_step" to "credential_exchange",
            ),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
        verify(wideEventClient).intervalStart(wideEventId = FLOW_ID, key = "total_duration_ms_bucketed")
    }

    @Test
    fun whenImportStartedWithWebFlowThenFlowStartedWithWebFlow() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = false)
        advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = "credential-import",
            flowEntryPoint = "password_management_empty_state",
            metadata = mapOf(
                "initial_flow" to "webflow",
                "fallback" to "false",
                "last_step" to "webflow",
            ),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun whenImportStartedWithOpenFlowsThenOpenFlowsFinishedAsUnknown() = runTest {
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(listOf(7L, 8L)))

        testee.onImportStarted(SOURCE, usesCredentialExchange = true)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(wideEventId = 7L, status = FlowStatus.Unknown)
        verify(wideEventClient).flowFinish(wideEventId = 8L, status = FlowStatus.Unknown)
    }

    @Test
    fun whenCredentialExchangeSucceedsWithKnownExporterThenStepRecordsExporter() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = true)

        testee.onCredentialExchangeSucceeded("com.x8bit.bitwarden")
        advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = FLOW_ID,
            stepName = "credential_exchange",
            success = true,
            metadata = mapOf("exporter" to "bitwarden", "last_step" to "importing"),
        )
    }

    @Test
    fun whenCredentialExchangeSucceedsWithUnlistedExporterThenExporterIsOther() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = true)

        testee.onCredentialExchangeSucceeded("com.example.passwords")
        advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = FLOW_ID,
            stepName = "credential_exchange",
            success = true,
            metadata = mapOf("exporter" to "other", "last_step" to "importing"),
        )
    }

    @Test
    fun whenCredentialExchangeSucceedsWithNoExporterThenExporterIsOther() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = true)

        testee.onCredentialExchangeSucceeded(null)
        advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = FLOW_ID,
            stepName = "credential_exchange",
            success = true,
            metadata = mapOf("exporter" to "other", "last_step" to "importing"),
        )
    }

    @Test
    fun whenCredentialExchangeFailsThenStepRecordsFallbackToWebFlow() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = true)

        testee.onCredentialExchangeFellBackToWebFlow(CredentialExchangeFailure.NO_EXPORTER_AVAILABLE, exporterPackageName = null)
        advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = FLOW_ID,
            stepName = "credential_exchange",
            success = false,
            metadata = mapOf(
                "cxf_failure_reason" to "no_exporter_available",
                "fallback" to "true",
                "last_step" to "webflow",
            ),
        )
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun whenCredentialExchangeFailsAfterExporterRespondedThenStepRecordsExporter() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = true)

        testee.onCredentialExchangeFellBackToWebFlow(CredentialExchangeFailure.MALFORMED_PAYLOAD, "com.dashlane")
        advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = FLOW_ID,
            stepName = "credential_exchange",
            success = false,
            metadata = mapOf(
                "cxf_failure_reason" to "malformed_payload",
                "exporter" to "dashlane",
                "fallback" to "true",
                "last_step" to "webflow",
            ),
        )
    }

    @Test
    fun whenCredentialExchangeFailsWithNoFallbackThenFlowFinishedAsFailure() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = true)

        testee.onCredentialExchangeFailed(CredentialExchangeFailure.NO_EXPORTER_AVAILABLE, exporterPackageName = null)
        advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = FLOW_ID,
            stepName = "credential_exchange",
            success = false,
            metadata = mapOf("cxf_failure_reason" to "no_exporter_available"),
        )
        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Failure("credential_exchange_failed"),
            metadata = emptyMap(),
        )
    }

    @Test
    fun whenCredentialExchangeCancelledThenFlowFinishedAsCancelled() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = true)

        testee.onCredentialExchangeCancelled()
        advanceUntilIdle()

        inOrder(wideEventClient) {
            verify(wideEventClient).intervalEnd(wideEventId = FLOW_ID, key = "total_duration_ms_bucketed")
            verify(wideEventClient).flowFinish(wideEventId = FLOW_ID, status = FlowStatus.Cancelled, metadata = emptyMap())
        }
    }

    @Test
    fun whenWebFlowSucceedsThenStepRecorded() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = false)

        testee.onWebFlowSucceeded()
        advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = FLOW_ID,
            stepName = "webflow",
            success = true,
            metadata = mapOf("last_step" to "importing"),
        )
    }

    @Test
    fun whenWebFlowCancelledThenFlowFinishedAsCancelled() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = false)

        testee.onWebFlowCancelled()
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Cancelled,
            metadata = emptyMap(),
        )
    }

    @Test
    fun whenWebFlowFailsParsingThenFlowFinishedAsFailure() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = false)

        testee.onWebFlowFailed(ErrorParsingCsv)
        advanceUntilIdle()

        inOrder(wideEventClient) {
            verify(wideEventClient).flowStep(wideEventId = FLOW_ID, stepName = "webflow", success = false)
            verify(wideEventClient).flowFinish(wideEventId = FLOW_ID, status = FlowStatus.Failure("error_parsing"), metadata = emptyMap())
        }
    }

    @Test
    fun whenWebFlowCrashesThenFlowFinishedAsFailure() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = false)

        testee.onWebFlowFailed(WebViewCrash)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(wideEventId = FLOW_ID, status = FlowStatus.Failure("webview_crash"), metadata = emptyMap())
    }

    @Test
    fun whenCredentialExchangeImportFinishesThenFlowFinishedAsSuccessWithBucketedCounts() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = true)
        testee.onCredentialExchangeSucceeded("com.x8bit.bitwarden")

        importStatus.emit(ImportResult.Finished(savedCredentials = 0, numberSkipped = 2, source = SOURCE))
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Success,
            metadata = mapOf("saved_credentials" to "none", "skipped_credentials" to "few"),
        )
    }

    @Test
    fun whenWebFlowImportFinishesThenFlowFinishedAsSuccess() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = false)
        testee.onWebFlowSucceeded()

        importStatus.emit(ImportResult.Finished(savedCredentials = 2, numberSkipped = 0, source = SOURCE))
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Success,
            metadata = mapOf("saved_credentials" to "few", "skipped_credentials" to "none"),
        )
    }

    @Test
    fun whenImportStillInProgressThenFlowNotFinished() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = true)
        testee.onCredentialExchangeSucceeded(null)

        importStatus.emit(ImportResult.InProgress)
        advanceUntilIdle()

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun whenNewAttemptStartsBeforeImportFinishesThenNewFlowNotFinishedAsSuccess() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = true)
        testee.onCredentialExchangeSucceeded(null)
        advanceUntilIdle()
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any())).thenReturn(Result.success(SECOND_FLOW_ID))
        testee.onImportStarted(SOURCE, usesCredentialExchange = false)

        importStatus.emit(ImportResult.Finished(savedCredentials = 1, numberSkipped = 0, source = SOURCE))
        advanceUntilIdle()

        verify(wideEventClient, never()).flowFinish(any(), eq(FlowStatus.Success), any())
    }

    @Test
    fun whenFlowFinishedThenLaterImportResultsDoNothing() = runTest {
        testee.onImportStarted(SOURCE, usesCredentialExchange = true)
        testee.onCredentialExchangeSucceeded(null)
        importStatus.emit(ImportResult.Finished(savedCredentials = 1, numberSkipped = 0, source = SOURCE))

        importStatus.emit(ImportResult.Finished(savedCredentials = 1, numberSkipped = 0, source = SOURCE))
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(any(), any(), any())
    }

    @Test
    fun whenNoFlowStartedInThisProcessThenOpenFlowIsUsed() = runTest {
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(listOf(3L, 4L)))

        testee.onWebFlowSucceeded()
        advanceUntilIdle()

        verify(wideEventClient).flowStep(wideEventId = 4L, stepName = "webflow", success = true, metadata = mapOf("last_step" to "importing"))
    }

    @Test
    fun whenNoFlowOpenThenNothingRecorded() = runTest {
        testee.onWebFlowCancelled()
        advanceUntilIdle()

        verify(wideEventClient).getFlowIds("credential-import")
        verifyNoMoreInteractions(wideEventClient)
    }

    companion object {
        private const val FLOW_ID = 42L
        private const val SECOND_FLOW_ID = 43L
        private val SOURCE = AutofillImportLaunchSource.PasswordManagementEmptyState
    }
}
