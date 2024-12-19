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
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.duckduckgo.malicioussiteprotection.impl.data.Filter
import kotlin.math.max

@Dao
interface MaliciousSiteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRevision(revision: RevisionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHashPrefixes(items: List<HashPrefixEntity>)

    @Delete(HashPrefixEntity::class)
    suspend fun deleteHashPrefixes()

    @Delete(FilterEntity::class)
    suspend fun deleteFilters()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilters(items: Set<FilterEntity>)

    @Query("SELECT * FROM revisions LIMIT 1")
    suspend fun getLatestRevision(): RevisionEntity?

    @Query("DELETE FROM revisions")
    suspend fun deleteRevisions()

    @Query("SELECT * FROM hash_prefixes WHERE hashPrefix = :hashPrefix")
    suspend fun getHashPrefix(hashPrefix: String): HashPrefixEntity?

    @Query("SELECT * FROM filters WHERE hash = :hash")
    suspend fun getFilter(hash: String): FilterEntity?

    @Transaction
    suspend fun insertData(
        phishingFilterSetRevision: Int?,
        malwareFilterSetRevision: Int?,
        phishingHashPrefixesRevision: Int?,
        malwareHashPrefixesRevision: Int?,
        phishingHashPrefixes: Set<String>,
        phishingFilterSet: Set<Filter>,
        malwareHashPrefixes: Set<String>,
        malwareFilterSet: Set<Filter>,
    ) {
        val lastRevision = getLatestRevision()
        deleteRevisions()
        deleteFilters()
        deleteHashPrefixes()

        insertRevision(
            RevisionEntity(
                phishingHashPrefixesRevision = max(lastRevision?.phishingHashPrefixesRevision ?: 0, phishingHashPrefixesRevision ?: 0),
                malwareHashPrefixesRevision = max(lastRevision?.malwareHashPrefixesRevision ?: 0, malwareHashPrefixesRevision ?: 0),
                phishingFiltersRevision = max(lastRevision?.phishingFiltersRevision ?: 0, phishingFilterSetRevision ?: 0),
                malwareFiltersRevision = max(lastRevision?.malwareFiltersRevision ?: 0, malwareFilterSetRevision ?: 0),
            ),
        )
        insertHashPrefixes(phishingHashPrefixes.map { HashPrefixEntity(hashPrefix = it, type = "phishing") })
        insertFilters(phishingFilterSet.map { FilterEntity(it.hash, it.regex, type = "phishing") }.toSet())
        insertHashPrefixes(malwareHashPrefixes.map { HashPrefixEntity(hashPrefix = it, type = "malware") })
        insertFilters(malwareFilterSet.map { FilterEntity(it.hash, it.regex, type = "malware") }.toSet())
    }
}
