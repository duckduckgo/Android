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
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import logcat.logcat
import org.iq80.leveldb.DB
import org.iq80.leveldb.Options
import org.iq80.leveldb.impl.Iq80DBFactory.factory

interface WebLocalStorageManager {
    fun clearWebLocalStorage()
}

@ContributesBinding(AppScope::class)
class DuckDuckGoWebLocalStorageManager @Inject constructor(
    private val databaseProvider: Lazy<DB>,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val webLocalStorageSettingsJsonParser: WebLocalStorageSettingsJsonParser,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val settingsDataStore: SettingsDataStore,
) : WebLocalStorageManager {

    private var domains = emptyList<String>()
    private var keysToDelete = emptyList<String>()
    private var matchingRegex = emptyList<String>()

    override fun clearWebLocalStorage() = runBlocking {
        val settings = androidBrowserConfigFeature.webLocalStorage().getSettings()
        val webLocalStorageSettings = webLocalStorageSettingsJsonParser.parseJson(settings)

        val fireproofedDomains = if (androidBrowserConfigFeature.fireproofedWebLocalStorage().isEnabled()) {
            withContext(dispatcherProvider.io()) {
                fireproofWebsiteRepository.fireproofWebsitesSync().map { it.domain }
            }
        } else {
            emptyList()
        }

        domains = webLocalStorageSettings.domains.list + fireproofedDomains
        keysToDelete = webLocalStorageSettings.keysToDelete.list
        matchingRegex = webLocalStorageSettings.matchingRegex.list

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
                if (domainForMatchingAllowedKey == null) {
                    db.delete(entry.key)
                    logcat { "WebLocalStorageManager: Deleted key: $key" }
                } else if (settingsDataStore.clearDuckAiData && domainForMatchingAllowedKey == DUCKDUCKGO_DOMAIN) {
                    if (key.endsWith("duckaiHasAgreedToTerms")) {
                        logcat { "WebLocalStorageManager: Don't delete key: $key" }
                    } else {
                        if (keysToDelete.any { key.endsWith(it) }) {
                            db.delete(entry.key)
                            logcat { "WebLocalStorageManager: Deleted key: $key" }
                        }
                    }
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
        const val DUCKDUCKGO_DOMAIN = "duckduckgo.com"
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
