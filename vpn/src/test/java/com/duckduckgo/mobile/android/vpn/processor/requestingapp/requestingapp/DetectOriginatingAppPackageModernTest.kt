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

package com.duckduckgo.mobile.android.vpn.processor.requestingapp.requestingapp

import android.content.pm.PackageManager
import android.net.ConnectivityManager
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.DetectOriginatingAppPackageModern
import org.mockito.kotlin.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress

class DetectOriginatingAppPackageModernTest {

    private val connectivityManager: ConnectivityManager = mock()
    private val packageManager: PackageManager = mock()
    private val addressCaptor = argumentCaptor<InetSocketAddress>()
    private val testee: DetectOriginatingAppPackageModern = DetectOriginatingAppPackageModern(connectivityManager, packageManager)

    @Test
    fun whenResolvingPackageThenCorrectProtocolPassedToConnectivityManager() {
        val expectedProtocol = 6
        val connection = aConnectionInfo(protocol = expectedProtocol)

        testee.resolvePackageId(connection)

        verify(connectivityManager).getConnectionOwnerUid(eq(expectedProtocol), any(), any())
    }

    @Test
    fun whenResolvingPackageThenCorrectDestinationAddressPassedToConnectivityManager() {
        val expectedAddress = anExternalIpAddress()
        val connection = aConnectionInfo(destinationAddress = InetAddress.getByName(expectedAddress))

        testee.resolvePackageId(connection)
        captureDestinationAddress()

        assertEquals(expectedAddress, addressCaptor.firstValue.hostName)
    }

    @Test
    fun whenResolvingPackageThenCorrectSourceAddressPassedToConnectivityManager() {
        val expectedAddress = anInternalIpAddress()
        val connection = aConnectionInfo(destinationAddress = InetAddress.getByName(expectedAddress))

        testee.resolvePackageId(connection)
        captureSourceAddress()

        assertEquals(expectedAddress, addressCaptor.firstValue.hostName)
    }

    @Test
    fun whenResolvingPackageThenCorrectDestinationPortPassedToConnectivityManager() {
        val connection = aConnectionInfo(destinationPort = 8080)

        testee.resolvePackageId(connection)
        captureDestinationAddress()

        assertEquals(8080, addressCaptor.firstValue.port)
    }

    @Test
    fun whenResolvingPackageThenCorrectSourcePortPassedToConnectivityManager() {
        val connection = aConnectionInfo(sourcePort = 40123)

        testee.resolvePackageId(connection)
        captureSourceAddress()

        assertEquals(40123, addressCaptor.firstValue.port)
    }

    @Test
    fun whenGetConnectionOwnerUidThrowsThenReturnUnknown() {
        val connection = aConnectionInfo(sourcePort = 40123)

        whenever(connectivityManager.getConnectionOwnerUid(any(), any(), any())).thenThrow(SecurityException())

        val packateId = testee.resolvePackageId(connection)
        assertEquals("unknown", packateId)
        verify(packageManager).getPackagesForUid(-1)
    }

    private fun captureDestinationAddress() {
        verify(connectivityManager).getConnectionOwnerUid(any(), any(), addressCaptor.capture())
    }

    private fun captureSourceAddress() {
        verify(connectivityManager).getConnectionOwnerUid(any(), addressCaptor.capture(), any())
    }
}
