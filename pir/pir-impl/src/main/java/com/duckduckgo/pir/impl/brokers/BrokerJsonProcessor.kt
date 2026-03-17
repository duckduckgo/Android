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

package com.duckduckgo.pir.impl.brokers

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.service.DbpService.PirJsonBroker
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import logcat.LogPriority.ERROR
import logcat.logcat
import okio.Source
import okio.buffer
import javax.inject.Inject

interface BrokerJsonProcessor {
    fun parseBroker(source: Source): PirJsonBroker?
    suspend fun processAndStoreBroker(
        fileName: String,
        source: Source,
    )
}

/**
 * Handles the common logic for parsing a broker JSON, persisting the parsed data via
 * [PirRepository], and reporting success/failure pixels.
 */
@ContributesBinding(AppScope::class)
class RealBrokerJsonProcessor @Inject constructor(
    private val pirRepository: PirRepository,
    private val pirPixelSender: PirPixelSender,
) : BrokerJsonProcessor {
    private val brokerAdapter by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .add(StepsAsStringAdapter())
            .build()
            .adapter(PirJsonBroker::class.java)
    }

    override fun parseBroker(source: Source): PirJsonBroker? =
        runCatching { source.buffer().use { brokerAdapter.fromJson(it) } }
            .onFailure { logcat(ERROR) { "PIR-update: Failed to parse broker JSON: $it" } }
            .getOrNull()

    override suspend fun processAndStoreBroker(
        fileName: String,
        source: Source,
    ) {
        processAndStoreBroker(fileName, parseBroker(source))
    }

    private suspend fun processAndStoreBroker(
        fileName: String,
        broker: PirJsonBroker?,
    ) {
        if (broker != null) {
            try {
                val storedVersion = pirRepository.getBrokerForName(broker.name)?.version
                if (storedVersion == broker.version) {
                    logcat { "PIR-update: $fileName already at version ${broker.version}, skipping" }
                    return
                }
                logcat { "PIR-update: $fileName version changed ($storedVersion → ${broker.version}), updating" }
                pirRepository.updateBrokerData(fileName, broker)
                pirPixelSender.reportUpdateBrokerJsonSuccess(
                    brokerJsonFileName = fileName,
                    removedAtMs = broker.removedAt ?: 0L,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                pirPixelSender.reportUpdateBrokerJsonFailure(
                    brokerJsonFileName = fileName,
                    removedAtMs = broker.removedAt ?: 0L,
                )
            }
        } else {
            pirPixelSender.reportUpdateBrokerJsonFailure(
                brokerJsonFileName = fileName,
                removedAtMs = 0L,
            )
        }
    }
}
