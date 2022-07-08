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

package com.duckduckgo.mobile.android.vpn.network.channels

import com.duckduckgo.di.scopes.VpnScope
import com.squareup.anvil.annotations.ContributesBinding
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import javax.inject.Inject

interface NetworkChannelCreator {

    /**
     * Creates a datagram socket channel and connects to the provided [InetSocketAddress].
     *
     * @return the already connected [DatagramChannel]
     * @throws IOException when connect fails. In that case the channel is also automatically close so no action is required
     * in the calling side
     */
    @Throws(IOException::class)
    fun createDatagramChannelAndConnect(inetSocketAddress: InetSocketAddress): DatagramChannel

    /**
     * Creates a socket channel and connects to the provided [InetSocketAddress].
     *
     * @return the already connected [SocketChannel]
     * @throws IOException when connect fails. In that case the channel is also automatically close so no action is required
     * in the calling side
     */
    @Throws(IOException::class)
    fun createSocketChannelAndConnect(inetSocketAddress: InetSocketAddress): SocketChannel
}

@ContributesBinding(VpnScope::class)
class NetworkChannelCreatorImpl @Inject constructor() : NetworkChannelCreator {

    @Throws(IOException::class)
    override fun createDatagramChannelAndConnect(inetSocketAddress: InetSocketAddress): DatagramChannel {
        return DatagramChannel.open().also { channel ->
            channel.configureBlocking(false)
            channel.socket().run {
                broadcast = true
            }
            try {
                channel.connect(inetSocketAddress)
            } catch (e: IOException) {
                channel.close()
                // re-throw
                throw e
            }
        }
    }

    @Throws(IOException::class)
    override fun createSocketChannelAndConnect(inetSocketAddress: InetSocketAddress): SocketChannel {
        return SocketChannel.open().also { channel ->
            channel.configureBlocking(false)
            try {
                channel.connect(inetSocketAddress)
            } catch (e: IOException) {
                channel.close()
                throw e
            }
        }
    }
}
