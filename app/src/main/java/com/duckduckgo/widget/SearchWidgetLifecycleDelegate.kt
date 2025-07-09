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

package com.duckduckgo.widget

import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.WIDGETS_DELETED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.widget.experiment.PostCtaExperienceExperiment
import com.duckduckgo.app.widget.ui.AppWidgetCapabilities
import javax.inject.Inject

class SearchWidgetLifecycleDelegate @Inject constructor(
    private val appInstallStore: AppInstallStore,
    private val widgetCapabilities: AppWidgetCapabilities,
    private val pixel: Pixel,
    private val postCtaExperienceExperiment: PostCtaExperienceExperiment,
) {

    suspend fun handleOnWidgetEnabled(widgetSpecificAddedPixel: AppPixelName) {
        if (!appInstallStore.widgetInstalled) {
            appInstallStore.widgetInstalled = true
            pixel.fire(AppPixelName.WIDGETS_ADDED)
        }
        pixel.fire(widgetSpecificAddedPixel)
        postCtaExperienceExperiment.fireSettingsWidgetAdd()
    }

    fun handleOnWidgetDisabled(widgetSpecificDeletedPixel: AppPixelName) {
        if (appInstallStore.widgetInstalled && !widgetCapabilities.hasInstalledWidgets) {
            appInstallStore.widgetInstalled = false
            pixel.fire(WIDGETS_DELETED)
        }
        pixel.fire(widgetSpecificDeletedPixel)
    }
}
