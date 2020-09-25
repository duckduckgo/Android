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
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_PERSISTED_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_TEMP_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.NO_SUBFOLDER
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.domain
import com.duckduckgo.app.global.faviconLocation
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.lang.Exception

interface FaviconManager {
    suspend fun saveToTemp(subFolder: String, icon: Bitmap, url: String): File?
    suspend fun prefetchToTemp(subFolder: String, url: String): File?
    suspend fun persistFavicon(subFolder: String, url: String)
    suspend fun loadFromTemp(subFolder: String, url: String): Bitmap?
    suspend fun loadToViewFromTemp(subFolder: String, url: String, view: ImageView)
    suspend fun loadToViewFromPersisted(url: String, view: ImageView)
    suspend fun deletePersistedFavicon(url: String)
    suspend fun deleteOldTempFavicon(subFolder: String, path: String?)
    suspend fun deleteAllTemp()
}

class DuckDuckGoFaviconManager constructor(
    private val faviconPersister: FaviconPersister,
    private val bookmarksDao: BookmarksDao,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val locationPermissionsRepository: LocationPermissionsRepository,
    private val faviconDownloader: FaviconDownloader,
    private val dispatcherProvider: DispatcherProvider
) : FaviconManager {

    override suspend fun loadFromTemp(subFolder: String, url: String): Bitmap? {
        val domain = extractDomain(url)
        return withContext(dispatcherProvider.io()) {
            val cachedFavicon = faviconPersister.faviconFile(FAVICON_TEMP_DIR, subFolder, domain)
            if (cachedFavicon != null) {
                faviconDownloader.getFaviconFromDisk(cachedFavicon)
            } else {
                null
            }
        }
    }

    override suspend fun loadToViewFromPersisted(url: String, view: ImageView) {
        loadToViewFromDirectory(url, FAVICON_PERSISTED_DIR, NO_SUBFOLDER, view)
    }

    override suspend fun loadToViewFromTemp(subFolder: String, url: String, view: ImageView) {
        loadToViewFromDirectory(url, FAVICON_TEMP_DIR, subFolder, view)
    }

    override suspend fun prefetchToTemp(subFolder: String, url: String): File? {
        val domain = url.toUri().domain() ?: return null
        val favicon = downloadFromUrl(url)
        return if (favicon != null) {
            saveToFile(FAVICON_TEMP_DIR, subFolder, favicon, domain)
        } else {
            null
        }
    }

    override suspend fun saveToTemp(subFolder: String, icon: Bitmap, url: String): File? {
        val domain = extractDomain(url)
        return withContext(dispatcherProvider.io()) {
            replacePersistedFavicons(icon, domain)
            faviconPersister.store(FAVICON_TEMP_DIR, subFolder, icon, domain)
        }
    }

    override suspend fun persistFavicon(subFolder: String, url: String) {
        val domain = extractDomain(url)
        withContext(dispatcherProvider.io()) {
            val cachedFavicon = faviconPersister.faviconFile(FAVICON_TEMP_DIR, subFolder, domain)
            if (cachedFavicon != null) {
                faviconPersister.copyToDirectory(cachedFavicon, FAVICON_PERSISTED_DIR, NO_SUBFOLDER, domain)
            }
        }
    }

    override suspend fun deletePersistedFavicon(url: String) {
        val domain = extractDomain(url)
        withContext(dispatcherProvider.io()) {
            val remainingFavicons = persistedFaviconsForDomain(domain) - 1 // -1 because we are about to delete one
            if (remainingFavicons <= 0) {
                faviconPersister.deletePersistedFavicon(domain)
            }
        }
    }

    override suspend fun deleteOldTempFavicon(subFolder: String, path: String?) {
        faviconPersister.deleteFaviconsForSubfolder(FAVICON_TEMP_DIR, subFolder, path)
    }

    override suspend fun deleteAllTemp() {
        faviconPersister.deleteAll(FAVICON_TEMP_DIR)
    }

    private suspend fun loadToViewFromDirectory(url: String, directory: String, subFolder: String, view: ImageView) {
        val domain = extractDomain(url)
        val cachedFavicon = withContext(dispatcherProvider.io()) {
            getFaviconForDomainOrFallback(directory, subFolder, domain)
        }

        cachedFavicon?.let {
            loadFaviconToView(cachedFavicon, view)
        }
    }

    private suspend fun downloadFromUrl(url: String): Bitmap? {
        return withContext(dispatcherProvider.io()) {
            try {
                val faviconUrl = getFaviconUrl(url)
                faviconDownloader.getFaviconFromUrl(faviconUrl)
            } catch (e: Exception) {
                Timber.d(e, "Error downloading favicon")
                null
            }
        }
    }

    private fun getFaviconUrl(url: String): Uri {
        return if (url.toUri().host.isNullOrBlank()) {
            "https://$url".toUri().faviconLocation() ?: throw IllegalArgumentException("Invalid favicon currentPageUrl")
        } else {
            url.toUri().faviconLocation() ?: throw IllegalArgumentException("Invalid favicon currentPageUrl")
        }
    }

    private suspend fun loadFaviconToView(file: File, view: ImageView) {
        withContext(dispatcherProvider.main()) {
            faviconDownloader.loadFaviconToView(file, view)
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
            val favicon = downloadFromUrl(domain)
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
        return withContext(dispatcherProvider.io()) {
            faviconPersister.store(directory, subFolder, icon, domain)
        }
    }

    private suspend fun persistedFaviconsForDomain(domain: String): Int {
        val query = "%$domain%"
        return bookmarksDao.bookmarksCountByUrl(query) +
                locationPermissionsRepository.permissionEntitiesCountByDomain(query) +
                fireproofWebsiteRepository.fireproofWebsitesCountByDomain(domain)
    }

    private fun extractDomain(url: String): String {
        return url.toUri().domain() ?: url
    }
}
