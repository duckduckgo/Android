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

package com.duckduckgo.app.browser.defaultbrowsing.prompts.ui.experiment

import com.duckduckgo.app.browser.defaultbrowsing.prompts.ui.experiment.OnboardingHomeScreenWidgetToggles.Cohorts.CONTROL
import com.duckduckgo.app.browser.defaultbrowsing.prompts.ui.experiment.OnboardingHomeScreenWidgetToggles.Cohorts.VARIANT_ONBOARDING_HOME_SCREEN_WIDGET_PROMPT
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.widget.experiment.store.WidgetSearchCountDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface OnboardingHomeScreenWidgetExperiment {
    suspend fun enroll()
    suspend fun isControl(): Boolean
    suspend fun isOnboardingHomeScreenWidgetExperiment(): Boolean

    suspend fun fireOnboardingWidgetDisplay()
    suspend fun fireOnboardingWidgetAdd()
    suspend fun fireOnboardingWidgetDismiss()
    suspend fun fireWidgetSearch()
    suspend fun fireWidgetSearchXCount()
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = OnboardingHomeScreenWidgetExperiment::class,
)
@SingleInstanceIn(AppScope::class)
class OnboardingHomeScreenWidgetExperimentImpl @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val onboardingHomeScreenWidgetToggles: OnboardingHomeScreenWidgetToggles,
    private val onboardingHomeScreenWidgetPixelsPlugin: OnboardingHomeScreenWidgetPixelsPlugin,
    private val pixel: Pixel,
    private val widgetSearchCountDataStore: WidgetSearchCountDataStore,
) : OnboardingHomeScreenWidgetExperiment {

    override suspend fun enroll() {
        onboardingHomeScreenWidgetToggles.onboardingHomeScreenWidgetExperimentJun25().enroll()
    }

    override suspend fun isControl(): Boolean =
        onboardingHomeScreenWidgetToggles.onboardingHomeScreenWidgetExperimentJun25().isEnrolledAndEnabled(CONTROL)

    override suspend fun isOnboardingHomeScreenWidgetExperiment(): Boolean =
        onboardingHomeScreenWidgetToggles.onboardingHomeScreenWidgetExperimentJun25().isEnrolledAndEnabled(
            VARIANT_ONBOARDING_HOME_SCREEN_WIDGET_PROMPT,
        )

    override suspend fun fireOnboardingWidgetDisplay() {
        withContext(dispatcherProvider.io()) {
            onboardingHomeScreenWidgetPixelsPlugin.getOnboardingWidgetDisplayMetric()?.fire()
        }
    }

    override suspend fun fireOnboardingWidgetAdd() {
        withContext(dispatcherProvider.io()) {
            onboardingHomeScreenWidgetPixelsPlugin.getOnboardingWidgetAddMetric()?.fire()
        }
    }

    override suspend fun fireOnboardingWidgetDismiss() {
        withContext(dispatcherProvider.io()) {
            onboardingHomeScreenWidgetPixelsPlugin.getOnboardingWidgetDismissMetric()?.fire()
        }
    }

    override suspend fun fireWidgetSearch() {
        withContext(dispatcherProvider.io()) {
            onboardingHomeScreenWidgetPixelsPlugin.getWidgetSearchMetric()?.fire()
        }
    }

    override suspend fun fireWidgetSearchXCount() {
        // TODO ANA implement this.
    }

    private fun MetricsPixel.fire() = getPixelDefinitions().forEach {
        pixel.fire(it.pixelName, it.params)
    }
}
