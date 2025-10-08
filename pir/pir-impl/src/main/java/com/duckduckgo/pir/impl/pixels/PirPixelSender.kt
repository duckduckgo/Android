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

package com.duckduckgo.pir.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_BROKER_OPT_OUT_COMPLETED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_BROKER_OPT_OUT_STARTED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_BROKER_SCAN_COMPLETED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_BROKER_SCAN_STARTED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_CPU_USAGE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_MANUAL_SCAN_COMPLETED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_MANUAL_SCAN_STARTED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_OPT_OUT_STATS
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_SCAN_STATS
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_SCHEDULED_SCAN_COMPLETED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_SCHEDULED_SCAN_SCHEDULED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_SCHEDULED_SCAN_STARTED
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import javax.inject.Inject

interface PirPixelSender {
    /**
     * Emits a pixel to signal that a manually initiated scan has started.
     */
    fun reportManualScanStarted()

    /**
     * Emits a pixel to signal that a manually initiated scan has been completed.
     *
     * @param totalTimeInMillis - how long it took for the scan to complete
     */
    fun reportManualScanCompleted(
        totalTimeInMillis: Long,
    )

    /**
     * Emits a pixel to signal that the scheduled scan has been scheduled.
     */
    fun reportScheduledScanScheduled()

    /**
     * Emits a pixel to signal that s scheduled scan run has started.
     */
    fun reportScheduledScanStarted()

    /**
     * Emits a pixel to signal that a scheduled scan run has been completed.
     */
    fun reportScheduledScanCompleted(
        totalTimeInMillis: Long,
    )

    /**
     * Emits a pixel to signal that a scan job for a specific broker x profile has been started.
     *
     * @param brokerName for which this scan job is for
     */
    fun reportBrokerScanStarted(
        brokerName: String,
    )

    /**
     * Emits a pixel to signal that a scan job for a specific broker x profile has been completed (status received: either error or success).
     *
     * @param brokerName - broker name
     * @param totalTimeInMillis - How long it took to complete the scan for the broker.
     * @param isSuccess - if result was not an error, it is a success. Doesn't tell us if the profile was found.
     */
    fun reportBrokerScanCompleted(
        brokerName: String,
        totalTimeInMillis: Long,
        isSuccess: Boolean,
    )

    /**
     * Emits a pixel to signal that an opt-out job for a specific extractedProfile has been started.
     *
     * @param brokerName Broker in which the ExtractedProfile being opted out was found.
     */
    fun reportOptOutStarted(
        brokerName: String,
    )

    /**
     * Emits a pixel to signal that an opt-out job for a specific extractedProfile has been completed.
     * It could mean that the opt-out for the record was successful or failed.
     *
     * @param brokerName for which the opt-out is for
     * @param totalTimeInMillis How long it took to complete the opt-out for the record.
     * @param isSuccess - if result was not an error, it is a success.
     */
    fun reportOptOutCompleted(
        brokerName: String,
        totalTimeInMillis: Long,
        isSuccess: Boolean,
    )

    /**
     * Emits a pixel that contains the scan related stats for the current run.
     *
     * @param totalScanToRun The total number of scans that are eligible for the current run.
     */
    fun reportScanStats(
        totalScanToRun: Int,
    )

    /**
     * Emits a pixel that contains the opt-out related stats for the current run.
     *
     * @param totalOptOutsToRun The total number of opt-outs that are eligible for the current run.
     */
    fun reportOptOutStats(
        totalOptOutsToRun: Int,
    )

    /**
     * Sends a pixel when the CPU Usage threshold has been reached while executing
     * PIR related work.
     *
     * @param averageCpuUsagePercent - average CPU usage percent
     */
    fun sendCPUUsageAlert(averageCpuUsagePercent: Int)
}

@ContributesBinding(AppScope::class)
class RealPirPixelSender @Inject constructor(
    private val pixelSender: Pixel,
) : PirPixelSender {
    override fun reportManualScanStarted() {
        fire(PIR_INTERNAL_MANUAL_SCAN_STARTED)
    }

    override fun reportManualScanCompleted(
        totalTimeInMillis: Long,
    ) {
        val params = mapOf(
            PARAM_KEY_TOTAL_TIME to totalTimeInMillis.toString(),
        )
        fire(PIR_INTERNAL_MANUAL_SCAN_COMPLETED, params)
    }

    override fun reportBrokerScanStarted(brokerName: String) {
        val params = mapOf(
            PARAM_KEY_BROKER_NAME to brokerName,
        )
        fire(PIR_INTERNAL_BROKER_SCAN_STARTED, params)
    }

    override fun reportBrokerScanCompleted(
        brokerName: String,
        totalTimeInMillis: Long,
        isSuccess: Boolean,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER_NAME to brokerName,
            PARAM_KEY_TOTAL_TIME to totalTimeInMillis.toString(),
            PARAM_KEY_SUCCESS to isSuccess.toString(),
        )
        fire(PIR_INTERNAL_BROKER_SCAN_COMPLETED, params)
    }

    override fun reportScheduledScanScheduled() {
        fire(PIR_INTERNAL_SCHEDULED_SCAN_SCHEDULED)
    }

    override fun reportScheduledScanStarted() {
        fire(PIR_INTERNAL_SCHEDULED_SCAN_STARTED)
    }

    override fun reportScheduledScanCompleted(
        totalTimeInMillis: Long,
    ) {
        val params = mapOf(
            PARAM_KEY_TOTAL_TIME to totalTimeInMillis.toString(),
        )
        fire(PIR_INTERNAL_SCHEDULED_SCAN_COMPLETED, params)
    }

    override fun reportOptOutStarted(
        brokerName: String,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER_NAME to brokerName,
        )
        fire(PIR_INTERNAL_BROKER_OPT_OUT_STARTED, params)
    }

    override fun reportOptOutCompleted(
        brokerName: String,
        totalTimeInMillis: Long,
        isSuccess: Boolean,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER_NAME to brokerName,
            PARAM_KEY_TOTAL_TIME to totalTimeInMillis.toString(),
            PARAM_KEY_SUCCESS to isSuccess.toString(),
        )
        fire(PIR_INTERNAL_BROKER_OPT_OUT_COMPLETED, params)
    }

    override fun reportScanStats(totalScanToRun: Int) {
        val params = mapOf(
            PARAM_KEY_SCAN_COUNT to totalScanToRun.toString(),
        )
        fire(PIR_INTERNAL_SCAN_STATS, params)
    }

    override fun reportOptOutStats(totalOptOutsToRun: Int) {
        val params = mapOf(
            PARAM_KEY_OPTOUT_COUNT to totalOptOutsToRun.toString(),
        )
        fire(PIR_INTERNAL_OPT_OUT_STATS, params)
    }

    override fun sendCPUUsageAlert(averageCpuUsagePercent: Int) {
        val params = mapOf(
            PARAM_KEY_CPU_USAGE to averageCpuUsagePercent.toString(),
        )
        fire(PIR_INTERNAL_CPU_USAGE, params)
    }

    private fun fire(
        pixel: PirPixel,
        params: Map<String, String> = emptyMap(),
    ) {
        pixel.getPixelNames().forEach { (pixelType, pixelName) ->
            logcat { "PIR-LOGGING: $pixelName params: $params" }
            pixelSender.fire(pixelName = pixelName, type = pixelType, parameters = params)
        }
    }

    companion object {
        private const val PARAM_KEY_BROKER_NAME = "brokerName"
        private const val PARAM_KEY_TOTAL_TIME = "totalTimeInMillis"
        private const val PARAM_KEY_SUCCESS = "isSuccess"
        private const val PARAM_KEY_SCAN_COUNT = "totalScanToRun"
        private const val PARAM_KEY_OPTOUT_COUNT = "totalOptOutToRun"
        private const val PARAM_KEY_CPU_USAGE = "cpuUsage"
    }
}
