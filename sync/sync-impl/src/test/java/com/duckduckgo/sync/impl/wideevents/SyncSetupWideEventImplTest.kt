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
import com.duckduckgo.sync.impl.AccountErrorCodes
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.RouteDecision
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncBuildConfig
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.SyncCodeType
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Denial
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.exchange.v2.AdvertisedExchangeV2Version
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State
import com.duckduckgo.sync.impl.exchange.v2.LocalTrigger
import com.duckduckgo.sync.impl.exchange.v2.PairingRole
import com.duckduckgo.sync.impl.exchange.v2.Role
import com.duckduckgo.sync.impl.exchange.v2.SessionErrorKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.ConnectMethod
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupCancellationReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupFailureReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupMode
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupStep
import com.duckduckgo.sync.impl.pixels.SyncPixels.TimeoutStage
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.pairing.process.ProcessSyncCodeViewModel
import com.duckduckgo.sync.impl.ui.pairing.process.SyncCodeSource
import com.duckduckgo.sync.impl.ui.pairing.read.ReadSyncCodeViewModel
import com.duckduckgo.sync.impl.ui.pairing.read.camera.CameraAccess
import com.duckduckgo.sync.impl.ui.setup.SyncThisDeviceViewModel
import com.duckduckgo.sync.store.SyncStore
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.seconds
import com.duckduckgo.sync.impl.Result as AccountResult

@RunWith(TestParameterInjector::class)
class SyncSetupWideEventImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val cameraAccess: CameraAccess = mock()
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
            cameraAccess = cameraAccess,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun `onFlowStarted starts a new flow with correct ui and flow parameters`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L))

        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

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

        wideEvent.onFlowStarted(launchSource = "promotion_passwords", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

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

        wideEvent.onFlowStarted(launchSource = "duckai_card", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

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

        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 99L,
            status = FlowStatus.Unknown,
        )
    }

    @Test
    fun `feature disabled results in no interactions`() = runTest {
        syncFeature.sendSyncSetupWideEvent().setRawStoredState(State(false))

        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onIntroScreenShown()
        advanceUntilIdle()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `onIntroScreenShown records step`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onIntroScreenShown()
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

        verify(wideEventClient).intervalStart(
            wideEventId = 1L,
            key = "account_creation_latency_ms_bucketed",
        )
    }

    @Test
    fun `onAccountCreationApiFinished ends interval`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onAccountCreationApiFinished()
        advanceUntilIdle()

        verify(wideEventClient).intervalEnd(
            wideEventId = 1L,
            key = "account_creation_latency_ms_bucketed",
        )
    }

    @Test
    fun `onInitialSyncStarted starts interval`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onInitialSyncStarted()
        advanceUntilIdle()

        verify(wideEventClient).intervalStart(
            wideEventId = 1L,
            key = "initial_sync_latency_ms_bucketed",
        )
    }

    @Test
    fun `onInitialSyncFinished ends interval`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onInitialSyncFinished()
        advanceUntilIdle()

        verify(wideEventClient).intervalEnd(
            wideEventId = 1L,
            key = "initial_sync_latency_ms_bucketed",
        )
    }

    @Test
    fun `onAccountCreated records step`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onAccountCreated()
        advanceUntilIdle()

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
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Failure(reason = "account_creation_failed"),
        )
    }

    @Test
    fun `onRecoveryCodeShown records step and finishes flow with success`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onRecoveryCodeShown()
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Failure(reason = "recovery_code_generation_failed"),
        )
    }

    @Test
    fun `onFlowCancelled finishes flow with cancelled`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onFlowCancelled()
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Cancelled,
        )
    }

    @Test
    fun `onSyncRestoreStarted aborts the flow`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onSyncRestoreStarted()
        advanceUntilIdle()

        verify(wideEventClient).flowAbort(wideEventId = 1L)
    }

    @Test
    fun `operations without flowId are no-ops`() = runTest {
        wideEvent.onIntroScreenShown()
        wideEvent.onFlowCancelled()
        advanceUntilIdle()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `non back up attempt sends nothing without the unified event`(
        @TestParameter("MULTI_DEVICE", "RECOVERY") setupMode: SetupMode,
    ) = runTest {
        syncFeature.sendUnifiedSyncSetupWideEvent().setRawStoredState(State(false))

        wideEvent.onFlowStarted(launchSource = "other", setupMode = setupMode)
        wideEvent.onStep(SetupStep.CODE_ENTRY)
        wideEvent.onUpdate { it.copy(role = SetupRole.JOINER) }
        wideEvent.onPairingSessionReset()
        wideEvent.onHostCodeSent()
        wideEvent.onHostWaitFinished(SetupPeerOutcome.FAILURE)
        wideEvent.onPeerLeft(SetupPeerOutcome.CANCELLED)
        wideEvent.onRecoveryCodeScreenShown()
        wideEvent.onRecoveryCodeScreenFailed(SetupFailureReason.ACCOUNT_CREATION_FAILED)
        wideEvent.onSucceeded()
        wideEvent.onFailed(SetupFailureReason.TRANSPORT_FAILURE)
        wideEvent.onCancelled(SetupCancellationReason.USER_CLOSED)
        process(RouteDecision.V2InProgress(SyncCodeType.LINKING, flowOf(DispatchOutcome.UpgradeRequired(3))))
        advanceUntilIdle()

        verifyNoInteractions(wideEventClient, cameraAccess)
    }

    @Test
    fun `non back up attempt preserves legacy reporting without the unified event`(
        @TestParameter("MULTI_DEVICE", "RECOVERY") setupMode: SetupMode,
    ) = runTest {
        syncFeature.sendUnifiedSyncSetupWideEvent().setRawStoredState(State(false))
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L))

        wideEvent.onFlowStarted(launchSource = "promotion_passwords", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onFlowStarted(launchSource = "other", setupMode = setupMode)
        wideEvent.onStep(SetupStep.CODE_ENTRY)
        wideEvent.onUpdate { it.copy(role = SetupRole.JOINER) }
        wideEvent.onPairingSessionReset()
        wideEvent.onHostCodeSent()
        wideEvent.onHostWaitFinished(SetupPeerOutcome.FAILURE)
        wideEvent.onPeerLeft(SetupPeerOutcome.CANCELLED)
        wideEvent.onRecoveryCodeScreenShown()
        wideEvent.onRecoveryCodeScreenFailed(SetupFailureReason.ACCOUNT_CREATION_FAILED)
        wideEvent.onSucceeded()
        wideEvent.onFailed(SetupFailureReason.TRANSPORT_FAILURE)
        wideEvent.onCancelled(SetupCancellationReason.USER_CLOSED)
        wideEvent.onRecoveryCodeShown()
        advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = "sync-setup",
            flowEntryPoint = "promotion_passwords",
            metadata = mapOf("user_auth_required" to "true", "ui_version" to "v2", "flow_version" to "v2.1"),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
        verify(wideEventClient).flowStep(wideEventId = 1L, stepName = "recovery_code_shown", success = true)
        verify(wideEventClient).flowFinish(wideEventId = 1L, status = FlowStatus.Success)
        verifyNoMoreInteractions(wideEventClient)
        verifyNoInteractions(cameraAccess)
    }

    @Test
    fun `back up attempt without the unified event sends no unified fields`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onStep(SetupStep.CHOICE_SCREEN)
        wideEvent.onUpdate { it.copy(setupMode = SetupMode.MULTI_DEVICE) }
        advanceUntilIdle()

        verify(wideEventClient).flowStart(any(), any(), any(), any(), any(), any())
        verifyNoMoreInteractions(wideEventClient)
    }

    @Test
    fun `unified attempt starts the flow and records started with its fields`() = runTest {
        enableUnifiedEvent()

        wideEvent.onFlowStarted(launchSource = "promotion_passwords", setupMode = SetupMode.MULTI_DEVICE)
        advanceUntilIdle()

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

        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

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

        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        verify(wideEventClient).flowAppendToPath(
            wideEventId = 1L,
            step = "started",
            metadata = unifiedMetadata() + ("flow_version" to "v2"),
        )
    }

    @Test
    fun `onStep appends the step with the current fields`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onStep(SetupStep.CHOICE_SCREEN)
        advanceUntilIdle()

        verify(wideEventClient).flowAppendToPath(wideEventId = 1L, step = "choice_screen", metadata = unifiedMetadata())
    }

    @Test
    fun `onUpdate re-appends the last step with the changed fields`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onStep(SetupStep.CHOICE_SCREEN)
        advanceUntilIdle()

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
        advanceUntilIdle()

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
                "connect_method" to "scan_code",
            ),
        )
    }

    @Test
    fun `unified attempt gets the original fields and keeps its own outcome`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onIntroScreenShown()
        wideEvent.onAccountCreationApiStarted()
        wideEvent.onRecoveryCodeShown()
        wideEvent.onAccountCreationFailed()
        wideEvent.onFlowCancelled()
        wideEvent.onSyncRestoreStarted()
        advanceUntilIdle()

        verify(wideEventClient).flowStep(wideEventId = 1L, stepName = "intro_screen_shown", success = true)
        verify(wideEventClient).intervalStart(wideEventId = 1L, key = "account_creation_latency_ms_bucketed")
        verify(wideEventClient).flowStep(wideEventId = 1L, stepName = "recovery_code_shown", success = true)
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
        verify(wideEventClient, never()).flowAbort(any())
    }

    @Test
    fun `second start finishes the unified attempt as duplicated and starts a new one`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onFlowStarted(launchSource = "promotion_passwords", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

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
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()
        syncFeature.sendUnifiedSyncSetupWideEvent().setRawStoredState(State(false))

        wideEvent.onStep(SetupStep.CHOICE_SCREEN)
        advanceUntilIdle()

        verify(wideEventClient).flowAppendToPath(wideEventId = 1L, step = "choice_screen", metadata = unifiedMetadata())
    }

    @Test
    fun `enabling the unified event finishes the open back up attempt without a reason`() = runTest {
        startFlow(flowId = 1L)
        syncFeature.sendUnifiedSyncSetupWideEvent().setRawStoredState(State(true))
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any())).thenReturn(Result.success(2L))

        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(wideEventId = 1L, status = FlowStatus.Unknown)
        verify(wideEventClient).flowAppendToPath(wideEventId = 2L, step = "started", metadata = unifiedMetadata())
    }

    @Test
    fun `onSucceeded finishes the unified attempt as success without a reason`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onSucceeded()
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(wideEventId = 1L, status = FlowStatus.Success, metadata = emptyMap())
    }

    @Test
    fun `onFailed finishes the unified attempt as failure with the reason`(
        @TestParameter(valuesProvider = FailureReasonsProvider::class) reason: SetupFailureReason,
    ) = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onFailed(reason)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Failure(reason = reason.value),
            metadata = mapOf("status_reason" to reason.value),
        )
    }

    @Test
    fun `onFailed finishes the unified attempt as cancelled when the failure is caused by the account state`(
        @TestParameter(valuesProvider = CancellingFailureReasonsProvider::class) reason: SetupFailureReason,
    ) = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onFailed(reason)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Cancelled,
            metadata = mapOf("status_reason" to reason.value),
        )
    }

    @Test
    fun `onFailed finishes the unified attempt as cancelled when the code was rejected`(
        @TestParameter(valuesProvider = CodeRejectingFailureReasonsProvider::class) reason: SetupFailureReason,
    ) = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onFailed(reason)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Cancelled,
            metadata = mapOf("status_reason" to "code_rejected"),
        )
    }

    @Test
    fun `session timeout while waiting for a person finishes the unified attempt as cancelled`(
        @TestParameter(valuesProvider = PersonTimeoutStagesProvider::class) stage: TimeoutStage,
    ) = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onFailed(SetupFailureReason.SESSION_TIMEOUT, stage)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Cancelled,
            metadata = mapOf("status_reason" to "session_timeout", "timeout_stage" to stage.value),
        )
    }

    @Test
    fun `session timeout while waiting for software finishes the unified attempt as failure`(
        @TestParameter(valuesProvider = SoftwareTimeoutStagesProvider::class) stage: TimeoutStage,
    ) = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onFailed(SetupFailureReason.SESSION_TIMEOUT, stage)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Failure(reason = "session_timeout"),
            metadata = mapOf("status_reason" to "session_timeout", "timeout_stage" to stage.value),
        )
    }

    @Test
    fun `session timeout without a stage finishes the unified attempt as failure at an unknown stage`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onFailed(SetupFailureReason.SESSION_TIMEOUT)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Failure(reason = "session_timeout"),
            metadata = mapOf("status_reason" to "session_timeout", "timeout_stage" to "unknown"),
        )
    }

    @Test
    fun `onFailed ignores the timeout stage for other reasons`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onFailed(SetupFailureReason.TRANSPORT_FAILURE, TimeoutStage.WAITING_FOR_PEER_HELLO)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Failure(reason = "transport_failure"),
            metadata = mapOf("status_reason" to "transport_failure"),
        )
    }

    @Test
    fun `onCancelled finishes the unified attempt as cancelled with the reason`(
        @TestParameter reason: SetupCancellationReason,
    ) = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onCancelled(reason)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Cancelled,
            metadata = mapOf("status_reason" to reason.value),
        )
    }

    @Test
    fun `finished unified attempt is not finished again`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onSucceeded()
        advanceUntilIdle()

        wideEvent.onFailed(SetupFailureReason.TRANSPORT_FAILURE)
        wideEvent.onCancelled(SetupCancellationReason.USER_CLOSED)
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(any(), any(), any())
    }

    @Test
    fun `unified outcomes leave the back up attempt open`() = runTest {
        startFlow(flowId = 1L)

        wideEvent.onSucceeded()
        wideEvent.onFailed(SetupFailureReason.TRANSPORT_FAILURE)
        wideEvent.onCancelled(SetupCancellationReason.USER_CLOSED)
        wideEvent.onFlowCancelled()
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(wideEventId = 1L, status = FlowStatus.Cancelled)
        verify(wideEventClient).flowFinish(any(), any(), any())
    }

    @Test
    fun `unified outcomes without an attempt are no-ops`() = runTest {
        enableUnifiedEvent()

        wideEvent.onSucceeded()
        wideEvent.onFailed(SetupFailureReason.TRANSPORT_FAILURE)
        wideEvent.onCancelled(SetupCancellationReason.USER_CLOSED)
        advanceUntilIdle()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `unified attempt starts total and last step latencies`() = runTest {
        enableUnifiedEvent()

        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        verify(wideEventClient).intervalStart(wideEventId = 1L, key = "total_latency_ms_bucketed", buckets = SHARED_LATENCY_BUCKETS)
        verify(wideEventClient).intervalStart(wideEventId = 1L, key = "last_step_latency_ms_bucketed", buckets = SHARED_LATENCY_BUCKETS)
    }

    @Test
    fun `back up attempt without the unified event starts no shared latencies`() = runTest {
        startFlow(flowId = 1L)

        verify(wideEventClient, never()).intervalStart(any(), any(), any(), any(), any())
    }

    @Test
    fun `new step restarts the last step latency`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onStep(SetupStep.CHOICE_SCREEN)
        advanceUntilIdle()

        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 1L, key = "last_step_latency_ms_bucketed")
        order.verify(wideEventClient).intervalStart(wideEventId = 1L, key = "last_step_latency_ms_bucketed", buckets = SHARED_LATENCY_BUCKETS)
    }

    @Test
    fun `repeating the last step keeps its latency running`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onStep(SetupStep.CHOICE_SCREEN)
        advanceUntilIdle()

        wideEvent.onStep(SetupStep.CHOICE_SCREEN)
        advanceUntilIdle()

        verify(wideEventClient).intervalEnd(wideEventId = 1L, key = "last_step_latency_ms_bucketed")
    }

    @Test
    fun `finished attempt ends its latencies before it finishes`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onSucceeded()
        advanceUntilIdle()

        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 1L, key = "total_latency_ms_bucketed")
        order.verify(wideEventClient).intervalEnd(wideEventId = 1L, key = "last_step_latency_ms_bucketed")
        order.verify(wideEventClient).flowFinish(wideEventId = 1L, status = FlowStatus.Success, metadata = emptyMap())
    }

    @Test
    fun `duplicated attempt ends its latencies before it finishes`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()

        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 1L, key = "total_latency_ms_bucketed")
        order.verify(wideEventClient).intervalEnd(wideEventId = 1L, key = "last_step_latency_ms_bucketed")
        order.verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Unknown,
            metadata = mapOf("status_reason" to "duplicated"),
        )
    }

    @Test
    fun `preparing sync latency runs through account created and stops on the next step`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onStep(SetupStep.PREPARING_SYNC)
        wideEvent.onStep(SetupStep.ACCOUNT_CREATED)
        advanceUntilIdle()
        verify(wideEventClient, never()).intervalEnd(wideEventId = 1L, key = "preparing_sync_latency_ms_bucketed")

        wideEvent.onStep(SetupStep.RECOVERY_CODE_SHOWN)
        wideEvent.onSucceeded()
        advanceUntilIdle()

        verify(wideEventClient).intervalStart(wideEventId = 1L, key = "preparing_sync_latency_ms_bucketed", buckets = SHARED_LATENCY_BUCKETS)
        verify(wideEventClient).intervalEnd(wideEventId = 1L, key = "preparing_sync_latency_ms_bucketed")
    }

    @Test
    fun `preparing sync latency ends when the attempt ends on preparing sync`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onStep(SetupStep.PREPARING_SYNC)
        advanceUntilIdle()

        wideEvent.onCancelled(SetupCancellationReason.USER_CLOSED)
        advanceUntilIdle()

        val order = inOrder(wideEventClient)
        order.verify(wideEventClient).intervalEnd(wideEventId = 1L, key = "preparing_sync_latency_ms_bucketed")
        order.verify(wideEventClient).flowFinish(any(), any(), any())
    }

    @Test
    fun `returning to preparing sync restarts its latency`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onStep(SetupStep.PREPARING_SYNC)
        wideEvent.onStep(SetupStep.ERROR_SCREEN)
        advanceUntilIdle()

        wideEvent.onStep(SetupStep.PREPARING_SYNC)
        advanceUntilIdle()

        verify(
            wideEventClient,
            times(2),
        ).intervalStart(wideEventId = 1L, key = "preparing_sync_latency_ms_bucketed", buckets = SHARED_LATENCY_BUCKETS)
    }

    @Test
    fun `attempt that never reached preparing sync doesn't end its latency`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onStep(SetupStep.CHOICE_SCREEN)
        advanceUntilIdle()

        wideEvent.onSucceeded()
        advanceUntilIdle()

        verify(wideEventClient, never()).intervalEnd(wideEventId = 1L, key = "preparing_sync_latency_ms_bucketed")
    }

    @Test
    fun `user closing after a device auth denial finishes with the denial reasons`(
        @TestParameter reason: Denial.Reason,
    ) = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onUpdate { it.copy(deviceAuthDenial = Denial(reason, "sub_reason")) }
        wideEvent.onCancelled(SetupCancellationReason.USER_CLOSED)
        advanceUntilIdle()

        val (status, statusReason) = when (reason) {
            Denial.Reason.Canceled -> FlowStatus.Cancelled to "device_auth_cancelled"
            Denial.Reason.Unavailable -> FlowStatus.Cancelled to "device_auth_unavailable"
            Denial.Reason.Error -> FlowStatus.Failure(reason = "device_auth_error") to "device_auth_error"
        }
        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = status,
            metadata = mapOf("status_reason" to statusReason, "status_sub_reason" to "sub_reason"),
        )
    }

    @Test
    fun `device auth passing after a denial clears the denial`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onUpdate { it.copy(deviceAuthDenial = Denial(Denial.Reason.Canceled, "user_canceled")) }
        wideEvent.onUpdate { it.copy(deviceAuthDenial = null) }
        wideEvent.onCancelled(SetupCancellationReason.USER_CLOSED)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Cancelled,
            metadata = mapOf("status_reason" to "user_closed"),
        )
    }

    @Test
    fun `device auth denial does not replace a cancellation other than user closed`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onUpdate { it.copy(deviceAuthDenial = Denial(Denial.Reason.Canceled, "user_canceled")) }
        wideEvent.onCancelled(SetupCancellationReason.CODE_REJECTED)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Cancelled,
            metadata = mapOf("status_reason" to "code_rejected"),
        )
    }

    @Test
    fun `device auth denial does not finish the attempt on its own`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onUpdate { it.copy(deviceAuthDenial = Denial(Denial.Reason.Canceled, "user_canceled")) }
        advanceUntilIdle()

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `success after a device auth denial finishes as success`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onUpdate { it.copy(deviceAuthDenial = Denial(Denial.Reason.Canceled, "user_canceled")) }
        wideEvent.onSucceeded()
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(wideEventId = 1L, status = FlowStatus.Success, metadata = emptyMap())
    }

    @Test
    fun `camera permission is read at the outcome after leaving code entry`(@TestParameter granted: Boolean) = runTest {
        enableUnifiedEvent()
        whenever(cameraAccess.isPermissionGranted()).thenReturn(!granted)
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.MULTI_DEVICE)
        wideEvent.onStep(SetupStep.CODE_ENTRY)
        wideEvent.onStep(SetupStep.PREPARING_SYNC)
        advanceUntilIdle()
        verifyNoInteractions(cameraAccess)

        whenever(cameraAccess.isPermissionGranted()).thenReturn(granted)
        wideEvent.onSucceeded()
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Success,
            metadata = mapOf("camera_permission" to if (granted) "granted" else "denied"),
        )
    }

    @Test
    fun `code rejection ends the scan attempt with its camera permission even if the user subsequently closes the flow`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.MULTI_DEVICE)
        wideEvent.onStep(SetupStep.CODE_ENTRY)
        val vm = process(RouteDecision.Legacy(SyncAuthCode.Unknown("unrecognized")), AccountResult.Error(AccountErrorCodes.INVALID_CODE.code))
        advanceUntilIdle()
        vm.onErrorDialogDismissed()
        wideEvent.onCancelled(SetupCancellationReason.USER_CLOSED)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Cancelled,
            metadata = mapOf("camera_permission" to "denied", "status_reason" to "code_rejected"),
        )
        verify(wideEventClient).flowFinish(any(), any(), any())
    }

    @Test
    fun `pasting a v1 linking code with no success screen finishes on login with no elected role`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.MULTI_DEVICE)
        wideEvent.onStep(SetupStep.CODE_ENTRY)

        process(RouteDecision.Legacy(SyncAuthCode.Connect(mock())))
        advanceUntilIdle()

        verify(wideEventClient).flowAppendToPath(
            wideEventId = 1L,
            step = "preparing_sync",
            metadata = unifiedMetadata(setupMode = "multi_device", initialSetupMode = "multi_device") +
                mapOf("flow_version" to "v1", "connect_method" to "paste_code"),
        )
        verify(wideEventClient).flowFinish(wideEventId = 1L, status = FlowStatus.Success, metadata = mapOf("camera_permission" to "denied"))
    }

    @Test
    fun `blocked switch on entering a recovery code keeps the initial pairing mode`() = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.MULTI_DEVICE)
        wideEvent.onStep(SetupStep.CODE_ENTRY)
        val blocked = DispatchOutcome.Failed(
            "internal detail",
            AccountErrorCodes.ALREADY_SIGNED_IN.code,
            cancellationReason = SetupCancellationReason.ACCOUNT_SWITCH_BLOCKED,
        )

        process(RouteDecision.V2InProgress(SyncCodeType.RECOVERY, flowOf(blocked)))
        advanceUntilIdle()

        assertEquals("recovery", payload["setup_mode"])
        assertEquals("multi_device", payload["initial_setup_mode"])
        assertEquals("paste_code", payload["connect_method"])
        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Cancelled,
            metadata = mapOf("status_reason" to "account_switch_blocked", "camera_permission" to "denied"),
        )
    }

    @Test
    fun `nonretryable protocol upgrade dialog finishes with needs_upgrade before dismissal`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.MULTI_DEVICE)
        val vm = process(RouteDecision.V2InProgress(SyncCodeType.LINKING, flowOf(DispatchOutcome.UpgradeRequired(3))))
        advanceUntilIdle()
        verify(wideEventClient).flowFinish(
            wideEventId = 1L,
            status = FlowStatus.Failure("needs_upgrade"),
            metadata = mapOf("status_reason" to "needs_upgrade"),
        )

        vm.onErrorDialogDismissed()
        advanceUntilIdle()
        verify(wideEventClient).flowFinish(any(), any(), any())
    }

    @Test
    fun `new legacy pairing code replaces recovery mode and the v2 session fields in the merged payload`() = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        startV2Session()

        wideEvent.onUpdate { it.copy(setupMode = SetupMode.RECOVERY) }
        wideEvent.onStep(SetupStep.CODE_ENTRY)
        process(RouteDecision.Legacy(SyncAuthCode.Connect(mock())))
        advanceUntilIdle()

        assertEquals("v1", payload["flow_version"])
        assertEquals("multi_device", payload["setup_mode"])
        assertEquals("multi_device", payload["initial_setup_mode"])
        assertEquals("preparing_sync", payload["last_step"])
        assertFalse(payload.containsKey("role"))
        assertFalse(payload.containsKey("peer_kind"))
    }

    @Test
    fun `new v2 linking input restores pairing mode after recovery`() = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.MULTI_DEVICE)
        wideEvent.onUpdate { it.copy(setupMode = SetupMode.RECOVERY) }

        process(RouteDecision.V2InProgress(SyncCodeType.LINKING, flowOf()))
        advanceUntilIdle()

        assertEquals("multi_device", payload["setup_mode"])
        assertEquals("multi_device", payload["initial_setup_mode"])
    }

    @Test
    fun `direct recovery after v2 resets its version and pairing fields and reads final permission`() = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        startV2Session()

        wideEvent.onStep(SetupStep.CODE_ENTRY)
        process(RouteDecision.Legacy(SyncAuthCode.Recovery(mock())))
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        advanceUntilIdle()

        assertEquals("v2.1", payload["flow_version"])
        assertEquals("recovery", payload["setup_mode"])
        assertEquals("multi_device", payload["initial_setup_mode"])
        assertEquals("granted", payload["camera_permission"])
        assertFalse(payload.containsKey("role"))
        assertFalse(payload.containsKey("peer_kind"))
    }

    @Test
    fun `returning from pairing to backup clears pairing fields and restores the advertised version`() = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        wideEvent.onStep(SetupStep.CODE_ENTRY)
        wideEvent.onUpdate {
            it.copy(
                setupMode = SetupMode.MULTI_DEVICE,
                role = SetupRole.JOINER,
                peerKind = PeerKind.THIRD_PARTY,
                flowVersion = ExchangeProtocolVersion.V2_0,
            )
        }
        val viewModel = SyncThisDeviceViewModel(
            isAuthRequired = false,
            deviceAuthenticator = mock(),
            syncAccountRepository = mock(),
            syncPixels = mock(),
            dispatchers = coroutineRule.testDispatcherProvider,
            syncSetupWideEvent = wideEvent,
        )

        viewModel.onPairingDismissed()
        wideEvent.onSucceeded()
        advanceUntilIdle()

        assertEquals("single_device", payload["setup_mode"])
        assertEquals("single_device", payload["initial_setup_mode"])
        assertEquals("v2.1", payload["flow_version"])
        assertEquals("denied", payload["camera_permission"])
        assertFalse(payload.containsKey("role"))
        assertFalse(payload.containsKey("peer_kind"))
    }

    @Test
    fun `v2 session sends role and peer only when the attempt finishes`() = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        startV2Session()
        assertFalse(payload.containsKey("role"))
        assertFalse(payload.containsKey("peer_kind"))

        wideEvent.onSucceeded()
        advanceUntilIdle()

        assertEquals("joiner", payload["role"])
        assertEquals("3party", payload["peer_kind"])
        assertEquals("v2", payload["flow_version"])
    }

    @Test
    fun `fatal pairing timeout preserves its operational last step`(
        @TestParameter("WAITING_FOR_CONFIRMATION", "PREPARING_SYNC") step: SetupStep,
    ) = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.MULTI_DEVICE)
        wideEvent.onStep(step)
        val stage = if (step == SetupStep.WAITING_FOR_CONFIRMATION) {
            TimeoutStage.WAITING_FOR_CONFIRMATION
        } else {
            TimeoutStage.WAITING_FOR_RECOVERY_CODE
        }

        process(
            RouteDecision.V2InProgress(
                SyncCodeType.LINKING,
                flowOf(DispatchOutcome.Failed("timeout", AccountErrorCodes.SESSION_TIMEOUT.code, timeoutStage = stage)),
            ),
        )
        advanceUntilIdle()

        assertEquals(step.value, payload["last_step"])
        assertFalse(payload.getValue("steps").contains("error_screen"))
        assertEquals("session_timeout", payload["status_reason"])
        assertEquals(stage.value, payload["timeout_stage"])
    }

    @Test
    fun `observed joiner timeout ends once before the unchanged dispatcher outcome`(
        @TestParameter("false", "true") hostConfirmed: Boolean,
    ) = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.MULTI_DEVICE)
        wideEvent.onStep(SetupStep.CODE_ENTRY)
        val observer = SyncSetupPairingObserver(wideEvent)
        observer.onEvent(
            ExchangeV2Event.RoleElected(
                timestampMs = 0,
                role = Role.Joiner,
                ownPairingRole = PairingRole.Scanner,
                ownSignedIn = false,
                ownKind = "ddg",
                peerKind = "ddg",
                peerSignedIn = true,
            ),
        )
        wideEvent.onStep(SetupStep.WAITING_FOR_CONFIRMATION)
        if (hostConfirmed) {
            observer.onEvent(
                ExchangeV2Event.Transition(
                    timestampMs = 0,
                    from = ExchangeV2State.Joiner.Waiting,
                    to = ExchangeV2State.Joiner.Waiting,
                    trigger = ExchangeV2Message.RecoveryCodeConfirmed.create(),
                    localTrigger = null,
                ),
            )
        }

        observer.onEvent(ExchangeV2Event.SessionError(0, "timeout", SessionErrorKind.SessionTimeout, TimeoutStage.WAITING_FOR_RECOVERY_CODE))
        wideEvent.onPairingOutcome(
            DispatchOutcome.Failed("timeout", AccountErrorCodes.SESSION_TIMEOUT.code, timeoutStage = TimeoutStage.WAITING_FOR_RECOVERY_CODE),
        )
        advanceUntilIdle()

        val expectedStatus = if (hostConfirmed) FlowStatus.Failure("session_timeout") else FlowStatus.Cancelled
        assertEquals(if (hostConfirmed) "waiting_for_recovery_code" else "waiting_for_confirmation", payload["timeout_stage"])
        assertEquals(if (hostConfirmed) "preparing_sync" else "waiting_for_confirmation", payload["last_step"])
        verify(wideEventClient).flowFinish(eq(1L), eq(expectedStatus), any())
        verify(wideEventClient).flowFinish(any(), any(), any())
    }

    @Test
    fun `fatal legacy failure preserves preparing sync as the last step`() = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.MULTI_DEVICE)
        process(RouteDecision.Legacy(SyncAuthCode.Connect(mock())), AccountResult.Error(AccountErrorCodes.CONNECT_FAILED.code))
        advanceUntilIdle()

        assertEquals("preparing_sync", payload["last_step"])
        assertFalse(payload.getValue("steps").contains("error_screen"))
    }

    @Test
    fun `empty paste followed by valid pairing sends the successful outcome`() = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        val clipboard = mock<Clipboard> {
            on { pasteFromClipboard() } doAnswer { "" }
        }
        val reader = ReadSyncCodeViewModel(
            entryPoint = SyncEntryPoint.ADD_DEVICE,
            launchSource = "other",
            clipboard = clipboard,
            syncPixels = mock(),
            codeDispatcher = mock(),
            setupEvent = wideEvent,
        )
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.MULTI_DEVICE)
        reader.onCodeEntryShown()

        reader.pasteSyncCode()
        advanceUntilIdle()
        process(RouteDecision.V2InProgress(SyncCodeType.LINKING, flowOf(DispatchOutcome.LoggedIn(SetupPath.PAIRING))))
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(eq(1L), eq(FlowStatus.Success), any())
        verify(wideEventClient).flowFinish(any(), any(), any())
        assertEquals("paste_code", payload["connect_method"])
        assertFalse(payload.containsKey("status_reason"))
    }

    @Test
    fun `host finishes immediately after sending code to a v2 peer`() = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        startHost(ExchangeProtocolVersion.V2_0)

        wideEvent.onHostCodeSent()
        wideEvent.onFailed(SetupFailureReason.TRANSPORT_FAILURE)
        wideEvent.onCancelled(SetupCancellationReason.USER_CLOSED)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(1L, FlowStatus.Success, mapOf("role" to "host", "peer_kind" to "ddg", "peer_outcome" to "unknown"))
        verify(wideEventClient).flowFinish(any(), any(), any())
        assertEquals("preparing_sync", payload["last_step"])
    }

    @Test
    fun `host awaits report after send and finishes once with the peer outcome`(@TestParameter peerOutcome: SetupPeerOutcome) = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        startHost()
        wideEvent.onHostCodeSent()
        advanceUntilIdle()
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
        assertEquals("host_awaiting_joiner", payload["last_step"])
        verify(wideEventClient).intervalEnd(1L, "preparing_sync_latency_ms_bucketed")

        wideEvent.onHostWaitFinished(peerOutcome)
        wideEvent.onSucceeded()
        wideEvent.onFailed(SetupFailureReason.TRANSPORT_FAILURE)
        wideEvent.onCancelled(SetupCancellationReason.USER_CLOSED)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(1L, FlowStatus.Success, mapOf("role" to "host", "peer_kind" to "ddg", "peer_outcome" to peerOutcome.value))
        verify(wideEventClient).flowFinish(any(), any(), any())
        verify(wideEventClient).intervalEnd(1L, "preparing_sync_latency_ms_bucketed")
        assertFalse(payload.containsKey("status_reason"))
        assertFalse(payload.containsKey("timeout_stage"))
    }

    @Test
    fun `host close or failure after send remains success without timeout metadata`(@TestParameter close: Boolean) = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        startHost()
        wideEvent.onHostCodeSent()
        if (close) {
            wideEvent.onCancelled(SetupCancellationReason.USER_CLOSED)
        } else {
            wideEvent.onFailed(SetupFailureReason.SESSION_TIMEOUT, TimeoutStage.WAITING_FOR_PEER_STATUS)
        }
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(1L, FlowStatus.Success, mapOf("role" to "host", "peer_kind" to "ddg", "peer_outcome" to "unknown"))
        assertFalse(payload.containsKey("status_reason"))
        assertFalse(payload.containsKey("timeout_stage"))
    }

    @Test
    fun `host peer leaving before send cancels with peer_left`() = runTest {
        enableUnifiedEvent()
        startHost()
        wideEvent.onPeerLeft(SetupPeerOutcome.CANCELLED)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(1L, FlowStatus.Cancelled, mapOf("role" to "host", "peer_kind" to "ddg", "status_reason" to "peer_left"))
    }

    @Test
    fun `host peer leaving after send finishes success`(@TestParameter peerOutcome: SetupPeerOutcome) = runTest {
        enableUnifiedEvent()
        startHost()
        wideEvent.onHostCodeSent()
        wideEvent.onPeerLeft(peerOutcome)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(1L, FlowStatus.Success, mapOf("role" to "host", "peer_kind" to "ddg", "peer_outcome" to peerOutcome.value))
    }

    @Test
    fun `host account and recovery callbacks keep mode role and preparing interval until code send`() = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        startHost()
        wideEvent.onStep(SetupStep.ACCOUNT_CREATED)
        wideEvent.onRecoveryCodeShown()
        wideEvent.onRecoveryCodeScreenShown()
        wideEvent.onRecoveryCodeScreenFailed(SetupFailureReason.ACCOUNT_CREATION_FAILED)
        wideEvent.onSucceeded()
        advanceUntilIdle()
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
        verify(wideEventClient, never()).intervalEnd(1L, "preparing_sync_latency_ms_bucketed")
        assertEquals("account_created", payload["last_step"])
        assertEquals("multi_device", payload["setup_mode"])

        wideEvent.onHostCodeSent()
        wideEvent.onStep(SetupStep.PREPARING_SYNC)
        wideEvent.onStep(SetupStep.RECOVERY_CODE_SHOWN)
        wideEvent.onHostWaitFinished(SetupPeerOutcome.SUCCESS)
        advanceUntilIdle()
        assertEquals("host_awaiting_joiner", payload["last_step"])
        assertEquals("host", payload["role"])
        verify(wideEventClient).intervalEnd(1L, "preparing_sync_latency_ms_bucketed")
    }

    @Test
    fun `recovery screen cannot complete v2 pairing before role election`() = runTest {
        enableUnifiedEvent()
        wideEvent.onFlowStarted("other", SetupMode.MULTI_DEVICE)
        wideEvent.onRecoveryCodeScreenShown()
        wideEvent.onRecoveryCodeScreenFailed(SetupFailureReason.RECOVERY_CODE_GENERATION_FAILED)
        advanceUntilIdle()
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `recovery screen completes the current non host attempt`(@TestParameter setupMode: SetupMode) = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        wideEvent.onFlowStarted("other", setupMode)
        if (setupMode == SetupMode.MULTI_DEVICE) wideEvent.onUpdate { it.copy(role = SetupRole.JOINER) }
        wideEvent.onRecoveryCodeScreenShown()
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(eq(1L), eq(FlowStatus.Success), any())
        assertEquals("recovery_code_shown", payload["last_step"])
        assertEquals(setupMode.value, payload["setup_mode"])
    }

    @Test
    fun `accepted host protocol events finish report deadline as success`() = runTest {
        enableUnifiedEvent()
        val payload = captureMergedMetadata()
        startHost()
        val observer = SyncSetupPairingObserver(wideEvent)
        observer.onEvent(ExchangeV2Event.RoleElected(1, Role.Host, PairingRole.Presenter, true, "ddg", "ddg", false))
        observer.onEvent(ExchangeV2Event.MessageSent(1, ExchangeV2Message.RecoveryCodeResponse.create("synthetic")))
        observer.onEvent(
            ExchangeV2Event.Transition(
                1,
                ExchangeV2State.Host.AwaitingStatus,
                ExchangeV2State.Host.Unknown,
                null,
                LocalTrigger.HostStatusDeadlineElapsed(30.seconds),
            ),
        )
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(1L, FlowStatus.Success, mapOf("role" to "host", "peer_kind" to "ddg", "peer_outcome" to "unknown"))
        assertEquals("host_awaiting_joiner", payload["last_step"])
        assertFalse(payload.containsKey("timeout_stage"))
    }

    private fun startHost(version: ExchangeProtocolVersion.Supported = ExchangeProtocolVersion.V2_1) {
        wideEvent.onFlowStarted("other", SetupMode.MULTI_DEVICE)
        wideEvent.onUpdate { it.copy(role = SetupRole.HOST, peerKind = PeerKind.DDG, flowVersion = version) }
        wideEvent.onStep(SetupStep.PREPARING_SYNC)
    }

    private suspend fun TestScope.startV2Session() {
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.MULTI_DEVICE)
        wideEvent.onStep(SetupStep.CODE_ENTRY)
        wideEvent.onUpdate { it.copy(role = SetupRole.JOINER, peerKind = PeerKind.THIRD_PARTY, flowVersion = ExchangeProtocolVersion.V2_0) }
        wideEvent.onStep(SetupStep.WAITING_FOR_CONFIRMATION)
        advanceUntilIdle()
    }

    private suspend fun captureMergedMetadata(): MutableMap<String, String> {
        val payload = mutableMapOf<String, String>()
        val steps = mutableListOf<String>()
        whenever(wideEventClient.flowAppendToPath(any(), any(), any())).thenAnswer { invocation ->
            payload.putAll(invocation.getArgument<Map<String, String>>(2))
            val step = invocation.getArgument<String>(1)
            if (steps.lastOrNull() != step) steps.add(step)
            payload["steps"] = steps.joinToString(",")
            payload["last_step"] = step
            Result.success(Unit)
        }
        whenever(wideEventClient.flowFinish(any(), any(), any())).thenAnswer { invocation ->
            payload.putAll(invocation.getArgument<Map<String, String>>(2))
            Result.success(Unit)
        }
        return payload
    }

    private fun process(
        decision: RouteDecision,
        result: AccountResult<Boolean> = AccountResult.Success(true),
    ): ProcessSyncCodeViewModel {
        val dispatcher = mock<SyncCodeDispatcher> {
            on { route(any()) } doAnswer { decision }
        }
        val accounts = mock<SyncAccountRepository> {
            on { processCode(any(), anyOrNull()) } doAnswer { result }
        }
        return ProcessSyncCodeViewModel(
            codeSource = SyncCodeSource.Pasted("test code", SyncEntryPoint.ADD_DEVICE),
            launchSource = "other",
            accountRepository = accounts,
            codeDispatcher = dispatcher,
            syncPixels = mock(),
            syncAutoRestoreManager = mock(),
            switchedAccountDetector = mock(),
            dispatchers = coroutineRule.testDispatcherProvider,
            setupEvent = wideEvent,
        )
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

    private suspend fun TestScope.startFlow(flowId: Long) {
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any())).thenReturn(Result.success(flowId))
        wideEvent.onFlowStarted(launchSource = "other", setupMode = SetupMode.SINGLE_DEVICE)
        advanceUntilIdle()
    }

    private class FailureReasonsProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context) =
            SetupFailureReason.entries - CANCELLING_FAILURE_REASONS - CODE_REJECTING_FAILURE_REASONS - SetupFailureReason.SESSION_TIMEOUT
    }

    private class CodeRejectingFailureReasonsProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context) = CODE_REJECTING_FAILURE_REASONS.toList()
    }

    private class PersonTimeoutStagesProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context) = PERSON_TIMEOUT_STAGES.toList()
    }

    private class SoftwareTimeoutStagesProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context) = TimeoutStage.entries - PERSON_TIMEOUT_STAGES
    }

    private class CancellingFailureReasonsProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context) = CANCELLING_FAILURE_REASONS.toList()
    }

    private companion object {
        val CANCELLING_FAILURE_REASONS = setOf(SetupFailureReason.ALREADY_PAIRED, SetupFailureReason.ALREADY_UPGRADED)
        val CODE_REJECTING_FAILURE_REASONS = setOf(SetupFailureReason.UNRECOGNIZED_CODE, SetupFailureReason.INCOMPATIBLE_CODE)
        val PERSON_TIMEOUT_STAGES = setOf(TimeoutStage.WAITING_FOR_PEER_HELLO, TimeoutStage.WAITING_FOR_CONFIRMATION)
    }
}
