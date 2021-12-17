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

import com.duckduckgo.mobile.android.vpn.processor.requestingapp.ConnectionInfo
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.OriginatingAppPackageIdentifier
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.OriginatingAppPackageIdentifierStrategy
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import java.net.InetAddress
import org.junit.Test
import xyz.hexene.localvpn.Packet

class OriginatingAppPackageIdentifierStrategyTest {

    private val mockResolverModern: OriginatingAppPackageIdentifier = mock()
    private val mockResolverLegacy: OriginatingAppPackageIdentifier = mock()
    private val testee =
        OriginatingAppPackageIdentifierStrategy(
            modern = mockResolverModern, legacy = mockResolverLegacy)

    @Test
    fun whenLegacyDeviceThenLegacyResolverUsed() {
        val connectionInfo = aConnectionInfo()
        testee.resolvePackageId(connectionInfo, sdkVersion = 28)
        verify(mockResolverLegacy).resolvePackageId(connectionInfo)
    }

    @Test
    fun whenModernDeviceThenModernResolverUsed() {
        val connectionInfo = aConnectionInfo()
        testee.resolvePackageId(connectionInfo, sdkVersion = 29)
        verify(mockResolverModern).resolvePackageId(connectionInfo)
    }

    private fun aConnectionInfo(): ConnectionInfo =
        ConnectionInfo(anAddress(), aPort(), anAddress(), aPort(), aProtocol())
    private fun anAddress(): InetAddress = InetAddress.getByName("192.168.0.1")
    private fun aPort(): Int = 80
    private fun aProtocol(): Int = Packet.IP4Header.TransportProtocol.TCP.number
}
