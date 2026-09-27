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

import android.net.Network
import com.duckduckgo.app.browser.suggestredirect.DnsLookup.LookupResult
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class DnsLookupPreApi29ImplTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val network: Network = mock()
    private val hostname: String = "example.com"

    private val testee: DnsLookup = DnsLookupPreApi29Impl(coroutineRule.testDispatcherProvider)

    @Test
    fun `when lookup succeeds, then returns success`() = runTest {
        val resolvedAddresses = arrayOf(mock<InetAddress>())
        whenever(network.getAllByName(hostname))
            .thenReturn(resolvedAddresses)

        val result = testee.lookup(network, hostname)

        assertIs<LookupResult.Success>(result)
        assertEquals(resolvedAddresses.toList(), result.addresses)
    }

    @Test
    fun `when lookup fails, then returns failure`() = runTest {
        whenever(network.getAllByName(hostname))
            .thenThrow(UnknownHostException())

        val result = testee.lookup(network, hostname)

        assertIs<LookupResult.Failure>(result)
        assertIs<UnknownHostException>(result.cause)
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <reified T> assertIs(value: Any?) {
        contract {
            returns() implies (value is T)
        }
        assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
    }
}
