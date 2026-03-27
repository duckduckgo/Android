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

package com.duckduckgo.youtubeadblocking.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureSettings
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.SingleInstanceIn
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

/**
 * Injection method for YouTube ad blocking scriptlets.
 *
 * Configured via remote config:
 * ```json
 * {
 *   "features": {
 *     "youTubeAdBlocking": {
 *       "state": "enabled",
 *       "settings": {
 *         "injectMethod": "intercept"
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * Valid values:
 * - `"none"` — disabled, no injection (useful for A/B testing)
 * - `"evaluate"` — evaluateJavascript in onPageStarted (no CSP issues, simpler)
 * - `"intercept"` — shouldInterceptRequest HTML modification (guaranteed timing, strips CSP)
 * - `"adsjs"` — addDocumentStartJavaScript (automatic iframe + SPA, but may crash)
 *
 * Defaults to `"intercept"` if not specified.
 */
enum class InjectMethod {
    NONE,
    EVALUATE,
    INTERCEPT,
    ADSJS;

    companion object {
        fun fromString(value: String?): InjectMethod {
            return when (value?.lowercase()) {
                "none" -> NONE
                "evaluate" -> EVALUATE
                "intercept" -> INTERCEPT
                "adsjs" -> ADSJS
                else -> INTERCEPT // default
            }
        }
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
@RemoteFeatureStoreNamed(YouTubeAdBlockingFeature::class)
class YouTubeAdBlockingSettingsStore @Inject constructor() : FeatureSettings.Store {

    @Volatile
    var injectMethod: InjectMethod = InjectMethod.INTERCEPT
        private set

    private val jsonAdapter: JsonAdapter<YouTubeAdBlockingSetting> by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        moshi.adapter(YouTubeAdBlockingSetting::class.java)
    }

    override fun store(jsonString: String) {
        try {
            jsonAdapter.fromJson(jsonString)?.let {
                injectMethod = InjectMethod.fromString(it.injectMethod)
                logcat { "YouTubeAdBlocking: Settings updated — injectMethod=$injectMethod" }
            }
        } catch (e: Exception) {
            logcat { "YouTubeAdBlocking: Failed to parse settings: ${e.asLog()}" }
        }
    }
}

@JsonClass(generateAdapter = true)
data class YouTubeAdBlockingSetting(
    @field:Json(name = "injectMethod")
    val injectMethod: String?,
)
