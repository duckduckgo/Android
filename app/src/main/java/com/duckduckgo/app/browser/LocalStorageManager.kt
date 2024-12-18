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
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import org.iq80.leveldb.DB
import org.iq80.leveldb.Options
import org.iq80.leveldb.impl.Iq80DBFactory.factory
import timber.log.Timber

interface LocalStorageManager {
    fun clearLocalStorage()
}

@ContributesBinding(AppScope::class)
class DuckDuckGoLocalStorageManager @Inject constructor(
    private val context: Context,
) : LocalStorageManager {

    override fun clearLocalStorage() {
        val options = Options().apply { createIfMissing(false) }
        val database: DB = factory.open(
            File(context.applicationInfo.dataDir, "app_webview/Default/Local Storage/leveldb"),
            options,
        )

        database.use {
            val iterator = it.iterator()
            iterator.seekToFirst()

            while (iterator.hasNext()) {
                val entry = iterator.next()
                val key = String(entry.key, StandardCharsets.UTF_8)

                if (!isAllowedKey(key)) {
                    it.delete(entry.key)
                    Timber.d("LocalStorageManager: Deleted key: $key")
                }
            }
        }
    }

    private fun isAllowedKey(key: String): Boolean {
        val allowedDomains = listOf("duckduckgo.com")

        // Entries have the format example.com??value
        val separator = '\u0000'

        return if (key.contains(separator)) {
            val beforeSeparator = key.substringBefore(separator)
            allowedDomains.any { beforeSeparator.contains(it) }
        } else {
            allowedDomains.any { key.endsWith(it) }
        }
    }
}
