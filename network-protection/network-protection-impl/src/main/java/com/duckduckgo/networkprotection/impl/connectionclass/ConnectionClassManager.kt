/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.connectionclass

import com.duckduckgo.app.di.AppCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class ConnectionClassManager @Inject constructor(
    private val latencyMeasurements: ExponentialGeometricAverage,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val connectionQualityStore: ConnectionQualityStore,
) {
    private val currentConnectionQuality = AtomicReference<ConnectionQuality>(ConnectionQuality.UNKNOWN)
    private val nextConnectionQuality = AtomicReference<ConnectionQuality>()
    private var initiateStateChange = false
    private var sampleCounter = 0
    private val connectionQualityFlow = MutableStateFlow(ConnectionQuality.UNKNOWN)
    private val mutex = Mutex()

    internal fun connectionQuality(): StateFlow<ConnectionQuality> {
        return connectionQualityFlow.asStateFlow()
    }

    /**
     * Adds the [latencyMs] in milliseconds and calculates the running average that can be retrieved using the
     * [getLatencyAverage]
     * @param latencyMs the latency in milliseconds
     */
    internal suspend fun addLatency(latencyMs: Double) {
        mutex.lock()
        try {
            this.latencyMeasurements.addMeasurement(latencyMs)
            connectionQualityStore.saveConnectionLatency(latencyMeasurements.average.toInt())

            if (initiateStateChange) {
                sampleCounter += 1
                if (getConnectionQuality() != nextConnectionQuality.get()) {
                    initiateStateChange = false
                    sampleCounter = 1
                }
                if (sampleCounter >= DEFAULT_SAMPLES_TO_QUALITY_CHANGE && significantlyOutsideCurrentBand()) {
                    initiateStateChange = false
                    sampleCounter = 1
                    currentConnectionQuality.set(nextConnectionQuality.get())
                    coroutineScope.launch {
                        connectionQualityFlow.emit(currentConnectionQuality.get())
                    }
                }
            } else if (currentConnectionQuality.get() != getConnectionQuality() ||
                currentConnectionQuality.get().isUnknown()
            ) {
                initiateStateChange = true
                nextConnectionQuality.set(getConnectionQuality())
            }
        } finally {
            mutex.unlock()
        }
    }

    /**
     * @return the [ConnectionQuality] based on the latency running average
     */
    internal fun getConnectionQuality(): ConnectionQuality {
        return latencyMeasurements.average.asConnectionQuality()
    }

    /**
     * @return the latency running average
     */
    internal fun getLatencyAverage(): Double {
        return latencyMeasurements.average
    }

    /**
     * Resets all latency measurements
     */
    internal suspend fun reset() {
        latencyMeasurements.reset()
        currentConnectionQuality.set(ConnectionQuality.UNKNOWN)
        connectionQualityStore.reset()
    }

    private fun significantlyOutsideCurrentBand(): Boolean {
        val currentQuality = currentConnectionQuality.get() ?: return false
        val (bottomOfBand, topOfBand) = when (currentQuality) {
            ConnectionQuality.TERRIBLE -> {
                ConnectionQuality.POOR.max to Double.MAX_VALUE
            }
            ConnectionQuality.POOR -> {
                ConnectionQuality.MODERATE.max to ConnectionQuality.POOR.max
            }
            ConnectionQuality.MODERATE -> {
                ConnectionQuality.GOOD.max to ConnectionQuality.MODERATE.max
            }
            ConnectionQuality.GOOD -> {
                ConnectionQuality.EXCELLENT.max to ConnectionQuality.GOOD.max
            }
            ConnectionQuality.EXCELLENT -> {
                0.0 to ConnectionQuality.EXCELLENT.max
            }
            ConnectionQuality.UNKNOWN -> {
                // If current quality is UNKNOWN, then changing is always valid.
                return true
            }
        }

        val average = latencyMeasurements.average
        return if (average / HYSTERESIS_TOP_MULTIPLIER > topOfBand) { // avoid Double.MAX_VALUE overflow
            true
        } else if (average < bottomOfBand * HYSTERESIS_BOTTOM_MULTIPLIER) {
            true
        } else {
            false
        }
    }

    private fun ConnectionQuality.isUnknown(): Boolean {
        return this == ConnectionQuality.UNKNOWN
    }

    companion object {
        private const val DEFAULT_SAMPLES_TO_QUALITY_CHANGE = 5
        private const val DEFAULT_HYSTERESIS_PERCENT: Long = 20
        private const val HYSTERESIS_TOP_MULTIPLIER = 100.0 / (100.0 - DEFAULT_HYSTERESIS_PERCENT)
        private const val HYSTERESIS_BOTTOM_MULTIPLIER = (100.0 - DEFAULT_HYSTERESIS_PERCENT) / 100.0
    }
}
