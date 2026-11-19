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

package com.duckduckgo.app.nextsteps

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.send
import dev.zacsweers.metro.ContributesBinding
import logcat.logcat
import javax.inject.Inject

/**
 * Sends Next Steps experiment metrics from the app module, with the same type, value and windows as next-steps-impl, so a
 * metric sent from both places counts a user once.
 */
interface NextStepsExperimentMetricSender {
    suspend fun send(metric: String)
}

@ContributesBinding(AppScope::class)
class RealNextStepsExperimentMetricSender @Inject constructor(
    private val inventory: FeatureTogglesInventory,
) : NextStepsExperimentMetricSender {

    override suspend fun send(metric: String) {
        val toggle = experimentToggle() ?: return
        // The sender drops repeats within a window, so this logs every request rather than what reaches the backend.
        logcat(tag = LOG_TAG) { "send $metric" }
        MetricsPixel(
            metric = metric,
            type = MetricType.NORMAL,
            value = "1",
            toggle = toggle,
            conversionWindow = listOf(
                ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ConversionWindow(lowerWindow = 0, upperWindow = 7),
            ),
        ).send()
    }

    // The experiment is declared in next-steps-impl, which this module cannot depend on, so it is resolved by name.
    private suspend fun experimentToggle(): Toggle? =
        inventory.getAllTogglesForParent(PARENT_FEATURE_NAME).firstOrNull { it.featureName().name == EXPERIMENT_FEATURE_NAME }

    private companion object {
        const val LOG_TAG = "NextStepsMetrics"
        const val PARENT_FEATURE_NAME = "nextStepsItems"
        const val EXPERIMENT_FEATURE_NAME = "nextStepsItemsExperiment"
    }
}
