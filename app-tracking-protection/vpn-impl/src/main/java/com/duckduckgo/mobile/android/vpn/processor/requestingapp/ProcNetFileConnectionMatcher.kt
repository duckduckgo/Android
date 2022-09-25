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

import com.duckduckgo.mobile.android.vpn.processor.requestingapp.DetectOriginatingAppPackageLegacy.NetworkFileSearchResult
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.DetectOriginatingAppPackageLegacy.NetworkFileSearchResult.Found
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.DetectOriginatingAppPackageLegacy.NetworkFileSearchResult.NotFound
import kotlinx.coroutines.ensureActive
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext

interface NetworkFileConnectionMatcher {
    suspend fun searchNetworkFile(
        file: File,
        pattern: Pattern,
        connectionInfo: ConnectionInfo
    ): NetworkFileSearchResult

    fun matchesConnection(
        connectionInfo: ConnectionInfo,
        localPort: Int
    ): Boolean {
        return connectionInfo.sourcePort == localPort
    }
}

class ProcNetFileConnectionMatcher : NetworkFileConnectionMatcher {

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun searchNetworkFile(
        file: File,
        pattern: Pattern,
        connectionInfo: ConnectionInfo
    ): NetworkFileSearchResult {
        BufferedReader(FileReader(file)).use { reader ->
            return searchLineByLine(reader, pattern, connectionInfo, file)
        }
    }

    private suspend fun searchLineByLine(
        reader: BufferedReader,
        pattern: Pattern,
        connectionInfo: ConnectionInfo,
        file: File
    ): NetworkFileSearchResult {
        var matcher: Matcher

        reader.lineSequence().forEach { line ->
            coroutineContext.ensureActive()

            matcher = pattern.matcher(line)
            if (matcher.find()) {
                val localPort = matcher.group(2)?.toIntOrNull(16) ?: 0
                val uid = matcher.group(6)?.toIntOrNull() ?: 0

                if (matchesConnection(connectionInfo, localPort)) {
                    return Found(uid)
                }
            }
        }
        Timber.v("No match found in ${file.absolutePath}")
        return NotFound
    }
}
