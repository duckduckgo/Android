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

import com.duckduckgo.di.scopes.VpnScope
import dev.zacsweers.metro.SingleIn
import okhttp3.Dns
import okhttp3.Interceptor
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Makes NetP's own network calls, to the controller and the controller host lookups, fail fast while a
 * [withNetworkBlocked] block runs.
 *
 * An in-place restart prepares NetP while the running tunnel, which routes this app, is still up, and a call that goes
 * into a dead tunnel hangs for seconds. The block is process-wide rather than tied to the calling coroutine because the
 * calls run on OkHttp and IO threads. The blocked prepare is short, but with endpoint port fallback on it includes port
 * probing and can take about 1.5 seconds. Other NetP controller callers, such as failure recovery or rekey, that race
 * the prepare fail fast with an [IOException] they already handle.
 */
@SingleIn(VpnScope::class)
class NetPNetworkGate @Inject constructor() {
    private val blockingScopes = AtomicInteger(0)

    val interceptor = Interceptor { chain ->
        checkNetworkAllowed()
        chain.proceed(chain.request())
    }

    suspend fun <T> withNetworkBlocked(block: suspend () -> T): T {
        blockingScopes.incrementAndGet()
        try {
            return block()
        } finally {
            blockingScopes.decrementAndGet()
        }
    }

    /**
     * The check sits outside [dns] so that a blocked lookup throws instead of reaching a fallback inside [dns] that
     * would answer with hardcoded addresses.
     */
    fun gate(dns: Dns): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            checkNetworkAllowed()
            return dns.lookup(hostname)
        }
    }

    // OkHttp hands only IOExceptions thrown by an interceptor to the caller; any other exception crashes its dispatcher thread
    private fun checkNetworkAllowed() {
        if (blockingScopes.get() > 0) throw NetworkBlockedException()
    }
}

class NetworkBlockedException : IOException("NetP network calls are blocked")

fun Throwable.isCausedByBlockedNetwork(): Boolean = generateSequence(this) { it.cause }.any { it is NetworkBlockedException }
