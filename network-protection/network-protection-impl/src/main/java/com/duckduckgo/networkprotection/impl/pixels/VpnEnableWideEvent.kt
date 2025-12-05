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

package com.duckduckgo.networkprotection.impl.pixels

import android.annotation.SuppressLint
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.VpnRemoteFeatures
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface VpnEnableWideEvent {
    fun onUserRequestedVpnStart(entryPoint: EntryPoint)
    fun onVpnConflictDialogShown()
    fun onVpnConflictDialogCancel()
    fun onAskForVpnPermission()
    fun onVpnPermissionRejected()
    fun onStartVpn()

    enum class EntryPoint {
        APP_SETTINGS,
        SYSTEM_TILE,
        NOTIFICATION,
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class VpnEnableWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val dispatchers: DispatcherProvider,
    private val networkProtectionState: NetworkProtectionState,
    private val subscriptions: Subscriptions,
    private val vpnRemoteFeatures: VpnRemoteFeatures,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : VpnEnableWideEvent {

    // This is to ensure modifications of the wide event are serialized
    @SuppressLint("AvoidComputationUsage")
    private val coroutineScope = CoroutineScope(
        context = appCoroutineScope.coroutineContext +
            dispatchers.computation().limitedParallelism(1),
    )

    private val mutex = Mutex()

    private var wideEventId: Long? = null

    override fun onUserRequestedVpnStart(entryPoint: VpnEnableWideEvent.EntryPoint) {
        coroutineScope.launch {
            mutex.withLock {
                if (!isFeatureEnabled()) return@launch

                wideEventId?.let { id ->
                    wideEventClient.flowFinish(wideEventId = id, status = FlowStatus.Unknown)
                    wideEventId = null
                }

                wideEventId = wideEventClient
                    .flowStart(
                        name = VPN_ENABLE_FEATURE_NAME,
                        metadata = mapOf(
                            KEY_SUBSCRIPTION_STATUS to runCatching { subscriptions.getSubscriptionStatus().statusName }.getOrDefault(""),
                            KEY_IS_FIRST_SETUP to runCatching { networkProtectionState.isOnboarded().not().toString() }.getOrDefault(""),
                        ),
                        flowEntryPoint = entryPoint.asString(),
                        cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
                    )
                    .getOrNull()
            }
        }
    }

    override fun onVpnConflictDialogShown() {
        updateWideEventAsync { eventId ->
            wideEventClient.flowStep(
                wideEventId = eventId,
                stepName = STEP_SHOW_VPN_CONFLICT_DIALOG,
            )
        }
    }

    override fun onVpnConflictDialogCancel() {
        updateWideEventAsync { eventId ->
            wideEventClient.flowFinish(
                wideEventId = eventId,
                status = FlowStatus.Cancelled,
                metadata = mapOf(
                    KEY_CANCELLATION_REASON to CANCELLATION_REASON_VPN_CONFLICT,
                ),
            )
            wideEventId = null
        }
    }

    override fun onAskForVpnPermission() {
        updateWideEventAsync { eventId ->
            wideEventClient.flowStep(
                wideEventId = eventId,
                stepName = STEP_ASK_FOR_VPN_PERMISSION,
            )
        }
    }

    override fun onVpnPermissionRejected() {
        updateWideEventAsync { eventId ->
            wideEventClient.flowFinish(
                wideEventId = eventId,
                status = FlowStatus.Cancelled,
                metadata = mapOf(
                    KEY_CANCELLATION_REASON to CANCELLATION_REASON_PERMISSION_DENIED,
                ),
            )
            wideEventId = null
        }
    }

    override fun onStartVpn() {
        updateWideEventAsync { eventId ->
            wideEventClient.flowStep(wideEventId = eventId, stepName = STEP_VPN_START_ATTEMPT)
            wideEventClient.intervalStart(wideEventId = eventId, key = KEY_INTERVAL_SERVICE_START_DURATION)
        }
    }

    private suspend fun isFeatureEnabled() = withContext(dispatchers.io()) {
        vpnRemoteFeatures.sendVpnEnableWideEvent().isEnabled()
    }

    private fun updateWideEventAsync(operation: suspend (Long) -> Unit) {
        coroutineScope.launch {
            mutex.withLock {
                if (isFeatureEnabled()) {
                    wideEventId?.let { id -> operation(id) }
                }
            }
        }
    }

    private companion object {
        const val VPN_ENABLE_FEATURE_NAME = "vpn-enable"

        const val STEP_SHOW_VPN_CONFLICT_DIALOG = "show_vpn_conflict_dialog"
        const val STEP_ASK_FOR_VPN_PERMISSION = "ask_for_vpn_permission"
        const val STEP_VPN_START_ATTEMPT = "vpn_start_attempt"

        const val KEY_IS_FIRST_SETUP = "is_first_setup"
        const val KEY_CANCELLATION_REASON = "cancellation_reason"
        const val KEY_SUBSCRIPTION_STATUS = "subscription_status"
        const val KEY_INTERVAL_SERVICE_START_DURATION = "service_start_duration_ms_bucketed"

        const val CANCELLATION_REASON_VPN_CONFLICT = "vpn_conflict"
        const val CANCELLATION_REASON_PERMISSION_DENIED = "permission_denied"
    }
}

private fun VpnEnableWideEvent.EntryPoint.asString(): String {
    return when (this) {
        VpnEnableWideEvent.EntryPoint.APP_SETTINGS -> "app_settings"
        VpnEnableWideEvent.EntryPoint.SYSTEM_TILE -> "system_tile"
        VpnEnableWideEvent.EntryPoint.NOTIFICATION -> "notification"
    }
}
