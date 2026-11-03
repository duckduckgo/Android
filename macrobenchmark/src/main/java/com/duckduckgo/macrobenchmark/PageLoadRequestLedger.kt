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

package com.duckduckgo.macrobenchmark

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "PageLoadRequests"

/**
 * Counts requests per host and logs them for pageload_benchmark.py, which checks hermeticity from the report.
 */
internal class RequestLedger(
    private val scenario: String,
    private val trackerHosts: Set<String>,
) {
    private val hosts = ConcurrentHashMap<String, Int>()
    private val connects = ConcurrentHashMap<String, Int>()
    private val unknown = ConcurrentHashMap<String, Int>()

    fun reset() {
        hosts.clear()
        connects.clear()
        unknown.clear()
    }

    fun recordServed(host: String) = hosts.merge(host, 1, Int::plus)
    fun recordConnect(authority: String) = connects.merge(authority, 1, Int::plus)
    fun recordUnknown(host: String) = unknown.merge(host, 1, Int::plus)

    fun logReport() {
        val prefix = "scenario=$scenario"
        Log.i(TAG, "$prefix begin")
        hosts.toSortedMap().forEach { (host, count) -> Log.i(TAG, "$prefix host name=$host tracker=${host in trackerHosts} count=$count") }
        connects.toSortedMap().forEach { (target, count) -> Log.i(TAG, "$prefix connect target=$target count=$count") }
        unknown.toSortedMap().forEach { (host, count) -> Log.i(TAG, "$prefix unknown host=$host count=$count") }
        Log.i(TAG, "$prefix end")
    }
}
