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

import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor.HealthState
import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor.HealthState.*
import com.duckduckgo.mobile.android.vpn.model.HealthTriggerEntity
import com.duckduckgo.mobile.android.vpn.store.AppHealthTriggersRepository
import javax.inject.Inject

class HealthClassifier @Inject constructor(
    private val appHealthTriggersRepository: AppHealthTriggersRepository,
) {

    fun determineHealthTunInputQueueReadRatio(
        tunInputs: Long,
        queueReads: QueueReads,
        name: String,
    ): HealthState {
        if (tunInputs < 100) return Initializing

        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("tunInputs-queueReads", rawMetrics)

        val percentage = percentage(queueReads.queueReads, tunInputs)
        val failureRate = 100 - percentage

        val trigger = appHealthTriggersRepository.triggers().trigger(name, failureRate.toLong(), defaultThreshold = 70)
        rawMetrics["tunInputsQueueReadRate"] = Metric(percentage.toString(), badHealthIf { trigger.evaluate() })
        rawMetrics["tunInputs"] = Metric(tunInputs.toString())
        rawMetrics["unknownPackets"] = Metric(queueReads.unknownPackets.toString())
        rawMetrics["queueReads"] = Metric(queueReads.queueReads.toString())
        rawMetrics["queueTCPReads"] = Metric(queueReads.queueTCPReads.toString())
        rawMetrics["queueUDPReads"] = Metric(queueReads.queueUDPReads.toString())

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthVpnConnectivity(connectivityEvents: Long, name: String): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("vpn-no-connectivity-events", rawMetrics)

        val trigger = appHealthTriggersRepository.triggers().trigger(name, connectivityEvents, defaultThreshold = 2)
        rawMetrics["events"] = Metric(connectivityEvents.toString(), badHealthIf { trigger.evaluate() }, isCritical = true)

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthSocketChannelReadExceptions(readExceptions: Long, name: String): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("socket-readExceptions", rawMetrics, informational = true)

        // Default value Int.MAX_VALUE disables the bad health trigger
        val trigger = appHealthTriggersRepository.triggers().trigger(name, readExceptions)
        rawMetrics["numberExceptions"] = Metric(readExceptions.toString(), badHealthIf { trigger.evaluate() })

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthSocketChannelWriteExceptions(writeExceptions: Long, name: String): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("socket-writeExceptions", rawMetrics)

        val trigger = appHealthTriggersRepository.triggers().trigger(name, writeExceptions, defaultThreshold = 20)
        rawMetrics["numberExceptions"] = Metric(writeExceptions.toString(), badHealthIf { trigger.evaluate() })

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthSocketChannelConnectExceptions(connectExceptions: Long, name: String): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("socket-connectExceptions", rawMetrics)

        val trigger = appHealthTriggersRepository.triggers().trigger(name, connectExceptions, defaultThreshold = 20)
        rawMetrics["numberExceptions"] = Metric(connectExceptions.toString(), badHealthIf { trigger.evaluate() })

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthTunReadExceptions(numberExceptions: Long, name: String): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("tun-ioReadExceptions", rawMetrics)

        val trigger = appHealthTriggersRepository.triggers().trigger(name, numberExceptions, defaultThreshold = 10)
        rawMetrics["numberExceptions"] = Metric(numberExceptions.toString(), badHealthIf { trigger.evaluate() })

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthTunWriteExceptions(numberExceptions: Long, name: String): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("tun-ioWriteExceptions", rawMetrics)

        val trigger = appHealthTriggersRepository.triggers().trigger(name, numberExceptions, defaultThreshold = 1)
        rawMetrics["numberExceptions"] = Metric(numberExceptions.toString(), badHealthIf { trigger.evaluate() })

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthTunWriteMemoryExceptions(numberExceptions: Long, name: String): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("tun-ioWriteMemoryExceptions", rawMetrics)

        // this is marked as isCritical because we may want to take extreme measures when bad health happens here
        val trigger = appHealthTriggersRepository.triggers().trigger(name, numberExceptions, defaultThreshold = 1)
        rawMetrics["numberExceptions"] = Metric(numberExceptions.toString(), badHealthIf { trigger.evaluate() }, isCritical = true)

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthBufferAllocations(bufferAllocations: Long): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("buffer-allocations", rawMetrics, informational = true)

        // never trigger an alert for this. We just one the info
        rawMetrics["numberAllocations"] = Metric(bufferAllocations.toString(), badHealthIf { false })

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthIpPackets(
        ipv4PacketCount: Long,
        ipv6PacketCount: Long,
    ): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("ipPacket-types", rawMetrics, informational = true)

        // never trigger an alert for this. We just one the info
        rawMetrics["ipv4"] = Metric(ipv4PacketCount.toString(), badHealthIf { false })
        rawMetrics["ipv6"] = Metric(ipv6PacketCount.toString(), badHealthIf { false })

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    private fun badHealthIf(function: () -> Boolean): Boolean {
        return function.invoke()
    }

    private fun List<HealthTriggerEntity>.trigger(name: String, value: Long, defaultThreshold: Long? = null): Trigger {
        // if there is no trigger use either the [defaultThreshold] or trigger disabled
        val trigger = firstOrNull {
            it.name.endsWith(name, ignoreCase = true)
        } ?: return (defaultThreshold?.let { Trigger { value >= defaultThreshold } } ?: Trigger { false })

        // if remote trigger state is enabled, use the trigger threshold or defaultThreshold or disable trigger
        return if (trigger.enabled) {
            trigger.threshold?.let { Trigger { value >= it } }
                ?: (defaultThreshold?.let { Trigger { value >= defaultThreshold } } ?: Trigger { false })
        } else {
            Trigger { false }
        }
    }

    private fun interface Trigger {
        fun evaluate(): Boolean
    }

    companion object {

        private fun percentage(
            numerator: Long,
            denominator: Long,
        ): Double {
            if (denominator == 0L) return 0.0
            return numerator.toDouble() / denominator * 100
        }
    }
}

data class QueueReads(
    val queueReads: Long,
    val queueTCPReads: Long,
    val queueUDPReads: Long,
    val unknownPackets: Long,
)
