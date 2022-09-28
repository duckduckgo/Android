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
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

/**
 * Used during performance tests - logs CPU usage to a file
 */
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class CPUPerformanceLoggerImpl @Inject constructor() : CPUPerformanceLogger {

    private val loggingJob = ConflatedJob()
    private val samples: MutableList<Double> = mutableListOf()

    override fun startLogging(coroutineScope: CoroutineScope) {
        val cpuUsageReader = CPUUsageReader()
        samples.clear()
        loggingJob += coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5_000)
                try {
                    val avgCPUUsagePercent = cpuUsageReader.readCPUUsage()
                    samples.add(avgCPUUsagePercent)
                } catch (e: Exception) {
                    Timber.e("Could not read CPU usage", e)
                }
            }
        }
    }

    override fun stopLogging(context: Context) {
        loggingJob.cancel()

        val saveDir = context.getExternalFilesDir(null)?.absolutePath
        val csvFile = File(saveDir + File.separator + "output.csv")
        csvFile.delete()
        csvFile.createNewFile()

        val fileWriter = FileWriter(csvFile)
        val header = "Sample,CPU (%)\n"
        fileWriter.write(header)

        // Write out individual samples
        for (i in 0 until samples.size) {
            fileWriter.write("$i," + samples[i] + "\n")
        }

        // Now write the average
        fileWriter.write("avg," + samples.average() + "\n")
        fileWriter.close()
    }
}
