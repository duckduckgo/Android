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

package com.duckduckgo.app.tabs.ui

import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_INFO_PANEL_DISMISSED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_INFO_PANEL_IMPRESSIONS
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_INFO_PANEL_TAPPED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedAppRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Handles pixel firing for tab switcher info panel interactions
 */
interface TabSwitcherAnimationInfoPanelPixels {
    fun fireInfoPanelTapped()
    fun fireInfoPanelImpression()
    suspend fun fireInfoPanelDismissed()
}

@ContributesBinding(ActivityScope::class)
class TabSwitcherAnimationInfoPanelPixelsImpl @Inject constructor(
    private val pixel: Pixel,
    private val webTrackersBlockedAppRepository: WebTrackersBlockedAppRepository,
    private val dispatcherProvider: DispatcherProvider,
) : TabSwitcherAnimationInfoPanelPixels {

    override fun fireInfoPanelTapped() {
        pixel.fire(pixel = TAB_MANAGER_INFO_PANEL_TAPPED)
    }

    override fun fireInfoPanelImpression() {
        pixel.fire(pixel = TAB_MANAGER_INFO_PANEL_IMPRESSIONS)
    }

    override suspend fun fireInfoPanelDismissed() {
        withContext(dispatcherProvider.io()) {
            val trackerCount = webTrackersBlockedAppRepository.getTrackerCountForLast7Days()
            val bucketSize: Int = when (trackerCount) {
                0 -> BUCKET_SIZE_0
                in 1..9 -> BUCKET_SIZE_1
                in 10..24 -> BUCKET_SIZE_10
                in 25..49 -> BUCKET_SIZE_25
                in 50..74 -> BUCKET_SIZE_50
                in 75..99 -> BUCKET_SIZE_75
                in 100..149 -> BUCKET_SIZE_100
                in 150..199 -> BUCKET_SIZE_150
                in 200..499 -> BUCKET_SIZE_200
                else -> BUCKET_SIZE_500
            }

            pixel.fire(
                pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
                parameters = mapOf("trackerCount" to bucketSize.toString()),
            )
        }
    }

    companion object {
        private const val BUCKET_SIZE_0 = 0
        private const val BUCKET_SIZE_1 = 1
        private const val BUCKET_SIZE_10 = 10
        private const val BUCKET_SIZE_25 = 40
        private const val BUCKET_SIZE_50 = 50
        private const val BUCKET_SIZE_75 = 75
        private const val BUCKET_SIZE_100 = 100
        private const val BUCKET_SIZE_150 = 150
        private const val BUCKET_SIZE_200 = 200
        private const val BUCKET_SIZE_500 = 500
    }
}
