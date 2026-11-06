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
import android.net.DnsResolver.DnsException
import android.net.Network
import android.os.CancellationSignal
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.suggestredirect.DnsLookup.LookupResult
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.net.InetAddress
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Robolectric is required in this test to provide a functioning [android.os.CancellationSignal]
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DnsLookupApi29ImplTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val network: Network = mock()
    private val dnsResolver: DnsResolver = mock()
    private val hostname: String = "example.com"

    private val testee: DnsLookup = DnsLookupApi29Impl(
        coroutineRule.testDispatcherProvider,
        dnsResolver,
    )

    @Test
    fun `when lookup is answered with a zero RCode, then returns success`() = runTest {
        val resolvedAddresses = listOf(mock<InetAddress>())
        stubLookupCall(network, hostname) { callback ->
            callback.onAnswer(resolvedAddresses, RCODE_NOERROR)
        }

        val result = testee.lookup(network, hostname)

        assertIs<LookupResult.Success>(result)
        assertEquals(resolvedAddresses, result.addresses)
    }

    @Test
    fun `when lookup is answered with a non-zero RCode, then returns failure`() = runTest {
        stubLookupCall(network, hostname) { callback ->
            callback.onAnswer(emptyList(), RCODE_NXDOMAIN)
        }

        val result = testee.lookup(network, hostname)

        assertIs<LookupResult.Failure>(result)
    }

    @Test
    fun `when lookup fails with DnsException, then returns failure`() = runTest {
        val error = DnsException(1, null)
        stubLookupCall(network, hostname) { callback ->
            callback.onError(error)
        }
        val result = testee.lookup(network, hostname)

        assertIs<LookupResult.Failure>(result)
        assertEquals(error, result.cause)
    }

    @Test
    fun `when error arrives after answer, then it is ignored`() = runTest {
        val resolvedAddresses = listOf(mock<InetAddress>())
        stubLookupCall(network, hostname) { callback ->
            callback.onAnswer(resolvedAddresses, RCODE_NOERROR)
            callback.onError(DnsException(1, null))
        }

        val result = testee.lookup(network, hostname)

        assertIs<LookupResult.Success>(result)
        assertEquals(resolvedAddresses, result.addresses)
    }

    @Test
    fun `when answer arrives after error, then it is ignored`() = runTest {
        val error = DnsException(1, null)
        stubLookupCall(network, hostname) { callback ->
            callback.onError(error)
            callback.onAnswer(listOf(mock<InetAddress>()), RCODE_NOERROR)
        }

        val result = testee.lookup(network, hostname)

        assertIs<LookupResult.Failure>(result)
        assertEquals(error, result.cause)
    }

    @Test
    fun `when canceled, then the underlying query is canceled`() = runTest {
        val job = launch { testee.lookup(network, hostname) }
        yield()

        val cancellationSignalCaptor = argumentCaptor<CancellationSignal>()
        verify(dnsResolver).query(
            eq(network),
            eq(hostname),
            any(),
            any(),
            cancellationSignalCaptor.capture(),
            any(),
        )
        assertFalse(cancellationSignalCaptor.firstValue.isCanceled)
        job.cancelAndJoin()
        assertTrue(cancellationSignalCaptor.firstValue.isCanceled)
    }

    private fun stubLookupCall(
        network: Network,
        @Suppress("SameParameterValue") hostname: String,
        block: (DnsResolver.Callback<List<InetAddress>>) -> Unit,
    ) {
        whenever(
            dnsResolver.query(
                eq(network),
                eq(hostname),
                any(),
                any(),
                any(),
                any(),
            ),
        )
            .thenAnswer { invocation ->
                val callback = invocation.getArgument<DnsResolver.Callback<List<InetAddress>>>(5)
                block.invoke(callback)
            }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <reified T> assertIs(value: Any?) {
        contract {
            returns() implies (value is T)
        }
        assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
    }

    companion object {
        private const val RCODE_NOERROR = 0 // Success
        private const val RCODE_NXDOMAIN = 3 // Non-existent domain
    }
}
