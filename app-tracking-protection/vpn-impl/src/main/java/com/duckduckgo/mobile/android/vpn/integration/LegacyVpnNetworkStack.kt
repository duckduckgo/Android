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

package com.duckduckgo.mobile.android.vpn.integration

import android.os.ParcelFileDescriptor
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import com.duckduckgo.mobile.android.vpn.processor.TunPacketReader
import com.duckduckgo.mobile.android.vpn.processor.TunPacketWriter
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor
import com.duckduckgo.mobile.android.vpn.processor.udp.UdpPacketProcessor
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// TODO we'll hopefully remove ths class once the VPN new networking stack is validated
private class LegacyVpnNetworkStack constructor(
    private val udpPacketProcessorFactory: UdpPacketProcessor.Factory,
    private val tcpPacketProcessorFactory: TcpPacketProcessor.Factory,
    private val tunPacketReaderFactory: TunPacketReader.Factory,
    private val tunPacketWriterFactory: TunPacketWriter.Factory,
    private val queues: VpnQueues,
    private val coroutineScope: CoroutineScope,
) : VpnNetworkStack {
    private lateinit var udpPacketProcessor: UdpPacketProcessor
    private lateinit var tcpPacketProcessor: TcpPacketProcessor
    private var executorService: ExecutorService? = null

    override val name: String = "legacy"

    override fun onCreateVpn(): Result<Unit> {
        udpPacketProcessor = udpPacketProcessorFactory.build()
        tcpPacketProcessor = tcpPacketProcessorFactory.build(coroutineScope)

        return Result.success(Unit)
    }

    override fun onStartVpn(tunfd: ParcelFileDescriptor): Result<Unit> {
        queues.clearAll()

        executorService?.shutdownNow()
        val processors = listOf(
            tcpPacketProcessor,
            udpPacketProcessor,
            tunPacketReaderFactory.create(tunfd),
            tunPacketWriterFactory.create(tunfd)
        )
        executorService = Executors.newFixedThreadPool(processors.size).also { executorService ->
            processors.forEach { executorService.submit(it) }
        }

        return Result.success(Unit)
    }

    override fun onStopVpn(): Result<Unit> {
        queues.clearAll()
        executorService?.shutdownNow()
        udpPacketProcessor.stop()
        tcpPacketProcessor.stop()

        return Result.success(Unit)
    }

    override fun onDestroyVpn(): Result<Unit> {
        // noop
        return Result.success(Unit)
    }

    override fun mtu(): Int = 16_384
}
