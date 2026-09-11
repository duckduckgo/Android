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

package com.duckduckgo.app.browser.tabs

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

/**
 * Reports the furthest back in the activation order a tab was when the user returned to it.
 *
 * The distance is expressed in the same unit the retention limit uses: the number of *distinct other
 * tabs* activated since this tab was last activated.
 */
interface TabReuseDistanceReporter {

    fun onTabActivated(tabId: String)

    fun onTabsRemoved(tabIds: Collection<String>)

    fun onTabCountChanged(tabCount: Int)

    fun onBrowserPaused()
}

@SingleInstanceIn(ActivityScope::class)
@ContributesBinding(ActivityScope::class)
class RealTabReuseDistanceReporter @Inject constructor(
    private val pixel: Pixel,
    private val tabManagerFeatureFlags: TabManagerFeatureFlags,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : TabReuseDistanceReporter {

    private val activationOrder = mutableListOf<String>()

    private var maxDistance = 0
    private var tabCount = 0

    private var pixelEnabled: Boolean? = null

    @Synchronized
    override fun onTabActivated(tabId: String) {
        val index = activationOrder.indexOf(tabId)
        if (index >= 0 && index == activationOrder.lastIndex) {
            return
        }

        if (index >= 0) {
            val distance = activationOrder.lastIndex - index
            maxDistance = maxOf(maxDistance, distance)
            activationOrder.removeAt(index)
        }

        activationOrder.add(tabId)
    }

    @Synchronized
    override fun onTabsRemoved(tabIds: Collection<String>) {
        activationOrder.removeAll(tabIds.toSet())
    }

    @Synchronized
    override fun onTabCountChanged(tabCount: Int) {
        this.tabCount = tabCount
    }

    @Synchronized
    override fun onBrowserPaused() {
        if (maxDistance == 0) {
            return
        }

        val parameters = mapOf(
            PARAM_DISTANCE_BUCKET to distanceBucketFor(maxDistance),
            PARAM_TAB_COUNT_BUCKET to tabCountBucketFor(tabCount),
        )
        maxDistance = 0

        appCoroutineScope.launch(dispatchers.io()) {
            if (isPixelEnabled()) {
                logcat(tag = TAG) { "firing ${AppPixelName.TAB_MAX_REUSE_DISTANCE.pixelName} with $parameters" }
                pixel.fire(AppPixelName.TAB_MAX_REUSE_DISTANCE, parameters)
            }
        }
    }

    private fun isPixelEnabled(): Boolean =
        pixelEnabled ?: (
            tabManagerFeatureFlags.self().isEnabled() && tabManagerFeatureFlags.tabMaxReuseDistancePixel().isEnabled()
            ).also { pixelEnabled = it }

    private fun distanceBucketFor(distance: Int): String = when {
        distance <= 3 -> "1_3"
        distance <= 6 -> "4_6"
        distance <= 9 -> "7_9"
        distance <= 12 -> "10_12"
        distance <= 15 -> "13_15"
        else -> "16_plus"
    }

    private fun tabCountBucketFor(tabCount: Int): String = when {
        tabCount <= 3 -> "1_3"
        tabCount <= 6 -> "4_6"
        tabCount <= 9 -> "7_9"
        tabCount <= 12 -> "10_12"
        tabCount <= 15 -> "13_15"
        tabCount <= 25 -> "16_25"
        tabCount <= 50 -> "26_50"
        else -> "51_plus"
    }

    companion object {
        private const val TAG = "TabReuseDistanceReporter"

        private const val PARAM_DISTANCE_BUCKET = "distance_bucket"
        private const val PARAM_TAB_COUNT_BUCKET = "tab_count_bucket"
    }
}
