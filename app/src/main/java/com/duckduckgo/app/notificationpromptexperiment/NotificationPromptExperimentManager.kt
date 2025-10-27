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

import android.annotation.SuppressLint
import android.os.Build
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.notificationpromptexperiment.NotificationPromptExperimentToggles.Cohorts.CONTROL
import com.duckduckgo.app.notificationpromptexperiment.NotificationPromptExperimentToggles.Cohorts.VARIANT_NO_NOTIFICATION_PROMPT
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface NotificationPromptExperimentManager {

    suspend fun enroll()
    fun isControl(): Boolean
    fun isExperimentalNoNotificationPrompt(): Boolean
    suspend fun waitForPrivacyConfig(): Boolean
    suspend fun isWaitForLocalPrivacyConfigEnabled(): Boolean
    suspend fun fireDdgSetAsDefault()
    suspend fun fireNotifyMeClickedLater()
    suspend fun fireNotificationsEnabledLater()
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = NotificationPromptExperimentManager::class,
)
@SingleInstanceIn(AppScope::class)
class NotificationPromptExperimentManagerImpl @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val notificationPromptExperimentToggles: NotificationPromptExperimentToggles,
    private val notificationPromptExperimentPixelsPlugin: NotificationPromptExperimentPixelsPlugin,
    private val pixel: Pixel,
    private val appBuildConfig: AppBuildConfig,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : NotificationPromptExperimentManager, PrivacyConfigCallbackPlugin {

    private var isExperimentEnabled: Boolean? = null
    private var notificationPromptExperimentCohort: NotificationPromptExperimentToggles.Cohorts? = null

    private var privacyPersisted: Boolean = false

    @SuppressLint("DenyListedApi")
    override suspend fun enroll() {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.TIRAMISU && !appBuildConfig.isAppReinstall()) {
            notificationPromptExperimentToggles.notificationPromptExperimentOct25().enroll()
        }
    }

    override fun isControl(): Boolean =
        isExperimentEnabled == true &&
            notificationPromptExperimentCohort == CONTROL

    override fun isExperimentalNoNotificationPrompt(): Boolean =
        isExperimentEnabled == true &&
            notificationPromptExperimentCohort == VARIANT_NO_NOTIFICATION_PROMPT

    override suspend fun waitForPrivacyConfig(): Boolean {
        while (!privacyPersisted) {
            delay(10)
        }
        return true
    }

    override suspend fun isWaitForLocalPrivacyConfigEnabled(): Boolean = notificationPromptExperimentToggles.waitForLocalPrivacyConfig().isEnabled()

    override suspend fun fireDdgSetAsDefault() {
        withContext(dispatcherProvider.io()) {
            notificationPromptExperimentPixelsPlugin.getDdgSetAsDefaultMetric()?.fire()
        }
    }

    override suspend fun fireNotifyMeClickedLater() {
        withContext(dispatcherProvider.io()) {
            notificationPromptExperimentPixelsPlugin.getNotifyMeClickedLaterMetric()?.fire()
        }
    }

    override suspend fun fireNotificationsEnabledLater() {
        withContext(dispatcherProvider.io()) {
            notificationPromptExperimentPixelsPlugin.getNotificationsEnabledLaterMetric()?.fire()
        }
    }

    override fun onPrivacyConfigPersisted() {
        privacyPersisted = true
        coroutineScope.launch {
            setCachedProperties()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        coroutineScope.launch {
            setCachedProperties()
        }
    }

    private suspend fun setCachedProperties() {
        withContext(dispatcherProvider.io()) {
            enroll()
            notificationPromptExperimentCohort = getEnrolledAndEnabledExperimentCohort()
            isExperimentEnabled =
                notificationPromptExperimentToggles.self().isEnabled() && notificationPromptExperimentToggles.notificationPromptExperimentOct25()
                    .isEnabled()
        }
    }

    private suspend fun getEnrolledAndEnabledExperimentCohort(): NotificationPromptExperimentToggles.Cohorts? {
        val cohort = notificationPromptExperimentToggles.notificationPromptExperimentOct25().getCohort()

        return when (cohort?.name) {
            CONTROL.cohortName -> CONTROL
            VARIANT_NO_NOTIFICATION_PROMPT.cohortName -> VARIANT_NO_NOTIFICATION_PROMPT
            else -> null
        }
    }

    private fun MetricsPixel.fire() = getPixelDefinitions().forEach {
        pixel.fire(it.pixelName, it.params)
    }
}
