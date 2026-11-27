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
import com.duckduckgo.app.trackerdetection.db.TrackerDetectionDatabase
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@SingleIn(AppScope::class)
class CachedTdsEntityLookup @Inject constructor(
    private val database: TrackerDetectionDatabase,
) : EntityLookupWithRefresh {

    private data class Snapshot(
        val version: Long,
        val domainToEntityName: Map<String, String>,
        val entityByName: Map<String, Entity>,
    )

    private val snapshot = AtomicReference<Snapshot?>(null)
    private val lastVersion = AtomicLong()

    // Takes no lock: TDS downloads call this while holding a database transaction, and the lazy load
    // below holds this object's monitor while waiting for the database.
    override fun refresh() {
        publish(loadSnapshot())
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
        snapshot.get()?.let { return it }
        return synchronized(this) {
            snapshot.get() ?: publish(loadSnapshot())
        }
    }

    private fun publish(loaded: Snapshot): Snapshot =
        snapshot.updateAndGet { current -> if (current == null || loaded.version > current.version) loaded else current }!!

    // The version is taken inside the transaction. Transactions on this database run one at a time, so a
    // higher version always saw the same or newer tables, whichever load finishes last.
    @WorkerThread
    private fun loadSnapshot(): Snapshot = database.runInTransaction(
        Callable {
            val version = lastVersion.incrementAndGet()
            Snapshot(
                version = version,
                entityByName = database.tdsEntityDao().getAll().associateBy { it.name },
                domainToEntityName = database.tdsDomainEntityDao().getAll().associate { it.domain to it.entityName },
            )
        },
    )
}

internal interface EntityLookupWithRefresh : EntityLookup, EntityLookupRefresher
