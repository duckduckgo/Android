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

import android.content.Context
import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor.HealthState
import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor.HealthState.*
import timber.log.Timber
import javax.inject.Inject

class HealthClassifier @Inject constructor(val applicationContext: Context) {

    fun determineHealthTunInputQueueReadRatio(
        tunInputs: Long,
        queueReads: QueueReads
    ): HealthState {
        if (tunInputs < 100) return Initializing

        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("tunInputs-queueReads", rawMetrics)

        val percentage = percentage(queueReads.queueReads, tunInputs)

        rawMetrics["tunInputsQueueReadRate"] = Metric(percentage.toString(), badHealthIf { percentage < 70 })
        rawMetrics["tunInputs"] = Metric(tunInputs.toString())
        rawMetrics["unknownPackets"] = Metric(queueReads.unknownPackets.toString())
        rawMetrics["queueReads"] = Metric(queueReads.queueReads.toString())
        rawMetrics["queueTCPReads"] = Metric(queueReads.queueTCPReads.toString())
        rawMetrics["queueUDPReads"] = Metric(queueReads.queueUDPReads.toString())

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthSocketChannelReadExceptions(readExceptions: Long): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("socket-readExceptions", rawMetrics)

        rawMetrics["numberExceptions"] = Metric(readExceptions.toString(), badHealthIf { readExceptions >= 20 })

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthSocketChannelWriteExceptions(writeExceptions: Long): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("socket-writeExceptions", rawMetrics)

        rawMetrics["numberExceptions"] = Metric(writeExceptions.toString(), badHealthIf { writeExceptions >= 20 })

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthSocketChannelConnectExceptions(connectExceptions: Long): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("socket-connectExceptions", rawMetrics)

        rawMetrics["numberExceptions"] = Metric(connectExceptions.toString(), badHealthIf { connectExceptions >= 20 })

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthTunWriteExceptions(numberExceptions: Long): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("tun-ioWriteExceptions", rawMetrics)

        rawMetrics["numberExceptions"] = Metric(numberExceptions.toString(), badHealthIf { numberExceptions >= 1 })

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthTunWriteMemoryExceptions(numberExceptions: Long): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("tun-ioWriteMemoryExceptions", rawMetrics)

        rawMetrics["numberExceptions"] = Metric(numberExceptions.toString(), badHealthIf { numberExceptions >= 1 })

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthBufferAllocations(bufferAllocations: Long): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("buffer-allocations", rawMetrics, informational = true)

        // never trigger an alert for this. We just one the info
        rawMetrics["numberAllocations"] = Metric(bufferAllocations.toString(), badHealthIf { false })

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    fun determineHealthMemory(): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("memory", rawMetrics, redacted = true)

        val memorySnapshot = CurrentMemorySnapshot(applicationContext)

        val percentageHeap = percentage(memorySnapshot.heapAllocated, memorySnapshot.heapMax)
        rawMetrics["heapPercentageUsed"] = Metric(percentageHeap.percentageFormatted(), badHealthIf { percentageHeap >= 95 })
        rawMetrics["heapMax"] = Metric(memorySnapshot.heapMax.toString())
        rawMetrics["heapAllocated"] = Metric(memorySnapshot.heapAllocated.toString())
        rawMetrics["heapRemaining"] = Metric(memorySnapshot.heapRemaining.toString())

        val percentageNative = percentage(memorySnapshot.nativeAllocated, memorySnapshot.nativeMax)
        rawMetrics["nativePercentageUsed"] = Metric(percentageNative.percentageFormatted(), badHealthIf { percentageNative >= 95 })
        rawMetrics["nativeMax"] = Metric(memorySnapshot.nativeMax.toString())
        rawMetrics["nativeAllocated"] = Metric(memorySnapshot.nativeAllocated.toString())
        rawMetrics["nativeRemaining"] = Metric(memorySnapshot.nativeRemaining.toString())

        val percentageDevice = memorySnapshot.percentageDeviceUsed
        rawMetrics["devicePercentageUsed"] = Metric(percentageDevice.percentageFormatted(), badHealthIf { percentageDevice >= 95 })
        rawMetrics["deviceMax"] = Metric(memorySnapshot.deviceMax.toString())
        rawMetrics["deviceAllocated"] = Metric(memorySnapshot.deviceAllocated.toString())
        rawMetrics["deviceRemaining"] = Metric(memorySnapshot.deviceRemaining.toString())

        val percentageWholeApp = memorySnapshot.percentageUsedWholeApp
        rawMetrics["totalMemoryBrowser"] = Metric(memorySnapshot.totalMemoryBrowserProcess.toString())
        rawMetrics["totalMemoryVpn"] = Metric(memorySnapshot.totalMemoryVpnProcess.toString())
        rawMetrics["totalMemoryWholeApp"] = Metric((memorySnapshot.totalMemoryVpnProcess + memorySnapshot.totalMemoryBrowserProcess).toString())
        rawMetrics["totalWholeAppPercentageUsed"] = Metric(percentageWholeApp.percentageFormatted(), badHealthIf { percentageWholeApp >= 95 })

        rawMetrics["lowMemoryDevice"] = Metric(memorySnapshot.lowMemoryDevice.toString())

        Timber.v("Memory health: %s\n%s", if (metricSummary.isInBadHealth()) "bad: %s" else "good", rawMetrics, metricSummary.badHealthReasons())
        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    private fun badHealthIf(function: () -> Boolean): Boolean {
        return function.invoke()
    }

    companion object {

        fun percentage(
            numerator: Long,
            denominator: Long
        ): Double {
            if (denominator == 0L) return 0.0
            return numerator.toDouble() / denominator * 100
        }

        fun Double.percentageFormatted(): String {
            return String.format("%.2f", this)
        }
    }
}

data class QueueReads(
    val queueReads: Long,
    val queueTCPReads: Long,
    val queueUDPReads: Long,
    val unknownPackets: Long,
)
