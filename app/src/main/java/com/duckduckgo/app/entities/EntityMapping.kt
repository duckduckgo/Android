/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.entities

import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import com.duckduckgo.app.entities.db.EntityListDao
import com.duckduckgo.app.entities.db.EntityListEntity
import com.duckduckgo.app.global.uri.removeOneSubdomain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntityMapping @Inject constructor(private val entityListDao: EntityListDao) {

    var entities: List<EntityListEntity> = emptyList()

    fun entityForUrl(url: String): EntityListEntity? {
        //return entities.find { UriString.sameOrSubdomain(url, it.domainName) }
        return entityForUrl2(url)
    }

    @WorkerThread
    fun entityForUrl2(url: String): EntityListEntity? {
        val uri = url.toUri()
        val host = uri.host ?: return null

        // try searching for exact domain
        val direct = lookUpEntityInDatabase(host)
        if (direct != null) return direct

        // remove the first subdomain, and try again
        val parentDomain = url.toUri().removeOneSubdomain() ?: return null
        return entityForUrl2(parentDomain)
    }

    @WorkerThread
    private fun lookUpEntityInDatabase(url: String): EntityListEntity? {
        return entityListDao.get(url)
    }
}