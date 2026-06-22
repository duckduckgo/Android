/*
 * Copyright (c) 2026 DuckDuckGo
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

import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import com.duckduckgo.app.trackerdetection.db.TdsDomainEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsEntityDao
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class CachedTdsEntityLookup @Inject constructor(
    private val entityDao: TdsEntityDao,
    private val domainEntityDao: TdsDomainEntityDao,
) : EntityLookupWithRefresh {

    private data class Snapshot(
        val domainToEntityName: Map<String, String>,
        val entityByName: Map<String, Entity>,
    )

    @Volatile
    private var snapshot: Snapshot? = null

    override fun refresh() {
        snapshot = loadSnapshot()
    }

    override fun entityForUrl(url: String): Entity? {
        val host = url.toUri().baseHost ?: return null
        return labelWalk(host)
    }

    override fun entityForUrl(url: Uri): Entity? {
        val host = url.host ?: return null
        return labelWalk(host)
    }

    override fun entityForName(name: String): Entity? = activeSnapshot().entityByName[name]

    private fun labelWalk(host: String): Entity? {
        val snap = activeSnapshot()
        var candidate = host
        while (true) {
            snap.domainToEntityName[candidate]?.let { entityName ->
                return snap.entityByName[entityName]
            }
            val dot = candidate.indexOf('.')
            if (dot < 0) return null
            candidate = candidate.substring(dot + 1)
        }
    }

    private fun activeSnapshot(): Snapshot {
        snapshot?.let { return it }
        return synchronized(this) {
            snapshot?.let { return it }
            loadSnapshot().also { snapshot = it }
        }
    }

    @WorkerThread
    private fun loadSnapshot(): Snapshot {
        val entityByName: Map<String, Entity> = entityDao.getAll().associateBy { it.name }
        val domainToEntityName: Map<String, String> =
            domainEntityDao.getAll().associate { it.domain to it.entityName }
        return Snapshot(
            domainToEntityName = domainToEntityName,
            entityByName = entityByName,
        )
    }
}

internal interface EntityLookupWithRefresh : EntityLookup, EntityLookupRefresher
