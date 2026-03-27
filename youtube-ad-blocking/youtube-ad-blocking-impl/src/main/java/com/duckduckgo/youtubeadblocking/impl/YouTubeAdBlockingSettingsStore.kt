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
 *         "injectMethod": "intercept",
 *         "timingIntercept": "enabled",
 *         "timingEvaluate": "disabled",
 *         "timingAdsjs": "disabled"
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * `injectMethod` — which mechanism injects the full ad-blocking scriptlet bundle:
 * - `"none"` — disabled, no injection (useful for A/B testing)
 * - `"evaluate"` — evaluateJavascript in onPageStarted (no CSP issues, simpler)
 * - `"intercept"` — shouldInterceptRequest HTML modification (guaranteed timing, strips CSP)
 * - `"adsjs"` — addDocumentStartJavaScript (automatic iframe + SPA, but may crash)
 * Defaults to `"intercept"` if not specified.
 *
 * `timingIntercept` / `timingEvaluate` / `timingAdsjs` — independently control whether
 * each mechanism fires its timing probe. Enable one at a time to get clean measurements
 * without interference from other mechanisms. All default to `true`.
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

    /** Whether the intercept (Mechanism B) timing probe fires. Default: true. */
    @Volatile
    var timingIntercept: Boolean = true
        private set

    /** Whether the evaluateJavascript (Mechanism C) timing probe fires. Default: true. */
    @Volatile
    var timingEvaluate: Boolean = true
        private set

    /** Whether the addDocumentStartJavaScript (Mechanism A) timing probe fires. Default: true. */
    @Volatile
    var timingAdsjs: Boolean = true
        private set

    private val jsonAdapter: JsonAdapter<YouTubeAdBlockingSetting> by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        moshi.adapter(YouTubeAdBlockingSetting::class.java)
    }

    override fun store(jsonString: String) {
        try {
            jsonAdapter.fromJson(jsonString)?.let {
                injectMethod = InjectMethod.fromString(it.injectMethod)
                timingIntercept = isEnabledString(it.timingIntercept, default = true)
                timingEvaluate = isEnabledString(it.timingEvaluate, default = true)
                timingAdsjs = isEnabledString(it.timingAdsjs, default = true)
                logcat {
                    "YouTubeAdBlocking: Settings updated — injectMethod=$injectMethod" +
                        " timingIntercept=$timingIntercept timingEvaluate=$timingEvaluate timingAdsjs=$timingAdsjs"
                }
            }
        } catch (e: Exception) {
            logcat { "YouTubeAdBlocking: Failed to parse settings: ${e.asLog()}" }
        }
    }

    /** Returns a compact summary of current settings for logcat. */
    fun settingsSummary(): String {
        return "injectMethod=$injectMethod timingIntercept=$timingIntercept timingEvaluate=$timingEvaluate timingAdsjs=$timingAdsjs"
    }

    private fun isEnabledString(value: String?, default: Boolean): Boolean {
        return when (value?.lowercase()) {
            "enabled" -> true
            "disabled" -> false
            else -> default
        }
    }
}

@JsonClass(generateAdapter = true)
data class YouTubeAdBlockingSetting(
    @field:Json(name = "injectMethod")
    val injectMethod: String?,
    @field:Json(name = "timingIntercept")
    val timingIntercept: String?,
    @field:Json(name = "timingEvaluate")
    val timingEvaluate: String?,
    @field:Json(name = "timingAdsjs")
    val timingAdsjs: String?,
)
