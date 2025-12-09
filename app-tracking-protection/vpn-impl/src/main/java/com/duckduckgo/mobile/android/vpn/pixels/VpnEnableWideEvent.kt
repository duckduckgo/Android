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

package com.duckduckgo.mobile.android.vpn.pixels

import android.annotation.SuppressLint
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

interface VpnEnableWideEvent {
    fun onNotifyVpnStartSuccess()
    fun onNotifyVpnStartFailed()
    fun onNullTunnelCreated()
    fun onVpnPrepared()
    fun onVpnStarted()
    fun onVpnStop(reason: VpnStateMonitor.VpnStopReason)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class VpnEnableWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
    dispatchers: DispatcherProvider,
) : VpnEnableWideEvent {

    // This is to ensure modifications of the wide event are serialized
    @SuppressLint("AvoidComputationUsage")
    private val coroutineScope = CoroutineScope(
        context = appCoroutineScope.coroutineContext +
            dispatchers.computation().limitedParallelism(1),
    )

    private val mutex = Mutex()
    private var cachedFlowId: Long? = null

    override fun onNotifyVpnStartSuccess() {
        updateWideEventAsync { wideEventId ->
            wideEventClient.flowStep(wideEventId = wideEventId, stepName = STEP_NOTIFY_VPN_START)
        }
    }

    override fun onNotifyVpnStartFailed() {
        updateWideEventAsync { wideEventId ->
            wideEventClient.flowFinish(wideEventId = wideEventId, FlowStatus.Failure("notify_vpn_start_failed"))
        }
    }

    override fun onNullTunnelCreated() {
        updateWideEventAsync { wideEventId ->
            wideEventClient.flowStep(wideEventId = wideEventId, stepName = STEP_NULL_TUNNEL_CREATED)
        }
    }

    override fun onVpnPrepared() {
        updateWideEventAsync { wideEventId ->
            wideEventClient.flowStep(wideEventId = wideEventId, stepName = NETWORK_STACK_INITIALIZED)
        }
    }

    override fun onVpnStarted() {
        updateWideEventAsync { wideEventId ->
            wideEventClient.intervalEnd(wideEventId = wideEventId, key = KEY_INTERVAL_SERVICE_START_DURATION)
            wideEventClient.flowFinish(wideEventId = wideEventId, status = FlowStatus.Success)
            cachedFlowId = null
        }
    }

    override fun onVpnStop(reason: VpnStateMonitor.VpnStopReason) {
        updateWideEventAsync { wideEventId ->
            wideEventClient.flowFinish(
                wideEventId = wideEventId,
                status = FlowStatus.Failure(reason = reason.javaClass.simpleName),
            )
            cachedFlowId = null
        }
    }

    private fun updateWideEventAsync(operation: suspend (Long) -> Unit) {
        coroutineScope.launch {
            mutex.withLock {
                getCurrentFlowId()?.let { id -> operation(id) }
            }
        }
    }

    private suspend fun getCurrentFlowId(): Long? {
        if (cachedFlowId == null) {
            cachedFlowId = try {
                wideEventClient
                    .getFlowIds(VPN_ENABLE_FEATURE_NAME)
                    .getOrNull()
                    ?.lastOrNull()
            } catch (_: Exception) {
                logcat(priority = LogPriority.WARN) { "Error getting current flow id" }
                null
            }
        }

        return cachedFlowId
    }

    private companion object {
        const val VPN_ENABLE_FEATURE_NAME = "vpn-enable"

        const val STEP_NOTIFY_VPN_START = "notify_vpn_start"
        const val STEP_NULL_TUNNEL_CREATED = "null_tunnel_created"
        const val NETWORK_STACK_INITIALIZED = "network_stack_initialized"

        const val KEY_INTERVAL_SERVICE_START_DURATION = "service_start_duration_ms_bucketed"
    }
}
