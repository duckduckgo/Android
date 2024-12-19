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

package com.duckduckgo.app.browser

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Provider
import org.iq80.leveldb.DB
import org.iq80.leveldb.Options
import org.iq80.leveldb.impl.Iq80DBFactory.factory
import timber.log.Timber

interface LocalStorageManager {
    fun clearLocalStorage()
}

@ContributesBinding(AppScope::class)
class DuckDuckGoLocalStorageManager @Inject constructor(
    private val databaseProvider: Provider<DB>,
) : LocalStorageManager {

    override fun clearLocalStorage() {
        databaseProvider.get().use { db ->
            val iterator = db.iterator()
            iterator.seekToFirst()

            while (iterator.hasNext()) {
                val entry = iterator.next()
                val key = String(entry.key, StandardCharsets.UTF_8)

                if (!isAllowedKey(key)) {
                    db.delete(entry.key)
                    Timber.d("LocalStorageManager: Deleted key: $key")
                }
            }
        }
    }

    private fun isAllowedKey(key: String): Boolean {
        val domains = listOf("duckduckgo.com")

        // Valid entries have these formats:
        // _https://example.com<NULL><SOH>value
        // META:https://example.com
        val regexList = listOf(
            "^_https://([a-zA-Z0-9.-]+\\.)?{domain}\u0000\u0001.+$",
            "^META:https://([a-zA-Z0-9.-]+\\.)?{domain}$",
        )
        val regexPatterns = domains.flatMap { domain ->
            val escapedDomain = Regex.escape(domain)
            regexList.map { pattern ->
                pattern.replace("{domain}", escapedDomain)
            }
        }
        return regexPatterns.any { pattern -> Regex(pattern).matches(key) }
    }
}

@Module
@ContributesTo(AppScope::class)
class LocalStorageManagerModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideLocalStorageManagerDB(context: Context): DB {
        val options = Options().apply { createIfMissing(false) }
        return factory.open(
            File(context.applicationInfo.dataDir, "app_webview/Default/Local Storage/leveldb"),
            options,
        )
    }
}
