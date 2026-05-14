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
import com.duckduckgo.adblocking.impl.ScriptletDownloader
import com.duckduckgo.adblocking.impl.remoteconfig.ScriptletsSettings
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

sealed interface ScriptletUpdateResult {
    data object Success : ScriptletUpdateResult
    data object Retry : ScriptletUpdateResult
}

interface ScriptletUpdater {
    suspend fun update(settings: ScriptletsSettings): ScriptletUpdateResult
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealScriptletUpdater @Inject constructor(
    private val repository: AdBlockingExtensionRepository,
    private val downloader: ScriptletDownloader,
    private val validator: ScriptletSignatureValidator,
) : ScriptletUpdater {

    override suspend fun update(settings: ScriptletsSettings): ScriptletUpdateResult {
        if (settings.version == repository.getStoredVersion()) {
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
                            throw ScriptletFailure()
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
                                throw ScriptletFailure()
                            }
                        }
                    }
                }.awaitAll().filterNotNull().toMap()
            }
        } catch (_: ScriptletFailure) {
            return ScriptletUpdateResult.Retry
        }

        logcat { "Storing scriptlets" }
        repository.storeScriptlets(settings.version, downloaded)
        return ScriptletUpdateResult.Success
    }

    private class ScriptletFailure : RuntimeException()
}
