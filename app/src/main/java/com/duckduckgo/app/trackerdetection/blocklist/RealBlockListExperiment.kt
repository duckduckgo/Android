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

package com.duckduckgo.app.trackerdetection.blocklist

import com.duckduckgo.app.trackerdetection.blocklist.BlockListExperiment.Metric
import com.duckduckgo.app.trackerdetection.blocklist.BlockListExperiment.Metric.THREE_X_REFRESH
import com.duckduckgo.app.trackerdetection.blocklist.BlockListExperiment.Metric.TWO_X_REFRESH
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealBlockListExperiment @Inject constructor(
    private val inventory: FeatureTogglesInventory,
    private val blockListPixelsPlugin: BlockListPixelsPlugin,
) : BlockListExperiment {

    override suspend fun activeExperimentCohort(): String? {
        val toggle = inventory.activeTdsFlag() ?: return null
        val cohort = toggle.getCohort() ?: return null
        return "${toggle.featureName().name}_${cohort.name}"
    }

    override suspend fun metric(metric: Metric): MetricsPixel? = when (metric) {
        TWO_X_REFRESH -> blockListPixelsPlugin.get2XRefresh()
        THREE_X_REFRESH -> blockListPixelsPlugin.get3XRefresh()
    }
}
