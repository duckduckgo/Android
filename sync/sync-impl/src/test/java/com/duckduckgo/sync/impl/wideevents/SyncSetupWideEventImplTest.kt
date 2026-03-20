/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.sync.impl.wideevents

import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class SyncSetupWideEventImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = mock()
    private val syncFeature: SyncFeature = mock()
    private val toggle: Toggle = mock()
    private val deviceAuthenticator: DeviceAuthenticator = mock()

    private lateinit var wideEvent: SyncSetupWideEventImpl

    @Before
    fun setup() {
        whenever(syncFeature.sendSyncSetupWideEvent()).thenReturn(toggle)
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(deviceAuthenticator.isAuthenticationRequired()).thenReturn(true)

        wideEvent = SyncSetupWideEventImpl(
            wideEventClient = wideEventClient,
            syncFeature = { syncFeature },
            dispatchers = coroutineRule.testDispatcherProvider,
            deviceAuthenticator = deviceAuthenticator,
        )
    }

    @Test
    fun `onFlowStarted starts a new flow with correct parameters`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(1L))

        wideEvent.onFlowStarted()

        verify(wideEventClient).flowStart(
            name = "sync-setup",
            metadata = mapOf("user_auth_required" to "true"),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `onFlowStarted passes source as flowEntryPoint`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(1L))

        wideEvent.onFlowStarted(source = "settings")

        verify(wideEventClient).flowStart(
            name = "sync-setup",
            flowEntryPoint = "settings",
            metadata = mapOf("user_auth_required" to "true"),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `onFlowStarted finishes existing flow with Unknown before starting new one`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(99L)))

        wideEvent.onFlowStarted()

        verify(wideEventClient).flowFinish(
            wideEventId = 99L,
            status = FlowStatus.Unknown,
        )
    }

    @Test
    fun `feature disabled results in no interactions`() = runTest {
        whenever(toggle.isEnabled()).thenReturn(false)

        wideEvent.onFlowStarted()
        wideEvent.onIntroScreenShown()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `onIntroScreenShown records step`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onIntroScreenShown()

        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "intro_screen_shown",
            success = true,
        )
    }

    @Test
    fun `onSyncEnabled records step`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onSyncEnabled()

        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "sync_enabled",
            success = true,
        )
    }

    @Test
    fun `onAccountCreationApiStarted starts interval`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onAccountCreationApiStarted()

        verify(wideEventClient).intervalStart(
            wideEventId = 1L,
            key = "account_creation_latency_ms_bucketed",
        )
    }

    @Test
    fun `onAccountCreationApiFinished ends interval`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onAccountCreationApiFinished()

        verify(wideEventClient).intervalEnd(
            wideEventId = 1L,
            key = "account_creation_latency_ms_bucketed",
        )
    }

    @Test
    fun `onInitialSyncStarted starts interval`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onInitialSyncStarted()

        verify(wideEventClient).intervalStart(
            wideEventId = 1L,
            key = "initial_sync_latency_ms_bucketed",
        )
    }

    @Test
    fun `onInitialSyncFinished ends interval`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onInitialSyncFinished()

        verify(wideEventClient).intervalEnd(
            wideEventId = 1L,
            key = "initial_sync_latency_ms_bucketed",
        )
    }

    @Test
    fun `onAccountCreated records step`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onAccountCreated()

        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "account_created",
            success = true,
        )
    }

    @Test
    fun `onAccountCreationFailed finishes flow with failure`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onAccountCreationFailed()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Failure(reason = "account_creation_failed"),
        )
    }

    @Test
    fun `onRecoveryCodeShown records step and finishes flow with success`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onRecoveryCodeShown()

        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "recovery_code_shown",
            success = true,
        )
        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Success,
        )
    }

    @Test
    fun `onDeviceAuthNotEnrolled records step`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onDeviceAuthNotEnrolled()

        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "device_auth_not_enrolled",
            success = true,
        )
    }

    @Test
    fun `onUserAuthSuccess records step`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onUserAuthSuccess()

        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "user_auth",
            success = true,
        )
    }

    @Test
    fun `onUserAuthCancelled aborts the flow`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onUserAuthCancelled()

        verify(wideEventClient).flowAbort(wideEventId = 1L)
    }

    @Test
    fun `onEnrollDeviceAuthDialogShown aborts the flow`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onEnrollDeviceAuthDialogShown()

        verify(wideEventClient).flowAbort(wideEventId = 1L)
    }

    @Test
    fun `onFlowCancelled finishes flow with cancelled`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(1L)))

        wideEvent.onFlowCancelled()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Cancelled,
        )
    }

    @Test
    fun `operations without flowId are no-ops`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(emptyList()))

        wideEvent.onIntroScreenShown()
        wideEvent.onFlowCancelled()

        verify(wideEventClient, org.mockito.kotlin.times(2)).getFlowIds("sync-setup")
        verifyNoMoreInteractions(wideEventClient)
    }
}
