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

package com.duckduckgo.app.autocomplete.api

import com.duckduckgo.app.autocomplete.AutocompleteSpeedFeature
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import java.util.Locale
import javax.inject.Inject

/**
 * Recent suggest responses, kept in memory only. The same query is often requested again within moments (after a search
 * the omnibar shows it again, focusing a filled omnibar, deleting back to a previous query), and each repeat would
 * otherwise be another request to the suggest endpoint.
 */
interface AutoCompleteSearchCache {
    fun get(query: String): List<AutoCompleteServiceRawResult>?

    fun put(query: String, results: List<AutoCompleteServiceRawResult>)

    fun clear()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAutoCompleteSearchCache @Inject constructor(
    private val autocompleteSpeedFeature: AutocompleteSpeedFeature,
    private val currentTimeProvider: CurrentTimeProvider,
) : AutoCompleteSearchCache {

    // Read once per process, so a remote change applies from the next app start.
    private val config: Config? by lazy { readConfig() }

    private val settingsAdapter by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(CacheSettings::class.java)
    }

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > (config?.maxEntries ?: 0)
    }

    override fun get(query: String): List<AutoCompleteServiceRawResult>? {
        val config = config ?: return null
        val key = key(query)
        synchronized(entries) {
            val entry = entries[key] ?: return null
            if (currentTimeProvider.elapsedRealtime() - entry.storedAtMillis > config.ttlMillis) {
                entries.remove(key)
                return null
            }
            return entry.results
        }
    }

    override fun put(query: String, results: List<AutoCompleteServiceRawResult>) {
        if (config == null) return
        synchronized(entries) {
            entries[key(query)] = Entry(results, currentTimeProvider.elapsedRealtime())
        }
    }

    override fun clear() {
        synchronized(entries) { entries.clear() }
    }

    // The service sends the device language with every request, so a language change must not reuse older responses.
    private fun key(query: String): String = "${Locale.getDefault().language}|$query"

    private fun readConfig(): Config? {
        val toggle = autocompleteSpeedFeature.cacheSuggestResults()
        if (!toggle.isEnabled()) return null

        val settings = runCatching {
            toggle.getSettings()?.let { settingsAdapter.fromJson(it) }
        }.getOrNull()

        val ttlSeconds = (settings?.ttlSeconds ?: DEFAULT_TTL_SECONDS).coerceIn(MIN_TTL_SECONDS, MAX_TTL_SECONDS)
        val maxEntries = (settings?.maxEntries ?: DEFAULT_MAX_ENTRIES).coerceIn(MIN_MAX_ENTRIES, MAX_MAX_ENTRIES)
        return Config(ttlMillis = ttlSeconds * 1000, maxEntries = maxEntries)
    }

    private data class Config(
        val ttlMillis: Long,
        val maxEntries: Int,
    )

    private data class CacheSettings(
        val ttlSeconds: Long?,
        val maxEntries: Int?,
    )

    private data class Entry(
        val results: List<AutoCompleteServiceRawResult>,
        val storedAtMillis: Long,
    )

    companion object {
        private const val DEFAULT_TTL_SECONDS = 120L
        private const val MIN_TTL_SECONDS = 1L
        private const val MAX_TTL_SECONDS = 600L
        private const val DEFAULT_MAX_ENTRIES = 50
        private const val MIN_MAX_ENTRIES = 1
        private const val MAX_MAX_ENTRIES = 200
    }
}
