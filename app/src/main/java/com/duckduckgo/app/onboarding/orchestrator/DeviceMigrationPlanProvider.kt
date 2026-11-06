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

package com.duckduckgo.app.onboarding.orchestrator

import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.pixels.AppPixelName.DEVICE_MIGRATION_ALL_SET_PROMPT_BUTTON_CLICKED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.AbortPlan
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Advance
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Stay
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Composes the plan shown once on an install whose data arrived from another phone.
 *
 * It never fires onboarding pixels, enrols in experiments or touches the user's app stage: the user may have
 * finished onboarding years ago on the previous phone, and nothing here may treat them as a new user. Its only
 * pixels are the device migration ones.
 */
class DeviceMigrationPlanProvider @Inject constructor(
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val appBuildConfig: AppBuildConfig,
    private val notificationManager: NotificationManagerCompat,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
) {

    /**
     * [onCompleted] runs however the plan ends, completed or skipped, before the terminal state is published.
     * It must not start another plan: the orchestrator rejects re-entrant calls from plan callbacks.
     */
    fun buildPlan(onCompleted: suspend () -> Unit): LinearOnboardingPlan =
        LinearOnboardingPlan(
            id = MIGRATION_PLAN_ID,
            steps = listOf(
                introAnimationStep(),
                notificationPermissionStep(),
                defaultBrowserStep(),
                allSetStep(),
            ),
            onCompleted = onCompleted,
            onSkipped = onCompleted,
        )

    private fun introAnimationStep() = migrationStep(
        id = NewUserOnboardingStepIds.MIGRATION_INTRO_ANIMATION,
        dialog = NewUserOnboardingActivityDialog.IntroAnimation(false),
    ) { event ->
        if (event is NewUserOnboardingEvent.IntroAnimationFinished) Advance else Stay
    }

    private fun notificationPermissionStep() = migrationStep(
        id = NewUserOnboardingStepIds.MIGRATION_NOTIFICATION_PERMISSION,
        dialog = NewUserOnboardingActivityDialog.NotificationPermission,
        precondition = {
            appBuildConfig.sdkInt >= 33 &&
                !withContext(dispatchers.io()) { notificationManager.areNotificationsEnabled() }
        },
    ) { event ->
        if (event is NewUserOnboardingEvent.NotificationPermissionFinished) Advance else Stay
    }

    // Decided by the detector alone: the role-dialog shown counter may be exhausted by the transferred data,
    // and the phone switch is exactly when the default browser was lost.
    private fun defaultBrowserStep() = migrationStep(
        id = NewUserOnboardingStepIds.MIGRATION_DEFAULT_BROWSER,
        dialog = NewUserOnboardingActivityDialog.MigrationDefaultBrowser,
        precondition = { !withContext(dispatchers.io()) { defaultBrowserDetector.isDefaultBrowser() } },
    ) { event ->
        when (event) {
            is NewUserOnboardingEvent.DefaultBrowserPromptFinished,
            NewUserOnboardingEvent.MigrationDefaultBrowserSkipped,
            -> Advance

            else -> Stay
        }
    }

    private fun allSetStep() = migrationStep(
        id = NewUserOnboardingStepIds.MIGRATION_ALL_SET,
        dialog = NewUserOnboardingActivityDialog.MigrationAllSet,
    ) { event ->
        if (event is NewUserOnboardingEvent.ContinueClicked) {
            pixel.fire(DEVICE_MIGRATION_ALL_SET_PROMPT_BUTTON_CLICKED, type = Unique())
            Advance
        } else {
            Stay
        }
    }

    private fun migrationStep(
        id: String,
        dialog: NewUserOnboardingActivityDialog,
        precondition: suspend () -> Boolean = { true },
        transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition,
    ) = NewUserOnboardingActivityStep(
        id = id,
        pixelName = null,
        precondition = precondition,
        resolveDialog = { dialog },
        transition = { event ->
            if (event is NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked) AbortPlan else transition(event)
        },
    )

    companion object {
        const val MIGRATION_PLAN_ID = "device-migration"
    }
}
