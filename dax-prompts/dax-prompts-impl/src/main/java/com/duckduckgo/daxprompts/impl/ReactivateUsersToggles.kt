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

package com.duckduckgo.daxprompts.impl

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.daxprompts.impl.ReactivateUsersToggles.Companion.BASE_EXPERIMENT_NAME
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = BASE_EXPERIMENT_NAME,
)
interface ReactivateUsersToggles {

    @Toggle.DefaultValue(Toggle.DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(Toggle.DefaultFeatureValue.FALSE)
    fun reactivateUsersExperimentMay25(): Toggle

    enum class Cohorts(override val cohortName: String) : CohortName {
        CONTROL("control"), // no Dax prompts
        VARIANT_DUCKPLAYER_PROMPT("duckplayerPrompt"), // show Duck Player Dax Prompt
        VARIANT_BROWSER_PROMPT("browserPrompt"), // show Browser Comparison Dax Prompt
    }

    companion object {
        internal const val BASE_EXPERIMENT_NAME = "reactivateUsers"
    }
}

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class ReactivateUsersPixelsPlugin @Inject constructor(
    private val toggles: ReactivateUsersToggles,
) : MetricsPixelPlugin {

    override suspend fun getMetrics(): List<MetricsPixel> {
        return listOf(
            MetricsPixel(
                metric = METRIC_DUCK_PLAYER_USE,
                value = "1",
                toggle = toggles.reactivateUsersExperimentMay25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                    ConversionWindow(lowerWindow = 0, upperWindow = 5),
                    ConversionWindow(lowerWindow = 0, upperWindow = 7),
                    ConversionWindow(lowerWindow = 0, upperWindow = 14),
                    ConversionWindow(lowerWindow = 5, upperWindow = 7),
                    ConversionWindow(lowerWindow = 8, upperWindow = 14),
                ),
            ),
            MetricsPixel(
                metric = METRIC_SET_BROWSER_AS_DEFAULT,
                value = "1",
                toggle = toggles.reactivateUsersExperimentMay25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                    ConversionWindow(lowerWindow = 0, upperWindow = 7),
                    ConversionWindow(lowerWindow = 5, upperWindow = 7),
                    ConversionWindow(lowerWindow = 8, upperWindow = 14),
                ),
            ),
            MetricsPixel(
                metric = METRIC_DUCK_PLAYER_CLICK,
                value = "1",
                toggle = toggles.reactivateUsersExperimentMay25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ),
            ),
            MetricsPixel(
                metric = METRIC_CHOOSE_YOUR_BROWSER_CLICK,
                value = "1",
                toggle = toggles.reactivateUsersExperimentMay25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ),
            ),
            MetricsPixel(
                metric = METRIC_CLOSE_SCREEN,
                value = "1",
                toggle = toggles.reactivateUsersExperimentMay25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ),
            ),
            MetricsPixel(
                metric = METRIC_PLUS_EVEN_MORE_PROTECTIONS_LINK_CLICK,
                value = "1",
                toggle = toggles.reactivateUsersExperimentMay25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ),
            ),
        )
    }

    suspend fun getDuckPlayerUseMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_DUCK_PLAYER_USE }
    }

    suspend fun getSetBrowserAsDefaultMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_SET_BROWSER_AS_DEFAULT }
    }

    suspend fun getDuckPlayerClickMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_DUCK_PLAYER_CLICK }
    }

    suspend fun getChooseYourBrowserClickMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_CHOOSE_YOUR_BROWSER_CLICK }
    }

    suspend fun getCloseScreenMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_CLOSE_SCREEN }
    }

    suspend fun getPlusEvenMoreProtectionsLinkClickMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_PLUS_EVEN_MORE_PROTECTIONS_LINK_CLICK }
    }

    companion object {
        internal const val METRIC_DUCK_PLAYER_USE = "duckPlayerUse"
        internal const val METRIC_SET_BROWSER_AS_DEFAULT = "setBrowserAsDefault"
        internal const val METRIC_DUCK_PLAYER_CLICK = "duckPlayerClick"
        internal const val METRIC_CHOOSE_YOUR_BROWSER_CLICK = "chooseYourBrowserClick"
        internal const val METRIC_CLOSE_SCREEN = "closeScreen"
        internal const val METRIC_PLUS_EVEN_MORE_PROTECTIONS_LINK_CLICK = "plusEvenMoreProtectionsLinkClick"
    }
}
