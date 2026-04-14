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

package com.duckduckgo.duckchat.store.impl.bridge

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Qualifier

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "messageBridge",
)
interface MessageBridgeFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle
}

interface MessageBridge {
    suspend fun isDuckAiNativeStorageFeatureEnabled(): Boolean
}

@ContributesBinding(AppScope::class)
@MessageBridgeFeatureApi
class RealMessageBridge @Inject constructor(
    private val messageBridgeFeature: MessageBridgeFeature,
    private val dispatcherProvider: DispatcherProvider,
) : MessageBridge {
    override suspend fun isDuckAiNativeStorageFeatureEnabled(): Boolean = withContext(dispatcherProvider.io()) {
        val settingsJson = messageBridgeFeature.self().getSettings() ?: return@withContext false
        return@withContext runCatching {
            val settings = JSONObject(settingsJson)
            val topLevelValue = settings.optString(DUCK_AI_NATIVE_STORAGE_KEY, "disabled")

            val patches = mutableListOf<String>()
            val domains = settings.optJSONArray("domains")
            if (domains != null) {
                for (i in 0 until domains.length()) {
                    val domainEntry = domains.getJSONObject(i)
                    val domainArray = domainEntry.optJSONArray("domain") ?: continue
                    val appliesToDuckAi = (0 until domainArray.length()).any { domainArray.getString(it) == DUCK_AI_DOMAIN }
                    if (!appliesToDuckAi) continue
                    val patchSettings = domainEntry.optJSONArray("patchSettings") ?: continue
                    for (j in 0 until patchSettings.length()) {
                        val patch = patchSettings.getJSONObject(j)
                        if (patch.optString("op") == "replace" &&
                            patch.optString("path") == "/$DUCK_AI_NATIVE_STORAGE_KEY"
                        ) {
                            patches.add(patch.optString("value"))
                        }
                    }
                }
            }

            val effectiveValue = when (patches.size) {
                0 -> topLevelValue
                1 -> patches.first()
                else -> {
                    logcat(ERROR) { "MessageBridge: multiple conflicting patches for $DUCK_AI_NATIVE_STORAGE_KEY, returning false" }
                    return@withContext false
                }
            }

            effectiveValue == "enabled"
        }.getOrElse { e ->
            logcat(ERROR) { "MessageBridge: failed to parse settings: ${e.message}" }
            false
        }
    }

    companion object {
        private const val DUCK_AI_NATIVE_STORAGE_KEY = "duckAiNativeStorage"
        private const val DUCK_AI_DOMAIN = "duck.ai"
    }
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
internal annotation class MessageBridgeFeatureApi
