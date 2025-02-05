/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.internal.store

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.pir.internal.service.DbpService.PirJsonBroker
import com.duckduckgo.pir.internal.store.PirRepository.BrokerJson
import com.duckduckgo.pir.internal.store.db.Broker
import com.duckduckgo.pir.internal.store.db.BrokerDao
import com.duckduckgo.pir.internal.store.db.BrokerJsonDao
import com.duckduckgo.pir.internal.store.db.BrokerJsonEtag
import com.duckduckgo.pir.internal.store.db.BrokerOptOut
import com.duckduckgo.pir.internal.store.db.BrokerScan
import com.duckduckgo.pir.internal.store.db.BrokerSchedulingConfig
import kotlinx.coroutines.withContext

interface PirRepository {
    suspend fun getCurrentMainEtag(): String?

    suspend fun updateMainEtag(etag: String)

    suspend fun updateBrokerJsons(brokers: Map<BrokerJson, Boolean>)

    suspend fun getAllLocalBrokerJsons(): Map<BrokerJson, Boolean>

    suspend fun getActiveBrokerJsons(): List<BrokerJson>

    suspend fun getEtagForFilename(fileName: String): String?

    suspend fun updateBrokerData(
        fileName: String,
        broker: PirJsonBroker,
    )

    data class BrokerJson(
        val fileName: String,
        val etag: String,
    )
}

class RealPirRepository(
    private val dispatcherProvider: DispatcherProvider,
    private val pirDataStore: PirDataStore,
    private val brokerJsonDao: BrokerJsonDao,
    private val brokerDao: BrokerDao,
) : PirRepository {
    override suspend fun getCurrentMainEtag(): String? = pirDataStore.mainConfigEtag

    override suspend fun updateMainEtag(etag: String) {
        pirDataStore.mainConfigEtag = etag
    }

    override suspend fun updateBrokerJsons(brokers: Map<BrokerJson, Boolean>) {
        withContext(dispatcherProvider.io()) {
            brokers.map {
                BrokerJsonEtag(
                    fileName = it.key.fileName,
                    etag = it.key.etag,
                    isActive = it.value,
                )
            }.also {
                brokerJsonDao.upsertAll(it)
            }
        }
    }

    override suspend fun getAllLocalBrokerJsons(): Map<BrokerJson, Boolean> = withContext(dispatcherProvider.io()) {
        return@withContext brokerJsonDao.getAllBrokers().associate {
            BrokerJson(
                fileName = it.fileName,
                etag = it.etag,
            ) to it.isActive
        }
    }

    override suspend fun getActiveBrokerJsons(): List<BrokerJson> = withContext(dispatcherProvider.io()) {
        return@withContext brokerJsonDao.getAllActiveBrokers().map { BrokerJson(fileName = it.fileName, etag = it.etag) }
    }

    override suspend fun getEtagForFilename(fileName: String): String? = withContext(dispatcherProvider.io()) {
        return@withContext brokerJsonDao.getEtag(fileName)
    }

    override suspend fun updateBrokerData(
        fileName: String,
        broker: PirJsonBroker,
    ) {
        withContext(dispatcherProvider.io()) {
            brokerDao.upsert(
                broker = Broker(
                    name = broker.name,
                    fileName = fileName,
                    url = broker.url,
                    version = broker.version,
                    parent = broker.parent,
                    addedDatetime = broker.addedDatetime,
                ),
                brokerScan = BrokerScan(
                    brokerName = broker.name,
                    stepsJson = broker.steps.first { it.contains("\"stepType\":\"scan\"") },
                ),
                brokerOptOut = BrokerOptOut(
                    brokerName = broker.name,
                    stepsJson = broker.steps.first { it.contains("\"stepType\":\"optOut\"") },
                    optOutUrl = broker.optOutUrl,
                ),
                schedulingConfig = BrokerSchedulingConfig(
                    brokerName = broker.name,
                    retryError = broker.schedulingConfig.retryError,
                    confirmOptOutScan = broker.schedulingConfig.confirmOptOutScan,
                    maintenanceScan = broker.schedulingConfig.maintenanceScan,
                    maxAttempts = broker.schedulingConfig.maxAttempts,
                ),
            )
        }
    }
}
