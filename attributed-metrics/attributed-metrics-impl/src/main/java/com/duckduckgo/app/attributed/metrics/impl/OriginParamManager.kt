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

package com.duckduckgo.app.attributed.metrics.impl

import com.duckduckgo.app.attributed.metrics.AttributedMetricsConfigFeature
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.Inject

interface OriginParamManager {
    /**
     * Determines whether the origin parameter should be included to metric based on the remote config.
     *
     * @return true if the origin should be included in the metric, false if not
     */
    fun shouldSendOrigin(origin: String?): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealOriginParamManager @Inject constructor(
    private val attributedMetricsConfigFeature: AttributedMetricsConfigFeature,
    private val moshi: Moshi,
) : OriginParamManager {
    private val sendOriginParamAdapter: JsonAdapter<SendOriginParamSettings> by lazy {
        moshi.adapter(SendOriginParamSettings::class.java)
    }

    // Cache parsed substrings. It's expected this to be a short list and not change frequently.
    private val cachedSubstrings: List<String> by lazy {
        kotlin.runCatching {
            attributedMetricsConfigFeature.sendOriginParam().getSettings()
                ?.let { sendOriginParamAdapter.fromJson(it) }
                ?.originCampaignSubstrings
        }.getOrNull() ?: emptyList()
    }

    override fun shouldSendOrigin(origin: String?): Boolean {
        // If toggle is disabled, don't send origin
        if (!attributedMetricsConfigFeature.sendOriginParam().isEnabled()) {
            return false
        }

        // If origin is null or blank, can't send it
        if (origin.isNullOrBlank()) {
            return false
        }

        // If no substrings configured, don't send origin
        if (cachedSubstrings.isEmpty()) {
            return false
        }

        // Check if origin matches any of the configured substrings (case-insensitive)
        return cachedSubstrings.any { substring ->
            origin.contains(substring, ignoreCase = true)
        }
    }
}
