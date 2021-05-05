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
import com.duckduckgo.app.global.view.loadDefaultFavicon
import com.duckduckgo.app.global.view.loadFavicon
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

interface FaviconManager {
    suspend fun storeFavicon(subFolder: String, faviconSource: FaviconSource): File?
    suspend fun tryFetchFaviconForUrl(subFolder: String, url: String): File?
    suspend fun persistFavicon(subFolder: String, url: String)
    suspend fun loadFromTemp(subFolder: String, url: String): Bitmap? // returns tmp stored image (to be used in add home shortcut)
    suspend fun loadToViewFromTemp(subFolder: String, url: String, view: ImageView) // load tmp stored image for tabid in view
    suspend fun loadToViewFromPersisted(url: String, view: ImageView) // load stored persisted image in view
    suspend fun deletePersistedFavicon(url: String) //removes persisted favicon for url
    suspend fun deleteOldTempFavicon(subFolder: String, path: String?) //removes favicons in subfolder, except for path file
    suspend fun deleteAllTemp() //clear data, remove all temp favicons
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

    override suspend fun loadFromTemp(subFolder: String, url: String): Bitmap? {
        val domain = url.extractDomain() ?: return null
        val cachedFavicon = faviconPersister.faviconFile(FAVICON_TEMP_DIR, subFolder, domain)
        return if (cachedFavicon != null) {
            faviconDownloader.getFaviconFromDisk(cachedFavicon)
        } else {
            null
        }
    }

    override suspend fun loadToViewFromPersisted(url: String, view: ImageView) {
        // avoid displaying the previous favicon when the holder is recycled
        view.loadDefaultFavicon(url)
        loadToViewFromDirectory(url, FAVICON_PERSISTED_DIR, NO_SUBFOLDER, view)
    }

    override suspend fun loadToViewFromTemp(subFolder: String, url: String, view: ImageView) {
        // avoid displaying the previous favicon when the holder is recycled
        view.loadDefaultFavicon(url)
        loadToViewFromDirectory(url, FAVICON_TEMP_DIR, subFolder, view)
    }

    override suspend fun tryFetchFaviconForUrl(subFolder: String, url: String): File? {
        val domain = url.extractDomain() ?: return null

        val favicon = downloadFaviconFor(domain)

        return if (favicon != null) {
            Timber.i("Favicon downloaded for $domain")
            return saveFavicon(subFolder, favicon, domain)
        } else {
            Timber.i("Favicon downloaded null for $domain")
            null
        }
    }

    override suspend fun storeFavicon(subFolder: String, faviconSource: FaviconSource): File? {
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

        return saveFavicon(subFolder, favicon, domain)
    }

    override suspend fun persistFavicon(subFolder: String, url: String) {
        val domain = url.extractDomain() ?: return
        val cachedFavicon = faviconPersister.faviconFile(FAVICON_TEMP_DIR, subFolder, domain)
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

    override suspend fun deleteOldTempFavicon(subFolder: String, path: String?) {
        faviconPersister.deleteFaviconsForSubfolder(FAVICON_TEMP_DIR, subFolder, path)
    }

    override suspend fun deleteAllTemp() {
        faviconPersister.deleteAll(FAVICON_TEMP_DIR)
    }

    private suspend fun saveFavicon(subFolder: String, favicon: Bitmap, domain: String): File? {
        val file = faviconPersister.store(FAVICON_TEMP_DIR, subFolder, favicon, domain)
        if (file != null) { // Only replace persisted favicons if we stored a new file
            replacePersistedFavicons(favicon, domain)
        }
        return file
    }

    private suspend fun loadToViewFromDirectory(url: String, directory: String, subFolder: String, view: ImageView) {
        val domain = url.extractDomain() ?: return
        val cachedFavicon = getFaviconForDomainOrFallback(directory, subFolder, domain)
        cachedFavicon?.let {
            view.loadFavicon(cachedFavicon, url)
        }
    }

    private suspend fun downloadFaviconFor(domain: String): Bitmap? {
        val faviconUrl = getFaviconUrl(domain) ?: return null
        val touchFaviconUrl = getTouchFaviconUrl(domain) ?: return null
        faviconDownloader.getFaviconFromUrl(touchFaviconUrl)?.let {
            Timber.i("Favicon downloaded from $touchFaviconUrl")
            return it
        } ?: faviconDownloader.getFaviconFromUrl(faviconUrl).let {
            Timber.i("Favicon downloaded from $faviconUrl")
            return it
        }
    }

    private fun getFaviconUrl(url: String): Uri? {
        return if (url.extractDomain().isNullOrBlank()) {
            "https://$url".toUri().faviconLocation()
        } else {
            url.toUri().faviconLocation()
        }
    }

    private fun getTouchFaviconUrl(url: String): Uri? {
        return if (url.extractDomain().isNullOrBlank()) {
            "https://$url".toUri().touchFaviconLocation()
        } else {
            url.toUri().touchFaviconLocation()
        }
    }

    private suspend fun replacePersistedFavicons(icon: Bitmap, domain: String) {
        if (persistedFaviconsForDomain(domain) > 0) {
            saveToFile(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, icon, domain)
        }
    }

    private suspend fun getFaviconForDomainOrFallback(directory: String, subFolder: String, domain: String): File? {
        val cachedFavicon: File? = faviconPersister.faviconFile(directory, subFolder, domain)

        return if (cachedFavicon == null) {
            val favicon = downloadFaviconFor(domain)
            if (favicon != null) {
                saveToFile(directory, subFolder, favicon, domain)
            } else {
                null
            }
        } else {
            cachedFavicon
        }
    }

    private suspend fun saveToFile(directory: String, subFolder: String, icon: Bitmap, domain: String): File? {
        return faviconPersister.store(directory, subFolder, icon, domain)
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
