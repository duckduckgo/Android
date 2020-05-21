/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection

import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.uri.removeSubdomain
import com.duckduckgo.app.trackerdetection.db.TdsDomainEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsEntityDao
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TdsEntity
import javax.inject.Inject
import javax.inject.Singleton

interface EntityLookup {
    @WorkerThread
    fun entityForUrl(url: String): Entity?

    @WorkerThread
    fun entityForName(name: String): Entity?
}

@Singleton
class TdsEntityLookup @Inject constructor(
    private val entityDao: TdsEntityDao,
    private val domainEntityDao: TdsDomainEntityDao
) : EntityLookup {

    var entities: List<TdsEntity> = emptyList()

    @WorkerThread
    override fun entityForUrl(url: String): Entity? {
        val uri = url.toUri()
        val host = uri.baseHost ?: return null

        // try searching for exact domain
        val direct = lookUpEntityInDatabase(host)
        if (direct != null) return direct

        // remove the first subdomain, and try again
        val parentDomain = uri.removeSubdomain() ?: return null
        return entityForUrl(parentDomain)
    }

    @WorkerThread
    override fun entityForName(name: String): Entity? {
        return entityDao.get(name)
    }

    @WorkerThread
    private fun lookUpEntityInDatabase(domain: String): Entity? {
        val domainEntity = domainEntityDao.get(domain) ?: return null
        return entityDao.get(domainEntity.entityName)
    }
}
