/*
 * Copyright (c) 2022 DuckDuckGo
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
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

@ContributesWorker(AppScope::class)
class CPUMonitorWorker(
    context: Context,
    workerParams: WorkerParameters,
) : RemoteCoroutineWorker(context, workerParams) {
    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var cpuUsageReader: CPUUsageReader

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    // TODO: move thresholds to remote config
    private val alertThresholds = listOf(30, 20, 10, 5).sortedDescending()

    override suspend fun doRemoteWork(): Result {
        return withContext(dispatcherProvider.io()) {
            try {
                val avgCPUUsagePercent = cpuUsageReader.readCPUUsage()
                alertThresholds.forEach {
                    if (avgCPUUsagePercent > it) {
                        deviceShieldPixels.sendCPUUsageAlert(it)
                        return@withContext Result.success()
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { e.asLog() }
                return@withContext Result.failure()
            }

            return@withContext Result.success()
        }
    }
}
