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
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSetWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.HashPrefixesWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.Type

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

    @Query("SELECT * FROM hash_prefixes WHERE hashPrefix = :prefix LIMIT 1")
    suspend fun getHashPrefix(prefix: String): HashPrefixEntity?

    @Query("SELECT * FROM filters WHERE hash = :hash LIMIT 1")
    suspend fun getFilter(hash: String): FilterEntity?

    @Transaction
    suspend fun updateHashPrefixes(
        hashPrefixes: HashPrefixesWithRevision?,
    ) {
        hashPrefixes ?: return

        updateData(
            type = hashPrefixes.type,
            feed = hashPrefixes.feed,
            updateWithRevision = { localRevision: Int -> updateHashPrefixes(hashPrefixes, localRevision) },
        )
    }

    @Transaction
    suspend fun updateFilters(
        filterSet: FilterSetWithRevision?,
    ) {
        filterSet ?: return

        updateData(
            type = filterSet.type,
            feed = filterSet.feed,
            updateWithRevision = { localRevision: Int -> updateFilters(filterSet, localRevision) },
        )
    }

    private suspend fun updateFilters(filterSet: FilterSetWithRevision, currentLocalRevision: Int): Int {
        return filterSet.takeIf { isNewRevisionNewerThanLocal(it.revision, currentLocalRevision) }?.apply {
            updateData(
                replace = filterSet.replace,
                deleteAll = { deleteFilters(filterSet.feed.name) },
                deleteItem = { deleteFilter(it.hash, filterSet.feed.name) },
                insertItems = { insertFilters(filterSet.insert.map { FilterEntity(it.hash, it.regex, type = filterSet.feed.name) }.toSet()) },
                itemsToDelete = filterSet.delete,
            )
        }?.revision ?: currentLocalRevision
    }

    private suspend fun updateHashPrefixes(hashPrefixes: HashPrefixesWithRevision, currentLocalRevision: Int): Int {
        return hashPrefixes.takeIf { isNewRevisionNewerThanLocal(it.revision, currentLocalRevision) }?.apply {
            updateData(
                replace = hashPrefixes.replace,
                deleteAll = { deleteHashPrefixes(hashPrefixes.feed.name) },
                deleteItem = { deleteHashPrefix(it, hashPrefixes.feed.name) },
                insertItems = { insertHashPrefixes(hashPrefixes.insert.map { HashPrefixEntity(hashPrefix = it, type = hashPrefixes.feed.name) }) },
                itemsToDelete = hashPrefixes.delete,
            )
        }?.revision ?: currentLocalRevision
    }

    private suspend fun updateData(
        type: Type,
        feed: Feed,
        updateWithRevision: suspend (Int) -> Int,
    ) {
        val currentLocalRevision = getLatestRevision(feed = feed, type = type)
        val newRevision = updateWithRevision(currentLocalRevision)
        if (currentLocalRevision != newRevision) {
            insertRevision(RevisionEntity(feed = feed.name, type = type.name, revision = newRevision))
        }
    }

    fun isNewRevisionNewerThanLocal(
        newRevision: Int,
        currentLocalRevision: Int,
    ) = newRevision > currentLocalRevision

    private suspend fun <T> updateData(
        replace: Boolean,
        deleteAll: suspend () -> Unit,
        deleteItem: suspend (T) -> Unit,
        insertItems: suspend () -> Unit,
        itemsToDelete: Set<T>,
    ) {
        if (replace) {
            deleteAll()
        } else {
            itemsToDelete.forEach { deleteItem(it) }
        }
        insertItems()
    }

    private suspend fun getLatestRevision(feed: Feed, type: Type): Int {
        return getLatestRevision(feed = feed.name, type = type.name)?.revision ?: 0
    }
}
