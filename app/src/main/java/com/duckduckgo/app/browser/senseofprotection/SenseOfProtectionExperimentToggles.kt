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

package com.duckduckgo.app.browser.senseofprotection

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionToggles.Companion.BASE_EXPERIMENT_NAME
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = BASE_EXPERIMENT_NAME,
)
interface SenseOfProtectionToggles {

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun senseOfProtectionNewUserExperiment27May25(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun senseOfProtectionExistingUserExperiment27May25(): Toggle

    enum class Cohorts(override val cohortName: String) : CohortName {
        MODIFIED_CONTROL("modifiedControl"), // without grey tracker logos from original animation
        VARIANT_1("variant1"), // Persistent Green Shield + X Trackers Blocked animation
        VARIANT_2("variant2"), // Persistent Green Shield + X Trackers Blocked animation + TabSwitcher animation
    }

    companion object {
        internal const val BASE_EXPERIMENT_NAME = "senseOfProtection"
    }
}

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SenseOfProtectionPixelsPlugin @Inject constructor(private val inventory: FeatureTogglesInventory) : MetricsPixelPlugin {

    override suspend fun getMetrics(): List<MetricsPixel> {
        val activeToggle = inventory.activeSenseOfProtectionFlag() ?: return emptyList()

        return listOf(
            MetricsPixel(
                metric = METRIC_PRIVACY_DASHBOARD_CLICKED,
                value = "1",
                toggle = activeToggle,
                conversionWindow = (0..7).map { ConversionWindow(lowerWindow = 0, upperWindow = it) },
            ),
        )
    }

    suspend fun getPrivacyDashboardClickedMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_PRIVACY_DASHBOARD_CLICKED }
    }

    companion object {
        internal const val METRIC_PRIVACY_DASHBOARD_CLICKED = "privacyDashboardClicked"
    }
}

suspend fun FeatureTogglesInventory.activeSenseOfProtectionFlag(): Toggle? {
    return this.getAllTogglesForParent(BASE_EXPERIMENT_NAME).firstOrNull {
        it.featureName().name.startsWith(BASE_EXPERIMENT_NAME) && it.isEnrolled() && it.isEnabled()
    }
}
