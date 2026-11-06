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

package com.duckduckgo.app.browser.suggestredirect

import android.net.DnsResolver
import android.net.DnsResolver.Callback
import android.net.DnsResolver.DnsException
import android.net.Network
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import com.duckduckgo.app.browser.suggestredirect.DnsLookup.LookupResult
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.coroutines.resume

interface DnsLookup {
    /**
     * @return a [LookupResult.Success] containing the resolved IP addresses for [hostname]
     * when the lookup succeeds, or [LookupResult.Failure] with its cause when it fails.
     */
    suspend fun lookup(network: Network, hostname: String): LookupResult

    sealed class LookupResult {
        data class Success(val addresses: List<InetAddress>) : LookupResult()
        data class Failure(val cause: Throwable) : LookupResult()
    }
}

@RequiresApi(29)
class DnsLookupApi29Impl(
    private val dispatcherProvider: DispatcherProvider,
    private val dnsResolver: DnsResolver,
) : DnsLookup {
    override suspend fun lookup(network: Network, hostname: String): LookupResult = suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }
        val dnsResolverCallback = object : Callback<List<InetAddress>> {
            override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                if (!continuation.isActive) {
                    return
                }
                continuation.resume(
                    if (rcode == 0) {
                        LookupResult.Success(answer)
                    } else {
                        LookupResult.Failure(RuntimeException("RCode was $rcode"))
                    },
                )
            }

            override fun onError(error: DnsException) {
                if (!continuation.isActive) {
                    return
                }
                continuation.resume(LookupResult.Failure(error))
            }
        }
        dnsResolver.query(
            network,
            hostname,
            DnsResolver.FLAG_NO_RETRY,
            dispatcherProvider.io().asExecutor(),
            cancellationSignal,
            dnsResolverCallback,
        )
    }
}

class DnsLookupPreApi29Impl(private val dispatcherProvider: DispatcherProvider) : DnsLookup {
    override suspend fun lookup(network: Network, hostname: String): LookupResult {
        return try {
            val addresses = withContext(dispatcherProvider.io()) {
                runInterruptible { network.getAllByName(hostname) }
            }
            LookupResult.Success(addresses.toList())
        } catch (throwable: UnknownHostException) {
            LookupResult.Failure(throwable)
        }
    }
}
