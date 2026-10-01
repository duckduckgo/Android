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
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.promptscoordinator.impl.exposure.PromptExposurePixelParams.DAYS_SINCE_INSTALL
import com.duckduckgo.promptscoordinator.impl.exposure.PromptExposurePixelParams.PROMPT_SHOWN
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/** A session ends once the app has stayed in the background this long, so a quick app switch does not split one visit into two. */
internal val MIN_BACKGROUND = 60.seconds

internal const val SESSION_SAMPLE_RATE = 0.05

/**
 * The weekly denominator fires on the first foreground of each install-week rather than for the week just
 * gone: a previous-week pixel would never fire for users who churn, while their exposures already had.
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class PromptExposureLifecycleObserver @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val weekTracker: PromptExposureWeekTracker,
    private val session: PromptExposureSession,
    private val onboardingFlowChecker: OnboardingFlowChecker,
    private val appInstall: AppInstall,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val random: Random = Random.Default,
) : MainProcessLifecycleObserver {

    private var pendingSessionEnd: Job? = null

    override fun onStart(owner: LifecycleOwner) {
        pendingSessionEnd?.cancel()
        pendingSessionEnd = null
    }

    override fun onResume(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            val week = weekTracker.currentWeek() ?: return@launch
            if (!onboardingFlowChecker.isOnboardingComplete()) return@launch

            pixel.firePromptExposurePixel(
                PromptExposurePixelName.PROMPT_USER_WEEK,
                DAYS_SINCE_INSTALL to daysSinceInstallBucket(week.daysSinceInstall),
                type = Pixel.PixelType.Unique(tag = "prompt_user_week_${week.weekIndex}"),
            )
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        pendingSessionEnd = appCoroutineScope.launch(dispatchers.io()) {
            delay(MIN_BACKGROUND)
            withContext(NonCancellable) { endSession() }
        }
    }

    private suspend fun endSession() {
        val promptShown = session.consumePromptShown()
        val sampledIn = random.nextDouble() < SESSION_SAMPLE_RATE
        if (!sampledIn || !onboardingFlowChecker.isOnboardingComplete()) return
        val daysSinceInstall = appInstall.getInstallAge()?.inWholeDays ?: return

        pixel.firePromptExposurePixel(
            PromptExposurePixelName.PROMPT_SESSION,
            DAYS_SINCE_INSTALL to daysSinceInstallBucket(daysSinceInstall),
            PROMPT_SHOWN to promptShown.toString(),
        )
    }
}
