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
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
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
import org.iq80.leveldb.DB
import org.iq80.leveldb.Options
import org.iq80.leveldb.impl.Iq80DBFactory.factory
import timber.log.Timber

interface WebLocalStorageManager {
    fun clearWebLocalStorage()
}

@ContributesBinding(AppScope::class)
class DuckDuckGoWebLocalStorageManager @Inject constructor(
    private val databaseProvider: Lazy<DB>,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val webLocalStorageSettingsJsonParser: WebLocalStorageSettingsJsonParser,
) : WebLocalStorageManager {

    private var domains = emptyList<String>()
    private var matchingRegex = emptyList<String>()

    override fun clearWebLocalStorage() = runBlocking {
        val settings = androidBrowserConfigFeature.webLocalStorage().getSettings()
        val webLocalStorageSettings = webLocalStorageSettingsJsonParser.parseJson(settings)

        domains = webLocalStorageSettings.domains.list
        matchingRegex = webLocalStorageSettings.matchingRegex.list

        Timber.d("WebLocalStorageManager: Allowed domains: $domains")
        Timber.d("WebLocalStorageManager: Matching regex: $matchingRegex")

        val db = databaseProvider.get()
        db.iterator().use { iterator ->
            iterator.seekToFirst()

            while (iterator.hasNext()) {
                val entry = iterator.next()
                val key = String(entry.key, StandardCharsets.UTF_8)

                if (!isAllowedKey(key)) {
                    db.delete(entry.key)
                    Timber.d("WebLocalStorageManager: Deleted key: $key")
                }
            }
        }
    }

    private fun isAllowedKey(key: String): Boolean {
        val regexPatterns = domains.flatMap { domain ->
            val escapedDomain = Regex.escape(domain)
            matchingRegex.map { pattern ->
                pattern.replace("{domain}", escapedDomain)
            }
        }
        return regexPatterns.any { pattern -> Regex(pattern).matches(key) }
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
