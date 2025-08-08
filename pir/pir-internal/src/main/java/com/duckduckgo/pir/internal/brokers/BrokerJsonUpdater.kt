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

package com.duckduckgo.pir.internal.brokers

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.service.DbpService
import com.duckduckgo.pir.internal.service.DbpService.PirMainConfig
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.PirRepository.BrokerJson
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat

interface BrokerJsonUpdater {
    /**
     * This method initiates the update process for broker json files.
     *
     * @return if the update was completed successfully with no errors
     */
    suspend fun update(): Boolean
}

@ContributesBinding(AppScope::class)
class RealBrokerJsonUpdater @Inject constructor(
    private val dbpService: DbpService,
    private val dispatcherProvider: DispatcherProvider,
    private val pirRepository: PirRepository,
    private val brokerDataDownloader: BrokerDataDownloader,
) : BrokerJsonUpdater {

    /**
     * Process:
     * - Main config is requested with current etag. If config is not modified, we don't do anything.
     * - If active brokers AND all json etag did not change, we don't do anything.
     * - If active brokers and/or any json etag has changed, we update the etags db.
     * - If any json etag changed, we download the json zip file.
     * - We extract the json files from the zip. For every json etag that changed, we parse the json file and update the data stored locally.
     * https://app.asana.com/0/1203581873609357/1207997441358507
     */
    override suspend fun update(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext kotlin.runCatching {
            confirmEtagIntegrity()
            dbpService.getMainConfig(pirRepository.getCurrentMainEtag()).also {
                logcat { "PIR-update: Main config result $it." }
                if (it.code() == 304) {
                    logcat { "PIR-update: Main config did not change, nothing to do here" }
                } else if (it.isSuccessful) {
                    logcat { "PIR-update: Main config is new." }
                    it.body()?.let { config ->
                        logcat { "PIR-update: Main config $config." }
                        checkUpdatesFromMainConfig(config)
                        pirRepository.updateMainEtag(config.etag)
                    }
                } else {
                    logcat(ERROR) { "PIR-update: Failed to get mainconfig ${it.code()}: ${it.message()}" }
                    return@withContext false
                }
            }
            true
        }.getOrElse {
            logcat(ERROR) { "PIR-update: Json update failed to complete due to: $it" }
            false
        }
    }

    private suspend fun confirmEtagIntegrity() {
        val storedEtag = pirRepository.getCurrentMainEtag()
        if (storedEtag != null) {
            val count = pirRepository.getStoredBrokersCount()
            if (count == 0) {
                // We clear etag because for some reason the store data in our db is not available anymore, making the etag unusable.
                pirRepository.updateMainEtag(null)
            }
        }
    }

    private suspend fun checkUpdatesFromMainConfig(pirMainConfig: PirMainConfig) {
        val currentBrokerJson = pirRepository.getAllLocalBrokerJsons()
        val newActiveBrokers = pirMainConfig.activeBrokers
        val newBrokerJson = pirMainConfig.jsonEtags.current.map {
            BrokerJson(
                fileName = it.key,
                etag = it.value,
            )
        }.associateWith { newActiveBrokers.contains(it.fileName) }

        val shouldDownloadJson = currentBrokerJson.keys != newBrokerJson.keys // Broker json changed
        val shouldUpdateLocalEtags = newBrokerJson.entries != currentBrokerJson.entries // broker state / active state changed

        if (shouldDownloadJson) {
            val diff = newBrokerJson.keys - currentBrokerJson.keys
            brokerDataDownloader.downloadBrokerData(diff.map { it.fileName })
        }

        // We update stored json etags once the actual json data have been stored.
        if (shouldUpdateLocalEtags) {
            pirRepository.updateBrokerJsons(newBrokerJson)
        }
    }
}
