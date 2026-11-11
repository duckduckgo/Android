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
import com.duckduckgo.pir.impl.PirUserUtils
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdateFailureReason.INVALID_JSON
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdateFailureReason.STORAGE_ERROR
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.service.DbpService.PirJsonBroker
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.CancellationException
import logcat.LogPriority.ERROR
import logcat.logcat
import okio.Source
import okio.buffer
import javax.inject.Inject

interface BrokerJsonProcessor {
    fun parseBroker(source: Source): PirJsonBroker?

    /**
     * Parses and validates a broker JSON and stores it, unless the stored broker is already at that version.
     * A broker JSON that can't be parsed, or has steps this app version can't execute, is not stored and the
     * previously stored version of the broker is kept.
     *
     * @return true if the stored broker is now at the version of this JSON, false if the JSON was rejected or
     *  couldn't be stored
     */
    suspend fun processAndStoreBroker(
        fileName: String,
        source: Source,
    ): Boolean
}

/**
 * Handles the common logic for parsing a broker JSON, persisting the parsed data via
 * [PirRepository], and reporting success/failure pixels.
 */
@ContributesBinding(AppScope::class)
class RealBrokerJsonProcessor @Inject constructor(
    private val pirRepository: PirRepository,
    private val pirPixelSender: PirPixelSender,
    private val pirUserUtils: PirUserUtils,
    private val brokerStepsValidator: BrokerStepsValidator,
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
    ): Boolean {
        val broker = parseBroker(source)
        if (broker == null) {
            reportFailure(fileName, removedAtMs = 0L, reason = INVALID_JSON)
            return false
        }

        brokerStepsValidator.validate(broker.steps)?.let { reason ->
            logcat(ERROR) { "PIR-update: Rejected $fileName version ${broker.version} ($reason), keeping the stored version" }
            reportFailure(fileName, removedAtMs = broker.removedAt ?: 0L, reason = reason)
            return false
        }

        val stored = try {
            val storedVersion = pirRepository.getBrokerForName(broker.name)?.version
            if (storedVersion == broker.version) {
                logcat { "PIR-update: $fileName already at version ${broker.version}, skipping" }
                return true
            }
            logcat { "PIR-update: $fileName version changed ($storedVersion → ${broker.version}), updating" }
            pirRepository.updateBrokerData(fileName, broker)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logcat(ERROR) { "PIR-update: Failed to store $fileName: $e" }
            false
        }

        if (stored) {
            pirPixelSender.reportUpdateBrokerJsonSuccess(
                brokerJsonFileName = fileName,
                removedAtMs = broker.removedAt ?: 0L,
                isFreeScan = isFreeScan(),
            )
        } else {
            reportFailure(fileName, removedAtMs = broker.removedAt ?: 0L, reason = STORAGE_ERROR)
        }
        return stored
    }

    private suspend fun reportFailure(
        fileName: String,
        removedAtMs: Long,
        reason: BrokerJsonUpdateFailureReason,
    ) {
        pirPixelSender.reportUpdateBrokerJsonFailure(
            brokerJsonFileName = fileName,
            removedAtMs = removedAtMs,
            isFreeScan = isFreeScan(),
            reason = reason,
        )
    }

    private suspend fun isFreeScan(): Boolean? = try {
        !pirUserUtils.hasPirSubscription()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logcat(ERROR) { "PIR-update: Failed to read the PIR subscription: $e" }
        null
    }
}
