/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.health

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor.HealthState.BadHealth
import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor.HealthState.GoodHealth
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.NO_VPN_CONNECTIVITY
import com.duckduckgo.vpn.di.VpnCoroutineScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.logcat

/**
 * Health monitor will periodically obtain the current health metrics across AppTP, and raise an
 * alarm if there is prolonged bad health detected.
 *
 * Periodically samples the health metrics. When sampling:
 *      get stats from last period of time, defined in SLIDING_WINDOW_DURATION_MS
 *      where appropriate, calculates a ratio of multiple values
 *
 * This sample isn't stored, but instead we check if the value/ratio is acceptable.
 *    If it is, all is well.
 *    If the rate is not acceptable, we flag it but don't yet raise the alarm
 *
 * On the next health sample, we check the value again.
 *    If it's fine now, remove the flag (false alarm)
 *    If the rate is still not good after N samples, we're now in bad health. Raise the alarm.
 */

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AppTPHealthMonitor @Inject constructor(
    @VpnCoroutineScope private val coroutineScope: CoroutineScope,
    private val healthMetricCounter: HealthMetricCounter,
    private val healthClassifier: HealthClassifier,
    private val callbacks: PluginPoint<AppHealthCallback>,
) : AppHealthMonitor {

    companion object {

        // how far back to look when obtaining health metrics
        const val SLIDING_WINDOW_DURATION_MS: Long = 60_000

        private const val MONITORING_FREQUENCY_MS: Long = 30_000
        private const val OLD_METRIC_CLEANUP_FREQUENCY_MS: Long = 60_000

        private val NO_NETWORK_CONNECTIVITY_SAMPLES: Int = (1.minutes.inWholeMilliseconds / MONITORING_FREQUENCY_MS).toInt()
        private val DEFAULT_ALERT_SAMPLES: Int = (2.minutes.inWholeMilliseconds / MONITORING_FREQUENCY_MS).toInt()
    }

    private val now: Long
        get() = System.currentTimeMillis()

    private val _healthState = MutableStateFlow(SystemHealthData(isBadHealth = false, emptyList()))

    private val monitoringJob = ConflatedJob()
    private val oldMetricCleanupJob = ConflatedJob()

    private val healthRules = mutableListOf<HealthRule>()

    private val noNetworkConnectivityAlert = object : HealthRule(
        "noNetworkConnectivityAlert",
        samplesToWaitBeforeAlerting = NO_NETWORK_CONNECTIVITY_SAMPLES,
    ) {}.also { healthRules.add(it) }

    private suspend fun checkCurrentHealth() {
        val timeWindow = now - SLIDING_WINDOW_DURATION_MS

        val healthStates = mutableListOf<HealthState>()
        healthStates += sampleVpnConnectivityEvents(timeWindow, noNetworkConnectivityAlert)

        val systemHealth = buildSystemHealthReport(healthStates)
        _healthState.emit(systemHealth)

        val prolongedBadHealthRules = prolongedBadHealthRules()
        callbacks.getPlugins().forEach { callback ->
            if (callback.onAppHealthUpdate(AppHealthData(prolongedBadHealthRules, systemHealth))) {
                return@forEach
            }
        }
    }

    private fun buildSystemHealthReport(healthStates: MutableList<HealthState>): SystemHealthData {
        val badHealthMetrics = healthStates.filterIsInstance<BadHealth>()
        val goodHealthMetrics = healthStates.filterIsInstance<GoodHealth>()

        val sortedMetrics = mutableListOf<RawMetricsSubmission>()
        badHealthMetrics.forEach { sortedMetrics.add(it.metrics) }
        goodHealthMetrics.forEach { sortedMetrics.add(it.metrics) }

        return if (badHealthMetrics.isEmpty()) {
            SystemHealthData(isBadHealth = false, sortedMetrics)
        } else {
            SystemHealthData(isBadHealth = true, rawMetrics = sortedMetrics)
        }
    }

    private fun sampleVpnConnectivityEvents(
        timeWindow: Long,
        healthAlerts: HealthRule,
    ): HealthState {
        val noConnectivityStats = healthMetricCounter.getStat(NO_VPN_CONNECTIVITY(), timeWindow)
        val state = healthClassifier.determineHealthVpnConnectivity(noConnectivityStats, healthAlerts.name)
        healthAlerts.updateAlert(state)
        return state
    }

    private fun HealthRule.updateAlert(healthState: HealthState) {
        when (healthState) {
            is BadHealth -> recordBadHealthSample()
            else -> resetBadHealthSampleCount()
        }
    }

    private fun prolongedBadHealthRules(): List<String> {
        val prolongedBadHealthRules = mutableListOf<String>()

        healthRules.forEach {
            if (it.shouldAlertBadHealth()) {
                prolongedBadHealthRules.add(it.name)
            }
        }

        return prolongedBadHealthRules
    }

    override fun startMonitoring() {
        logcat { "AppTp Health - start monitoring" }

        monitoringJob += coroutineScope.launch {
            while (isActive) {
                delay(MONITORING_FREQUENCY_MS)
                checkCurrentHealth()
            }
        }

        oldMetricCleanupJob += coroutineScope.launch {
            while (isActive) {
                logcat { "Cleaning up old health metrics" }
                healthMetricCounter.purgeOldMetrics()
                delay(OLD_METRIC_CLEANUP_FREQUENCY_MS)
            }
        }
    }

    override fun stopMonitoring() {
        logcat { "AppTp Health - stop monitoring" }

        monitoringJob.cancel()
        oldMetricCleanupJob.cancel()
    }

    override fun isMonitoringStarted(): Boolean {
        return monitoringJob.isActive
    }

    sealed class HealthState(open val metrics: RawMetricsSubmission?) {
        data class GoodHealth(override val metrics: RawMetricsSubmission) : HealthState(metrics)
        data class BadHealth(override val metrics: RawMetricsSubmission) : HealthState(metrics)
    }

    private abstract class HealthRule(
        open val name: String,
        open var samplesToWaitBeforeAlerting: Int = DEFAULT_ALERT_SAMPLES,
    ) {
        var badHealthSampleCount: Int = 0

        fun recordBadHealthSample() {
            badHealthSampleCount++
        }

        fun resetBadHealthSampleCount() {
            badHealthSampleCount = 0
        }

        fun shouldAlertBadHealth(): Boolean {
            if (badHealthSampleCount == 0) return false
            return badHealthSampleCount >= samplesToWaitBeforeAlerting
        }
    }
}
