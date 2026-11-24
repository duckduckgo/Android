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

package com.duckduckgo.widget

import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.nextsteps.NextStepsAdoptionMetrics
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.widget.api.HomeScreenWidgetLifecycle
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealHomeScreenWidgetLifecycle @Inject constructor(
    private val appInstallStore: AppInstallStore,
    private val widgetCapabilities: WidgetCapabilities,
    private val pixel: Pixel,
    private val nextStepsAdoptionMetrics: NextStepsAdoptionMetrics,
) : HomeScreenWidgetLifecycle {

    override fun onWidgetEnabled() {
        if (!appInstallStore.widgetInstalled) {
            appInstallStore.widgetInstalled = true
            pixel.fire(AppPixelName.WIDGETS_ADDED)
        }
        nextStepsAdoptionMetrics.onWidgetAdded()
    }

    override fun onWidgetDisabled() {
        if (appInstallStore.widgetInstalled && !widgetCapabilities.hasInstalledWidgets) {
            appInstallStore.widgetInstalled = false
            pixel.fire(AppPixelName.WIDGETS_DELETED)
        }
    }
}
