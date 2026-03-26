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
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface SyncSetupWideEvent {
    suspend fun onFlowStarted(source: String? = null)
    suspend fun onDeviceAuthNotEnrolled()
    suspend fun onEnrollDeviceAuthDialogShown()
    suspend fun onUserAuthSuccess()
    suspend fun onUserAuthCancelled()
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
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SyncSetupWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val syncFeature: Lazy<SyncFeature>,
    private val dispatchers: DispatcherProvider,
    private val deviceAuthenticator: DeviceAuthenticator,
) : SyncSetupWideEvent {

    private var cachedFlowId: Long? = null

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        syncFeature.get().sendSyncSetupWideEvent().isEnabled()
    }

    private suspend fun getCurrentWideEventId(): Long? {
        if (cachedFlowId == null) {
            cachedFlowId = wideEventClient
                .getFlowIds(FLOW_NAME)
                .getOrNull()
                ?.lastOrNull()
        }
        return cachedFlowId
    }

    override suspend fun onFlowStarted(source: String?) {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { existingId ->
            wideEventClient.flowFinish(
                wideEventId = existingId,
                status = FlowStatus.Unknown,
            )
            cachedFlowId = null
        }

        cachedFlowId = wideEventClient.flowStart(
            name = FLOW_NAME,
            flowEntryPoint = source,
            metadata = mapOf(
                KEY_USER_AUTH_REQUIRED to deviceAuthenticator.isAuthenticationRequired().toString(),
            ),
            cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        ).getOrNull()
    }

    /**
     * In production, we always require user to be authenticated before setting up sync.
     *
     * If device auth is not enrolled at all, note that as a step.
     *
     * If the dialog prompting user to enroll in device auth is shown, abort the flow, see [onEnrollDeviceAuthDialogShown].
     * If the dialog is never shown, the flow will become stale and be reported as [FlowStatus.Unknown] with last step being [STEP_DEVICE_AUTH_NOT_ENROLLED]
     * which indicates an issue.
     */
    override suspend fun onDeviceAuthNotEnrolled() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(wideEventId = id, stepName = STEP_DEVICE_AUTH_NOT_ENROLLED, success = true)
    }

    /**
     * @see onDeviceAuthNotEnrolled
     */
    override suspend fun onEnrollDeviceAuthDialogShown() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.flowAbort(wideEventId = id)
        cachedFlowId = null
    }

    override suspend fun onUserAuthSuccess() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(wideEventId = id, stepName = STEP_USER_AUTH, success = true)
    }

    override suspend fun onUserAuthCancelled() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.flowFinish(
            wideEventId = id,
            status = FlowStatus.Cancelled,
        )
        cachedFlowId = null
    }

    override suspend fun onIntroScreenShown() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(wideEventId = id, stepName = STEP_INTRO_SCREEN_SHOWN, success = true)
    }

    override suspend fun onSyncEnabled() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(wideEventId = id, stepName = STEP_SYNC_ENABLED, success = true)
    }

    override suspend fun onAccountCreationApiStarted() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.intervalStart(wideEventId = id, key = KEY_ACCOUNT_CREATION_LATENCY)
    }

    override suspend fun onAccountCreationApiFinished() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.intervalEnd(wideEventId = id, key = KEY_ACCOUNT_CREATION_LATENCY)
    }

    override suspend fun onInitialSyncStarted() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.intervalStart(wideEventId = id, key = KEY_INITIAL_SYNC_LATENCY)
    }

    override suspend fun onInitialSyncFinished() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.intervalEnd(wideEventId = id, key = KEY_INITIAL_SYNC_LATENCY)
    }

    override suspend fun onAccountCreated() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(wideEventId = id, stepName = STEP_ACCOUNT_CREATED, success = true)
    }

    override suspend fun onAccountCreationFailed() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.flowFinish(
            wideEventId = id,
            status = FlowStatus.Failure(reason = STEP_ACCOUNT_CREATION_FAILED),
        )
        cachedFlowId = null
    }

    override suspend fun onRecoveryCodeShown() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(wideEventId = id, stepName = STEP_RECOVERY_CODE_SHOWN, success = true)
        wideEventClient.flowFinish(wideEventId = id, status = FlowStatus.Success)
        cachedFlowId = null
    }

    override suspend fun onRecoveryCodeGenerationFailed() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.flowFinish(
            wideEventId = id,
            status = FlowStatus.Failure(reason = STEP_RECOVERY_CODE_GENERATION_FAILED),
        )
        cachedFlowId = null
    }

    override suspend fun onFlowCancelled() {
        if (!isFeatureEnabled()) return
        val id = getCurrentWideEventId() ?: return

        wideEventClient.flowFinish(
            wideEventId = id,
            status = FlowStatus.Cancelled,
        )
        cachedFlowId = null
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
    }
}
