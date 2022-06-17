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

package com.duckduckgo.mobile.android.vpn.processor

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel

/**
 * To provide a VPN packet [VpnPacketInterceptor] just implement the type and return the binding
 * Example:
 * ```kotlin
 * @ContributesMultibinding(...)
 * class MyPacketInterceptor @Inject constructor(...): VpnPacketInterceptor {
 * }
 * ```
 */
fun interface VpnPacketInterceptor {
    /**
     * This method is called just before AppTP is about to send a packet.
     *
     * Interceptors are called in the same order they're added using the [Registar.addInterceptor] method
     *
     * When an interceptor wants to proceed with the interceptor chain it should call [SocketChain.proceed] and return its value
     * Example:
     * ```kotlin
     * val interceptor = VpnPacketInterceptor { chain: VpnPacketInterceptor.SocketChain ->
     *     chain.proceed(chain.request())
     * }
     * ```
     *
     * Interceptors can modify and potentially short-circuit packets going out.
     * Example:
     * ```kotlin
     * val interceptor = VpnPacketInterceptor { chain: VpnPacketInterceptor.SocketChain ->
     *     -1
     * }
     * ```
     *
     * @return [VpnPacketInterceptor] shall return the number of bytes sent
     */
    fun intercept(chain: SocketChain): Int

    companion object {
        inline operator fun invoke(crossinline block: (chain: SocketChain) -> Int): VpnPacketInterceptor =
            VpnPacketInterceptor { block(it) }
    }

    interface SocketChain {
        fun proceed(packetRequest: PacketRequest): Int

        fun request(): PacketRequest
    }
}

data class PacketRequest(
    val packetInfo: PacketInfo,
    val byteBuffer: ByteBuffer,
    val byteChannel: ByteChannel,
)

data class PacketInfo(
    val IPVersion: Int,
    val transportProtocol: Int,
    val destinationAddress: InetAddress,
    val destinationPort: Int,
)
