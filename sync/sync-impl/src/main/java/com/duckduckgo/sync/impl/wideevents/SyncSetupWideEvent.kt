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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy.OnProcessStart
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.AccountErrorCodes
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.SyncBuildConfig
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Denial
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.exchange.v2.AdvertisedExchangeV2Version
import com.duckduckgo.sync.impl.pixels.RealSyncPixels.Companion.FLOW_VERSION_V2_1
import com.duckduckgo.sync.impl.pixels.RealSyncPixels.Companion.UI_VERSION_V2
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_FLOW_VERSION
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_UI_VERSION
import com.duckduckgo.sync.impl.pixels.SyncPixels.ConnectMethod
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupCancellationReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupFailureReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupMode
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupStep
import com.duckduckgo.sync.impl.pixels.SyncPixels.TimeoutStage
import com.duckduckgo.sync.impl.pixels.toKnownLaunchSource
import com.duckduckgo.sync.impl.pixels.toSetupFailureReason
import com.duckduckgo.sync.impl.ui.pairing.read.camera.CameraAccess
import com.duckduckgo.sync.store.SyncStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface SyncSetupWideEvent {
    fun onFlowStarted(launchSource: String, setupMode: SetupMode)
    fun onDeviceAuthNotEnrolled()
    fun onUserAuthSuccess()
    fun onIntroScreenShown()
    fun onSyncEnabled()
    fun onAccountCreationApiStarted()
    fun onAccountCreationApiFinished()
    fun onInitialSyncStarted()
    fun onInitialSyncFinished()
    fun onAccountCreated()
    fun onAccountCreationFailed()
    fun onRecoveryCodeShown()
    fun onRecoveryCodeGenerationFailed()
    fun onFlowCancelled()
    fun onSyncRestoreStarted()

    // Unified wide event API
    fun onPairingSessionReset()
    fun onStep(step: SetupStep)
    fun onUpdate(change: (SetupFields) -> SetupFields)
    fun onSucceeded()
    fun onFailed(reason: SetupFailureReason, timeoutStage: TimeoutStage? = null)
    fun onCancelled(reason: SetupCancellationReason)
}

internal fun SyncSetupWideEvent.onPairingOutcome(outcome: DispatchOutcome) {
    when (outcome) {
        DispatchOutcome.AlreadyConnected -> {
            onCancelled(SetupCancellationReason.ALREADY_PAIRED)
        }
        is DispatchOutcome.UpgradeRequired -> {
            onFailed(SetupFailureReason.NEEDS_UPGRADE)
        }
        is DispatchOutcome.Failed -> {
            val cancelled = outcome.cancellationReason ?: when (outcome.code) {
                AccountErrorCodes.PAIRING_CANCELLED.code, AccountErrorCodes.PAIRING_REJECTED.code -> SetupCancellationReason.SYNC_CONFIRMATION_DENIED
                else -> null
            }
            if (cancelled != null) onCancelled(cancelled) else onFailed(outcome.code.toSetupFailureReason(), outcome.timeoutStage)
        }
        else -> Unit
    }
}

data class SetupFields(
    val setupMode: SetupMode,
    val role: SetupRole? = null,
    val peerKind: PeerKind? = null,
    val connectMethod: ConnectMethod? = null,
    val accountSwitch: Boolean = false,
    val flowVersion: ExchangeProtocolVersion.Supported,
    val deviceAuthShown: Boolean = false,
    val deviceAuthDenial: Denial? = null,
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
    private val cameraAccess: CameraAccess,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : SyncSetupWideEvent {
    private val serialDispatcher = dispatchers.io().limitedParallelism(1)
    private val mutex = Mutex()
    private var currentData: EventData? = null

    private fun enqueue(command: suspend () -> Unit) {
        appCoroutineScope.launch(serialDispatcher) {
            mutex.withLock {
                try {
                    command()
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    logcat(WARN) { "Sync setup wide event: report failed: ${e.message}" }
                }
            }
        }
    }

    override fun onFlowStarted(launchSource: String, setupMode: SetupMode) = enqueue {
        val isUnified = isUnifiedEventEnabled()
        // Without the unified event, we keep sending only the back up attempts the event has always covered.
        if (!isUnified && setupMode != SetupMode.SINGLE_DEVICE) return@enqueue
        finishOpenEvent()
        if (!isEventEnabled()) return@enqueue

        val flowId = startFlow(launchSource) ?: return@enqueue
        val unified = if (isUnified) createUnifiedData(setupMode) else null
        currentData = EventData(flowId = flowId, unified = unified)
        if (unified != null) {
            startInterval(flowId, SetupInterval.Total)
            startInterval(flowId, SetupInterval.LastStep)
            unified.appendLastStep(flowId)
        }
    }

    /**
     * In production, we always require user to be authenticated before setting up sync. We authenticate on the setup screens,
     * where the enroll dialog does not abort the flow, because the user can enroll and retry without leaving the screen.
     * There, [STEP_DEVICE_AUTH_NOT_ENROLLED] can be followed by [STEP_USER_AUTH] and a successful finish,
     * and leaving the screen finishes the flow as [FlowStatus.Cancelled].
     */
    override fun onDeviceAuthNotEnrolled() = recordStep(STEP_DEVICE_AUTH_NOT_ENROLLED)

    override fun onUserAuthSuccess() = recordStep(STEP_USER_AUTH)

    override fun onIntroScreenShown() = recordStep(STEP_INTRO_SCREEN_SHOWN)

    override fun onSyncEnabled() = recordStep(STEP_SYNC_ENABLED)

    override fun onAccountCreationApiStarted() = withFlowId { flowId ->
        wideEventClient.intervalStart(wideEventId = flowId, key = KEY_ACCOUNT_CREATION_LATENCY)
    }

    override fun onAccountCreationApiFinished() = withFlowId { flowId ->
        wideEventClient.intervalEnd(wideEventId = flowId, key = KEY_ACCOUNT_CREATION_LATENCY)
    }

    override fun onInitialSyncStarted() = withFlowId { flowId ->
        wideEventClient.intervalStart(wideEventId = flowId, key = KEY_INITIAL_SYNC_LATENCY)
    }

    override fun onInitialSyncFinished() = withFlowId { flowId ->
        wideEventClient.intervalEnd(wideEventId = flowId, key = KEY_INITIAL_SYNC_LATENCY)
    }

    override fun onAccountCreated() = recordStep(STEP_ACCOUNT_CREATED)

    override fun onAccountCreationFailed() = finishBackUpAttempt(FlowStatus.Failure(reason = STEP_ACCOUNT_CREATION_FAILED))

    override fun onRecoveryCodeShown() {
        recordStep(STEP_RECOVERY_CODE_SHOWN)
        finishBackUpAttempt(FlowStatus.Success)
    }

    override fun onRecoveryCodeGenerationFailed() =
        finishBackUpAttempt(FlowStatus.Failure(reason = STEP_RECOVERY_CODE_GENERATION_FAILED))

    override fun onFlowCancelled() = finishBackUpAttempt(FlowStatus.Cancelled)

    override fun onSyncRestoreStarted() = enqueue {
        val backUpAttempt = currentData?.takeIf { it.unified == null } ?: return@enqueue
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
        val unified = openAttempt.unified
        if (unified != null) {
            finishUnifiedAttempt(openAttempt.flowId, unified, FlowStatus.Unknown, mapOf(KEY_STATUS_REASON to STATUS_REASON_DUPLICATED))
        } else {
            wideEventClient.flowFinish(wideEventId = openAttempt.flowId, status = FlowStatus.Unknown)
        }
        currentData = null
    }

    private fun recordStep(stepName: String) = withFlowId { flowId ->
        wideEventClient.flowStep(wideEventId = flowId, stepName = stepName, success = true)
    }

    private fun finishBackUpAttempt(status: FlowStatus) = enqueue {
        // The unified event follows the spec's outcome rules instead, so these back up outcomes only end attempts without it.
        val backUpAttempt = currentData?.takeIf { it.unified == null } ?: return@enqueue
        wideEventClient.flowFinish(wideEventId = backUpAttempt.flowId, status = status)
        currentData = null
    }

    override fun onStep(step: SetupStep) = enqueue {
        val openAttempt = currentData ?: return@enqueue
        val previous = openAttempt.unified ?: return@enqueue
        val unified = if (step == previous.lastStep) previous else previous.moveToStep(openAttempt.flowId, step)
        currentData = openAttempt.copy(unified = unified)
        unified.appendLastStep(openAttempt.flowId)
    }

    private suspend fun UnifiedData.moveToStep(flowId: Long, step: SetupStep): UnifiedData {
        endInterval(flowId, SetupInterval.LastStep)
        startInterval(flowId, SetupInterval.LastStep)
        val preparingSyncRunning = when (step) {
            SetupStep.PREPARING_SYNC -> {
                if (!isPreparingSyncRunning) startInterval(flowId, SetupInterval.PreparingSync)
                true
            }
            // Account creation is recorded while Preparing sync is still on screen, so it doesn't stop the Preparing sync latency.
            SetupStep.ACCOUNT_CREATED -> isPreparingSyncRunning
            else -> {
                if (isPreparingSyncRunning) endInterval(flowId, SetupInterval.PreparingSync)
                false
            }
        }
        return copy(lastStep = step, isPreparingSyncRunning = preparingSyncRunning, sawCodeEntry = sawCodeEntry || step == SetupStep.CODE_ENTRY)
    }

    override fun onPairingSessionReset() = enqueue {
        val openAttempt = currentData ?: return@enqueue
        val unified = openAttempt.unified ?: return@enqueue
        val fields = unified.fields.copy(role = null, peerKind = null, flowVersion = advertisedExchangeVersion.resolve())
        val updated = unified.copy(fields = fields)
        currentData = openAttempt.copy(unified = updated)
        updated.appendLastStep(openAttempt.flowId)
    }

    override fun onUpdate(change: (SetupFields) -> SetupFields) = enqueue {
        val openAttempt = currentData ?: return@enqueue
        val unified = openAttempt.unified?.let { it.copy(fields = change(it.fields)) } ?: return@enqueue
        currentData = openAttempt.copy(unified = unified)
        unified.appendLastStep(openAttempt.flowId)
    }

    override fun onSucceeded() = enqueue { finishUnifiedAttempt(FlowStatus.Success, metadata = emptyMap()) }

    override fun onFailed(reason: SetupFailureReason, timeoutStage: TimeoutStage?) = enqueue {
        when (reason) {
            SetupFailureReason.ALREADY_PAIRED -> cancel(SetupCancellationReason.ALREADY_PAIRED)
            SetupFailureReason.ALREADY_UPGRADED -> cancel(SetupCancellationReason.ALREADY_UPGRADED)
            SetupFailureReason.UNRECOGNIZED_CODE,
            SetupFailureReason.INCOMPATIBLE_CODE,
            -> cancel(SetupCancellationReason.CODE_REJECTED)
            SetupFailureReason.SESSION_TIMEOUT -> timeOut(timeoutStage)
            else -> finishUnifiedAttempt(FlowStatus.Failure(reason = reason.value), metadata = mapOf(KEY_STATUS_REASON to reason.value))
        }
    }

    override fun onCancelled(reason: SetupCancellationReason) = enqueue { cancel(reason) }

    private suspend fun cancel(reason: SetupCancellationReason) {
        val denial = currentData?.unified?.fields?.deviceAuthDenial
        // A user who leaves after device auth stopped them didn't get past it, so we report why instead of user_closed.
        if (reason == SetupCancellationReason.USER_CLOSED && denial != null) {
            finishWithDeviceAuthDenial(denial)
        } else {
            finishUnifiedAttempt(FlowStatus.Cancelled, metadata = mapOf(KEY_STATUS_REASON to reason.value))
        }
    }

    private suspend fun finishWithDeviceAuthDenial(denial: Denial) {
        val (status, statusReason) = when (denial.reason) {
            Denial.Reason.Canceled -> FlowStatus.Cancelled to SetupCancellationReason.DEVICE_AUTH_CANCELLED.value
            Denial.Reason.Unavailable -> FlowStatus.Cancelled to SetupCancellationReason.DEVICE_AUTH_UNAVAILABLE.value
            Denial.Reason.Error -> FlowStatus.Failure(reason = SetupFailureReason.DEVICE_AUTH_ERROR.value) to
                SetupFailureReason.DEVICE_AUTH_ERROR.value
        }
        finishUnifiedAttempt(status, metadata = mapOf(KEY_STATUS_REASON to statusReason, KEY_STATUS_SUB_REASON to denial.subReason))
    }

    private suspend fun timeOut(stage: TimeoutStage?) {
        val status = when (stage) {
            TimeoutStage.WAITING_FOR_PEER_HELLO,
            TimeoutStage.WAITING_FOR_CONFIRMATION,
            -> FlowStatus.Cancelled
            TimeoutStage.WAITING_FOR_PEER_STATUS,
            TimeoutStage.WAITING_FOR_RECOVERY_CODE,
            TimeoutStage.LOGGING_IN,
            null,
            -> FlowStatus.Failure(reason = SetupFailureReason.SESSION_TIMEOUT.value)
        }
        val metadata = mapOf(
            KEY_STATUS_REASON to SetupFailureReason.SESSION_TIMEOUT.value,
            KEY_TIMEOUT_STAGE to (stage?.value ?: TIMEOUT_STAGE_UNKNOWN),
        )
        finishUnifiedAttempt(status, metadata)
    }

    private suspend fun finishUnifiedAttempt(status: FlowStatus, metadata: Map<String, String>) {
        val openAttempt = currentData ?: return
        val unified = openAttempt.unified ?: return
        finishUnifiedAttempt(openAttempt.flowId, unified, status, metadata)
        currentData = null
    }

    private suspend fun finishUnifiedAttempt(flowId: Long, unified: UnifiedData, status: FlowStatus, metadata: Map<String, String>) {
        endInterval(flowId, SetupInterval.Total)
        endInterval(flowId, SetupInterval.LastStep)
        if (unified.isPreparingSyncRunning) endInterval(flowId, SetupInterval.PreparingSync)
        val sessionMetadata = buildMap {
            unified.fields.role?.let { put(KEY_ROLE, it.value) }
            unified.fields.peerKind?.let { put(KEY_PEER_KIND, it.value) }
        }
        val finalMetadata = sessionMetadata + if (unified.sawCodeEntry) {
            metadata + ("camera_permission" to if (cameraAccess.isPermissionGranted()) "granted" else "denied")
        } else {
            metadata
        }
        wideEventClient.flowFinish(wideEventId = flowId, status = status, metadata = finalMetadata)
    }

    private suspend fun startInterval(flowId: Long, interval: SetupInterval) {
        wideEventClient.intervalStart(wideEventId = flowId, key = interval.key, buckets = interval.buckets)
    }

    private suspend fun endInterval(flowId: Long, interval: SetupInterval) {
        wideEventClient.intervalEnd(wideEventId = flowId, key = interval.key)
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
        fields.connectMethod?.let { put(KEY_CONNECT_METHOD, it.value) }
    }

    private fun withFlowId(block: suspend (Long) -> Unit) = enqueue {
        val flowId = currentData?.flowId ?: return@enqueue
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
        val isPreparingSyncRunning: Boolean = false,
        val sawCodeEntry: Boolean = false,
    )

    private enum class SetupInterval(val key: String, val buckets: Set<Duration>) {
        Total(key = "total_latency_ms_bucketed", buckets = SHARED_LATENCY_BUCKETS),
        LastStep(key = "last_step_latency_ms_bucketed", buckets = SHARED_LATENCY_BUCKETS),
        PreparingSync(key = "preparing_sync_latency_ms_bucketed", buckets = SHARED_LATENCY_BUCKETS),
    }

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
        const val KEY_STATUS_SUB_REASON = "status_sub_reason"
        const val STATUS_REASON_DUPLICATED = "duplicated"
        const val KEY_TIMEOUT_STAGE = "timeout_stage"
        const val TIMEOUT_STAGE_UNKNOWN = "unknown"
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

internal val SHARED_LATENCY_BUCKETS = setOf(
    1.seconds,
    2.seconds,
    3.seconds,
    5.seconds,
    10.seconds,
    30.seconds,
    1.minutes,
    2.minutes,
    3.minutes,
    5.minutes,
    10.minutes,
)
