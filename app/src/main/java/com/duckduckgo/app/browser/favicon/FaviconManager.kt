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

import android.graphics.Bitmap
import android.net.Uri
import android.widget.ImageView
import androidx.core.net.toUri
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_PERSISTED_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_TEMP_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.NO_SUBFOLDER
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.domain
import com.duckduckgo.app.global.faviconLocation
import com.duckduckgo.app.global.touchFaviconLocation
import com.duckduckgo.app.global.view.loadFavicon
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

interface FaviconManager {
    suspend fun storeFavicon(tabId: String, faviconSource: FaviconSource): File?
    suspend fun tryFetchFaviconForUrl(tabId: String, url: String): File?
    suspend fun persistCachedFavicon(tabId: String, url: String)
    suspend fun loadToViewFromLocalOrFallback(tabId: String? = null, url: String, view: ImageView)
    suspend fun loadFromLocal(tabId: String?, url: String): Bitmap?
    suspend fun deletePersistedFavicon(url: String)
    suspend fun deleteOldTempFavicon(tabId: String, path: String?)
    suspend fun deleteAllTemp()
}

sealed class FaviconSource {
    data class ImageFavicon(val icon: Bitmap, val url: String) : FaviconSource()
    data class UrlFavicon(val faviconUrl: String, val url: String) : FaviconSource()
}

class DuckDuckGoFaviconManager constructor(
    private val faviconPersister: FaviconPersister,
    private val bookmarksDao: BookmarksDao,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val locationPermissionsRepository: LocationPermissionsRepository,
    private val favoritesRepository: FavoritesRepository,
    private val faviconDownloader: FaviconDownloader,
    private val dispatcherProvider: DispatcherProvider
) : FaviconManager {

    override suspend fun storeFavicon(tabId: String, faviconSource: FaviconSource): File? {
        val (domain, favicon) = when (faviconSource) {
            is FaviconSource.ImageFavicon -> {
                val domain = faviconSource.url.extractDomain() ?: return null
                Timber.i("Favicon received for ${faviconSource.url}")
                Pair(domain, faviconSource.icon)
            }
            is FaviconSource.UrlFavicon -> {
                val domain = faviconSource.url.extractDomain() ?: return null
                val bitmap = faviconDownloader.getFaviconFromUrl(faviconSource.faviconUrl.toUri()) ?: return null
                Timber.i("Favicon downloaded for $domain from ${faviconSource.faviconUrl}")
                Pair(domain, bitmap)
            }
        }

        return saveFavicon(tabId, favicon, domain)
    }

    override suspend fun tryFetchFaviconForUrl(tabId: String, url: String): File? {
        val domain = url.extractDomain() ?: return null

        val favicon = downloadFaviconFor(domain)

        return if (favicon != null) {
            Timber.i("Favicon downloaded for $domain")
            return saveFavicon(tabId, favicon, domain)
        } else {
            Timber.i("Favicon downloaded null for $domain")
            null
        }
    }

    override suspend fun loadFromLocal(tabId: String?, url: String): Bitmap? {
        val domain = url.extractDomain() ?: return null

        var cachedFavicon: File? = null
        if (tabId != null) {
            cachedFavicon = faviconPersister.faviconFile(FAVICON_TEMP_DIR, tabId, domain)
            if (cachedFavicon != null) {
                Timber.i("Favicon loaded from temp")
            }
        }
        if (cachedFavicon == null) {
            cachedFavicon = faviconPersister.faviconFile(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, domain)
            if (cachedFavicon != null) {
                Timber.i("Favicon loaded from persisted")
            }
        }

        return if (cachedFavicon != null) {
            faviconDownloader.getFaviconFromDisk(cachedFavicon)
        } else null
    }

    override suspend fun loadToViewFromLocalOrFallback(tabId: String?, url: String, view: ImageView) {
        val bitmap = loadFromLocal(tabId, url)

        if (bitmap == null) {
            Timber.i("No favicon loaded")
            view.loadFavicon(bitmap, url)
            val domain = url.extractDomain() ?: return
            tryRemoteFallbackFavicon(subFolder = tabId, domain)?.let {
                view.loadFavicon(it, url)
            }
        } else {
            view.loadFavicon(bitmap, url)
        }

    }

    override suspend fun persistCachedFavicon(tabId: String, url: String) {
        val domain = url.extractDomain() ?: return
        val cachedFavicon = faviconPersister.faviconFile(FAVICON_TEMP_DIR, tabId, domain)
        if (cachedFavicon != null) {
            faviconPersister.copyToDirectory(cachedFavicon, FAVICON_PERSISTED_DIR, NO_SUBFOLDER, domain)
        }
    }

    override suspend fun deletePersistedFavicon(url: String) {
        val domain = url.extractDomain() ?: return
        val remainingFavicons = persistedFaviconsForDomain(domain)
        if (remainingFavicons == 1) {
            faviconPersister.deletePersistedFavicon(domain)
        }
    }

    override suspend fun deleteOldTempFavicon(tabId: String, path: String?) {
        faviconPersister.deleteFaviconsForSubfolder(FAVICON_TEMP_DIR, tabId, path)
    }

    override suspend fun deleteAllTemp() {
        faviconPersister.deleteAll(FAVICON_TEMP_DIR)
    }

    private suspend fun saveFavicon(subFolder: String?, favicon: Bitmap, domain: String): File? {
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
        Timber.i("Favicon will try on $faviconUrl or $touchFaviconUrl")
        faviconDownloader.getFaviconFromUrl(touchFaviconUrl)?.let {
            Timber.i("Favicon downloaded from $touchFaviconUrl")
            return it
        } ?: faviconDownloader.getFaviconFromUrl(faviconUrl).let {
            Timber.i("Favicon downloaded from $faviconUrl")
            return it
        }
    }

    private fun getFaviconUrl(domain: String): Uri? {
        return "https://$domain".toUri().faviconLocation()
    }

    private fun getTouchFaviconUrl(domain: String): Uri? {
        return "https://$domain".toUri().touchFaviconLocation()
    }

    private suspend fun replacePersistedFavicons(icon: Bitmap, domain: String): File? {
        return if (persistedFaviconsForDomain(domain) > 0) {
            faviconPersister.store(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, icon, domain)
        } else null
    }

    private suspend fun tryRemoteFallbackFavicon(subFolder: String?, domain: String): File? {
        val favicon = downloadFaviconFor(domain)
        return if (favicon != null) {
            saveFavicon(subFolder, favicon, domain)
        } else {
            null
        }
    }

    private suspend fun persistedFaviconsForDomain(domain: String): Int {
        val query = "%$domain%"
        return withContext(dispatcherProvider.io()) {
            bookmarksDao.bookmarksCountByUrl(query) +
                locationPermissionsRepository.permissionEntitiesCountByDomain(query) +
                fireproofWebsiteRepository.fireproofWebsitesCountByDomain(domain) +
                favoritesRepository.favoritesCountByDomain(query)
        }
    }

    private fun String.extractDomain(): String? {
        return if (this.startsWith("http")) {
            this.toUri().domain()
        } else {
            "https://$this".extractDomain()
        }
    }
}
