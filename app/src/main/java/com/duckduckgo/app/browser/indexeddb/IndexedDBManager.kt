/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.indexeddb

import android.content.Context
import com.duckduckgo.app.browser.UriString.Companion.sameOrSubdomain
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.withContext
import logcat.logcat
import java.io.File
import javax.inject.Inject

interface IndexedDBManager {
    suspend fun clearIndexedDB()
}

data class IndexedDBSettings(
    val domains: List<String>?,
)

@ContributesBinding(AppScope::class)
class DuckDuckGoIndexedDBManager @Inject constructor(
    private val context: Context,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val fileDeleter: FileDeleter,
    private val moshi: Moshi,
    private val dispatcherProvider: DispatcherProvider,
    private val settingsDataStore: SettingsDataStore,
) : IndexedDBManager {

    private val jsonAdapter: JsonAdapter<IndexedDBSettings> by lazy {
        moshi.adapter(IndexedDBSettings::class.java)
    }

    override suspend fun clearIndexedDB() = withContext(dispatcherProvider.io()) {
        val allowedDomains = getAllowedDomains()
        logcat { "IndexedDBManager: Allowed domains: $allowedDomains" }

        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        val excludedFolders = getExcludedFolders(rootFolder, allowedDomains)

        fileDeleter.deleteContents(rootFolder, excludedFolders)
    }

    private fun getAllowedDomains(): List<String> {
        val settings = androidBrowserConfigFeature.indexedDB().getSettings()?.let {
            runCatching { jsonAdapter.fromJson(it) }.getOrNull()
        }
        return settings?.domains?.plus(getFireproofedDomains()) ?: emptyList()
    }

    private fun getFireproofedDomains(): List<String> {
        return if (androidBrowserConfigFeature.fireproofedIndexedDB().isEnabled()) {
            fireproofWebsiteRepository.fireproofWebsitesSync().map { it.domain }
        } else {
            emptyList()
        }
    }

    private fun getExcludedFolders(
        rootFolder: File,
        allowedDomains: List<String>,
        clearDuckAiData: Boolean = settingsDataStore.clearDuckAiData,
    ): List<String> {
        return (rootFolder.listFiles() ?: emptyArray())
            .filter {
                // IndexedDB folders have this format: <scheme>_<host>_<port>.indexeddb.leveldb
                val host = it.name.split("_").getOrNull(1) ?: return@filter false
                val isAllowed = allowedDomains.any { domain -> sameOrSubdomain(host, domain) }

                if (clearDuckAiData && sameOrSubdomain(host, DUCKDUCKGO_DOMAIN)) {
                    false
                } else {
                    isAllowed
                }
            }
            .map { it.name }
    }

    companion object {
        const val DUCKDUCKGO_DOMAIN = "duckduckgo.com"
    }
}
