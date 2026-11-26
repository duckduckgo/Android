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
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.impl.SyncBuildConfig
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.exchange.v2.AdvertisedExchangeV2Version
import com.duckduckgo.sync.impl.pixels.SyncPixels.ConnectMethod
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupMode
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupStep
import com.duckduckgo.sync.store.SyncStore
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@RunWith(TestParameterInjector::class)
class SyncSetupWideEventImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)

    var isAuthRequired = true
    private val buildConfig = mock<SyncBuildConfig> {
        on { isAuthRequired } doAnswer { isAuthRequired }
    }

    private var isSignedIn = false
    private val syncStore = mock<SyncStore> {
        on { isSignedIn() } doAnswer { isSignedIn }
    }

    private val advertisedExchangeVersion = mock<AdvertisedExchangeV2Version> {
        on { resolve() } doAnswer { ExchangeProtocolVersion.V2_1 }
    }

    private lateinit var wideEvent: SyncSetupWideEventImpl

    @Before
    fun setup() {
        syncFeature.sendSyncSetupWideEvent().setRawStoredState(State(true))

        wideEvent = SyncSetupWideEventImpl(
            wideEventClient = wideEventClient,
            syncFeature = lazy { syncFeature },
            dispatchers = coroutineRule.testDispatcherProvider,
            buildConfig = buildConfig,
            syncStore = syncStore,
            advertisedExchangeVersion = advertisedExchangeVersion,
        )
    }

    @Test
    fun `onFlowStarted starts a new flow with correct ui and flow parameters`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L))

        wideEvent.onFlowStarted()

        verify(wideEventClient).flowStart(
            name = "sync-setup",
            flowEntryPoint = "other",
            metadata = mapOf("user_auth_required" to "true", "ui_version" to "v2", "flow_version" to "v2.1"),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `onFlowStarted passes source as flowEntryPoint`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L))

        wideEvent.onFlowStarted(launchSource = "promotion_passwords")

        verify(wideEventClient).flowStart(
            name = "sync-setup",
            flowEntryPoint = "promotion_passwords",
            metadata = mapOf("user_auth_required" to "true", "ui_version" to "v2", "flow_version" to "v2.1"),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `onFlowStarted passes unknown source as other flowEntryPoint`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L))

        wideEvent.onFlowStarted(launchSource = "duckai_card")

        verify(wideEventClient).flowStart(
            name = "sync-setup",
            flowEntryPoint = "other",
            metadata = mapOf("user_auth_required" to "true", "ui_version" to "v2", "flow_version" to "v2.1"),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `onFlowStarted finishes existing flow with Unknown before starting new one`() = runTest {
        startFlow(flowId = 99L)

        wideEvent.onFlowStarted()

        verify(wideEventClient).flowFinish(
            wideEventId = 99L,
            status = FlowStatus.Unknown,
        )
    }

    @Test
    fun `feature disabled results in no interactions`() = runTest {
        syncFeature.sendSyncSetupWideEvent().setRawStoredState(State(false))

        wideEvent.onFlowStarted()
        wideEvent.onIntroScreenShown()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `onIntroScreenShown records step`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onIntroScreenShown()

        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "intro_screen_shown",
            success = true,
        )
    }

    @Test
    fun `onSyncEnabled records step`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onSyncEnabled()

        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "sync_enabled",
            success = true,
        )
    }

    @Test
    fun `onAccountCreationApiStarted starts interval`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onAccountCreationApiStarted()

        verify(wideEventClient).intervalStart(
            wideEventId = 1L,
            key = "account_creation_latency_ms_bucketed",
        )
    }

    @Test
    fun `onAccountCreationApiFinished ends interval`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onAccountCreationApiFinished()

        verify(wideEventClient).intervalEnd(
            wideEventId = 1L,
            key = "account_creation_latency_ms_bucketed",
        )
    }

    @Test
    fun `onInitialSyncStarted starts interval`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onInitialSyncStarted()

        verify(wideEventClient).intervalStart(
            wideEventId = 1L,
            key = "initial_sync_latency_ms_bucketed",
        )
    }

    @Test
    fun `onInitialSyncFinished ends interval`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onInitialSyncFinished()

        verify(wideEventClient).intervalEnd(
            wideEventId = 1L,
            key = "initial_sync_latency_ms_bucketed",
        )
    }

    @Test
    fun `onAccountCreated records step`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onAccountCreated()

        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "account_created",
            success = true,
        )
    }

    @Test
    fun `onAccountCreationFailed finishes flow with failure`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onAccountCreationFailed()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Failure(reason = "account_creation_failed"),
        )
    }

    @Test
    fun `onRecoveryCodeShown records step and finishes flow with success`() = runTest {
        startFlow(flowId = 1L)

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
        startFlow(flowId = 1L)

        wideEvent.onDeviceAuthNotEnrolled()

        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "device_auth_not_enrolled",
            success = true,
        )
    }

    @Test
    fun `onUserAuthSuccess records step`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onUserAuthSuccess()

        verify(wideEventClient).flowStep(
            wideEventId = 1L,
            stepName = "user_auth",
            success = true,
        )
    }

    @Test
    fun `onRecoveryCodeGenerationFailed finishes flow with failure`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onRecoveryCodeGenerationFailed()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Failure(reason = "recovery_code_generation_failed"),
        )
    }

    @Test
    fun `onFlowCancelled finishes flow with cancelled`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onFlowCancelled()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Cancelled,
        )
    }

    @Test
    fun `onSyncRestoreStarted aborts the flow`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onSyncRestoreStarted()

        verify(wideEventClient).flowAbort(wideEventId = 1L)
    }

    @Test
    fun `operations without flowId are no-ops`() = runTest {
        wideEvent.onIntroScreenShown()
        wideEvent.onFlowCancelled()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `non back up attempt sends nothing without the unified event`() = runTest {
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.MULTI_DEVICE)
        wideEvent.onIntroScreenShown()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `back up attempt without the unified event sends no unified fields`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onStep(SetupStep.CHOICE_SCREEN)
        wideEvent.onUpdate { it.copy(setupMode = SetupMode.MULTI_DEVICE) }

        verify(wideEventClient).flowStart(any(), any(), any(), any(), any(), any())
        verifyNoMoreInteractions(wideEventClient)
    }

    @Test
    fun `unified attempt starts the flow and records started with its fields`() = runTest {
        enableUnifiedEvent()

        wideEvent.onFlowStarted(launchSource = "promotion_passwords", setupMode = SetupMode.MULTI_DEVICE)

        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).flowStart(
            name = "sync-setup",
            flowEntryPoint = "promotion_passwords",
            metadata = mapOf("user_auth_required" to "true", "ui_version" to "v2", "flow_version" to "v2.1"),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
        order.verify(wideEventClient).flowAppendToPath(
            wideEventId = 1L,
            step = "started",
            metadata = unifiedMetadata(setupMode = "multi_device", initialSetupMode = "multi_device"),
        )
    }

    @Test
    fun `unified attempt reports existing account when signed in`() = runTest {
        enableUnifiedEvent()
        isSignedIn = true

        wideEvent.onFlowStarted()

        verify(wideEventClient).flowAppendToPath(
            wideEventId = 1L,
            step = "started",
            metadata = unifiedMetadata(existingAccount = "true"),
        )
    }

    @Test
    fun `unified attempt reports the advertised exchange version until one is negotiated`() = runTest {
        enableUnifiedEvent()
        whenever(advertisedExchangeVersion.resolve()).thenReturn(ExchangeProtocolVersion.V2_0)

        wideEvent.onFlowStarted()

        verify(wideEventClient).flowAppendToPath(
            wideEventId = 1L,
            step = "started",
            metadata = unifiedMetadata() + ("flow_version" to "v2"),
        )
    }

    @Test
    fun `onStep appends the step with the current fields`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted()

        wideEvent.onStep(SetupStep.CHOICE_SCREEN)

        verify(wideEventClient).flowAppendToPath(wideEventId = 1L, step = "choice_screen", metadata = unifiedMetadata())
    }

    @Test
    fun `onUpdate re-appends the last step with the changed fields`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted()
        wideEvent.onStep(SetupStep.CHOICE_SCREEN)

        wideEvent.onUpdate {
            it.copy(
                setupMode = SetupMode.MULTI_DEVICE,
                role = SetupRole.JOINER,
                peerKind = PeerKind.THIRD_PARTY,
                connectMethod = ConnectMethod.SCAN_CODE,
                accountSwitch = true,
                flowVersion = ExchangeProtocolVersion.V2_0,
                deviceAuthShown = true,
            )
        }

        verify(wideEventClient).flowAppendToPath(
            wideEventId = 1L,
            step = "choice_screen",
            metadata = mapOf(
                "existing_account" to "false",
                "account_switch" to "true",
                "flow_version" to "v2",
                "ui_version" to "v2",
                "device_auth_shown" to "true",
                "setup_mode" to "multi_device",
                "initial_setup_mode" to "single_device",
                "role" to "joiner",
                "peer_kind" to "3party",
                "connect_method" to "scan_code",
            ),
        )
    }

    @Test
    fun `unified attempt gets the original fields and keeps its own outcome`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted()

        wideEvent.onIntroScreenShown()
        wideEvent.onAccountCreationApiStarted()
        wideEvent.onRecoveryCodeShown()
        wideEvent.onAccountCreationFailed()
        wideEvent.onFlowCancelled()
        wideEvent.onSyncRestoreStarted()

        verify(wideEventClient).flowStep(wideEventId = 1L, stepName = "intro_screen_shown", success = true)
        verify(wideEventClient).intervalStart(wideEventId = 1L, key = "account_creation_latency_ms_bucketed")
        verify(wideEventClient).flowStep(wideEventId = 1L, stepName = "recovery_code_shown", success = true)
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
        verify(wideEventClient, never()).flowAbort(any())
    }

    @Test
    fun `second start finishes the unified attempt as duplicated and starts a new one`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted()

        wideEvent.onFlowStarted(launchSource = "promotion_passwords")

        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Unknown,
            metadata = mapOf("status_reason" to "duplicated"),
        )
        order.verify(wideEventClient).flowStart(any(), eq("promotion_passwords"), any(), any(), any(), any())
        order.verify(wideEventClient).flowAppendToPath(wideEventId = 2L, step = "started", metadata = unifiedMetadata())
    }

    @Test
    fun `unified attempt keeps its fields after the unified event is disabled`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted()
        syncFeature.sendUnifiedSyncSetupWideEvent().setRawStoredState(State(false))

        wideEvent.onStep(SetupStep.CHOICE_SCREEN)

        verify(wideEventClient).flowAppendToPath(wideEventId = 1L, step = "choice_screen", metadata = unifiedMetadata())
    }

    @Test
    fun `enabling the unified event finishes the open back up attempt without a reason`() = runTest {
        startFlow(flowId = 1L)
        syncFeature.sendUnifiedSyncSetupWideEvent().setRawStoredState(State(true))
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any())).thenReturn(Result.success(2L))

        wideEvent.onFlowStarted()

        verify(wideEventClient).flowFinish(wideEventId = 1L, status = FlowStatus.Unknown)
        verify(wideEventClient).flowAppendToPath(wideEventId = 2L, step = "started", metadata = unifiedMetadata())
    }

    private suspend fun enableUnifiedEvent() {
        syncFeature.sendUnifiedSyncSetupWideEvent().setRawStoredState(State(true))
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any())).thenReturn(Result.success(1L), Result.success(2L))
    }

    private fun unifiedMetadata(
        setupMode: String = "single_device",
        initialSetupMode: String = "single_device",
        existingAccount: String = "false",
    ) = mapOf(
        "existing_account" to existingAccount,
        "account_switch" to "false",
        "flow_version" to "v2.1",
        "ui_version" to "v2",
        "device_auth_shown" to "false",
        "setup_mode" to setupMode,
        "initial_setup_mode" to initialSetupMode,
    )

    private suspend fun startFlow(flowId: Long) {
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any())).thenReturn(Result.success(flowId))
        wideEvent.onFlowStarted()
    }
}
