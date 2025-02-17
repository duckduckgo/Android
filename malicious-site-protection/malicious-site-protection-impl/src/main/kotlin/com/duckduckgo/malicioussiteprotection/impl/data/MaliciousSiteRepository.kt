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

package com.duckduckgo.malicioussiteprotection.impl.data

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.MALWARE
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.PHISHING
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSiteDao
import com.duckduckgo.malicioussiteprotection.impl.data.db.RevisionEntity
import com.duckduckgo.malicioussiteprotection.impl.data.network.FilterResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.FilterSetResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.HashPrefixResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.MaliciousSiteService
import com.duckduckgo.malicioussiteprotection.impl.models.Filter
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSet
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSetWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSetWithRevision.MalwareFilterSetWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSetWithRevision.PhishingFilterSetWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.HashPrefixesWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.HashPrefixesWithRevision.MalwareHashPrefixesWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.HashPrefixesWithRevision.PhishingHashPrefixesWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.Match
import com.duckduckgo.malicioussiteprotection.impl.models.Type
import com.duckduckgo.malicioussiteprotection.impl.models.Type.FILTER_SET
import com.duckduckgo.malicioussiteprotection.impl.models.Type.HASH_PREFIXES
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface MaliciousSiteRepository {
    suspend fun containsHashPrefix(hashPrefix: String): Boolean
    suspend fun getFilters(hash: String): List<FilterSet>?
    suspend fun matches(hashPrefix: String): List<Match>
    suspend fun loadFilters(): Result<Unit>
    suspend fun loadHashPrefixes(): Result<Unit>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealMaliciousSiteRepository @Inject constructor(
    private val maliciousSiteDao: MaliciousSiteDao,
    private val maliciousSiteService: MaliciousSiteService,
    private val dispatcherProvider: DispatcherProvider,
) : MaliciousSiteRepository {

    override suspend fun containsHashPrefix(hashPrefix: String): Boolean {
        return maliciousSiteDao.getHashPrefix(hashPrefix) != null
    }

    override suspend fun getFilters(hash: String): List<FilterSet>? {
        return maliciousSiteDao.getFilter(hash)?.groupBy { it.type }?.map { (type, filters) ->
            FilterSet(
                filters = filters.map { Filter(it.hash, it.regex) },
                feed = when (type) {
                    PHISHING.name -> PHISHING
                    MALWARE.name -> MALWARE
                    else -> throw IllegalArgumentException("Unknown feed $type")
                },
            )
        }
    }

    override suspend fun matches(hashPrefix: String): List<Match> {
        return try {
            maliciousSiteService.getMatches(hashPrefix).matches.mapNotNull {
                val feed = when (it.feed.uppercase()) {
                    PHISHING.name -> PHISHING
                    MALWARE.name -> MALWARE
                    else -> null
                }
                if (feed != null) {
                    Match(it.hostname, it.url, it.regex, it.hash, feed)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            listOf()
        }
    }

    override suspend fun loadFilters(): Result<Unit> {
        return loadDataOfType(FILTER_SET) { latestRevision, networkRevision, feed -> loadFilters(latestRevision, networkRevision, feed) }
    }

    override suspend fun loadHashPrefixes(): Result<Unit> {
        return loadDataOfType(HASH_PREFIXES) { latestRevision, networkRevision, feed -> loadHashPrefixes(latestRevision, networkRevision, feed) }
    }

    private suspend fun loadDataOfType(
        type: Type,
        loadData: suspend (revisions: List<RevisionEntity>, networkRevision: Int, feed: Feed) -> Unit,
    ): Result<Unit> {
        return withContext(dispatcherProvider.io()) {
            val networkRevision = maliciousSiteService.getRevision().revision

            val localRevisions = getLocalRevisions(type)

            val result = Feed.entries.fold(Result.success(Unit)) { acc, feed ->
                try {
                    loadData(localRevisions, networkRevision, feed)
                    acc
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            result
        }
    }

    private suspend fun <T> loadAndUpdateData(
        latestRevision: List<RevisionEntity>,
        networkRevision: Int,
        feed: Feed,
        getFunction: suspend (Int) -> T?,
        updateFunction: suspend (T?) -> Unit,
    ) {
        val revision = latestRevision.getRevisionForFeed(feed)
        val data: T? = if (networkRevision > revision) {
            getFunction(revision)
        } else {
            null
        }

        updateFunction(data)
    }

    private suspend fun loadFilters(
        latestRevision: List<RevisionEntity>,
        networkRevision: Int,
        feed: Feed,
    ) {
        loadAndUpdateData(
            latestRevision,
            networkRevision,
            feed,
            when (feed) {
                PHISHING -> maliciousSiteService::getPhishingFilterSet
                MALWARE -> maliciousSiteService::getMalwareFilterSet
            },
        ) { maliciousSiteDao.updateFilters(it?.toFilterSetWithRevision(feed)) }
    }

    private suspend fun loadHashPrefixes(
        latestRevision: List<RevisionEntity>,
        networkRevision: Int,
        feed: Feed,
    ) {
        loadAndUpdateData(
            latestRevision,
            networkRevision,
            feed,
            when (feed) {
                PHISHING -> maliciousSiteService::getPhishingHashPrefixes
                MALWARE -> maliciousSiteService::getMalwareHashPrefixes
            },
        ) { maliciousSiteDao.updateHashPrefixes(it?.toHashPrefixesWithRevision(feed)) }
    }

    private fun FilterSetResponse.toFilterSetWithRevision(feed: Feed): FilterSetWithRevision {
        val insert = insert.toFilterSet()
        val delete = delete.toFilterSet()
        return when (feed) {
            PHISHING -> PhishingFilterSetWithRevision(insert, delete, revision, replace)
            MALWARE -> MalwareFilterSetWithRevision(insert, delete, revision, replace)
        }
    }

    private fun HashPrefixResponse.toHashPrefixesWithRevision(feed: Feed): HashPrefixesWithRevision {
        return when (feed) {
            PHISHING -> PhishingHashPrefixesWithRevision(insert, delete, revision, replace)
            MALWARE -> MalwareHashPrefixesWithRevision(insert, delete, revision, replace)
        }
    }

    private suspend fun getLocalRevisions(type: Type) = (maliciousSiteDao.getLatestRevision()?.filter { it.type == type.name } ?: listOf())

    private fun Set<FilterResponse>.toFilterSet(): Set<Filter> {
        return map { Filter(it.hash, it.regex) }.toSet()
    }

    private fun List<RevisionEntity>.getRevisionForFeed(feed: Feed): Int {
        return firstOrNull { it.feed == feed.name }?.revision ?: 0
    }
}
