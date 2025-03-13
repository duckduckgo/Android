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

package com.duckduckgo.pir.internal.pixels

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.pixels.PirPixel.PIR_INTERNAL_MANUAL_SCAN_BROKER_COMPLETED
import com.duckduckgo.pir.internal.pixels.PirPixel.PIR_INTERNAL_MANUAL_SCAN_BROKER_STARTED
import com.duckduckgo.pir.internal.pixels.PirPixel.PIR_INTERNAL_MANUAL_SCAN_COMPLETED
import com.duckduckgo.pir.internal.pixels.PirPixel.PIR_INTERNAL_MANUAL_SCAN_STARTED
import com.duckduckgo.pir.internal.pixels.PirPixel.PIR_INTERNAL_SCHEDULED_SCAN_BROKER_COMPLETED
import com.duckduckgo.pir.internal.pixels.PirPixel.PIR_INTERNAL_SCHEDULED_SCAN_BROKER_STARTED
import com.duckduckgo.pir.internal.pixels.PirPixel.PIR_INTERNAL_SCHEDULED_SCAN_COMPLETED
import com.duckduckgo.pir.internal.pixels.PirPixel.PIR_INTERNAL_SCHEDULED_SCAN_SCHEDULED
import com.duckduckgo.pir.internal.pixels.PirPixel.PIR_INTERNAL_SCHEDULED_SCAN_STARTED
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface PirPixelSender {
    // Manual Scan Pixels
    fun reportManualScanStarted()

    /**
     * Tells us the important data about the Manual scan
     *
     * @param totalTimeInMillis - how long it took for the scan to complete
     * @param totalParallelWebViews - total number of parallel webview used.
     * @param totalBrokerSuccess - how many didn't result to a PirError
     * @param totalBrokerFailed - how many resulted to an error - includes timeout, failed to find
     */
    fun reportManualScanCompleted(
        totalTimeInMillis: Long,
        totalParallelWebViews: Int,
        totalBrokerSuccess: Int,
        totalBrokerFailed: Int,
    )

    fun reportManualScanBrokerStarted(
        brokerName: String,
    )

    /**
     * For each broker, we will emit how long it took for the scan to be completed (status received: either error or success).
     *
     * @param brokerName - broker name
     * @param totalTimeInMillis - How long it took to complete the scan for the broker.
     * @param isSuccess - if result was not an error, it is a success. Doesn't tell us if the profile was found.
     */
    fun reportManualScanBrokerCompleted(
        brokerName: String,
        totalTimeInMillis: Long,
        isSuccess: Boolean,
    )

    /**
     * Tells us how many scheduled scans were actually scheduled.
     */
    fun reportScheduledScanScheduled()

    /**
     * Tells us whenever a scheduled scan is run.
     */
    fun reportScheduledScanStarted()

    /**
     * Tells us whenever a scheduled scan is marked as completed.
     * This means that all brokers have been scanned and received result from.
     *
     * This also emits a unique pixel which tells us when the first complete scan is achieved.
     */
    fun reportScheduledScanCompleted(
        totalTimeInMillis: Long,
        totalParallelWebViews: Int,
        totalBrokerSuccess: Int,
        totalBrokerFailed: Int,
    )

    fun reportScheduledScanBrokerStarted(
        brokerName: String,
    )

    /**
     * For each broker, we will emit how long it took for the scan to be completed (status received: either error or success).
     *
     * @param brokerName - broker name
     * @param totalTimeInMillis - How long it took to complete the scan for the broker.
     * @param isSuccess - if result was not an error, it is a success. Doesn't tell us if the profile was found.
     */
    fun reportScheduledScanBrokerCompleted(
        brokerName: String,
        totalTimeInMillis: Long,
        isSuccess: Boolean,
    )
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
        totalParallelWebViews: Int,
        totalBrokerSuccess: Int,
        totalBrokerFailed: Int,
    ) {
        val params = mapOf(
            "totalTimeInMillis" to totalTimeInMillis.toString(),
            "totalParallelWebViews" to totalParallelWebViews.toString(),
            "totalBrokerSuccess" to totalBrokerSuccess.toString(),
            "totalBrokerFailed" to totalBrokerFailed.toString(),
        )
        fire(PIR_INTERNAL_MANUAL_SCAN_COMPLETED, params)
    }

    override fun reportManualScanBrokerStarted(brokerName: String) {
        val params = mapOf(
            "brokerName" to brokerName,
        )
        fire(PIR_INTERNAL_MANUAL_SCAN_BROKER_STARTED, params)
    }

    override fun reportManualScanBrokerCompleted(
        brokerName: String,
        totalTimeInMillis: Long,
        isSuccess: Boolean,
    ) {
        val params = mapOf(
            "brokerName" to brokerName,
            "totalTimeInMillis" to totalTimeInMillis.toString(),
            "isSuccess" to isSuccess.toString(),
        )
        fire(PIR_INTERNAL_MANUAL_SCAN_BROKER_COMPLETED, params)
    }

    override fun reportScheduledScanScheduled() {
        fire(PIR_INTERNAL_SCHEDULED_SCAN_SCHEDULED)
    }

    override fun reportScheduledScanStarted() {
        fire(PIR_INTERNAL_SCHEDULED_SCAN_STARTED)
    }

    override fun reportScheduledScanCompleted(
        totalTimeInMillis: Long,
        totalParallelWebViews: Int,
        totalBrokerSuccess: Int,
        totalBrokerFailed: Int,
    ) {
        val params = mapOf(
            "totalTimeInMillis" to totalTimeInMillis.toString(),
            "totalParallelWebViews" to totalParallelWebViews.toString(),
            "totalBrokerSuccess" to totalBrokerSuccess.toString(),
            "totalBrokerFailed" to totalBrokerFailed.toString(),
        )
        fire(PIR_INTERNAL_SCHEDULED_SCAN_COMPLETED, params)
    }

    override fun reportScheduledScanBrokerCompleted(
        brokerName: String,
        totalTimeInMillis: Long,
        isSuccess: Boolean,
    ) {
        val params = mapOf(
            "brokerName" to brokerName,
            "totalTimeInMillis" to totalTimeInMillis.toString(),
            "isSuccess" to isSuccess.toString(),
        )
        fire(PIR_INTERNAL_SCHEDULED_SCAN_BROKER_COMPLETED, params)
    }

    override fun reportScheduledScanBrokerStarted(brokerName: String) {
        val params = mapOf(
            "brokerName" to brokerName,
        )
        fire(PIR_INTERNAL_SCHEDULED_SCAN_BROKER_STARTED, params)
    }

    private fun fire(
        pixel: PirPixel,
        params: Map<String, String> = emptyMap(),
    ) {
        pixel.getPixelNames().forEach { (pixelType, pixelName) ->
            pixelSender.fire(pixelName = pixelName, type = pixelType, parameters = params)
        }
    }
}
