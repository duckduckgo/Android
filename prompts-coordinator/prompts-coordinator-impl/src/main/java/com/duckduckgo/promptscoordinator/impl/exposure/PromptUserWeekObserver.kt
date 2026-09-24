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

package com.duckduckgo.promptscoordinator.impl.exposure

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.onboarding.OnboardingFlowChecker
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.promptscoordinator.impl.exposure.PromptExposurePixelParams.DAYS_SINCE_INSTALL
import com.duckduckgo.promptscoordinator.impl.exposure.PromptExposurePixelParams.OPENS_PREV_WEEK
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Counts app opens and fires the weekly denominator for the prompt exposure pixels.
 *
 * The denominator fires on the first foreground of each install-week rather than for the week just
 * gone: rollover only happens for users who return, so a previous-week pixel would never fire for
 * anyone who churns while their exposures already had.
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class PromptUserWeekObserver @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val weekTracker: PromptExposureWeekTracker,
    private val onboardingFlowChecker: OnboardingFlowChecker,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            val appOpen = weekTracker.recordAppOpen() ?: return@launch
            // The unique tag is only consumed when the pixel fires, so a user finishing onboarding
            // mid-week is still counted that week.
            if (!onboardingFlowChecker.isOnboardingComplete()) return@launch

            pixel.fire(
                PromptExposurePixelName.PROMPT_USER_WEEK,
                mapOf(
                    DAYS_SINCE_INSTALL to daysSinceInstallBucket(appOpen.daysSinceInstall),
                    OPENS_PREV_WEEK to opensPrevWeekBucket(appOpen.opensPrevWeek),
                ),
                type = Pixel.PixelType.Unique(tag = "prompt_user_week_${appOpen.weekIndex}"),
            )
        }
    }
}
