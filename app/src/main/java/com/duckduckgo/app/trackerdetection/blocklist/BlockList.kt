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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "blockList",
)
interface BlockList {
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperimentBaselineBackup6(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperimentBaselineBackup7(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperimentBaselineBackup8(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperimentBaselineBackup9(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperimentBaselineBackup10(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperimentMar25(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperimentApr25(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperimentMay25(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperimentJun25(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment001(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment002(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment003(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment004(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment005(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment006(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment007(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment008(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment009(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment010(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment011(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment012(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment013(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment014(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment015(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment016(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment017(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment018(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment019(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment020(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment021(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment022(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment023(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment024(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment025(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment026(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment027(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment028(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment029(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tdsNextExperiment030(): Toggle

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
    private val dispatcherProvider: DispatcherProvider,
) : PrivacyConfigCallbackPlugin {
    override fun onPrivacyConfigDownloaded() {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (inventory.activeTdsFlag() != null) {
                trackerDataDownloader.downloadTds()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({}, { logcat(WARN) { "Failed to download from blocklist experiment" } })
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
