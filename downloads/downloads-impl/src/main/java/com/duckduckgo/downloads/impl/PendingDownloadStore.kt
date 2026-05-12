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

package com.duckduckgo.downloads.impl

import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import java.util.UUID

/**
 * In-memory store for [PendingFileDownload] objects that may be too large to pass
 * through a Bundle (e.g., data URIs containing base64-encoded file contents).
 *
 * Instead of serializing the full object into fragment arguments (which risks
 * TransactionTooLargeException), callers store the download here and pass only
 * the returned key through the Bundle.
 *
 * Callers are responsible for calling [remove] when the entry is no longer needed.
 * The store is capped at [MAX_ENTRIES]; the oldest entry is evicted when full.
 */
object PendingDownloadStore {

    private const val MAX_ENTRIES = 5

    private val lock = Any()
    private val store = object : LinkedHashMap<String, PendingFileDownload>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PendingFileDownload>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun put(download: PendingFileDownload): String {
        val key = UUID.randomUUID().toString()
        synchronized(lock) { store[key] = download }
        return key
    }

    fun get(key: String): PendingFileDownload? = synchronized(lock) { store[key] }

    fun remove(key: String) {
        synchronized(lock) { store.remove(key) }
    }
}
