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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSiteDao
import com.duckduckgo.malicioussiteprotection.impl.data.network.FilterSetResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.HashPrefixResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.MaliciousSiteService
import com.duckduckgo.malicioussiteprotection.impl.models.Feed.MALWARE
import com.duckduckgo.malicioussiteprotection.impl.models.Feed.PHISHING
import com.duckduckgo.malicioussiteprotection.impl.models.Filter
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSetWithRevision.MalwareFilterSetWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSetWithRevision.PhishingFilterSetWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.HashPrefixesWithRevision.MalwareHashPrefixesWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.HashPrefixesWithRevision.PhishingHashPrefixesWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.Match
import com.duckduckgo.malicioussiteprotection.impl.models.Type
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import timber.log.Timber

interface MaliciousSiteRepository {
    suspend fun containsHashPrefix(hashPrefix: String): Boolean
    suspend fun getFilters(hash: String): List<Filter>?
    suspend fun matches(hashPrefix: String): List<Match>
    suspend fun loadFilters()
    suspend fun loadHashPrefixes()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealMaliciousSiteRepository @Inject constructor(
    private val maliciousSiteDao: MaliciousSiteDao,
    private val maliciousSiteService: MaliciousSiteService,
) : MaliciousSiteRepository {

    override suspend fun loadFilters() {
        try {
            val networkRevision = maliciousSiteService.getRevision().revision

            (maliciousSiteDao.getLatestRevision()?.filter { it.type == Type.FILTER_SET.name } ?: listOf()).let { latestRevision ->
                val phishingFilterSetRevision = latestRevision.firstOrNull() { it.feed == PHISHING.name }?.revision ?: 0
                val phishingFilterSet: FilterSetResponse? = if (networkRevision > phishingFilterSetRevision) {
                    maliciousSiteService.getPhishingFilterSet(phishingFilterSetRevision)
                } else {
                    null
                }
                val malwareFilterSetRevision = latestRevision.firstOrNull() { it.feed == MALWARE.name }?.revision ?: 0
                val malwareFilterSet: FilterSetResponse? = if (networkRevision > malwareFilterSetRevision) {
                    maliciousSiteService.getMalwareFilterSet(malwareFilterSetRevision)
                } else {
                    null
                }

                maliciousSiteDao.updateFilters(
                    phishingFilterSet?.let {
                        PhishingFilterSetWithRevision(
                            it.insert.map { insert -> Filter(insert.hash, insert.regex) }.toSet(),
                            it.delete.map { delete -> Filter(delete.hash, delete.regex) }.toSet(),
                            it.revision,
                            it.replace,
                        )
                    },
                )
                maliciousSiteDao.updateFilters(
                    malwareFilterSet?.let {
                        MalwareFilterSetWithRevision(
                            it.insert.map { insert -> Filter(insert.hash, insert.regex) }.toSet(),
                            it.delete.map { delete -> Filter(delete.hash, delete.regex) }.toSet(),
                            it.revision,
                            it.replace,
                        )
                    },
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download malicious site protection list")
        }
    }

    override suspend fun loadHashPrefixes() {
        try {
            val networkRevision = maliciousSiteService.getRevision().revision

            (maliciousSiteDao.getLatestRevision()?.filter { it.type == Type.HASH_PREFIXES.name } ?: listOf()).let {
                val phishingHashPrefixesRevision = it.firstOrNull() { it.feed == PHISHING.name }?.revision ?: 0
                val phishingHashPrefixes: HashPrefixResponse? = if (networkRevision > phishingHashPrefixesRevision) {
                    maliciousSiteService.getPhishingHashPrefixes(phishingHashPrefixesRevision)
                } else {
                    null
                }
                val malwareHashPrefixesRevision = it.firstOrNull() { it.feed == MALWARE.name }?.revision ?: 0
                val malwareHashPrefixes: HashPrefixResponse? = if (networkRevision > malwareHashPrefixesRevision) {
                    maliciousSiteService.getMalwareHashPrefixes(malwareHashPrefixesRevision)
                } else {
                    null
                }

                maliciousSiteDao.updateHashPrefixes(
                    phishingHashPrefixes?.let { PhishingHashPrefixesWithRevision(it.insert, it.delete, it.revision, it.replace) },
                )
                maliciousSiteDao.updateHashPrefixes(
                    malwareHashPrefixes?.let { MalwareHashPrefixesWithRevision(it.insert, it.delete, it.revision, it.replace) },
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download malicious site protection list")
        }
    }

    override suspend fun containsHashPrefix(hashPrefix: String): Boolean {
        return maliciousSiteDao.getHashPrefix(hashPrefix) != null
    }

    override suspend fun getFilters(hash: String): List<Filter>? {
        return maliciousSiteDao.getFilter(hash)?.let {
            it.map {
                Filter(it.hash, it.regex)
            }
        }
    }

    override suspend fun matches(hashPrefix: String): List<Match> {
        return try {
            maliciousSiteService.getMatches(hashPrefix).matches.map {
                Match(it.hostname, it.url, it.regex, it.hash)
            }
        } catch (e: Exception) {
            listOf()
        }
    }
}
