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

package com.duckduckgo.malicioussiteprotection.impl

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface MaliciousSiteRepository {
    suspend fun containsHashPrefix(hashPrefix: String): Boolean
    suspend fun getFilter(hash: String): Filter?
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealMaliciousSiteRepository @Inject constructor(
    private val dataProvider: MaliciousSiteProtectionEmbeddedDataProvider,
    private val maliciousSiteDao: MaliciousSiteDao,
    @IsMainProcess private val isMainProcess: Boolean,
    maliciousSiteProtectionFeature: MaliciousSiteProtectionFeature,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : MaliciousSiteRepository {

    init {
        if (isMainProcess) {
            appCoroutineScope.launch(dispatcherProvider.io()) {
                if (maliciousSiteProtectionFeature.self().isEnabled()) {
                    loadEmbeddedData()
                }
            }
        }
    }

    private suspend fun loadEmbeddedData() {
        val embeddedPhishingHashPrefixes = dataProvider.loadEmbeddedPhishingHashPrefixes()

        val embeddedPhishingFilterSet = dataProvider.loadEmbeddedPhishingFilterSet()

        val embeddedMalwareHashPrefixes = dataProvider.loadEmbeddedMalwareHashPrefixes()

        val embeddedMalwareFilterSet = dataProvider.loadEmbeddedMalwareFilterSet()

        // TODO (cbarreiro): Once we have the download scheduler, we should check the revision and update the data accordingly

        maliciousSiteDao.insertData(
            phishingFilterSetRevision = embeddedPhishingFilterSet?.revision,
            malwareFilterSetRevision = embeddedMalwareFilterSet?.revision,
            phishingHashPrefixesRevision = embeddedPhishingHashPrefixes?.revision,
            malwareHashPrefixesRevision = embeddedMalwareHashPrefixes?.revision,
            phishingHashPrefixes = embeddedPhishingHashPrefixes?.insert?.toSet() ?: setOf(),
            phishingFilterSet = embeddedPhishingFilterSet?.insert?.map { FilterEntity(it.hash, it.regex, type = "phishing") }?.toSet() ?: setOf(),
            malwareHashPrefixes = embeddedMalwareHashPrefixes?.insert?.toSet() ?: setOf(),
            malwareFilterSet = embeddedMalwareFilterSet?.insert?.map { FilterEntity(it.hash, it.regex, type = "malware") }?.toSet() ?: setOf(),
        )
    }

    override suspend fun containsHashPrefix(hashPrefix: String): Boolean {
        return maliciousSiteDao.getHashPrefix(hashPrefix) != null
    }

    override suspend fun getFilter(hash: String): Filter? {
        return maliciousSiteDao.getPhishingFilters().firstOrNull { it.hash == hash }?.let {
            Filter(it.hash, it.regex)
        }
    }
}
