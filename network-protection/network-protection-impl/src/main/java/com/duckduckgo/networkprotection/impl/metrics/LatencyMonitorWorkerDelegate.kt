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

package com.duckduckgo.networkprotection.impl.metrics

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.WorkerThread
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import logcat.LogPriority
import logcat.logcat

/**
 * Class used by @LatencyMonitorWorker
 * it only exists to make unit testing function without inserting delays
 * see discussion in https://github.com/duckduckgo/Android/pull/2489
 */
interface LatencyMonitorWorkerDelegate {
    @WorkerThread
    fun doDelegateWork(inputData: Data, connectivityManager: ConnectivityManager): Result
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class LatencyMonitorWorkerDelegateImpl @Inject constructor(
    private val netpPixels: NetworkProtectionPixels,
    private val latencyMeasurer: LatencyMeasurer,
) : LatencyMonitorWorkerDelegate {

    override fun doDelegateWork(inputData: Data, connectivityManager: ConnectivityManager): Result {
        val serverIP = inputData.getString(LatencyMonitorCallback.SERVER_IP_INPUT)
        if (serverIP == null) {
            // Should not happen as we do a check before passing the input
            logcat(LogPriority.WARN) { "server IP not available - cannot measure latency" }
            return Result.failure()
        }

        val serverName = inputData.getString(LatencyMonitorCallback.SERVER_NAME_INPUT)?.let { it } ?: "unknown"
        val latency = latencyMeasurer.measureLatency(serverIP)
        val networkType = connectivityManager.activeNetwork?.let { an ->
            connectivityManager.getNetworkCapabilities(an)?.let { capabilities ->
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
                    else -> "unknown"
                }
            }
        } ?: "unknown"

        val metadata = mapOf(
            PIXEL_PARAM_LATENCY to latency.toString(),
            PIXEL_PARAM_SERVER_NAME to serverName,
            PIXEL_PARAM_NETWORK_TYPE to networkType,
        )

        netpPixels.reportLatency(metadata)
        return Result.success()
    }

    companion object {
        internal const val PIXEL_PARAM_LATENCY = "latency"
        internal const val PIXEL_PARAM_SERVER_NAME = "server"
        internal const val PIXEL_PARAM_NETWORK_TYPE = "net_type"
    }
}
