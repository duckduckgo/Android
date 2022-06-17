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

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import java.net.InetAddress
import java.nio.ByteBuffer

class RealPacketInterceptorChainTest {

    private val testRequest = PacketRequest(
        PacketInfo(4, 0, InetAddress.getByName("1.1.1.1"), 1),
        ByteBuffer.allocate(0),
        mock()
    )

    @Test(expected = IllegalStateException::class)
    fun whenEmptyInterceptorsThenThrowIllegalStateException() {
        RealPacketInterceptorChain(listOf(), 0, testRequest).proceed(testRequest)
    }

    @Test
    fun whenOneInterceptorThenCallThroughIt() {
        val result = RealPacketInterceptorChain(
            listOf(
                VpnPacketInterceptor { return@VpnPacketInterceptor 1 }
            ),
            0,
            testRequest
        ).proceed(testRequest)

        assertEquals(1, result)
    }

    @Test
    fun whenInterceptorListThenCallThroughAllOfThem() {
        val result = RealPacketInterceptorChain(
            listOf(
                VpnPacketInterceptor { chain -> return@VpnPacketInterceptor chain.proceed(chain.request()) },
                VpnPacketInterceptor { chain -> return@VpnPacketInterceptor chain.proceed(chain.request()) },
                VpnPacketInterceptor { chain -> return@VpnPacketInterceptor chain.proceed(chain.request()) },
                VpnPacketInterceptor { return@VpnPacketInterceptor 1 },
            ),
            0,
            testRequest
        ).proceed(testRequest)

        assertEquals(1, result)

    }

    @Test
    fun whenInterceptorShortcircuitsChainThenReturnItsValue() {
        val result = RealPacketInterceptorChain(
            listOf(
                VpnPacketInterceptor { chain -> return@VpnPacketInterceptor chain.proceed(chain.request()) },
                VpnPacketInterceptor { return@VpnPacketInterceptor 2 },
                VpnPacketInterceptor { chain -> return@VpnPacketInterceptor chain.proceed(chain.request()) },
                VpnPacketInterceptor { return@VpnPacketInterceptor 1 },
            ),
            0,
            testRequest
        ).proceed(testRequest)

        assertEquals(2, result)

    }
}
