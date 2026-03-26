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

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.annotation.WorkerThread
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.logcat
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.InvalidPropertiesFormatException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

interface CPUUsageReader {
    fun readCPUUsage(): Double
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealCPUUsageReader @Inject constructor() : CPUUsageReader {

    @WorkerThread
    override fun readCPUUsage(): Double {
        val pid = android.os.Process.myPid()
        logcat { "PIR-MONITOR: Reading CPU load for process with pid=$pid" }
        val procFile = File("/proc/$pid/stat")
        val statsText = (FileReader(procFile)).buffered().use(BufferedReader::readText)

        val procArray = statsText.split(WHITE_SPACE)
        if (procArray.size < PROC_SIZE) {
            throw InvalidPropertiesFormatException("Unexpected /proc file size: " + procArray.size)
        }

        val procCPUTimeSec = (procArray[UTIME_IDX].toLong() + procArray[STIME_IDX].toLong()) / CLOCK_SPEED_HZ
        val systemUptimeSec = SystemClock.elapsedRealtime() / 1.seconds.inWholeMilliseconds.toDouble()
        val procTimeSec = systemUptimeSec - (procArray[STARTTIME_IDX].toLong() / CLOCK_SPEED_HZ)

        return (100 * (procCPUTimeSec / procTimeSec)) / NUM_CORES
    }

    companion object {
        private val CLOCK_SPEED_HZ = Os.sysconf(OsConstants._SC_CLK_TCK).toDouble()
        private val NUM_CORES = Os.sysconf(OsConstants._SC_NPROCESSORS_CONF)

        private val WHITE_SPACE = "\\s".toRegex()

        // Indices in /proc/[pid]/stat (https://linux.die.net/man/5/proc)
        private val UTIME_IDX = 13
        private val STIME_IDX = 14
        private val STARTTIME_IDX = 21
        private val PROC_SIZE = 44
    }
}
