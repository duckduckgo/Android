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

import android.net.InetAddresses
import android.os.Build
import android.util.Patterns
import androidx.annotation.WorkerThread
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.LogPriority.WARN
import logcat.logcat
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Measures latency to provided IP and returns it in milliseconds (ms)
 */
@WorkerThread
interface LatencyMeasurer {
    /**
     * Measures latency to the provided server
     * @return latency in ms, or a negative value if an error occurred
     */
    fun measureLatency(serverIP: String): Int
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class LatencyMeasurerImpl @Inject constructor() : LatencyMeasurer {

    companion object {
        const val INVALID_IP_ERROR = -1
        const val PARSING_ERROR_1 = -2
        const val PARSING_ERROR_2 = -3
        const val NUMBER_FORMAT_ERROR = -4

        private val WHITE_SPACE = "\\s".toRegex()
        private val EXPECTED_OUTPUT_ELEMENTS = 8
        private val TIME_INDEX = 6
        private val EXPECTED_TIME_ELEMENTS = 2
    }

    /**
     * Measures latency to the provided server
     * @return latency in ms, or a negative value if an error occurred
     */
    override fun measureLatency(serverIP: String): Int {
        logcat { "measure latency" }

        // Validate IP as we are passing it to a shell
        val isValidIP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            InetAddresses.isNumericAddress(serverIP)
        } else {
            Patterns.IP_ADDRESS.matcher(serverIP).matches()
        }

        if (!isValidIP) {
            return INVALID_IP_ERROR
        }

        val process = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", serverIP))
        val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

        // Skip first line - e.g. PING 109.200.208.196 (109.200.208.196) 56(84) bytes of data
        bufferedReader.readLine()

        // Parse second line to get latency - e.g. 64 bytes from 109.200.208.196: icmp_seq=1 ttl=52 time=69.0 ms
        val splitLine = bufferedReader.readLine()?.split(WHITE_SPACE)
        if (splitLine == null || splitLine.size < EXPECTED_OUTPUT_ELEMENTS) {
            logcat(WARN) { "Unexpected ping output size: $splitLine" }
            return PARSING_ERROR_1
        }

        // Parse time=69.0
        val timeSplit = splitLine[TIME_INDEX].split("=")
        if (timeSplit.size < EXPECTED_TIME_ELEMENTS || timeSplit[0] != "time") {
            logcat(WARN) { "Unexpected time format: ${splitLine[TIME_INDEX]}" }
            return PARSING_ERROR_2
        }

        return try {
            timeSplit[1].toDouble().roundToInt()
        } catch (e: NumberFormatException) {
            logcat(WARN) { "Could not convert to number: ${timeSplit[1]}" }
            NUMBER_FORMAT_ERROR
        }
    }
}
