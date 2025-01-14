/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.malicioussiteprotection.impl.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSetWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.HashPrefixesWithRevision
import timber.log.Timber

@Dao
interface MaliciousSiteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRevision(revision: RevisionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHashPrefixes(items: List<HashPrefixEntity>)

    @Query("DELETE FROM hash_prefixes")
    suspend fun deleteHashPrefixes()

    @Query("DELETE FROM filters")
    suspend fun deleteFilters()

    @Query("DELETE FROM hash_prefixes WHERE type = :type")
    suspend fun deleteHashPrefixes(type: String)

    @Query("DELETE FROM hash_prefixes WHERE hashPrefix = :hashPrefix AND type = :type")
    suspend fun deleteHashPrefix(
        hashPrefix: String,
        type: String,
    )

    @Query("DELETE FROM filters WHERE hash = :hash AND type = :type")
    suspend fun deleteFilter(
        hash: String,
        type: String,
    )

    @Query("DELETE FROM filters WHERE type = :type")
    suspend fun deleteFilters(type: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilters(items: Set<FilterEntity>)

    @Query("SELECT * FROM revisions")
    suspend fun getLatestRevision(): List<RevisionEntity>?

    @Query("SELECT * FROM revisions WHERE feed = :feed AND type = :type")
    suspend fun getLatestRevision(feed: String, type: String): RevisionEntity?

    @Query("DELETE FROM revisions")
    suspend fun deleteRevisions()

    @Query("SELECT * FROM hash_prefixes WHERE hashPrefix = :hashPrefix")
    suspend fun getHashPrefix(hashPrefix: String): HashPrefixEntity?

    @Query("SELECT * FROM filters WHERE hash = :hash")
    suspend fun getFilter(hash: String): List<FilterEntity>?

    @Transaction
    suspend fun updateHashPrefixes(
        hashPrefixes: HashPrefixesWithRevision?,
    ) {
        hashPrefixes ?: return

        val currentLocalRevision = getLatestRevision(feed = hashPrefixes.feed.name, type = hashPrefixes.type.name)?.revision ?: 0
        val newRevision = hashPrefixes.takeIf { it.revision > currentLocalRevision }?.apply {
            Timber.d("Cris. Updating hash prefixes")
            if (hashPrefixes.replace) {
                deleteHashPrefixes(hashPrefixes.feed.name)
            } else {
                hashPrefixes.delete.forEach {
                    deleteHashPrefix(it, hashPrefixes.feed.name)
                }
            }
            insertHashPrefixes(hashPrefixes.insert.map { HashPrefixEntity(hashPrefix = it, type = hashPrefixes.feed.name) })
        }?.revision ?: currentLocalRevision
        if (currentLocalRevision != newRevision) {
            insertRevision(RevisionEntity(feed = hashPrefixes.feed.name, type = hashPrefixes.type.name, revision = newRevision))
        }
    }

    @Transaction
    suspend fun updateFilters(
        filterSet: FilterSetWithRevision?,
    ) {
        filterSet ?: return

        val currentLocalRevision = getLatestRevision(feed = filterSet.feed.name, type = filterSet.type.name)?.revision ?: 0
        val newRevision = filterSet.takeIf { it.revision > currentLocalRevision }?.apply {
            Timber.d("Cris. Updating filters")
            if (filterSet.replace) {
                deleteFilters(filterSet.feed.name)
            } else {
                filterSet.delete.forEach {
                    deleteFilter(it.hash, filterSet.feed.name)
                }
            }
            insertFilters(filterSet.insert.map { FilterEntity(it.hash, it.regex, type = filterSet.feed.name) }.toSet())
        }?.revision ?: currentLocalRevision
        if (currentLocalRevision != newRevision) {
            insertRevision(RevisionEntity(feed = filterSet.feed.name, type = filterSet.type.name, revision = newRevision))
        }
    }
}
