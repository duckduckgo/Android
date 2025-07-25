/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.favicon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_PERSISTED_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_TEMP_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.NO_SUBFOLDER
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.view.generateDefaultDrawable
import com.duckduckgo.app.global.view.loadFavicon
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.faviconLocation
import com.duckduckgo.common.utils.touchFaviconLocation
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.sync.api.favicons.FaviconsFetchingStore
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FAVICON_LOAD_RETRY_DELAY = 2000L
private const val FAVICON_LOAD_RETRIES = 3

class DuckDuckGoFaviconManager constructor(
    private val faviconPersister: FaviconPersister,
    private val savedSitesDao: SavedSitesEntitiesDao,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val savedSitesRepository: SavedSitesRepository,
    private val faviconDownloader: FaviconDownloader,
    private val dispatcherProvider: DispatcherProvider,
    private val autofillStore: AutofillStore,
    private val faviconsFetchingStore: FaviconsFetchingStore,
    private val context: Context,
) : FaviconManager {

    private val tempFaviconCache: HashMap<String, Pair<String, MutableList<String>>> = hashMapOf()

    override suspend fun storeFavicon(
        tabId: String,
        faviconSource: FaviconSource,
    ): File? {
        val (domain, favicon) = when (faviconSource) {
            is FaviconSource.ImageFavicon -> {
                val domain = faviconSource.url.extractDomain() ?: return null
                invalidateCacheIfNewDomain(tabId, domain)
                Pair(domain, faviconSource.icon)
            }
            is FaviconSource.UrlFavicon -> {
                val domain = faviconSource.url.extractDomain() ?: return null
                invalidateCacheIfNewDomain(tabId, domain)
                if (shouldSkipNetworkRequest(tabId, faviconSource)) return null
                val bitmap = faviconDownloader.getFaviconFromUrl(faviconSource.faviconUrl.toUri()) ?: return null
                addFaviconUrlToCache(tabId, faviconSource)
                Pair(domain, bitmap)
            }
        }

        return saveFavicon(tabId, favicon, domain)
    }

    override suspend fun tryFetchFaviconForUrl(
        tabId: String,
        url: String,
    ): File? {
        return withContext(dispatcherProvider.io()) {
            val domain = url.extractDomain() ?: return@withContext null

            val favicon = downloadFaviconFor(domain)

            return@withContext if (favicon != null) {
                saveFavicon(tabId, favicon, domain)
            } else {
                null
            }
        }
    }

    override suspend fun tryFetchFaviconForUrl(url: String): Bitmap? {
        return withContext(dispatcherProvider.io()) {
            val domain = url.extractDomain() ?: return@withContext null

            val favicon = downloadFaviconFor(domain)

            return@withContext if (favicon != null) {
                saveFavicon(null, favicon, domain)
                favicon
            } else {
                null
            }
        }
    }

    override suspend fun loadFromDisk(
        tabId: String?,
        url: String,
    ): Bitmap? {
        val domain = url.extractDomain() ?: return null

        return withContext(dispatcherProvider.io()) {
            var cachedFavicon: File? = null
            if (tabId != null) {
                cachedFavicon = faviconPersister.faviconFile(FAVICON_TEMP_DIR, tabId, domain)
            }
            if (cachedFavicon == null) {
                cachedFavicon = faviconPersister.faviconFile(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, domain)
            }

            return@withContext if (cachedFavicon != null) {
                faviconDownloader.getFaviconFromDisk(cachedFavicon)
            } else {
                null
            }
        }
    }

    override suspend fun loadFromDiskWithParams(
        tabId: String?,
        url: String,
        cornerRadius: Int,
        width: Int,
        height: Int,
    ): Bitmap? {
        val domain = url.extractDomain() ?: return null
        return withContext(dispatcherProvider.io()) {
            var cachedFavicon: File? = null
            if (tabId != null) {
                cachedFavicon = faviconPersister.faviconFile(FAVICON_TEMP_DIR, tabId, domain)
            }
            if (cachedFavicon == null) {
                cachedFavicon = faviconPersister.faviconFile(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, domain)
            }

            return@withContext if (cachedFavicon != null) {
                faviconDownloader.getFaviconFromDisk(cachedFavicon, cornerRadius, width, height)
            } else {
                cachedFavicon
            }
        }
    }

    override suspend fun loadToViewMaybeFromRemoteWithPlaceholder(
        url: String,
        view: ImageView,
        placeholder: String?,
    ) {
        val bitmap = loadFromDisk(tabId = null, url = url)
        if (bitmap == null && faviconsFetchingStore.isFaviconsFetchingEnabled) {
            tryFetchFaviconForUrl(url)
            view.loadFavicon(loadFromDisk(tabId = null, url = url), url, placeholder)
        } else {
            view.loadFavicon(bitmap, url, placeholder)
        }
    }

    override suspend fun loadToViewFromLocalWithPlaceholder(tabId: String?, url: String, view: ImageView, placeholder: String?) {
        var bitmap = loadFromDisk(tabId, url)
        view.loadFavicon(bitmap, url, placeholder)

        // sometimes it takes a while to fetch a favicon, so we try to load it from disk again
        repeat(FAVICON_LOAD_RETRIES) {
            if (bitmap == null) {
                view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    delay(FAVICON_LOAD_RETRY_DELAY)
                    bitmap = loadFromDisk(tabId, url)
                    if (bitmap != null) {
                        view.loadFavicon(bitmap, url, placeholder)
                    }
                }
            }
        }
    }

    override suspend fun persistCachedFavicon(
        tabId: String,
        url: String,
    ) {
        withContext(dispatcherProvider.io()) {
            val domain = url.extractDomain() ?: return@withContext
            val cachedFavicon = faviconPersister.faviconFile(FAVICON_TEMP_DIR, tabId, domain)
            if (cachedFavicon != null) {
                faviconPersister.copyToDirectory(cachedFavicon, FAVICON_PERSISTED_DIR, NO_SUBFOLDER, domain)
            }
        }
    }

    override suspend fun persistFavicon(
        favicon: Bitmap,
        url: String
    ) {
        withContext(dispatcherProvider.io()) {
            url.extractDomain()?.let {
                faviconPersister.store(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, favicon, it)
            }
        }
    }

    override suspend fun deletePersistedFavicon(url: String) {
        withContext(dispatcherProvider.io()) {
            val domain = url.extractDomain() ?: return@withContext
            val remainingFavicons = persistedFaviconsForDomain(domain)
            if (remainingFavicons == 1) {
                faviconPersister.deletePersistedFavicon(domain)
            }
        }
    }

    override suspend fun deleteOldTempFavicon(
        tabId: String,
        path: String?,
    ) {
        withContext(dispatcherProvider.io()) {
            removeCacheForTab(path, tabId)
            faviconPersister.deleteFaviconsForSubfolder(FAVICON_TEMP_DIR, tabId, path)
        }
    }

    override suspend fun deleteAllTemp() {
        withContext(dispatcherProvider.io()) {
            faviconPersister.deleteAll(FAVICON_TEMP_DIR)
        }
    }

    override fun generateDefaultFavicon(
        placeholder: String?,
        domain: String,
    ): Drawable {
        return generateDefaultDrawable(context, domain, placeholder)
    }

    private suspend fun saveFavicon(
        subFolder: String?,
        favicon: Bitmap,
        domain: String,
    ): File? {
        return if (subFolder != null) {
            faviconPersister.store(FAVICON_TEMP_DIR, subFolder, favicon, domain)
                ?.also { replacePersistedFavicons(favicon, domain) }
        } else {
            replacePersistedFavicons(favicon, domain)
        }
    }

    private suspend fun downloadFaviconFor(domain: String): Bitmap? {
        val faviconUrl = getFaviconUrl(domain) ?: return null
        val touchFaviconUrl = getTouchFaviconUrl(domain) ?: return null
        faviconDownloader.getFaviconFromUrl(touchFaviconUrl)?.let {
            return it
        } ?: faviconDownloader.getFaviconFromUrl(faviconUrl).let {
            return it
        }
    }

    private fun getFaviconUrl(domain: String): Uri? {
        return "https://$domain".toUri().faviconLocation()
    }

    private fun getTouchFaviconUrl(domain: String): Uri? {
        return "https://$domain".toUri().touchFaviconLocation()
    }

    private suspend fun replacePersistedFavicons(
        icon: Bitmap,
        domain: String,
    ): File? {
        return if (persistedFaviconsForDomain(domain) > 0) {
            faviconPersister.store(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, icon, domain)
        } else {
            null
        }
    }

    private suspend fun persistedFaviconsForDomain(domain: String): Int {
        val query = "%$domain%"

        return withContext(dispatcherProvider.io()) {
            savedSitesDao.countEntitiesByUrl(query) +
                fireproofWebsiteRepository.fireproofWebsitesCountByDomain(domain) +
                savedSitesRepository.getFavoritesCountByDomain(query) +
                autofillStore.getCredentials(domain).size
        }
    }

    private fun String.extractDomain(): String? {
        return if (this.startsWith("http")) {
            this.toUri().baseHost
        } else {
            "https://$this".extractDomain()
        }
    }

    private fun invalidateCacheIfNewDomain(
        tabId: String,
        domain: String,
    ) {
        if (tempFaviconCache[tabId]?.first != domain) {
            tempFaviconCache[tabId] = Pair(domain, mutableListOf())
        }
    }

    private fun removeCacheForTab(
        path: String?,
        tabId: String,
    ) {
        if (path == null) {
            tempFaviconCache.remove(tabId)
        }
    }

    private fun shouldSkipNetworkRequest(
        tabId: String,
        faviconSource: FaviconSource.UrlFavicon,
    ) =
        tempFaviconCache[tabId]?.second?.contains(faviconSource.faviconUrl) == true

    private fun addFaviconUrlToCache(
        tabId: String,
        faviconSource: FaviconSource.UrlFavicon,
    ) {
        tempFaviconCache[tabId]?.second?.add(faviconSource.faviconUrl)
    }
}
