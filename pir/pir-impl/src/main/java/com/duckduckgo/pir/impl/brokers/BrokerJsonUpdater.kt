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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.service.DbpService.PirMainConfig
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirRepository.BrokerJson
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

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
    private val bundledBrokerDataLoader: BundledBrokerDataLoader,
    private val pixelSender: PirPixelSender,
) : BrokerJsonUpdater {

    /**
     * Process:
     * - Main config is requested with current etag. If config is not modified, we don't do anything.
     * - If active brokers AND all json etag did not change, we don't do anything.
     * - If active brokers and/or any json etag has changed, we update the etags db.
     * - If any json etag changed, we download the json zip file.
     * - We extract the json files from the zip. For every json etag that changed, we parse the json file and update the data stored locally.
     * - If the network update fails (getMainConfig or downloadBrokerData), we fall back to loading broker JSONs from bundled raw resources,
     *   but only if no broker data has been stored previously.
     * - Returns true if either the network update or the bundled fallback succeeded. Returns false only if both fail.
     * https://app.asana.com/0/1203581873609357/1207997441358507
     */
    override suspend fun update(): Boolean = withContext(dispatcherProvider.io()) {
        if (!pirRepository.isRepositoryAvailable()) return@withContext false

        val networkUpdateSucceeded = runNetworkUpdate()

        return@withContext if (!networkUpdateSucceeded) {
            if (pirRepository.getStoredBrokersCount() == 0) {
                logcat { "PIR-update: Network update failed and no broker data stored, loading bundled broker data" }
                runCatching { bundledBrokerDataLoader.loadBundledBrokerData() }
                    .onFailure { logcat(ERROR) { "PIR-update: Bundled broker load failed: $it" } }
                    .isSuccess
            } else {
                logcat { "PIR-update: Network update failed but brokers already seeded from network, skipping bundle" }
                return@withContext true
            }
        } else {
            true
        }
    }

    private suspend fun runNetworkUpdate(): Boolean {
        return kotlin.runCatching {
            confirmEtagIntegrity()
            dbpService.getMainConfig(pirRepository.getCurrentMainEtag()).also { response ->
                logcat { "PIR-update: Main config result $response." }
                when {
                    response.code() == 304 -> {
                        logcat { "PIR-update: Main config did not change, nothing to do here" }
                    }

                    response.isSuccessful -> {
                        logcat { "PIR-update: Main config is new." }
                        response.body()?.let { config ->
                            logcat { "PIR-update: Main config $config." }
                            try {
                                checkUpdatesFromMainConfig(config)
                                pirRepository.updateMainEtag(config.etag)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                logcat(ERROR) { "PIR-update: Failed to download broker json files: $e" }
                                val message = e.asLog().sanitize() ?: e.message ?: "Unknown error"
                                pixelSender.reportDownloadBrokerJsonFailure(message)
                                return false
                            }
                        }
                    }

                    else -> {
                        logcat(ERROR) { "PIR-update: Failed to get mainconfig ${response.code()}: ${response.message()}" }
                        pixelSender.reportDownloadMainConfigBEFailure(response.code().toString())
                        return false
                    }
                }
            }
            true
        }.getOrElse {
            logcat(ERROR) { "PIR-update: Json update failed to complete due to: $it" }
            val message = it.asLog().sanitize() ?: it.message ?: "Unknown error"
            pixelSender.reportDownloadMainConfigFailure(message)
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
        val existingEtags = pirRepository.getAllLocalBrokerJsons()
        val jsonEtagsFromConfig = pirMainConfig.jsonEtags.current.map {
            BrokerJson(
                fileName = it.key,
                etag = it.value,
            )
        }

        val updatedJsons = jsonEtagsFromConfig.toSet() - existingEtags.toSet()

        if (updatedJsons.isNotEmpty()) {
            logcat { "PIR-update: Downloading updated broker json files: $updatedJsons" }
            brokerDataDownloader.downloadBrokerData(updatedJsons.map { it.fileName })
        } else {
            logcat { "PIR-update: No broker json files to update." }
        }

        pirRepository.updateBrokerJsons(jsonEtagsFromConfig)
    }

    private fun String.sanitize(): String? {
        // if we fail for whatever reason, we don't include the stack trace
        return runCatching {
            val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
            val phoneRegex = Regex("\\b(?:\\d[\\s()-]?){6,14}\\b") // This regex matches common phone number formats
            val phoneRegex2 = Regex("\\b\\+?\\d[- (]*\\d{3}[- )]*\\d{3}[- ]*\\d{4}\\b") // enhanced to redact also other phone number formats
            val urlRegex = Regex("\\b(?:https?://|www\\.)\\S+\\b")
            val ipv4Regex = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

            var sanitizedStackTrace = this
            sanitizedStackTrace = sanitizedStackTrace.replace(urlRegex, "[REDACTED_URL]")
            sanitizedStackTrace = sanitizedStackTrace.replace(emailRegex, "[REDACTED_EMAIL]")
            sanitizedStackTrace = sanitizedStackTrace.replace(phoneRegex2, "[REDACTED_PHONE]")
            sanitizedStackTrace = sanitizedStackTrace.replace(phoneRegex, "[REDACTED_PHONE]")
            sanitizedStackTrace = sanitizedStackTrace.replace(ipv4Regex, "[REDACTED_IPV4]")

            return sanitizedStackTrace
        }.getOrNull()
    }
}
