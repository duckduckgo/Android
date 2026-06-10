/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.domain

import com.duckduckgo.adblocking.impl.AdBlockingExtensionRepository
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_SCRIPTLETS_FETCH_ERROR_COUNT
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_SCRIPTLETS_FETCH_ERROR_DAILY
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_SCRIPTLETS_INSTALLED_COUNT
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_SCRIPTLETS_INSTALLED_DAILY
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_SCRIPTLETS_INSTALL_ERROR_COUNT
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_SCRIPTLETS_INSTALL_ERROR_DAILY
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_SCRIPTLETS_VALIDATION_ERROR_COUNT
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_SCRIPTLETS_VALIDATION_ERROR_DAILY
import com.duckduckgo.adblocking.impl.ScriptletDownloader
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionSettings
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

sealed interface ScriptletUpdateResult {
    data object Success : ScriptletUpdateResult
    data object Retry : ScriptletUpdateResult
}

interface ScriptletUpdater {
    suspend fun update(settings: AdBlockingExtensionSettings): ScriptletUpdateResult
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealScriptletUpdater @Inject constructor(
    private val repository: AdBlockingExtensionRepository,
    private val downloader: ScriptletDownloader,
    private val validator: ScriptletSignatureValidator,
    private val pixel: Pixel,
) : ScriptletUpdater {

    override suspend fun update(settings: AdBlockingExtensionSettings): ScriptletUpdateResult {
        val storedVersion = repository.getStoredVersion()
        if (settings.version == storedVersion) {
            logcat { "Version matches stored. Skipping" }
            return ScriptletUpdateResult.Success
        }

        val downloaded = try {
            coroutineScope {
                settings.scriptlets.map { (name, entry) ->
                    async {
                        logcat { "Downloading ${entry.url}" }
                        val bytes = downloader.download(entry.url).getOrElse {
                            logcat(WARN) { "ScriptletUpdater: download failed for $name: ${it.message}" }
                            throw ScriptletFailure.Fetch()
                        }

                        when (val validationResult = validator.validate(bytes, entry.signature)) {
                            ScriptletValidationResult.Valid -> if (bytes.isEmpty()) {
                                logcat { "ScriptletUpdater: skipping empty scriptlet $name" }
                                null
                            } else {
                                name to bytes
                            }
                            is ScriptletValidationResult.Invalid -> {
                                logcat(WARN) { "ScriptletUpdater: validation failed for $name: $validationResult" }
                                throw ScriptletFailure.Validation(validationResult::class.simpleName ?: "Unknown")
                            }
                        }
                    }
                }.awaitAll().filterNotNull().toMap()
            }
        } catch (failure: ScriptletFailure) {
            // Multiple scriptlets download/validate in parallel, but coroutineScope + awaitAll
            // propagate only the first failure (siblings are cancelled), so this fires exactly once.
            when (failure) {
                is ScriptletFailure.Fetch -> {
                    pixel.enqueueFire(AD_BLOCKING_SCRIPTLETS_FETCH_ERROR_DAILY, type = Pixel.PixelType.Daily())
                    pixel.enqueueFire(AD_BLOCKING_SCRIPTLETS_FETCH_ERROR_COUNT)
                }
                is ScriptletFailure.Validation -> {
                    val params = mapOf(PARAM_REASON to failure.reason)
                    pixel.enqueueFire(AD_BLOCKING_SCRIPTLETS_VALIDATION_ERROR_DAILY, parameters = params, type = Pixel.PixelType.Daily())
                    pixel.enqueueFire(AD_BLOCKING_SCRIPTLETS_VALIDATION_ERROR_COUNT, parameters = params)
                }
            }
            return ScriptletUpdateResult.Retry
        }

        logcat { "Storing scriptlets" }
        try {
            repository.storeScriptlets(settings.version, downloaded)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logcat(WARN) { "ScriptletUpdater: storing scriptlets failed: ${e.message}" }
            pixel.enqueueFire(AD_BLOCKING_SCRIPTLETS_INSTALL_ERROR_DAILY, type = Pixel.PixelType.Daily())
            pixel.enqueueFire(AD_BLOCKING_SCRIPTLETS_INSTALL_ERROR_COUNT)
            return ScriptletUpdateResult.Retry
        }

        val installedParams = mapOf(PARAM_VERSION to settings.version)
        pixel.enqueueFire(AD_BLOCKING_SCRIPTLETS_INSTALLED_DAILY, parameters = installedParams, type = Pixel.PixelType.Daily())
        pixel.enqueueFire(AD_BLOCKING_SCRIPTLETS_INSTALLED_COUNT, parameters = installedParams)
        return ScriptletUpdateResult.Success
    }

    private sealed class ScriptletFailure : RuntimeException() {
        class Fetch : ScriptletFailure()
        data class Validation(val reason: String) : ScriptletFailure()
    }

    companion object {
        private const val PARAM_VERSION = "version"
        private const val PARAM_REASON = "reason"
    }
}
