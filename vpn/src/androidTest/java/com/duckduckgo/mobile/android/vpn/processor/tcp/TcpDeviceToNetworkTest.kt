/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.processor.tcp

import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.TCB
import java.net.InetAddress
import java.nio.channels.Selector
import kotlin.experimental.and


class TcpDeviceToNetworkTest {

    private lateinit var testee: TcpDeviceToNetwork
    private val queues = VpnQueues()
    private val selector: Selector = mock()
    private val socketWriter: SocketWriter = mock()
    private val connectionInitializer: ConnectionInitializer = mock()

    @Before
    fun setup() {
        TCB.closeAll()
        testee = TcpDeviceToNetwork(queues, selector, socketWriter, connectionInitializer)
    }

    @Test
    fun whenNoDeviceToNetworkPacketsThenNoInteractionsHappen() {
        testee.deviceToNetworkProcessing()
        verify(connectionInitializer, never()).initializeConnection(any())
        assertTrue(queues.networkToDevice.isEmpty())
    }

    @Test
    fun whenSynPacketAndNoOpenConnectionThenConnectionInitialized() {
        queues.tcpDeviceToNetwork.offer(mockPacket())
        testee.deviceToNetworkProcessing()
        verify(connectionInitializer).initializeConnection(any())
    }

    private fun mockPacket() = mock<Packet>()

    private fun tcpPacket(sourceAddress: String, destinationAddress: String, isSyn: Boolean, isAck: Boolean, isFin: Boolean, isReset: Boolean) {
        val optionLen = if (isSyn) 8 else 0
        val dataLength = 0
        val totalLength = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE + optionLen + dataLength
        val packet = ByteArray(totalLength)
        packet[0] = 0b01000101 // version (4),  header length (5) which is scaled by 4 to mean 20
        packet[1] // unused byte
        packet[2] = totalLength.shr(8).toByte()
        packet[3] = totalLength.toByte()

        // IP Identification bytes
        packet[5] = 0
        packet[6] = 0

        packet[7] // flags and fragment offset - not needed
        packet[8] = 20.toByte() // TTL
        packet[9] = 6 // Protocol - 6 means TCP

        packet[10] // header checksum - come back to this
        packet[11] // header checksum - come back to this

        splitIpAddressString(sourceAddress).also {
            packet[12] = it[0]
            packet[13] = it[1]
            packet[14] = it[2]
            packet[15] = it[3]
        }

        splitIpAddressString(destinationAddress).also {
            packet[16] = it[0]
            packet[17] = it[1]
            packet[18] = it[2]
            packet[19] = it[3]
        }
    }

    private fun splitIpAddressString(ipAddressString: String): ByteArray {
        val address = InetAddress.getByName(ipAddressString).address ?: throw IllegalArgumentException("Invalid address$ipAddressString")
        val addressByteArray = ByteArray(4)
        for (i in 0..address.size) {
            addressByteArray[i] = address[i].and(0xFF.toByte())
        }
        return addressByteArray
    }
}