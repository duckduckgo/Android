/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.processor.requestingapp.requestingapp

import com.duckduckgo.mobile.android.vpn.processor.requestingapp.*
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.DetectOriginatingAppPackageLegacy.Companion.ipv4RegexPattern
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.DetectOriginatingAppPackageLegacy.Companion.ipv6RegexPattern
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.DetectOriginatingAppPackageLegacy.NetworkFileSearchResult
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.DetectOriginatingAppPackageLegacy.NetworkFileSearchResult.Found
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.DetectOriginatingAppPackageLegacy.NetworkFileSearchResult.NotFound
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProcNetFileConnectionMatcherTest {

    private val testee = ProcNetFileConnectionMatcher()

    @Test
    fun whenValidTcpV4FileButGivenConnectionNotInFileThenNoMatchFound() = runBlocking {
        val file = "valid_tcp4_proc_net".getFile()
        val connectionInfo = noMatchConnectionInfo()
        val result = testee.searchNetworkFile(file = file, connectionInfo = connectionInfo, pattern = ipv4RegexPattern())
        result.assertMatchNotFound()
    }

    @Test
    fun whenValidTcpV4FileAndGivenConnectionEntryFirstInFileThenMatchFound() = runBlocking {
        val file = "valid_tcp4_proc_net".getFile()
        val result = testee.searchNetworkFile(file = file, connectionInfo = matchingConnectionFirstEntry(), pattern = ipv4RegexPattern())
        result.assertMatchFound(expectedUid = 10013)
    }

    @Test
    fun whenValidTcpV4FileAndGivenConnectionEntryLastInFileThenMatchFound() = runBlocking {
        val file = "valid_tcp4_proc_net".getFile()
        val result = testee.searchNetworkFile(file = file, connectionInfo = matchingConnectionLastEntry(), pattern = ipv4RegexPattern())
        result.assertMatchFound(expectedUid = 10055)
    }

    @Test
    fun whenValidTcpV6FileButGivenConnectionNotInFileThenNoMatchFound() = runBlocking {
        val file = "valid_tcp6_proc_net".getFile()
        val connectionInfo = noMatchConnectionInfo()
        val result = testee.searchNetworkFile(file = file, connectionInfo = connectionInfo, pattern = ipv6RegexPattern())
        result.assertMatchNotFound()
    }

    @Test
    fun whenValidTcpV6FileAndGivenConnectionEntryFirstInFileThenMatchFound() = runBlocking {
        val file = "valid_tcp6_proc_net".getFile()
        val result = testee.searchNetworkFile(file = file, connectionInfo = matchingConnectionFirstEntry(), pattern = ipv6RegexPattern())
        result.assertMatchFound(expectedUid = 10073)
    }

    @Test
    fun whenValidTcpV6FileAndGivenConnectionEntryLastInFileThenMatchFound() = runBlocking {
        val file = "valid_tcp6_proc_net".getFile()
        val result = testee.searchNetworkFile(file = file, connectionInfo = matchingConnectionLastEntry(), pattern = ipv6RegexPattern())
        result.assertMatchFound(expectedUid = 10013)
    }

    @Test
    fun whenInvalidEmptyFileThenNoMatchFound() = runBlocking {
        val file = "invalid_proc_net_empty".getFile()
        val connectionInfo = noMatchConnectionInfo()
        val result = testee.searchNetworkFile(file = file, connectionInfo = connectionInfo, pattern = ipv4RegexPattern())
        result.assertMatchNotFound()
    }

    @Test
    fun whenInvalidCorruptedFileThenNoMatchFound() = runBlocking {
        val file = "invalid_proc_net_corrupted".getFile()
        val connectionInfo = noMatchConnectionInfo()
        val result = testee.searchNetworkFile(file = file, connectionInfo = connectionInfo, pattern = ipv4RegexPattern())
        result.assertMatchNotFound()
    }

    private fun NetworkFileSearchResult.assertMatchNotFound() {
        assertTrue("Expected not to find a match, but found one", this is NotFound)
    }

    private fun NetworkFileSearchResult.assertMatchFound(expectedUid: Int) {
        assertTrue("Expected a match but wasn't found", this is Found)
        assertEquals(expectedUid, (this as Found).uid)
    }

    private fun matchingConnectionFirstEntry(): ConnectionInfo {
        return aConnectionInfo(sourcePort = 45716, destinationPort = 443)
    }

    private fun matchingConnectionLastEntry(): ConnectionInfo {
        return aConnectionInfo(sourcePort = 45483, destinationPort = 80)
    }

    private fun noMatchConnectionInfo(): ConnectionInfo {
        return aConnectionInfo()
    }

    private fun String.getFile(): File {
        return File(testDir, this).also {
            assertTrue("File $this does not exist", it.exists())
        }
    }

    companion object {
        private const val testDir = "src/test/resources/proc_net"
    }
}
