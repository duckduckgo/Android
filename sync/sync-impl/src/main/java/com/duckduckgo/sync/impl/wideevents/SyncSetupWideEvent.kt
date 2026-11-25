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

import com.duckduckgo.app.statistics.wideevents.CleanupPolicy.OnProcessStart
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.SyncBuildConfig
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.exchange.v2.AdvertisedExchangeV2Version
import com.duckduckgo.sync.impl.pixels.RealSyncPixels.Companion.FLOW_VERSION_V2_1
import com.duckduckgo.sync.impl.pixels.RealSyncPixels.Companion.UI_VERSION_V2
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_FLOW_VERSION
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_UI_VERSION
import com.duckduckgo.sync.impl.pixels.SyncPixels.ConnectMethod
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupMode
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupStep
import com.duckduckgo.sync.impl.pixels.toKnownLaunchSource
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivity.Companion.LAUNCH_SOURCE_OTHER
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEventImpl.Companion.STEP_DEVICE_AUTH_NOT_ENROLLED
import com.duckduckgo.sync.store.SyncStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface SyncSetupWideEvent {
    /**
     * Starts a new attempt in [setupMode]. An attempt that is still open ends as unknown.
     * Without the unified event, only [SetupMode.SINGLE_DEVICE] attempts are sent.
     */
    suspend fun onFlowStarted(
        launchSource: String = LAUNCH_SOURCE_OTHER,
        setupMode: SetupMode = SetupMode.SINGLE_DEVICE,
    )

    suspend fun onDeviceAuthNotEnrolled()
    suspend fun onUserAuthSuccess()
    suspend fun onIntroScreenShown()
    suspend fun onSyncEnabled()
    suspend fun onAccountCreationApiStarted()
    suspend fun onAccountCreationApiFinished()
    suspend fun onInitialSyncStarted()
    suspend fun onInitialSyncFinished()
    suspend fun onAccountCreated()
    suspend fun onAccountCreationFailed()
    suspend fun onRecoveryCodeShown()
    suspend fun onRecoveryCodeGenerationFailed()
    suspend fun onFlowCancelled()
    suspend fun onSyncRestoreStarted()

    /**
     * Records that the attempt reached [step]. Does nothing unless the attempt sends the unified event.
     */
    suspend fun onStep(step: SetupStep)

    /**
     * Applies [change] to the attempt's fields. Does nothing unless the attempt sends the unified event.
     */
    suspend fun onUpdate(change: (SetupFields) -> SetupFields)
}

data class SetupFields(
    val setupMode: SetupMode,
    val role: SetupRole? = null,
    val peerKind: PeerKind? = null,
    val connectMethod: ConnectMethod? = null,
    val accountSwitch: Boolean = false,
    val flowVersion: ExchangeProtocolVersion.Supported,
    val deviceAuthShown: Boolean = false,
)

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SyncSetupWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val syncFeature: Lazy<SyncFeature>,
    private val dispatchers: DispatcherProvider,
    private val buildConfig: SyncBuildConfig,
    private val syncStore: SyncStore,
    private val advertisedExchangeVersion: AdvertisedExchangeV2Version,
) : SyncSetupWideEvent {
    private val mutex = Mutex()
    private var currentData: EventData? = null

    override suspend fun onFlowStarted(launchSource: String, setupMode: SetupMode) = mutex.withLock {
        finishOpenEvent()
        if (!isEventEnabled()) return@withLock
        val isUnified = isUnifiedEventEnabled()
        // Without the unified event, we keep sending only the back up attempts the event has always covered.
        if (!isUnified && setupMode != SetupMode.SINGLE_DEVICE) return@withLock

        val flowId = startFlow(launchSource) ?: return@withLock
        val unified = if (isUnified) createUnifiedData(setupMode) else null
        currentData = EventData(flowId = flowId, unified = unified)
        unified?.appendLastStep(flowId)
    }

    /**
     * In production, we always require user to be authenticated before setting up sync. We authenticate on the setup screens,
     * where the enroll dialog does not abort the flow, because the user can enroll and retry without leaving the screen.
     * There, [STEP_DEVICE_AUTH_NOT_ENROLLED] can be followed by [STEP_USER_AUTH] and a successful finish,
     * and leaving the screen finishes the flow as [FlowStatus.Cancelled].
     */
    override suspend fun onDeviceAuthNotEnrolled() = recordStep(STEP_DEVICE_AUTH_NOT_ENROLLED)

    override suspend fun onUserAuthSuccess() = recordStep(STEP_USER_AUTH)

    override suspend fun onIntroScreenShown() = recordStep(STEP_INTRO_SCREEN_SHOWN)

    override suspend fun onSyncEnabled() = recordStep(STEP_SYNC_ENABLED)

    override suspend fun onAccountCreationApiStarted() = withFlowId { flowId ->
        wideEventClient.intervalStart(wideEventId = flowId, key = KEY_ACCOUNT_CREATION_LATENCY)
    }

    override suspend fun onAccountCreationApiFinished() = withFlowId { flowId ->
        wideEventClient.intervalEnd(wideEventId = flowId, key = KEY_ACCOUNT_CREATION_LATENCY)
    }

    override suspend fun onInitialSyncStarted() = withFlowId { flowId ->
        wideEventClient.intervalStart(wideEventId = flowId, key = KEY_INITIAL_SYNC_LATENCY)
    }

    override suspend fun onInitialSyncFinished() = withFlowId { flowId ->
        wideEventClient.intervalEnd(wideEventId = flowId, key = KEY_INITIAL_SYNC_LATENCY)
    }

    override suspend fun onAccountCreated() = recordStep(STEP_ACCOUNT_CREATED)

    override suspend fun onAccountCreationFailed() = finishBackUpAttempt(FlowStatus.Failure(reason = STEP_ACCOUNT_CREATION_FAILED))

    override suspend fun onRecoveryCodeShown() {
        recordStep(STEP_RECOVERY_CODE_SHOWN)
        finishBackUpAttempt(FlowStatus.Success)
    }

    override suspend fun onRecoveryCodeGenerationFailed() =
        finishBackUpAttempt(FlowStatus.Failure(reason = STEP_RECOVERY_CODE_GENERATION_FAILED))

    override suspend fun onFlowCancelled() = finishBackUpAttempt(FlowStatus.Cancelled)

    override suspend fun onSyncRestoreStarted() = mutex.withLock {
        val backUpAttempt = currentData?.takeIf { it.unified == null } ?: return@withLock
        wideEventClient.flowAbort(wideEventId = backUpAttempt.flowId)
        currentData = null
    }

    private suspend fun startFlow(launchSource: String): Long? = wideEventClient.flowStart(
        name = FLOW_NAME,
        flowEntryPoint = launchSource.toKnownLaunchSource(),
        metadata = mapOf(
            KEY_USER_AUTH_REQUIRED to buildConfig.isAuthRequired.toString(),
            SYNC_SETUP_UI_VERSION to UI_VERSION_V2,
            SYNC_SETUP_FLOW_VERSION to FLOW_VERSION_V2_1,
        ),
        cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
    ).getOrNull()

    private suspend fun finishOpenEvent() {
        val openAttempt = currentData ?: return
        val metadata = if (openAttempt.unified != null) mapOf(KEY_STATUS_REASON to STATUS_REASON_DUPLICATED) else emptyMap()
        wideEventClient.flowFinish(wideEventId = openAttempt.flowId, status = FlowStatus.Unknown, metadata = metadata)
        currentData = null
    }

    private suspend fun recordStep(stepName: String) = withFlowId { flowId ->
        wideEventClient.flowStep(wideEventId = flowId, stepName = stepName, success = true)
    }

    private suspend fun finishBackUpAttempt(status: FlowStatus) = mutex.withLock {
        // The unified event follows the spec's outcome rules instead, so these back up outcomes only end attempts without it.
        val backUpAttempt = currentData?.takeIf { it.unified == null } ?: return@withLock
        wideEventClient.flowFinish(wideEventId = backUpAttempt.flowId, status = status)
        currentData = null
    }

    override suspend fun onStep(step: SetupStep) = mutex.withLock {
        val openAttempt = currentData ?: return@withLock
        val unified = openAttempt.unified?.copy(lastStep = step) ?: return@withLock
        currentData = openAttempt.copy(unified = unified)
        unified.appendLastStep(openAttempt.flowId)
    }

    override suspend fun onUpdate(change: (SetupFields) -> SetupFields) = mutex.withLock {
        val openAttempt = currentData ?: return@withLock
        val unified = openAttempt.unified?.let { it.copy(fields = change(it.fields)) } ?: return@withLock
        currentData = openAttempt.copy(unified = unified)
        unified.appendLastStep(openAttempt.flowId)
    }

    private suspend fun createUnifiedData(setupMode: SetupMode) = UnifiedData(
        // Until a pairing session negotiates a version, we report the newest one this device supports.
        fields = SetupFields(setupMode = setupMode, flowVersion = advertisedExchangeVersion.resolve()),
        initialSetupMode = setupMode,
        existingAccount = withContext(dispatchers.io()) { syncStore.isSignedIn() },
        lastStep = SetupStep.STARTED,
    )

    private suspend fun UnifiedData.appendLastStep(flowId: Long) {
        wideEventClient.flowAppendToPath(wideEventId = flowId, step = lastStep.value, metadata = metadata())
    }

    private fun UnifiedData.metadata(): Map<String, String> = buildMap {
        put(KEY_EXISTING_ACCOUNT, existingAccount.toString())
        put(KEY_ACCOUNT_SWITCH, fields.accountSwitch.toString())
        put(SYNC_SETUP_FLOW_VERSION, "v${fields.flowVersion}")
        put(SYNC_SETUP_UI_VERSION, UI_VERSION_V2)
        put(KEY_DEVICE_AUTH_SHOWN, fields.deviceAuthShown.toString())
        put(KEY_SETUP_MODE, fields.setupMode.value)
        put(KEY_INITIAL_SETUP_MODE, initialSetupMode.value)
        fields.role?.let { put(KEY_ROLE, it.value) }
        fields.peerKind?.let { put(KEY_PEER_KIND, it.value) }
        fields.connectMethod?.let { put(KEY_CONNECT_METHOD, it.value) }
    }

    private suspend fun withFlowId(block: suspend (Long) -> Unit) = mutex.withLock {
        val flowId = currentData?.flowId ?: return@withLock
        block(flowId)
    }

    private suspend fun isEventEnabled(): Boolean = withContext(dispatchers.io()) {
        syncFeature.value.sendSyncSetupWideEvent().isEnabled()
    }

    private suspend fun isUnifiedEventEnabled(): Boolean = withContext(dispatchers.io()) {
        syncFeature.value.sendUnifiedSyncSetupWideEvent().isEnabled()
    }

    private data class EventData(
        val flowId: Long,
        val unified: UnifiedData?,
    )

    private data class UnifiedData(
        val fields: SetupFields,
        val initialSetupMode: SetupMode,
        val existingAccount: Boolean,
        val lastStep: SetupStep,
    )

    private companion object {
        const val FLOW_NAME = "sync-setup"
        const val KEY_USER_AUTH_REQUIRED = "user_auth_required"
        const val KEY_ACCOUNT_CREATION_LATENCY = "account_creation_latency_ms_bucketed"
        const val KEY_INITIAL_SYNC_LATENCY = "initial_sync_latency_ms_bucketed"
        const val STEP_DEVICE_AUTH_NOT_ENROLLED = "device_auth_not_enrolled"
        const val STEP_USER_AUTH = "user_auth"
        const val STEP_INTRO_SCREEN_SHOWN = "intro_screen_shown"
        const val STEP_SYNC_ENABLED = "sync_enabled"
        const val STEP_ACCOUNT_CREATED = "account_created"
        const val STEP_ACCOUNT_CREATION_FAILED = "account_creation_failed"
        const val STEP_RECOVERY_CODE_SHOWN = "recovery_code_shown"
        const val STEP_RECOVERY_CODE_GENERATION_FAILED = "recovery_code_generation_failed"
        const val KEY_STATUS_REASON = "status_reason"
        const val STATUS_REASON_DUPLICATED = "duplicated"
        const val KEY_EXISTING_ACCOUNT = "existing_account"
        const val KEY_ACCOUNT_SWITCH = "account_switch"
        const val KEY_DEVICE_AUTH_SHOWN = "device_auth_shown"
        const val KEY_SETUP_MODE = "setup_mode"
        const val KEY_INITIAL_SETUP_MODE = "initial_setup_mode"
        const val KEY_ROLE = "role"
        const val KEY_PEER_KIND = "peer_kind"
        const val KEY_CONNECT_METHOD = "connect_method"
    }
}
