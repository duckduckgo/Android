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

package com.duckduckgo.app.fire.fireproofwebsite.data

import android.net.Uri
import androidx.lifecycle.LiveData
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.UriString
import dagger.Lazy
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FireproofWebsiteRepository @Inject constructor(
    private val fireproofWebsiteDao: FireproofWebsiteDao,
    private val dispatchers: DispatcherProvider,
    private val faviconManager: Lazy<FaviconManager>
) {
    suspend fun fireproofWebsite(domain: String): FireproofWebsiteEntity? {
        if (!UriString.isValidDomain(domain)) return null

        val fireproofWebsiteEntity = FireproofWebsiteEntity(domain = domain)
        val id = withContext(dispatchers.io()) {
            fireproofWebsiteDao.insert(fireproofWebsiteEntity)
        }

        return if (id >= 0) {
            fireproofWebsiteEntity
        } else {
            null
        }
    }

    fun getFireproofWebsites(): LiveData<List<FireproofWebsiteEntity>> = fireproofWebsiteDao.fireproofWebsitesEntities()

    fun isDomainFireproofed(domain: String): Boolean {
        val uri = Uri.parse(domain)
        val host = uri.host ?: return false

        return fireproofWebsiteDao.getFireproofWebsiteSync(host) != null
    }

    suspend fun removeFireproofWebsite(fireproofWebsiteEntity: FireproofWebsiteEntity) {
        withContext(dispatchers.io()) {
            faviconManager.get().deletePersistedFavicon(fireproofWebsiteEntity.domain)
            fireproofWebsiteDao.delete(fireproofWebsiteEntity)
        }
    }

    suspend fun fireproofWebsitesCountByDomain(domain: String): Int {
        return withContext(dispatchers.io()) {
            fireproofWebsiteDao.fireproofWebsitesCountByDomain(domain)
        }
    }
}
