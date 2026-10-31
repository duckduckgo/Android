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

package com.duckduckgo.mobile.android.vpn.pixels

import android.annotation.SuppressLint
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.feature.AppTpRemoteFeatures
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Measures how long each phase of a VPN service restart takes, so in-place restarts can be compared with the
 * legacy tear-down-and-rebuild path.
 */
interface VpnRestartWideEvent {
    fun onRestartStarted(networkStackName: String, inPlace: Boolean)
    fun onPhaseStarted(phase: Phase)
    fun onPhaseCompleted(phase: Phase)
    fun onRestartSucceeded()
    fun onRestartFailed(reason: FailureReason)

    enum class Phase(val stepName: String) {
        NETWORK_STACK_PREPARE("network_stack_prepare"),
        TUNNEL_ESTABLISH("tunnel_establish"),
        NETWORK_STACK_SWITCH("network_stack_switch"),
        NETWORK_STACK_START("network_stack_start"),
        ;

        val intervalKey: String = "${stepName}_duration_ms_bucketed"
    }

    enum class FailureReason(val value: String) {
        NULL_TUNNEL_FAILED("null_tunnel_failed"),
        NETWORK_STACK_PREPARE_FAILED("network_stack_prepare_failed"),
        TUNNEL_ESTABLISH_FAILED("tunnel_establish_failed"),
        NETWORK_STACK_START_FAILED("network_stack_start_failed"),
    }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class VpnRestartWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val appTpRemoteFeatures: AppTpRemoteFeatures,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
    dispatchers: DispatcherProvider,
) : VpnRestartWideEvent {

    // This is to ensure modifications of the wide event are serialized
    @SuppressLint("AvoidComputationUsage")
    private val coroutineScope = CoroutineScope(
        context = appCoroutineScope.coroutineContext +
            dispatchers.computation().limitedParallelism(1),
    )

    private val mutex = Mutex()
    private var wideEventId: Long? = null
    private var activePhase: VpnRestartWideEvent.Phase? = null

    override fun onRestartStarted(networkStackName: String, inPlace: Boolean) {
        coroutineScope.launch {
            mutex.withLock {
                if (!appTpRemoteFeatures.restartWideEvent().isEnabled()) return@launch

                // A restart that never reported an outcome is still open, e.g. it was interrupted by another restart
                wideEventId?.let { id ->
                    wideEventClient.flowFinish(wideEventId = id, status = FlowStatus.Unknown)
                    wideEventId = null
                    activePhase = null
                }

                val id = wideEventClient.flowStart(
                    name = VPN_RESTART_FEATURE_NAME,
                    metadata = mapOf(
                        KEY_RESTART_MODE to if (inPlace) RESTART_MODE_IN_PLACE else RESTART_MODE_LEGACY,
                        KEY_NETWORK_STACK to networkStackName.toNetworkStackValue(),
                    ),
                    cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
                    samplingProbability = SAMPLING_PROBABILITY,
                ).getOrNull() ?: return@launch

                wideEventId = id
                wideEventClient.intervalStart(wideEventId = id, key = KEY_RESTART_DURATION, buckets = RESTART_BUCKETS)
            }
        }
    }

    override fun onPhaseStarted(phase: VpnRestartWideEvent.Phase) {
        updateWideEventAsync { id ->
            activePhase = phase
            wideEventClient.intervalStart(wideEventId = id, key = phase.intervalKey, buckets = RESTART_BUCKETS)
        }
    }

    override fun onPhaseCompleted(phase: VpnRestartWideEvent.Phase) {
        updateWideEventAsync { id ->
            activePhase = null
            wideEventClient.intervalEnd(wideEventId = id, key = phase.intervalKey)
            wideEventClient.flowStep(wideEventId = id, stepName = phase.stepName)
        }
    }

    override fun onRestartSucceeded() {
        updateWideEventAsync { id ->
            wideEventClient.intervalEnd(wideEventId = id, key = KEY_RESTART_DURATION)
            wideEventClient.flowFinish(wideEventId = id, status = FlowStatus.Success)
            wideEventId = null
        }
    }

    override fun onRestartFailed(reason: VpnRestartWideEvent.FailureReason) {
        updateWideEventAsync { id ->
            // Intervals still running when the flow finishes are dropped, and a failure is when the timings matter most
            activePhase?.let { phase -> wideEventClient.intervalEnd(wideEventId = id, key = phase.intervalKey) }
            activePhase = null
            wideEventClient.intervalEnd(wideEventId = id, key = KEY_RESTART_DURATION)
            wideEventClient.flowFinish(wideEventId = id, status = FlowStatus.Failure(reason = reason.value))
            wideEventId = null
        }
    }

    private fun updateWideEventAsync(operation: suspend (Long) -> Unit) {
        coroutineScope.launch {
            mutex.withLock {
                wideEventId?.let { id -> operation(id) }
            }
        }
    }

    private fun String.toNetworkStackValue(): String = when (this) {
        APPTP_STACK_NAME -> NETWORK_STACK_APPTP
        NETP_STACK_NAME -> NETWORK_STACK_NETP
        else -> NETWORK_STACK_OTHER
    }

    private companion object {
        const val VPN_RESTART_FEATURE_NAME = "vpn-restart"
        const val SAMPLING_PROBABILITY = 0.1f

        const val KEY_RESTART_MODE = "restart_mode"
        const val RESTART_MODE_IN_PLACE = "in_place"
        const val RESTART_MODE_LEGACY = "legacy"

        const val KEY_NETWORK_STACK = "network_stack"
        const val NETWORK_STACK_APPTP = "apptp"
        const val NETWORK_STACK_NETP = "netp"
        const val NETWORK_STACK_OTHER = "other"

        val APPTP_STACK_NAME = AppTpVpnFeature.APPTP_VPN.featureName

        // NetPVpnFeature.NETP_VPN.featureName lives in network-protection-impl, which this module cannot depend on
        const val NETP_STACK_NAME = "NETP_VPN"

        const val KEY_RESTART_DURATION = "restart_duration_ms_bucketed"

        val RESTART_BUCKETS: Set<Duration> = setOf(
            100.milliseconds,
            250.milliseconds,
            500.milliseconds,
            1.seconds,
            2.seconds,
            5.seconds,
            10.seconds,
            30.seconds,
        )
    }
}
