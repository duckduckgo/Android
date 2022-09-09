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
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo.State.CANCELLED
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AppTPCPUMonitor @Inject constructor(
    private val workManager: WorkManager,
) : CPUMonitor {

    companion object {
        private const val APP_TRACKER_CPU_MONITOR_WORKER_TAG = "APP_TRACKER_CPU_MONITOR_WORKER_TAG"
    }

    override fun startMonitoring() {
        Timber.v("AppTPCPU - startMonitoring")
        val work = PeriodicWorkRequestBuilder<CPUMonitorWorker>(1, TimeUnit.HOURS)
            .build()

        workManager.enqueueUniquePeriodicWork(APP_TRACKER_CPU_MONITOR_WORKER_TAG, ExistingPeriodicWorkPolicy.KEEP, work)
    }

    override fun stopMonitoring() {
        Timber.v("AppTPCPU - stopMonitoring")
        workManager.cancelUniqueWork(APP_TRACKER_CPU_MONITOR_WORKER_TAG)
    }

    override fun isMonitoringStarted(): Boolean {
        val listenableFuture = workManager.getWorkInfosForUniqueWork(APP_TRACKER_CPU_MONITOR_WORKER_TAG)
        val workerList = listenableFuture.get()

        return workerList != null && workerList.size > 0 && workerList[0].state != CANCELLED
    }
}

@ContributesWorker(AppScope::class)
class CPUMonitorWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    companion object {
        private val CLOCK_SPEED_HZ = Os.sysconf(OsConstants._SC_CLK_TCK)
        private val NUM_CORES = Os.sysconf(OsConstants._SC_NPROCESSORS_CONF)

        private val WHITE_SPACE = "\\s".toRegex()

        // Indices in /proc/[pid]/stat (https://linux.die.net/man/5/proc)
        private val UTIME_IDX = 13
        private val STIME_IDX = 14
        private val STARTTIME_IDX = 21
        private val PROC_SIZE = 44
    }

    override fun doWork(): Result {
        val pid = android.os.Process.myPid()
        try {
            val procFile = File("/proc/$pid/stat")

            val statsText = (FileReader(procFile)).buffered().use(BufferedReader::readText)
            val procArray = statsText.split(WHITE_SPACE)

            if (procArray.size < PROC_SIZE) {
                Timber.e("Unexpected /proc file size: " + procArray.size)
                return Result.failure()
            }

            val procCPUTimeSec = (procArray[UTIME_IDX].toLong() + procArray[STIME_IDX].toLong()) / CLOCK_SPEED_HZ.toDouble()
            val systemUptimeSec = SystemClock.elapsedRealtime() / 1.seconds.inWholeMilliseconds.toDouble()
            val procTimeSec = systemUptimeSec - (procArray[STARTTIME_IDX].toLong() / CLOCK_SPEED_HZ.toDouble())

            val avgCPUUsagePercent = (100 * (procCPUTimeSec / procTimeSec)) / NUM_CORES
            deviceShieldPixels.sendCPUUsage(avgCPUUsagePercent.roundToInt())
        } catch (e: IOException) {
            Timber.e("Could not read CPU usage", e)
            return Result.failure()
        }

        return Result.success()
    }
}
