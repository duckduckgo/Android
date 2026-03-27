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
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.SingleInstanceIn
import logcat.logcat
import javax.inject.Inject

/**
 * Injection method for YouTube ad blocking scriptlets.
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
                else -> NONE // default to NONE until explicitly set
            }
        }
    }
}

/**
 * Reads settings from the feature toggle's `getSettings()` JSON on each access.
 *
 * Uses `Toggle.getSettings()` (the current API) rather than the deprecated
 * `FeatureSettings.Store` pattern. Settings are parsed fresh each time from
 * the toggle, so changes take effect immediately without needing `store()`.
 *
 * Config example:
 * ```json
 * {
 *   "features": {
 *     "youTubeAdBlocking": {
 *       "state": "enabled",
 *       "settings": {
 *         "injectMethod": "intercept",
 *         "injectMain": "enabled",
 *         "injectIsolated": "enabled",
 *         "timingIntercept": "enabled",
 *         "timingEvaluate": "disabled",
 *         "timingAdsjs": "disabled"
 *       }
 *     }
 *   }
 * }
 * ```
 */
interface YouTubeAdBlockingSettingsProvider {
    val injectMethod: InjectMethod
    val injectMain: Boolean
    val injectIsolated: Boolean
    val timingIntercept: Boolean
    val timingEvaluate: Boolean
    val timingAdsjs: Boolean
    fun settingsSummary(): String
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealYouTubeAdBlockingSettingsProvider @Inject constructor(
    private val youTubeAdBlockingFeature: YouTubeAdBlockingFeature,
) : YouTubeAdBlockingSettingsProvider {

    private val jsonAdapter: JsonAdapter<YouTubeAdBlockingSetting> by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        moshi.adapter(YouTubeAdBlockingSetting::class.java)
    }

    private val parsedSettings: YouTubeAdBlockingSetting?
        get() {
            val json = youTubeAdBlockingFeature.self().getSettings() ?: return null
            return try {
                jsonAdapter.fromJson(json)
            } catch (e: Exception) {
                logcat { "YouTubeAdBlocking: Failed to parse settings JSON: ${e.message}" }
                null
            }
        }

    override val injectMethod: InjectMethod
        get() = InjectMethod.fromString(parsedSettings?.injectMethod)

    override val injectMain: Boolean
        get() = isEnabled(parsedSettings?.injectMain, default = true)

    override val injectIsolated: Boolean
        get() = isEnabled(parsedSettings?.injectIsolated, default = true)

    override val timingIntercept: Boolean
        get() = isEnabled(parsedSettings?.timingIntercept, default = false)

    override val timingEvaluate: Boolean
        get() = isEnabled(parsedSettings?.timingEvaluate, default = false)

    override val timingAdsjs: Boolean
        get() = isEnabled(parsedSettings?.timingAdsjs, default = false)

    override fun settingsSummary(): String {
        return "injectMethod=$injectMethod injectMain=$injectMain injectIsolated=$injectIsolated" +
            " timingIntercept=$timingIntercept timingEvaluate=$timingEvaluate timingAdsjs=$timingAdsjs"
    }

    private fun isEnabled(value: String?, default: Boolean): Boolean {
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
    @field:Json(name = "injectMain")
    val injectMain: String?,
    @field:Json(name = "injectIsolated")
    val injectIsolated: String?,
    @field:Json(name = "timingIntercept")
    val timingIntercept: String?,
    @field:Json(name = "timingEvaluate")
    val timingEvaluate: String?,
    @field:Json(name = "timingAdsjs")
    val timingAdsjs: String?,
)
