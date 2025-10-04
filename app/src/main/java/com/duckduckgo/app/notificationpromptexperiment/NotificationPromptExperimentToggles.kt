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

package com.duckduckgo.app.notificationpromptexperiment

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.notificationpromptexperiment.NotificationPromptExperimentToggles.Companion.BASE_EXPERIMENT_NAME
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
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
interface NotificationPromptExperimentToggles {

    /**
     * Toggle to enable/disable the "self" notification prompt experiment.
     * Default value: false (disabled).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    /**
     * Toggle to enable/disable the "sub-feature" notification prompt experiment.
     * Default value: false (disabled).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun notificationPromptExperimentOct25(): Toggle

    /**
     * Toggle to enable/disable the "sub-feature" waitForLocalPrivacyConfig.
     * Default value: true (enabled).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun waitForLocalPrivacyConfig(): Toggle

    enum class Cohorts(override val cohortName: String) : CohortName {
        // Current experience, where the Notification Prompt is shown at app start after fresh install.
        CONTROL("control"),

        // New experience, where the Notification Prompt is never shown at app start after fresh install.
        VARIANT_NO_NOTIFICATION_PROMPT("experimentalNoNotificationPrompt"),
    }

    companion object {
        internal const val BASE_EXPERIMENT_NAME = "notificationPromptExperiment"
    }
}

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class NotificationPromptExperimentPixelsPlugin @Inject constructor(
    private val toggles: NotificationPromptExperimentToggles,
) : MetricsPixelPlugin {

    override suspend fun getMetrics(): List<MetricsPixel> {
        return listOf(
            MetricsPixel(
                metric = METRIC_NOTIFICATION_PROMPT_DDG_SET_AS_DEFAULT,
                value = "1",
                toggle = toggles.notificationPromptExperimentOct25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ),
            ),
            MetricsPixel(
                metric = METRIC_NOTIFICATION_PROMPT_NOTIFY_ME_CLICKED_LATER,
                value = "1",
                toggle = toggles.notificationPromptExperimentOct25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                    ConversionWindow(lowerWindow = 1, upperWindow = 1),
                    ConversionWindow(lowerWindow = 2, upperWindow = 7),
                    ConversionWindow(lowerWindow = 8, upperWindow = 14),
                ),
            ),
            MetricsPixel(
                metric = METRIC_NOTIFICATION_PROMPT_NOTIFICATIONS_ENABLED_LATER,
                value = "1",
                toggle = toggles.notificationPromptExperimentOct25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                    ConversionWindow(lowerWindow = 1, upperWindow = 1),
                    ConversionWindow(lowerWindow = 2, upperWindow = 7),
                    ConversionWindow(lowerWindow = 8, upperWindow = 14),
                ),
            ),
        )
    }

    suspend fun getDdgSetAsDefaultMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_NOTIFICATION_PROMPT_DDG_SET_AS_DEFAULT }
    }

    suspend fun getNotifyMeClickedLaterMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_NOTIFICATION_PROMPT_NOTIFY_ME_CLICKED_LATER }
    }

    suspend fun getNotificationsEnabledLaterMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_NOTIFICATION_PROMPT_NOTIFICATIONS_ENABLED_LATER }
    }

    companion object {
        internal const val METRIC_NOTIFICATION_PROMPT_DDG_SET_AS_DEFAULT = "ddgSetAsDefault"
        internal const val METRIC_NOTIFICATION_PROMPT_NOTIFY_ME_CLICKED_LATER = "notifyMeClickedLater"
        internal const val METRIC_NOTIFICATION_PROMPT_NOTIFICATIONS_ENABLED_LATER = "notificationsEnabledLater"
    }
}
