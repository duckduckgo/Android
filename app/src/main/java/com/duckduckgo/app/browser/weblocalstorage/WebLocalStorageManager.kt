/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.weblocalstorage

import android.content.Context
import com.duckduckgo.app.browser.api.DuckAiChatDeletionListener
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.logcat
import org.iq80.leveldb.DB
import org.iq80.leveldb.Options
import org.iq80.leveldb.impl.Iq80DBFactory.factory
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject

interface WebLocalStorageManager {
    /**
     * Clears web local storage based on predefined settings and fireproofed websites (legacy).
     *
     * Uses settingsDataStore.clearDuckAiData to determine if DuckAi data should be cleared.
     */
    suspend fun clearWebLocalStorage()

    /**
     * Clears web local storage based on the specified options.
     * @param shouldClearBrowserData If true, clears browser web data (cache, history, form data, authentication, cookies, directories).
     * @param shouldClearDuckAiData If true, clears chat-related data from WebStorage.
     */
    suspend fun clearWebLocalStorage(
        shouldClearBrowserData: Boolean,
        shouldClearDuckAiData: Boolean,
    )
}

@ContributesBinding(AppScope::class)
class DuckDuckGoWebLocalStorageManager @Inject constructor(
    private val databaseProvider: Lazy<DB>,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val webLocalStorageSettingsJsonParser: WebLocalStorageSettingsJsonParser,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val settingsDataStore: SettingsDataStore,
    private val duckAiChatDeletionListeners: PluginPoint<DuckAiChatDeletionListener>,
) : WebLocalStorageManager {

    private var domains = emptyList<String>()
    private var keysToDelete = emptyList<String>()
    private var matchingRegex = emptyList<String>()

    override suspend fun clearWebLocalStorage() = withContext(dispatcherProvider.io()) {
        val shouldClearBrowserData = true // As per legacy behavior, we always clear browser data
        val shouldClearDuckAiData = settingsDataStore.clearDuckAiData
        clearWebLocalStorage(shouldClearBrowserData, shouldClearDuckAiData)
    }

    override suspend fun clearWebLocalStorage(
        shouldClearBrowserData: Boolean,
        shouldClearDuckAiData: Boolean,
    ) {
        withContext(dispatcherProvider.io()) {
            val settings = androidBrowserConfigFeature.webLocalStorage().getSettings()
            val webLocalStorageSettings = webLocalStorageSettingsJsonParser.parseJson(settings)

            val fireproofedDomains = fireproofWebsiteRepository.fireproofWebsitesSync().map { it.domain }

            domains = webLocalStorageSettings.domains.list + fireproofedDomains
            keysToDelete = webLocalStorageSettings.keysToDelete.list
            matchingRegex = webLocalStorageSettings.matchingRegex.list
            var duckAiDataDeleted = false

            logcat { "WebLocalStorageManager: Allowed domains: $domains" }
            logcat { "WebLocalStorageManager: Keys to delete: $keysToDelete" }
            logcat { "WebLocalStorageManager: Matching regex: $matchingRegex" }

            val db = databaseProvider.get()
            db.iterator().use { iterator ->
                iterator.seekToFirst()

                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    val key = String(entry.key, StandardCharsets.UTF_8)

                    val domainForMatchingAllowedKey = getDomainForMatchingAllowedKey(key)
                    if (domainForMatchingAllowedKey == null && shouldClearBrowserData) {
                        db.delete(entry.key)
                        logcat { "WebLocalStorageManager: Deleted key: $key" }
                    } else if (shouldClearDuckAiData && DUCKDUCKGO_DOMAINS.contains(domainForMatchingAllowedKey)) {
                        if (keysToDelete.any { key.endsWith(it) }) {
                            db.delete(entry.key)
                            duckAiDataDeleted = true
                            logcat { "WebLocalStorageManager: Deleted key: $key" }
                        }
                    }
                }
            }

            logcat { "WebLocalStorageManager: finished deleting local storage data. duck AI chats cleared:$duckAiDataDeleted" }
            if (duckAiDataDeleted) {
                // Notify all listeners that Duck AI chats have been deleted
                duckAiChatDeletionListeners.getPlugins().forEach { listener ->
                    listener.onDuckAiChatsDeleted()
                }
            }
        }
    }

    private fun getDomainForMatchingAllowedKey(key: String): String? {
        for (domain in domains) {
            val escapedDomain = Regex.escape(domain)
            val regexPatterns = matchingRegex.map { pattern ->
                pattern.replace("{domain}", escapedDomain)
            }
            if (regexPatterns.any { pattern -> Regex(pattern).matches(key) }) {
                return domain
            }
        }
        return null
    }

    companion object {
        val DUCKDUCKGO_DOMAINS = listOf("duckduckgo.com", "duck.ai")
    }
}

@Module
@ContributesTo(AppScope::class)
class WebLocalStorageManagerModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideWebLocalStorageManagerDB(context: Context): DB {
        val options = Options().apply { createIfMissing(false) }
        return factory.open(
            File(context.applicationInfo.dataDir, "app_webview/Default/Local Storage/leveldb"),
            options,
        )
    }
}
