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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.promptscoordinator.api.PromptExposureReporter
import com.duckduckgo.promptscoordinator.impl.exposure.PromptExposurePixelParams.DAYS_SINCE_INSTALL
import com.duckduckgo.promptscoordinator.impl.exposure.PromptExposurePixelParams.NTH_IN_WEEK
import com.duckduckgo.promptscoordinator.impl.exposure.PromptExposurePixelParams.PROMPT_ID
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPromptExposureReporter @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val weekTracker: PromptExposureWeekTracker,
    private val session: PromptExposureSession,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
) : PromptExposureReporter {

    override fun reportPromptShown(promptId: String) {
        // Before launching, so a prompt reported just as the app goes to the background still counts
        // for the session it was shown in.
        session.markPromptShown()
        appCoroutineScope.launch(dispatchers.io()) {
            val exposure = weekTracker.recordPromptShown() ?: return@launch
            fireExposure(promptId, exposure)
        }
    }

    override fun reportNewTabPageCardShown(messageId: String) {
        appCoroutineScope.launch(dispatchers.io()) {
            val exposure = weekTracker.recordNtpCardShown(messageId) ?: return@launch
            // The card can sit on the page for days: only the session that first counts it was prompted.
            session.markPromptShown()
            fireExposure(REMOTE_MESSAGE_CARD_PROMPT_ID, exposure)
        }
    }

    private fun fireExposure(promptId: String, exposure: PromptExposureWeekTracker.Exposure) {
        val daysSinceInstall = daysSinceInstallBucket(exposure.daysSinceInstall)
        val boundedPromptId = promptId.takeIf { it in KNOWN_PROMPT_IDS } ?: OTHER_PROMPT_ID.also {
            logcat { "PromptExposureReporter: unregistered prompt id '$promptId' sent as '$OTHER_PROMPT_ID'" }
        }

        pixel.firePromptExposurePixel(
            PromptExposurePixelName.PROMPT_EXPOSURE,
            DAYS_SINCE_INSTALL to daysSinceInstall,
            NTH_IN_WEEK to nthInWeekBucket(exposure.nthInWeek),
        )
        pixel.firePromptExposurePixel(
            PromptExposurePixelName.PROMPT_SHOWN,
            DAYS_SINCE_INSTALL to daysSinceInstall,
            PROMPT_ID to boundedPromptId,
        )
    }
}
