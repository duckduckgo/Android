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

package com.duckduckgo.pir.impl.callbacks

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PirCallbacks::class,
)
@SingleInstanceIn(AppScope::class)
class PirCpuMonitor @Inject constructor(
    private val pixelSender: PirPixelSender,
    private val dispatcherProvider: DispatcherProvider,
    private val cpuUsageReader: CPUUsageReader,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : PirCallbacks {
    private val alertThresholds = listOf(30, 20, 10).sortedDescending()
    private val monitorJob = ConflatedJob()

    override fun onPirJobStarted() {
        monitorJob += appCoroutineScope.launch(dispatcherProvider.io()) {
            logcat { "PIR-MONITOR: ${this@PirCpuMonitor} onPirJobStarted " }
            delay(10_000) // Add delay before measuring
            while (isActive) {
                try {
                    val avgCPUUsagePercent = cpuUsageReader.readCPUUsage()
                    logcat { "PIR-MONITOR: avgCPUUsagePercent: $avgCPUUsagePercent on ${android.os.Process.myPid()} " }
                    // If any threshold has been reached, we will a emit a pixel.
                    alertThresholds.forEach {
                        if (avgCPUUsagePercent > it) {
                            pixelSender.sendCPUUsageAlert(it)
                        }
                    }
                    delay(60_000)
                } catch (e: Exception) {
                    logcat(ERROR) { e.asLog() }
                    if (monitorJob.isActive) {
                        monitorJob.cancel()
                    }
                }
            }
        }
    }

    override fun onPirJobCompleted() {
        logcat { "PIR-MONITOR: ${this@PirCpuMonitor} onPirJobCompleted" }
        if (monitorJob.isActive) {
            monitorJob.cancel()
        }
    }

    override fun onPirJobStopped() {
        logcat { "PIR-MONITOR: ${this@PirCpuMonitor} onPirJobStopped" }
        if (monitorJob.isActive) {
            monitorJob.cancel()
        }
    }
}
