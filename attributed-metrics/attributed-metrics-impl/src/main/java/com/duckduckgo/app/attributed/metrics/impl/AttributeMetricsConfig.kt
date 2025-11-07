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
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricConfig
import com.duckduckgo.app.attributed.metrics.api.MetricBucket
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@ContributesBinding(AppScope::class, AttributedMetricConfig::class)
@SingleInstanceIn(AppScope::class)
class AttributeMetricsConfig @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val attributedMetricsConfigFeature: AttributedMetricsConfigFeature,
    private val featureTogglesInvestory: FeatureTogglesInventory,
    private val moshi: Moshi,
) : AttributedMetricConfig {

    private val jsonAdapter: JsonAdapter<Map<String, JsonMetricBucket>> by lazy {
        val type = Types.newParameterizedType(Map::class.java, String::class.java, JsonMetricBucket::class.java)
        moshi.adapter(type)
    }

    data class JsonMetricBucket(
        @Json(name = "buckets") val buckets: List<Int>,
        @Json(name = "version") val version: Int,
    )

    override suspend fun metricsToggles(): List<Toggle> {
        if (!attributedMetricsConfigFeature.self().isEnabled()) {
            return emptyList()
        }
        return featureTogglesInvestory.getAllTogglesForParent(attributedMetricsConfigFeature.self().featureName().name)
    }

    override suspend fun getBucketConfiguration(): Map<String, MetricBucket> {
        if (!attributedMetricsConfigFeature.self().isEnabled()) {
            return emptyMap()
        }

        val metricConfigs = kotlin.runCatching {
            attributedMetricsConfigFeature.self().getSettings()?.let { jsonAdapter.fromJson(it) }
        }.getOrNull()?.map { entry ->
            entry.key to MetricBucket(
                buckets = entry.value.buckets,
                version = entry.value.version,
            )
        }?.toMap() ?: emptyMap()

        return metricConfigs
    }
}
