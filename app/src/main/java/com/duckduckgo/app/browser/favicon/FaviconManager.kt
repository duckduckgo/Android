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
import android.net.Uri
import android.widget.ImageView
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_PERSISTED_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_TEMP_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.NO_SUBFOLDER
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.domain
import com.duckduckgo.app.global.faviconLocation
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.lang.Exception
import javax.inject.Inject

interface FaviconManager {

    suspend fun saveToTemp(subFolder: String, icon: Bitmap, domain: String): File
    suspend fun prefetchToTemp(subFolder: String, url: String): File?
    suspend fun persistFavicon(subFolder: String, domain: String)
    suspend fun loadFromTemp(subFolder: String, domain: String): Bitmap?
    suspend fun loadToViewFromTemp(subFolder: String, domain: String, view: ImageView)
    suspend fun loadToViewFromPersisted(domain: String, view: ImageView)
    suspend fun deletePersistedFavicon(domain: String)
    suspend fun deleteOldTempFavicon(subFolder: String, domain: String?)
    suspend fun deleteAllTemp()
}

class GlideFaviconManager @Inject constructor(
    private val context: Context,
    private val faviconPersister: FaviconPersister,
    private val bookmarksDao: BookmarksDao,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val locationPermissionsRepository: LocationPermissionsRepository,
    private val dispatcherProvider: DispatcherProvider
) : FaviconManager {

    override suspend fun loadFromTemp(subFolder: String, domain: String): Bitmap? {
        return withContext(dispatcherProvider.io()) {
            val cachedFavicon = faviconPersister.faviconFile(FAVICON_TEMP_DIR, subFolder, domain)
            if (cachedFavicon.exists()) {
                Glide.with(context)
                    .asBitmap()
                    .load(cachedFavicon)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .submit()
                    .get()
            } else {
                null
            }
        }
    }

    override suspend fun loadToViewFromPersisted(domain: String, view: ImageView) {
        GlobalScope.launch {
            val cachedFavicon = getFaviconForDomainOrFallback(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, domain)
            if (cachedFavicon != null && cachedFavicon.exists()) {
                loadFaviconToView(cachedFavicon, view)
            }
        }
    }

    override suspend fun loadToViewFromTemp(subFolder: String, domain: String, view: ImageView) {
        GlobalScope.launch {
            val cachedFavicon = getFaviconForDomainOrFallback(FAVICON_TEMP_DIR, subFolder, domain)
            if (cachedFavicon != null && cachedFavicon.exists()) {
                loadFaviconToView(cachedFavicon, view)
            }
        }
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

    override suspend fun saveToTemp(subFolder: String, icon: Bitmap, domain: String): File {
        return withContext(dispatcherProvider.io()) {
            replacePersistedFavicons(icon, domain)
            faviconPersister.store(FAVICON_TEMP_DIR, subFolder, icon, domain)
        }
    }

    override suspend fun persistFavicon(subFolder: String, domain: String) {
        withContext(dispatcherProvider.io()) {
            val cachedFavicon = faviconPersister.faviconFile(FAVICON_TEMP_DIR, subFolder, domain)
            if (cachedFavicon.exists()) {
                faviconPersister.copyToDirectory(cachedFavicon, FAVICON_PERSISTED_DIR, NO_SUBFOLDER, domain)
            }
        }
    }

    override suspend fun deletePersistedFavicon(domain: String) {
        val remainingFavicons = persistedFaviconsForDomain(domain) - 1 // -1 because we are about to delete one
        if (remainingFavicons <= 0) {
            faviconPersister.deletePersistedFavicon(domain)
        }
    }

    override suspend fun deleteOldTempFavicon(subFolder: String, domain: String?) {
        faviconPersister.deleteFaviconsForSubfolder(FAVICON_TEMP_DIR, subFolder, domain)
    }

    override suspend fun deleteAllTemp() {
        faviconPersister.deleteAll(FAVICON_TEMP_DIR)
    }

    private suspend fun downloadFromUrl(url: String): Bitmap? {
        return withContext(dispatcherProvider.io()) {
            try {
                val faviconUrl = getFaviconUrl(url)
                Glide.with(context)
                    .asBitmap()
                    .load(faviconUrl)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .submit()
                    .get()
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
        if (file.exists()) {
            withContext(dispatcherProvider.main()) {
                Glide.with(context)
                    .load(file)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .placeholder(R.drawable.ic_globe_gray_16dp)
                    .error(R.drawable.ic_globe_gray_16dp)
                    .into(view)
            }
        }
    }

    private suspend fun replacePersistedFavicons(icon: Bitmap, domain: String) {
        if (persistedFaviconsForDomain(domain) > 0) {
            saveToFile(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, icon, domain)
        }
    }

    private suspend fun getFaviconForDomainOrFallback(directory: String, subFolder: String, domain: String): File? {
        val cachedFavicon: File = faviconPersister.faviconFile(directory, subFolder, domain)

        return if (!cachedFavicon.exists()) {
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

    private suspend fun saveToFile(directory: String, subFolder: String, icon: Bitmap, domain: String): File {
        return withContext(dispatcherProvider.io()) {
            faviconPersister.store(directory, subFolder, icon, domain)
        }
    }

    private suspend fun persistedFaviconsForDomain(domain: String): Int {
        return bookmarksDao.bookmarksCountByUrl("%$domain%") +
                locationPermissionsRepository.permissionEntitiesCountByDomain(domain) +
                fireproofWebsiteRepository.fireproofWebsitesCountByDomain(domain)
    }
}
