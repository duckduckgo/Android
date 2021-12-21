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

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import javax.inject.Inject
import timber.log.Timber

class CurrentMemorySnapshot @Inject constructor(val applicationContext: Context) {

    // heap
    val heapMax: Long
    val heapAllocated: Long
    val heapRemaining: Long
    val percentageHeapUsed: Double
        get() = if (heapMax == 0L) 100.0 else (100.0 * heapAllocated / heapMax)

    // native
    val nativeMax: Long
    val nativeAllocated: Long
    val nativeRemaining: Long
    val percentageNativeUsed: Double
        get() = if (nativeMax == 0L) 100.0 else (100.0 * nativeAllocated / nativeMax)

    // device
    val deviceLowMemoryThreshold: Long
    val deviceAllocated: Long
    val deviceRemaining: Long
    val deviceMax: Long
    val percentageDeviceUsed: Double
        get() = if (deviceMax == 0L) 100.0 else 100.0 * deviceRemaining / deviceMax

    val lowMemoryDevice: Boolean
    val maxMemoryPerApp: Int
    val percentageUsedWholeApp: Double
        get() = 100.0 * (totalMemoryVpnProcess + totalMemoryBrowserProcess) / maxMemoryPerApp

    // memory per process
    val totalMemoryBrowserProcess: Int
    val totalMemoryVpnProcess: Int

    init {
        with(Runtime.getRuntime()) {
            heapMax = (maxMemory() / BYTES_PER_MB)
            heapAllocated = (totalMemory() - freeMemory()) / BYTES_PER_MB
            heapRemaining = heapMax - heapAllocated
        }

        nativeMax = Debug.getNativeHeapSize() / BYTES_PER_MB
        nativeAllocated = Debug.getNativeHeapAllocatedSize() / BYTES_PER_MB
        nativeRemaining = Debug.getNativeHeapFreeSize() / BYTES_PER_MB

        val activityManager =
            applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processMemoryStats = getMemoryPerProcess(activityManager)

        if (processMemoryStats.isEmpty()) {
            totalMemoryBrowserProcess = 0
            totalMemoryVpnProcess = 0
        } else {
            totalMemoryBrowserProcess =
                processMemoryStats.filterNot { it.name.endsWith(":vpn") }.firstOrNull()?.memoryPssMb
                    ?: 0
            totalMemoryVpnProcess =
                processMemoryStats.firstOrNull { it.name.endsWith(":vpn") }?.memoryPssMb ?: 0
        }

        maxMemoryPerApp = activityManager.largeMemoryClass

        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        lowMemoryDevice = activityManager.isLowRamDevice

        deviceRemaining = memoryInfo.availMem / BYTES_PER_MB
        deviceMax = memoryInfo.totalMem / BYTES_PER_MB
        deviceAllocated = deviceMax - deviceRemaining
        deviceLowMemoryThreshold = memoryInfo.threshold / BYTES_PER_MB
    }

    private fun getMemoryPerProcess(
        activityManager: ActivityManager
    ): List<ProcessMemoryConsumption> {
        val processes: List<Pair<Int, String>> =
            activityManager.runningAppProcesses.filterNotNull().map { Pair(it.pid, it.processName) }

        val pids = IntArray(processes.size)
        processes.forEachIndexed { index, process -> pids[index] = process.first }

        val processMemoryInfo = activityManager.getProcessMemoryInfo(pids)
        val processMemoryStats =
            processMemoryInfo.mapIndexed { index, memoryInfo ->
                ProcessMemoryConsumption(
                    processes[index].first, processes[index].second, memoryInfo.totalPss / 1024)
            }

        Timber.v("Memory per process:\n%s", processMemoryStats.joinToString(separator = "\n"))

        return processMemoryStats
    }

    data class ProcessMemoryConsumption(val pid: Int, val name: String, val memoryPssMb: Int) {
        override fun toString(): String {
            return String.format("pid: %d, %d MB, %s", pid, memoryPssMb, name)
        }
    }

    override fun toString(): String {
        val totalMemoryConsumed = (totalMemoryBrowserProcess + totalMemoryVpnProcess)

        return String.format(
            "Used by our app: %.2f%%" +
                "\nMax=%d MB, Remaining=%d MB" +
                "\nAllocated=%d MB\n  Browser Process: %d MB\n  VPN Process: %d MB" +
                "\n\nHeap %.2f%%. Allocated=%d MB\nMax=%d MB, Remaining=%d MB" +
                "\n\nNative %.2f%%. Allocated=%d MB\nMax=%d MB, Remaining=%d MB," +
                "\n\nDevice total %.2f%%. Allocated=%d MB\nMax=%d MB, Remaining=%d MB",
            percentageUsedWholeApp,
            maxMemoryPerApp,
            maxMemoryPerApp - totalMemoryConsumed,
            totalMemoryConsumed,
            totalMemoryBrowserProcess,
            totalMemoryVpnProcess,
            percentageHeapUsed,
            heapAllocated,
            heapMax,
            heapRemaining,
            percentageNativeUsed,
            nativeAllocated,
            nativeMax,
            nativeRemaining,
            percentageDeviceUsed,
            deviceAllocated,
            deviceMax,
            deviceRemaining,
        )
    }

    companion object {
        private const val BYTES_PER_KB = 1024
        private const val BYTES_PER_MB = 1024 * 1024
    }
}
