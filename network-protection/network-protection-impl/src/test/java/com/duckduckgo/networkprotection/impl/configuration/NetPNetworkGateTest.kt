/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.configuration

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress

class NetPNetworkGateTest {

    private val gate = NetPNetworkGate()

    private val lookups = mutableListOf<String>()
    private val gatedDns = gate.gate(
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                lookups.add(hostname)
                return listOf(InetAddress.getByName("10.0.0.1"))
            }
        },
    )

    private val requests = mutableListOf<String>()
    private val client = OkHttpClient.Builder()
        .addInterceptor(gate.interceptor)
        .addInterceptor(
            Interceptor { chain ->
                requests.add(chain.request().url.toString())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            },
        )
        .build()

    @Test
    fun whenNetworkIsNotBlockedThenLookupsAndCallsGoThrough() {
        gatedDns.lookup("controller.netp.duckduckgo.com")
        call().close()

        assertEquals(listOf("controller.netp.duckduckgo.com"), lookups)
        assertEquals(1, requests.size)
    }

    @Test
    fun whenNetworkIsBlockedThenLookupsAndCallsFailWithIOException() = runTest {
        gate.withNetworkBlocked {
            assertTrue(runCatching { gatedDns.lookup("controller.netp.duckduckgo.com") }.exceptionOrNull() is NetworkBlockedException)
            assertTrue(runCatching { call() }.exceptionOrNull() is NetworkBlockedException)
        }

        assertTrue(lookups.isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun whenBlockedScopeEndsThenNetworkIsAllowedAgain() = runTest {
        gate.withNetworkBlocked { }

        gatedDns.lookup("controller.netp.duckduckgo.com")

        assertEquals(1, lookups.size)
    }

    @Test
    fun whenBlockedScopeThrowsThenNetworkIsAllowedAgain() = runTest {
        runCatching { gate.withNetworkBlocked { throw IllegalStateException() } }

        gatedDns.lookup("controller.netp.duckduckgo.com")

        assertEquals(1, lookups.size)
    }

    @Test
    fun whenOneOfTwoOverlappingBlockedScopesEndsThenNetworkIsStillBlocked() = runTest {
        val secondScopeMayEnd = CompletableDeferred<Unit>()
        val secondScopeStarted = CompletableDeferred<Unit>()
        val secondScope = async {
            gate.withNetworkBlocked {
                secondScopeStarted.complete(Unit)
                secondScopeMayEnd.await()
            }
        }
        secondScopeStarted.await()

        gate.withNetworkBlocked { }
        val blockedWhileSecondScopeRuns = runCatching { gatedDns.lookup("controller.netp.duckduckgo.com") }.isFailure
        secondScopeMayEnd.complete(Unit)
        secondScope.await()

        assertTrue(blockedWhileSecondScopeRuns)
        assertFalse(runCatching { gatedDns.lookup("controller.netp.duckduckgo.com") }.isFailure)
    }

    @Test
    fun whenBlockedFailureIsWrappedThenItIsStillRecognised() {
        assertTrue(RuntimeException(IOException(NetworkBlockedException())).isCausedByBlockedNetwork())
        assertFalse(RuntimeException(IOException()).isCausedByBlockedNetwork())
    }

    private fun call(): Response = client.newCall(Request.Builder().url("https://controller.netp.duckduckgo.com/locations").build()).execute()
}
