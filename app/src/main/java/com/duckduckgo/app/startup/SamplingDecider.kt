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

package com.duckduckgo.app.startup

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import kotlin.random.Random

/**
 * Interface for probabilistic sampling decisions.
 */
interface SamplingDecider {
    /**
     * Determines if metrics should be sampled based on the provided feature flag settings JSON.
     *
     * @param settingsJson the raw settings JSON from the feature toggle, or null if unavailable
     * @return true if metrics should be reported, false otherwise
     */
    fun shouldSample(settingsJson: String?): Boolean
}

/**
 * Real implementation of SamplingDecider using random sampling.
 */
@ContributesBinding(AppScope::class)
class RealSamplingDecider @Inject constructor(
    private val moshi: Moshi,
    private val random: Random = Random.Default,
) : SamplingDecider {

    private val jsonAdapter by lazy {
        moshi.newBuilder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(AppStartupMetricsJson::class.java)
    }

    override fun shouldSample(settingsJson: String?): Boolean {
        val samplingRate = getSamplingRate(settingsJson)
        val clampedRate = samplingRate.coerceIn(0.0, 1.0)
        if (clampedRate <= 0.0) return false
        if (clampedRate >= 1.0) return true
        return random.nextDouble() < clampedRate
    }

    /**
     * Parses the sampling rate from the provided settings JSON.
     * Falls back to 1% if the JSON is absent or malformed.
     */
    private fun getSamplingRate(settingsJson: String?): Double {
        val config = settingsJson?.let {
            runCatching {
                jsonAdapter.fromJson(it)
            }.getOrDefault(AppStartupMetricsJson())
        } ?: AppStartupMetricsJson()
        return config.sampling
    }

    private data class AppStartupMetricsJson(
        val sampling: Double = 0.01,
    )
}
