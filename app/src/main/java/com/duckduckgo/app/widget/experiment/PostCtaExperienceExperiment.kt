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

package com.duckduckgo.app.widget.experiment

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.widget.experiment.PostCtaExperienceToggles.Cohorts.CONTROL
import com.duckduckgo.app.widget.experiment.PostCtaExperienceToggles.Cohorts.VARIANT_SIMPLE_SEARCH_WIDGET_PROMPT
import com.duckduckgo.app.widget.experiment.store.WidgetSearchCountDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.PixelDefinition
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface PostCtaExperienceExperiment {
    suspend fun enroll()
    suspend fun isControl(): Boolean
    suspend fun isSimpleSearchWidgetPrompt(): Boolean

    suspend fun fireSettingsWidgetDisplay()
    suspend fun fireSettingsWidgetAdd()

    suspend fun fireSettingsWidgetDismiss()
    suspend fun fireWidgetSearch()
    suspend fun fireWidgetSearchXCount()
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = PostCtaExperienceExperiment::class,
)
@SingleInstanceIn(AppScope::class)
class PostCtaExperienceExperimentImpl @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val postCtaExperienceToggles: PostCtaExperienceToggles,
    private val postCtaExperiencePixelsPlugin: PostCtaExperiencePixelsPlugin,
    private val pixel: Pixel,
    private val widgetSearchCountDataStore: WidgetSearchCountDataStore,
) : PostCtaExperienceExperiment {

    override suspend fun enroll() {
        postCtaExperienceToggles.postCtaExperienceExperimentJun25().enroll()
    }

    override suspend fun isControl(): Boolean =
        postCtaExperienceToggles.postCtaExperienceExperimentJun25().isEnrolledAndEnabled(CONTROL)

    override suspend fun isSimpleSearchWidgetPrompt(): Boolean =
        postCtaExperienceToggles.postCtaExperienceExperimentJun25().isEnrolledAndEnabled(VARIANT_SIMPLE_SEARCH_WIDGET_PROMPT)

    override suspend fun fireSettingsWidgetDisplay() {
        withContext(dispatcherProvider.io()) {
            postCtaExperiencePixelsPlugin.getSettingsWidgetDisplayMetric()?.fire()
        }
    }

    override suspend fun fireSettingsWidgetAdd() {
        withContext(dispatcherProvider.io()) {
            postCtaExperiencePixelsPlugin.getSettingsWidgetAddMetric()?.fire()
        }
    }

    override suspend fun fireSettingsWidgetDismiss() {
        withContext(dispatcherProvider.io()) {
            postCtaExperiencePixelsPlugin.getSettingsWidgetDismissMetric()?.fire()
        }
    }

    override suspend fun fireWidgetSearch() {
        withContext(dispatcherProvider.io()) {
            postCtaExperiencePixelsPlugin.getWidgetSearchMetric()?.fire()
        }
    }

    override suspend fun fireWidgetSearchXCount() {
        withContext(dispatcherProvider.io()) {
            postCtaExperiencePixelsPlugin.getWidgetSearch3xMetric()?.getPixelDefinitions()?.forEach { definition ->
                if (isInConversionWindow(definition)) {
                    widgetSearchCountDataStore.getMetricForPixelDefinition(definition).takeIf { it < 3 }?.let {
                        widgetSearchCountDataStore.increaseMetricForPixelDefinition(definition).takeIf { it == 3 }?.apply {
                            pixel.fire(definition.pixelName, definition.params)
                        }
                    }
                }
            }

            postCtaExperiencePixelsPlugin.getWidgetSearch5xMetric()?.getPixelDefinitions()?.forEach { definition ->
                if (isInConversionWindow(definition)) {
                    widgetSearchCountDataStore.getMetricForPixelDefinition(definition).takeIf { it < 5 }?.let {
                        widgetSearchCountDataStore.increaseMetricForPixelDefinition(definition).takeIf { it == 5 }?.apply {
                            pixel.fire(definition.pixelName, definition.params)
                        }
                    }
                }
            }
        }
    }

    private fun isInConversionWindow(definition: PixelDefinition): Boolean {
        val enrollmentDate = definition.params["enrollmentDate"] ?: return false
        val lowerWindow = definition.params["conversionWindowDays"]?.split("-")?.first()?.toInt() ?: return false
        val upperWindow = definition.params["conversionWindowDays"]?.split("-")?.last()?.toInt() ?: return false
        val daysDiff = daysBetweenTodayAnd(enrollmentDate)

        return (daysDiff in lowerWindow..upperWindow)
    }

    private fun daysBetweenTodayAnd(date: String): Long {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York"))
        val localDate = LocalDate.parse(date)
        val zoneDateTime: ZonedDateTime = localDate.atStartOfDay(ZoneId.of("America/New_York"))
        return ChronoUnit.DAYS.between(zoneDateTime, today)
    }

    private fun MetricsPixel.fire() = getPixelDefinitions().forEach {
        pixel.fire(it.pixelName, it.params)
    }
}
