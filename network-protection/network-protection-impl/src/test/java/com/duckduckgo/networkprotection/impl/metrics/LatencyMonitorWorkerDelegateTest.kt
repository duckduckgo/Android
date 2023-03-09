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
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class LatencyMonitorWorkerDelegateTest {

    @Mock
    private lateinit var mockNetpPixels: NetworkProtectionPixels

    @Mock
    private lateinit var mockLatencyMeasurer: LatencyMeasurer

    @Mock
    private lateinit var mockConnectivityManager: ConnectivityManager

    @Mock
    private lateinit var mockNetwork: Network

    @Mock
    private lateinit var mockCapabilities: NetworkCapabilities

    // Values for testing
    private val testLatency = 5
    private val testServerIP = "31.204.129.36"
    private val testServerName = "euw.q"

    private lateinit var testee: LatencyMonitorWorkerDelegate

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        whenever(mockLatencyMeasurer.measureLatency(any())).thenReturn(testLatency)
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(any())).thenReturn(mockCapabilities)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true)

        testee = LatencyMonitorWorkerDelegateImpl(mockNetpPixels, mockLatencyMeasurer)
    }

    @Test
    fun whenServerIPNullReturnFailure() {
        val result = testee.doDelegateWork(workDataOf(), mockConnectivityManager)
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun whenServerNameNullWifiReturnSuccess() {
        val inputData = workDataOf(
            LatencyMonitorCallback.SERVER_IP_INPUT to testServerIP,
        )

        val result = testee.doDelegateWork(inputData, mockConnectivityManager)
        assertEquals(ListenableWorker.Result.success(), result)

        verify(mockNetpPixels).reportLatency(
            eq(
                mapOf(
                    LatencyMonitorWorkerDelegateImpl.PIXEL_PARAM_LATENCY to testLatency.toString(),
                    LatencyMonitorWorkerDelegateImpl.PIXEL_PARAM_SERVER_NAME to "unknown",
                    LatencyMonitorWorkerDelegateImpl.PIXEL_PARAM_NETWORK_TYPE to "wifi",
                ),
            ),
        )
        verifyNoMoreInteractions(mockNetpPixels)
    }

    @Test
    fun whenServerNameNotNullCellReturnSuccess() {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(false)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true)

        val inputData = workDataOf(
            LatencyMonitorCallback.SERVER_IP_INPUT to testServerIP,
            LatencyMonitorCallback.SERVER_NAME_INPUT to testServerName,
        )

        val result = testee.doDelegateWork(inputData, mockConnectivityManager)
        assertEquals(ListenableWorker.Result.success(), result)

        verify(mockNetpPixels).reportLatency(
            eq(
                mapOf(
                    LatencyMonitorWorkerDelegateImpl.PIXEL_PARAM_LATENCY to testLatency.toString(),
                    LatencyMonitorWorkerDelegateImpl.PIXEL_PARAM_SERVER_NAME to testServerName,
                    LatencyMonitorWorkerDelegateImpl.PIXEL_PARAM_NETWORK_TYPE to "cell",
                ),
            ),
        )
        verifyNoMoreInteractions(mockNetpPixels)
    }

    @Test
    fun whenUnknownNetworkReturnSuccess() {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(false)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(false)

        val inputData = workDataOf(
            LatencyMonitorCallback.SERVER_IP_INPUT to testServerIP,
            LatencyMonitorCallback.SERVER_NAME_INPUT to testServerName,
        )

        val result = testee.doDelegateWork(inputData, mockConnectivityManager)
        assertEquals(ListenableWorker.Result.success(), result)

        verify(mockNetpPixels).reportLatency(
            eq(
                mapOf(
                    LatencyMonitorWorkerDelegateImpl.PIXEL_PARAM_LATENCY to testLatency.toString(),
                    LatencyMonitorWorkerDelegateImpl.PIXEL_PARAM_SERVER_NAME to testServerName,
                    LatencyMonitorWorkerDelegateImpl.PIXEL_PARAM_NETWORK_TYPE to "unknown",
                ),
            ),
        )
        verifyNoMoreInteractions(mockNetpPixels)
    }
}
