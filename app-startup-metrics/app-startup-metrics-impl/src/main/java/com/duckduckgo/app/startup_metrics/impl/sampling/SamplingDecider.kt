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

package com.duckduckgo.app.startup_metrics.impl.sampling

import com.duckduckgo.app.startup_metrics.impl.feature.StartupMetricsFeature
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import kotlin.random.Random

/**
 * Interface for probabilistic sampling decisions based on feature flag configuration.
 */
interface SamplingDecider {
    /**
     * Determines if metrics should be sampled based on feature flag configuration.
     *
     * @return true if metrics should be reported, false otherwise
     */
    fun shouldSample(): Boolean
}

/**
 * Real implementation of SamplingDecider using random sampling with feature flag configuration.
 */
@ContributesBinding(AppScope::class)
class RealSamplingDecider @Inject constructor(
    private val startupMetricsFeature: StartupMetricsFeature,
    private val moshi: Moshi,
    private val random: Random = Random.Default,
) : SamplingDecider {

    private val jsonAdapter by lazy {
        moshi.adapter<Map<String, String>>(Types.newParameterizedType(Map::class.java, String::class.java, String::class.java))
    }

    override fun shouldSample(): Boolean {
        val samplingRate = getSamplingRate()
        val clampedRate = samplingRate.coerceIn(0.0, 1.0)
        if (clampedRate <= 0.0) return false
        if (clampedRate >= 1.0) return true
        return random.nextDouble() < clampedRate
    }

    /**
     * Retrieves the sampling rate from feature flag settings.
     */
    private fun getSamplingRate(): Double {
        val config = startupMetricsFeature.self().getSettings()?.let {
            runCatching {
                jsonAdapter.fromJson(it)
            }.getOrDefault(emptyMap())
        } ?: emptyMap()
        return config["sampling"]?.toDoubleOrNull() ?: 0.01
    }
}
