/*
 * Copyright (c) 2024 DuckDuckGo
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

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.trackerdetection.api.TrackerDataDownloader
import com.duckduckgo.app.trackerdetection.blocklist.BlockList.Companion.EXPERIMENT_PREFIX
import com.duckduckgo.app.trackerdetection.blocklist.ExperimentTestAA.Cohorts.CONTROL
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "blockList",
)
interface BlockList {
    @Toggle.DefaultValue(false)
    fun self(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentBaseline(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentBaselineBackup(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentBaselineBackup2(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentBaselineBackup3(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentBaselineBackup4(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentBaselineBackup5(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentNov24(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentDec24(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentJan25(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentFeb25(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentMar25(): Toggle

    enum class Cohorts(override val cohortName: String) : CohortName {
        CONTROL("control"),
        TREATMENT("treatment"),
    }

    companion object {
        internal const val EXPERIMENT_PREFIX = "tds"
    }
}

@ContributesMultibinding(AppScope::class)
class BlockListPixelsPlugin @Inject constructor(private val inventory: FeatureTogglesInventory) : MetricsPixelPlugin {

    override suspend fun getMetrics(): List<MetricsPixel> {
        val activeToggle = inventory.activeTdsFlag() ?: return emptyList()

        return listOf(
            MetricsPixel(
                metric = "2xRefresh",
                value = "1",
                toggle = activeToggle,
                conversionWindow = (0..5).map { ConversionWindow(lowerWindow = 0, upperWindow = it) },
            ),
            MetricsPixel(
                metric = "3xRefresh",
                value = "1",
                toggle = activeToggle,
                conversionWindow = (0..5).map { ConversionWindow(lowerWindow = 0, upperWindow = it) },
            ),
            MetricsPixel(
                metric = "privacyToggleUsed",
                value = "1",
                toggle = activeToggle,
                conversionWindow = (0..5).map { ConversionWindow(lowerWindow = 0, upperWindow = it) },
            ),
        )
    }
}

@ContributesMultibinding(AppScope::class)
class BlockListPrivacyConfigCallbackPlugin @Inject constructor(
    private val inventory: FeatureTogglesInventory,
    private val trackerDataDownloader: TrackerDataDownloader,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val experimentAA: ExperimentTestAA,
    private val dispatcherProvider: DispatcherProvider,
) : PrivacyConfigCallbackPlugin {
    override fun onPrivacyConfigDownloaded() {
        coroutineScope.launch(dispatcherProvider.io()) {
            experimentAA.experimentTestAA().isEnabled(CONTROL)
            inventory.activeTdsFlag()?.let {
                trackerDataDownloader.downloadTds()
            }
        }
    }
}

internal suspend fun BlockListPixelsPlugin.get2XRefresh(): MetricsPixel? {
    return this.getMetrics().firstOrNull { it.metric == "2xRefresh" }
}

suspend fun BlockListPixelsPlugin.get3XRefresh(): MetricsPixel? {
    return this.getMetrics().firstOrNull { it.metric == "3xRefresh" }
}

suspend fun BlockListPixelsPlugin.getPrivacyToggleUsed(): MetricsPixel? {
    return this.getMetrics().firstOrNull { it.metric == "privacyToggleUsed" }
}

suspend fun FeatureTogglesInventory.activeTdsFlag(): Toggle? {
    return this.getAllTogglesForParent("blockList").firstOrNull {
        it.featureName().name.startsWith(EXPERIMENT_PREFIX) && it.isEnabled()
    }
}

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "experimentTest",
)
interface ExperimentTestAA {
    @Toggle.DefaultValue(false)
    fun self(): Toggle

    @Toggle.DefaultValue(false)
    fun experimentTestAA(): Toggle

    enum class Cohorts(override val cohortName: String) : CohortName {
        CONTROL("control"),
        TREATMENT("treatment"),
    }
}
