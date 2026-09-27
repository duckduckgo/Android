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

import android.net.ConnectivityManager
import android.net.Network
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds

class HostnameResolverTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val connectivityManager: ConnectivityManager = mock()
    private val dnsLookup: DnsLookup = mock()
    private val network: Network = mock()
    private val hostname: String = "example.com"

    private val testee: HostnameResolver = HostnameResolverImpl(
        2.seconds,
        connectivityManager,
        coroutineRule.testDispatcherProvider,
        dnsLookup,
    )

    @Test
    fun `when hostname is syntactically invalid, then returns false and performs no lookups`() = runTest {
        listOf(
            "",
            "exa mple.com",
            "-example.com",
            "example-.com",
            "example..com",
        ).forEach { invalidHostname ->
            assertFalse("Expected resolves(\"$invalidHostname\") to be false", testee.resolves(invalidHostname))
        }
        verifyNoInteractions(connectivityManager, dnsLookup)
    }

    @Test
    fun `when hostname is an IPv4 literal, then returns false and performs no lookups`() = runTest {
        listOf(
            "192.168.0.1",
            "8.8.8.8",
            "255.255.255.255",
        ).forEach { ipv4Literal ->
            assertFalse("Expected resolves(\"$ipv4Literal\") to be false", testee.resolves(ipv4Literal))
        }
        verifyNoInteractions(connectivityManager, dnsLookup)
    }

    @Test
    fun `when hostname is an IPv6 literal, then returns false and performs no lookups`() = runTest {
        listOf(
            "::1",
            "2001:db8::1",
            "fe80::1%eth0",
            "[2001:db8::1]",
        ).forEach { ipv6Literal ->
            assertFalse("Expected resolves(\"$ipv6Literal\") to be false", testee.resolves(ipv6Literal))
        }
        verifyNoInteractions(connectivityManager, dnsLookup)
    }

    @Test
    fun `when active network is null, then returns false and performs no lookups`() = runTest {
        whenever(connectivityManager.activeNetwork).thenReturn(null)

        assertFalse(testee.resolves(hostname))
        verifyNoInteractions(dnsLookup)
    }

    @Test
    fun `when lookup is answered with at least one address, then returns true`() = runTest {
        whenever(connectivityManager.activeNetwork).thenReturn(network)
        whenever(dnsLookup.lookup(eq(network), eq(hostname)))
            .thenReturn(DnsLookup.LookupResult.Success(listOf(mock<InetAddress>())))

        assertTrue(testee.resolves(hostname))
    }

    @Test
    fun `when lookup is answered with no addresses, then returns false`() = runTest {
        whenever(connectivityManager.activeNetwork).thenReturn(network)
        whenever(dnsLookup.lookup(eq(network), eq(hostname)))
            .thenReturn(DnsLookup.LookupResult.Success(emptyList()))

        assertFalse(testee.resolves(hostname))
    }

    @Test
    fun `when lookup fails, then returns false`() = runTest {
        whenever(connectivityManager.activeNetwork).thenReturn(network)
        whenever(dnsLookup.lookup(eq(network), eq(hostname)))
            .thenReturn(DnsLookup.LookupResult.Failure(RuntimeException("Lookup failed!")))

        assertFalse(testee.resolves(hostname))
    }

    @Test
    fun `when no response arrives within the lookup timeout, then returns false`() = runTest {
        whenever(connectivityManager.activeNetwork).thenReturn(network)
        whenever(dnsLookup.lookup(eq(network), eq(hostname)))
            .doSuspendableAnswer {
                delay(3.seconds) // Wait longer than lookupTimeout (2 seconds)
                DnsLookup.LookupResult.Success(listOf(mock()))
            }

        assertFalse(testee.resolves(hostname))
    }
}
