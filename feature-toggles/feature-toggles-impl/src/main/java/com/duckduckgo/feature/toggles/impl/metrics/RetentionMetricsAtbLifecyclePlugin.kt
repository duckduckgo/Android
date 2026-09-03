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

package com.duckduckgo.feature.toggles.impl.metrics

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.send
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class RetentionMetricsAtbLifecyclePlugin(
    private val searchMetricPixelsPlugin: SearchMetricPixelsPlugin,
    private val appUseMetricPixelsPlugin: AppUseMetricPixelsPlugin,
    private val duckAiPromptSentMetricPixelsPlugin: DuckAiPromptSentMetricPixelsPlugin,
    private val appCoroutineScope: CoroutineScope,
    private val experimentsExcludedFromDuckAiSearchMetric: Set<String>,
) : AtbLifecyclePlugin {

    @Inject
    constructor(
        searchMetricPixelsPlugin: SearchMetricPixelsPlugin,
        appUseMetricPixelsPlugin: AppUseMetricPixelsPlugin,
        duckAiPromptSentMetricPixelsPlugin: DuckAiPromptSentMetricPixelsPlugin,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
    ) : this(
        searchMetricPixelsPlugin,
        appUseMetricPixelsPlugin,
        duckAiPromptSentMetricPixelsPlugin,
        appCoroutineScope,
        EXPERIMENTS_EXCLUDED_FROM_DUCK_AI_SEARCH_METRIC,
    )

    override fun onSearchRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        appCoroutineScope.launch {
            searchMetricPixelsPlugin.getMetrics().forEach { it.send() }
        }
    }

    override fun onAppRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        appCoroutineScope.launch {
            appUseMetricPixelsPlugin.getMetrics().forEach { it.send() }
        }
    }

    override fun onDuckAiRetentionAtbRefreshed(oldAtb: String, newAtb: String, metadata: Map<String, String?>) {
        appCoroutineScope.launch {
            duckAiPromptSentMetricPixelsPlugin.getMetrics().forEach { it.send() }
            searchMetricPixelsPlugin.getMetrics(excluding = experimentsExcludedFromDuckAiSearchMetric).forEach { it.send() }
        }
    }

    companion object {
        /**
         * Experiments that were enrolling/running before Duck.ai prompts were added towards search retention metrics.
         * They are excluded from counting prompts to avoid skewing the metrics.
         */
        private val EXPERIMENTS_EXCLUDED_FROM_DUCK_AI_SEARCH_METRIC = setOf(
            "tdsNextExperiment016",
            "contentScopeExperiment7",
            "addToDockAndWidgetExperimentJul25",
        )
    }
}
