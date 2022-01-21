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
import com.duckduckgo.mobile.android.vpn.di.VpnCoroutineScope
import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor.HealthState.BadHealth
import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor.HealthState.GoodHealth
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.REMOVE_FROM_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.REMOVE_FROM_TCP_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.REMOVE_FROM_UDP_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.SOCKET_CHANNEL_CONNECT_EXCEPTION
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.SOCKET_CHANNEL_READ_EXCEPTION
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.SOCKET_CHANNEL_WRITE_EXCEPTION
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.TUN_READ
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.TUN_WRITE_IO_EXCEPTION
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

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
    private val tracerPacketBuilder: TracerPacketBuilder,
    private val vpnQueues: VpnQueues,
    private val callbacks: PluginPoint<AppHealthCallback>
) : AppHealthMonitor {

    companion object {

        // how far back to look when obtaining health metrics
        const val SLIDING_WINDOW_DURATION_MS: Long = 60_000

        private const val MONITORING_FREQUENCY_MS: Long = 30_000
        private const val TRACER_INJECTION_FREQUENCY_MS: Long = 5_000
        private const val OLD_METRIC_CLEANUP_FREQUENCY_MS: Long = 60_000

        private val MEMORY_ALERT_SAMPLES: Int = (15.minutes.inWholeMilliseconds / MONITORING_FREQUENCY_MS).toInt()
        private val TUN_READ_ALERT_SAMPLES: Int = (4.minutes.inWholeMilliseconds / MONITORING_FREQUENCY_MS).toInt()
    }

    private val now: Long
        get() = System.currentTimeMillis()

    private val _healthState = MutableStateFlow(SystemHealthData(isBadHealth = false, emptyList()))
    val healthState: StateFlow<SystemHealthData> = _healthState

    private val monitoringJob = ConflatedJob()
    private val tracerInjectionJob = ConflatedJob()
    private val oldMetricCleanupJob = ConflatedJob()

    private var simulatedGoodHealth: Boolean? = null

    private val healthRules = mutableListOf<HealthRule>()

    private val tunReadAlerts =
        object : HealthRule("tunReadAlerts", samplesToWaitBeforeAlerting = TUN_READ_ALERT_SAMPLES) {}.also { healthRules.add(it) }
    private val socketReadExceptionAlerts = object : HealthRule("socketReadExceptionAlerts") {}.also { healthRules.add(it) }
    private val socketWriteExceptionAlerts = object : HealthRule("socketWriteExceptionAlerts") {}.also { healthRules.add(it) }
    private val socketConnectExceptionAlerts = object : HealthRule("socketConnectExceptionAlerts") {}.also { healthRules.add(it) }
    private val tunWriteExceptionAlerts = object : HealthRule("tunWriteIOExceptions") {}.also { healthRules.add(it) }
    private val memoryAlerts = object : HealthRule("memoryAlerts", samplesToWaitBeforeAlerting = MEMORY_ALERT_SAMPLES) {}.also { healthRules.add(it) }
    private val tracerPacketsAlerts = object : HealthRule("tracerPacketsAlerts", samplesToWaitBeforeAlerting = 2) {}.also { healthRules.add(it) }

    private suspend fun checkCurrentHealth() {
        val timeWindow = now - SLIDING_WINDOW_DURATION_MS

        val healthStates = mutableListOf<HealthState>()
        healthStates += sampleTunReadQueueReadRate(timeWindow, tunReadAlerts)
        healthStates += sampleSocketReadExceptions(timeWindow, socketReadExceptionAlerts)
        healthStates += sampleSocketWriteExceptions(timeWindow, socketWriteExceptionAlerts)
        healthStates += sampleSocketConnectExceptions(timeWindow, socketConnectExceptionAlerts)
        healthStates += sampleTunWriteExceptions(timeWindow, tunWriteExceptionAlerts)
        healthStates += sampleTracerPackets(timeWindow, tracerPacketsAlerts)
        // healthStates += sampleMemoryUsage(memoryAlerts)

        /*
         * useful for testing notifications; can trigger good or bad health from diagnostics screen
         */
        simulateHealthStatusIfEnabled(healthStates)

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

    private fun sampleMemoryUsage(healthAlerts: HealthRule): HealthState {
        val state = healthClassifier.determineHealthMemory()
        healthAlerts.updateAlert(state)
        return state
    }

    private fun sampleTracerPackets(
        timeWindowMillis: Long,
        healthAlerts: HealthRule
    ): HealthState {
        val allTraces = healthMetricCounter.getAllPacketTraces(timeWindowMillis)
        val state = healthClassifier.determineHealthTracerPackets(allTraces)
        healthAlerts.updateAlert(state)
        return state
    }

    private fun sampleTunReadQueueReadRate(
        timeWindow: Long,
        healthAlerts: HealthRule
    ): HealthState {
        val tunReads = healthMetricCounter.getStat(TUN_READ(), timeWindow)
        val readFromNetworkQueue = healthMetricCounter.getStat(REMOVE_FROM_DEVICE_TO_NETWORK_QUEUE(), timeWindow)
        val readFromTCPNetworkQueue = healthMetricCounter.getStat(REMOVE_FROM_TCP_DEVICE_TO_NETWORK_QUEUE(), timeWindow)
        val readFromUDPNetworkQueue = healthMetricCounter.getStat(REMOVE_FROM_UDP_DEVICE_TO_NETWORK_QUEUE(), timeWindow)

        val state = healthClassifier.determineHealthTunInputQueueReadRatio(
            tunReads,
            QueueReads(queueReads = readFromNetworkQueue, queueTCPReads = readFromTCPNetworkQueue, queueUDPReads = readFromUDPNetworkQueue)
        )
        healthAlerts.updateAlert(state)
        return state
    }

    private fun sampleSocketReadExceptions(
        timeWindow: Long,
        healthAlerts: HealthRule
    ): HealthState {
        val readExceptions = healthMetricCounter.getStat(SOCKET_CHANNEL_READ_EXCEPTION(), timeWindow)
        val state = healthClassifier.determineHealthSocketChannelReadExceptions(readExceptions)
        healthAlerts.updateAlert(state)
        return state
    }

    private fun sampleSocketWriteExceptions(
        timeWindow: Long,
        healthAlerts: HealthRule
    ): HealthState {
        val writeExceptions = healthMetricCounter.getStat(SOCKET_CHANNEL_WRITE_EXCEPTION(), timeWindow)
        val state = healthClassifier.determineHealthSocketChannelWriteExceptions(writeExceptions)
        healthAlerts.updateAlert(state)
        return state
    }

    private fun sampleSocketConnectExceptions(
        timeWindow: Long,
        healthAlerts: HealthRule
    ): HealthState {
        val connectExceptions = healthMetricCounter.getStat(SOCKET_CHANNEL_CONNECT_EXCEPTION(), timeWindow)
        val state = healthClassifier.determineHealthSocketChannelConnectExceptions(connectExceptions)
        healthAlerts.updateAlert(state)
        return state
    }

    private fun sampleTunWriteExceptions(
        timeWindow: Long,
        healthAlerts: HealthRule
    ): HealthState {
        val numberExceptions = healthMetricCounter.getStat(TUN_WRITE_IO_EXCEPTION(), timeWindow)
        val state = healthClassifier.determineHealthTunWriteExceptions(numberExceptions)
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

    private fun simulateHealthStatusIfEnabled(healthStates: MutableList<HealthState>) {
        if (simulatedGoodHealth == true) {
            Timber.d("Pretending good health")
            tunReadAlerts.resetBadHealthSampleCount()
            healthStates.clear()
        } else if (simulatedGoodHealth == false) {
            Timber.d("Pretending bad health")
            for (i in 0..40) {
                tunReadAlerts.recordBadHealthSample()
            }

            val fakeMetrics = mutableMapOf<String, Metric>().also {
                it["fakeMetric1"] = Metric("foo", isBadState = true)
                it["fakeMetric2"] = Metric("bar")
            }
            val submission = RawMetricsSubmission("Fake", fakeMetrics)
            healthStates.add(BadHealth(submission))
        }
    }

    override fun startMonitoring() {
        Timber.v("AppTp Health - start monitoring")

        monitoringJob += coroutineScope.launch {
            while (isActive) {
                checkCurrentHealth()
                delay(MONITORING_FREQUENCY_MS)
            }
        }

        tracerInjectionJob += coroutineScope.launch {
            while (isActive) {
                injectTracerPacket()
                delay(TRACER_INJECTION_FREQUENCY_MS)
            }
        }

        oldMetricCleanupJob += coroutineScope.launch {
            while (isActive) {
                Timber.i("Cleaning up old health metrics")
                healthMetricCounter.purgeOldMetrics()
                delay(OLD_METRIC_CLEANUP_FREQUENCY_MS)
            }
        }
    }

    override fun stopMonitoring() {
        Timber.v("AppTp Health - stop monitoring")

        monitoringJob.cancel()
        tracerInjectionJob.cancel()
        oldMetricCleanupJob.cancel()
    }

    override fun isMonitoringStarted(): Boolean {
        return monitoringJob.isActive
    }

    private fun injectTracerPacket() {
        val packet = tracerPacketBuilder.build()
        healthMetricCounter.logTracerPacketEvent(TracerEvent(packet.tracerId, TracedState.CREATED))
        healthMetricCounter.logTracerPacketEvent(TracerEvent(packet.tracerId, TracedState.ADDED_TO_DEVICE_TO_NETWORK_QUEUE))
        vpnQueues.tcpDeviceToNetwork.offer(packet)
    }

    sealed class HealthState(open val metrics: RawMetricsSubmission?) {
        object Initializing : HealthState(null)
        data class GoodHealth(override val metrics: RawMetricsSubmission) : HealthState(metrics)
        data class BadHealth(override val metrics: RawMetricsSubmission) : HealthState(metrics)
    }

    fun simulateHealthState(goodHealth: Boolean?) {
        this.simulatedGoodHealth = goodHealth
    }

    private abstract class HealthRule(
        open val name: String,
        open var samplesToWaitBeforeAlerting: Int = 4
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
