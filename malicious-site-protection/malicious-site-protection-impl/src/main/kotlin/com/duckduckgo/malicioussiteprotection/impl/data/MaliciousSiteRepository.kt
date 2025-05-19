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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.MALWARE
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.PHISHING
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.SCAM
import com.duckduckgo.malicioussiteprotection.impl.MaliciousSitePixelName.MALICIOUS_SITE_CLIENT_TIMEOUT
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSiteDao
import com.duckduckgo.malicioussiteprotection.impl.data.db.RevisionEntity
import com.duckduckgo.malicioussiteprotection.impl.data.network.FilterResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.FilterSetResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.HashPrefixResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.MaliciousSiteDatasetService
import com.duckduckgo.malicioussiteprotection.impl.data.network.MaliciousSiteService
import com.duckduckgo.malicioussiteprotection.impl.domain.WriteInProgressException
import com.duckduckgo.malicioussiteprotection.impl.models.Filter
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSet
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSetWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSetWithRevision.MalwareFilterSetWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSetWithRevision.PhishingFilterSetWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSetWithRevision.ScamFilterSetWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.HashPrefixesWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.HashPrefixesWithRevision.MalwareHashPrefixesWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.HashPrefixesWithRevision.PhishingHashPrefixesWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.HashPrefixesWithRevision.ScamHashPrefixesWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.Match
import com.duckduckgo.malicioussiteprotection.impl.models.MatchesResult
import com.duckduckgo.malicioussiteprotection.impl.models.Type
import com.duckduckgo.malicioussiteprotection.impl.models.Type.FILTER_SET
import com.duckduckgo.malicioussiteprotection.impl.models.Type.HASH_PREFIXES
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

interface MaliciousSiteRepository {
    /**
     * @throws WriteInProgressException if a write is in progress
     */
    suspend fun getFeedForHashPrefix(hashPrefix: String): Feed?

    /**
     * @throws WriteInProgressException if a write is in progress
     */
    suspend fun getFilters(hash: String): FilterSet?
    suspend fun matches(hashPrefix: String): MatchesResult
    suspend fun loadFilters(vararg feeds: Feed): Result<Unit>
    suspend fun loadHashPrefixes(vararg feeds: Feed): Result<Unit>
}

private const val MATCHES_ENDPOINT_TIMEOUT = 5000L

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealMaliciousSiteRepository @Inject constructor(
    private val maliciousSiteDao: MaliciousSiteDao,
    private val maliciousSiteService: MaliciousSiteService,
    private val maliciousSiteDatasetService: MaliciousSiteDatasetService,
    private val dispatcherProvider: DispatcherProvider,
    private val pixels: Pixel,
) : MaliciousSiteRepository {

    private val writeMutex = Mutex()

    override suspend fun getFeedForHashPrefix(hashPrefix: String): Feed? {
        return withContext(dispatcherProvider.io()) {
            if (writeMutex.isLocked) {
                throw WriteInProgressException()
            }
            maliciousSiteDao.getHashPrefix(hashPrefix)?.type?.let {
                when (it) {
                    PHISHING.name -> PHISHING
                    MALWARE.name -> MALWARE
                    SCAM.name -> SCAM
                    else -> null
                }
            }
        }
    }

    override suspend fun getFilters(hash: String): FilterSet? {
        return withContext(dispatcherProvider.io()) {
            if (writeMutex.isLocked) {
                throw WriteInProgressException()
            }
            maliciousSiteDao.getFilter(hash)?.let {
                FilterSet(
                    filters = Filter(it.hash, it.regex),
                    feed = when (it.type) {
                        PHISHING.name -> PHISHING
                        MALWARE.name -> MALWARE
                        SCAM.name -> SCAM
                        else -> return@let null
                    },
                )
            }
        }
    }

    override suspend fun matches(hashPrefix: String): MatchesResult {
        return try {
            withContext(dispatcherProvider.io()) {
                withTimeout(MATCHES_ENDPOINT_TIMEOUT) {
                    maliciousSiteService.getMatches(hashPrefix).matches.mapNotNull {
                        val feed = when (it.feed.uppercase()) {
                            PHISHING.name -> PHISHING
                            MALWARE.name -> MALWARE
                            SCAM.name -> SCAM
                            else -> null
                        }
                        if (feed != null) {
                            Match(it.hostname, it.url, it.regex, it.hash, feed)
                        } else {
                            null
                        }
                    }
                }.let { MatchesResult.Result(it) }
            }
        } catch (e: TimeoutCancellationException) {
            pixels.fire(MALICIOUS_SITE_CLIENT_TIMEOUT)
            MatchesResult.Ignored
        } catch (e: SocketTimeoutException) {
            pixels.fire(MALICIOUS_SITE_CLIENT_TIMEOUT)
            MatchesResult.Ignored
        } catch (e: Exception) {
            MatchesResult.Ignored
        }
    }

    override suspend fun loadFilters(vararg feeds: Feed): Result<Unit> {
        return loadDataOfType(FILTER_SET) {
                localRevisions, networkRevision ->
            feeds.forEach { feed ->
                loadFilters(localRevisions, networkRevision, feed)
            }
        }
    }

    override suspend fun loadHashPrefixes(vararg feeds: Feed): Result<Unit> {
        return loadDataOfType(HASH_PREFIXES) {
                localRevisions, networkRevision ->
            feeds.forEach { feed ->
                loadHashPrefixes(localRevisions, networkRevision, feed)
            }
        }
    }

    private suspend fun loadDataOfType(
        type: Type,
        loadData: suspend (revisions: List<RevisionEntity>, networkRevision: Int) -> Unit,
    ): Result<Unit> {
        return withContext(dispatcherProvider.io()) {
            val networkRevision = maliciousSiteService.getRevision().revision
            val localRevisions = writeMutex.withLock { getLocalRevisions(type) }

            try {
                loadData(localRevisions, networkRevision)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
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
        val data = if (networkRevision > revision) {
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
                PHISHING -> maliciousSiteDatasetService::getPhishingFilterSet
                MALWARE -> maliciousSiteDatasetService::getMalwareFilterSet
                SCAM -> maliciousSiteDatasetService::getScamFilterSet
            },
        ) {
            writeMutex.withLock {
                maliciousSiteDao.updateFilters(it?.toFilterSetWithRevision(feed))
            }
        }
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
                PHISHING -> maliciousSiteDatasetService::getPhishingHashPrefixes
                MALWARE -> maliciousSiteDatasetService::getMalwareHashPrefixes
                SCAM -> maliciousSiteDatasetService::getScamHashPrefixes
            },
        ) {
            writeMutex.withLock {
                maliciousSiteDao.updateHashPrefixes(it?.toHashPrefixesWithRevision(feed))
            }
        }
    }

    private fun FilterSetResponse.toFilterSetWithRevision(feed: Feed): FilterSetWithRevision {
        val insert = insert.toFilterSet()
        val delete = delete.toFilterSet()
        return when (feed) {
            PHISHING -> PhishingFilterSetWithRevision(insert, delete, revision, replace)
            MALWARE -> MalwareFilterSetWithRevision(insert, delete, revision, replace)
            SCAM -> ScamFilterSetWithRevision(insert, delete, revision, replace)
        }
    }

    private fun HashPrefixResponse.toHashPrefixesWithRevision(feed: Feed): HashPrefixesWithRevision {
        return when (feed) {
            PHISHING -> PhishingHashPrefixesWithRevision(insert, delete, revision, replace)
            MALWARE -> MalwareHashPrefixesWithRevision(insert, delete, revision, replace)
            SCAM -> ScamHashPrefixesWithRevision(insert, delete, revision, replace)
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
