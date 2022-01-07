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

package com.duckduckgo.mobile.android.vpn.processor.requestingapp

import android.content.pm.PackageManager
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.DetectOriginatingAppPackageLegacy.NetworkFileSearchResult.Found
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.DetectOriginatingAppPackageLegacy.NetworkFileSearchResult.NotFound
import kotlinx.coroutines.*
import timber.log.Timber
import xyz.hexene.localvpn.Packet.IP4Header.TransportProtocol.TCP
import xyz.hexene.localvpn.Packet.IP4Header.TransportProtocol.UDP
import java.io.File
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * Parses networking level proc/net files to match connection details to an open connection
 *
 * This implementation should not be used on Android >= 29 as access to these files are restricted on them.
 * On newer APIs, a different implementation is used {@code DetectOriginatingAppPackageModern.kt}
 *
 * Reads the tcp, udp, tcp6, udp6 files living in /proc/net directory
 * Reference on file structure: https://metacpan.org/pod/Linux::Proc::Net::TCP
 */

class DetectOriginatingAppPackageLegacy(
    private val packageManager: PackageManager,
    private val procNetFileConnectionMatcher: NetworkFileConnectionMatcher,
    private val coroutineScope: CoroutineScope
) : OriginatingAppPackageIdentifier {

    // using 2 threads here to match that there are two files we might want to search in parallel
    private val fileSearchDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    private val tcpSearchOrder = listOf(
        SearchFile(fileNameTcpV6, ipv6RegexPattern()),
        SearchFile(fileNameTcpV4, ipv4RegexPattern()),
    )

    private val udpSearchOrder = listOf(
        SearchFile(fileNameUdpV6, ipv6RegexPattern()),
        SearchFile(fileNameUdpV4, ipv4RegexPattern()),
    )

    override fun resolvePackageId(connectionInfo: ConnectionInfo): String {
        Timber.v("Looking for a matching connection for $connectionInfo")

        val filesToSearch = determineFilesToSearch(connectionInfo.protocolNumber)

        return when (val searchResult = filesToSearch.find(connectionInfo)) {
            is Found -> packageFromUid(searchResult.uid)
            NotFound -> {
                Timber.w("Failed to find matching app for $connectionInfo")
                packageFromUid(null)
            }
        }
    }

    /**
     * Searches through each of the network files, in parallel, looking for a line matching the given connection info
     *
     * When a match is found, it will cancel all other running searches. e.g., if it finds a match in TCPv6, it'll cancel search in TCPv4 file
     */
    private fun List<SearchFile>.find(connectionInfo: ConnectionInfo): NetworkFileSearchResult {
        val jobs = mutableListOf<Job>()
        var searchResult: NetworkFileSearchResult = NotFound

        runBlocking {
            forEach {
                coroutineScope.launch(fileSearchDispatcher) {
                    val result = procNetFileConnectionMatcher.searchNetworkFile(File(it.filename), it.pattern, connectionInfo)
                    if (result is Found) {
                        searchResult = result
                        jobs.forEach { it.cancel() }
                    }
                    Timber.v("Searched ${it.filename}. Connection was ${if (result !is Found) "not " else ""}found")
                }.also { jobs.add(it) }
            }

            jobs.map { it.join() }
        }

        return searchResult
    }

    private fun determineFilesToSearch(protocolNumber: Int): List<SearchFile> {
        return when (protocolNumber) {
            TCP.number -> tcpSearchOrder
            UDP.number -> udpSearchOrder
            else -> emptyList()
        }
    }

    private fun packageFromUid(uid: Int?): String {
        if (uid == null) {
            return OriginatingAppPackageIdentifierStrategy.UNKNOWN
        }

        val matchingPackages = packageManager.getPackagesForUid(uid)
        if (matchingPackages.isNullOrEmpty()) {
            Timber.w("No packages returned matching uid=$uid")
            return OriginatingAppPackageIdentifierStrategy.UNKNOWN
        }

        Timber.d("Matching packages for uid=$uid: ${matchingPackages.joinToString(separator = ", ")}")
        return matchingPackages.first()
    }

    sealed class NetworkFileSearchResult {
        object NotFound : NetworkFileSearchResult() {
            override fun toString(): String = "NotFound"
        }

        data class Found(val uid: Int) : NetworkFileSearchResult() {
            override fun toString(): String = "Found: $uid"
        }
    }

    private data class SearchFile(
        val filename: String,
        val pattern: Pattern
    )

    companion object {
        private const val fileNameTcpV4 = "proc/net/tcp"
        private const val fileNameTcpV6 = "proc/net/tcp6"
        private const val fileNameUdpV4 = "proc/net/udp"
        private const val fileNameUdpV6 = "proc/net/udp6"

        private const val regexFlags = Pattern.CASE_INSENSITIVE or Pattern.UNIX_LINES or Pattern.DOTALL
        private fun regex(addressLength: Int): String =
            "\\s+\\d+:\\s([0-9A-F]{$addressLength}):([0-9A-F]{4})\\s([0-9A-F]{$addressLength}):([0-9A-F]{4})\\s([0-9A-F]{2})\\s[0-9A-F]{8}:[0-9A-F]{8}\\s[0-9A-F]{2}:[0-9A-F]{8}\\s[0-9A-F]{8}\\s+([0-9A-F]+)"

        fun ipv4RegexPattern(): Pattern = Pattern.compile(regex(8), regexFlags)
        fun ipv6RegexPattern(): Pattern = Pattern.compile(regex(32), regexFlags)
    }
}
