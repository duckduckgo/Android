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

package com.duckduckgo.autofill.impl.importing

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.send
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Experiment metrics for importing passwords from Google, fired for every entry point into the
 * import web flow so that onboarding and non-onboarding imports are comparable across cohorts.
 */
interface PasswordImportExperimentMetrics {

    suspend fun fireImportStartedMetric()

    suspend fun fireImportSuccessMetric()

    suspend fun fireImportFailedMetric()

    suspend fun fireImportCancelledMetric()
}

@ContributesBinding(AppScope::class)
class PasswordImportExperimentMetricsImpl @Inject constructor(
    private val inventory: FeatureTogglesInventory,
) : PasswordImportExperimentMetrics {

    override suspend fun fireImportStartedMetric() = fire("password_import_started")

    override suspend fun fireImportSuccessMetric() = fire("password_import_success")

    override suspend fun fireImportFailedMetric() = fire("password_import_failed")

    override suspend fun fireImportCancelledMetric() = fire("password_import_cancelled")

    private suspend fun fire(metric: String) {
        val toggle = experimentToggle() ?: return
        MetricsPixel(
            metric = metric,
            type = MetricType.NORMAL,
            value = "1",
            toggle = toggle,
            conversionWindow = listOf(
                ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ConversionWindow(lowerWindow = 0, upperWindow = 14),
            ),
        ).send()
    }

    // The experiment is declared in the onboarding feature, which this module cannot depend on, so it
    // is resolved through the inventory by name instead.
    private suspend fun experimentToggle(): Toggle? {
        return inventory.getAllTogglesForParent(PARENT_FEATURE_NAME).firstOrNull {
            it.featureName().name == EXPERIMENT_FEATURE_NAME
        }
    }

    private companion object {
        const val PARENT_FEATURE_NAME = "onboardingPasswordImport"
        const val EXPERIMENT_FEATURE_NAME = "passwordImportExperimentAug25"
    }
}
