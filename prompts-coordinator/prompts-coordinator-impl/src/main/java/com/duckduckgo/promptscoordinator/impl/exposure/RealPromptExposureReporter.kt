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
import com.duckduckgo.promptscoordinator.impl.exposure.PromptExposurePixelParams.OPENS_PREV_WEEK
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
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
) : PromptExposureReporter {

    override fun reportPromptShown(promptId: String) {
        appCoroutineScope.launch(dispatchers.io()) {
            val exposure = weekTracker.recordPromptShown() ?: return@launch
            fireExposure(promptId, exposure)
        }
    }

    override fun reportNewTabPageCardShown(messageId: String) {
        appCoroutineScope.launch(dispatchers.io()) {
            val exposure = weekTracker.recordNtpCardShown(messageId) ?: return@launch
            fireExposure(REMOTE_MESSAGE_CARD_PROMPT_ID, exposure)
        }
    }

    private fun fireExposure(promptId: String, exposure: PromptExposureWeekTracker.Exposure) {
        val daysSinceInstall = daysSinceInstallBucket(exposure.daysSinceInstall)
        val boundedPromptId = promptId.takeIf { it in KNOWN_PROMPT_IDS } ?: OTHER_PROMPT_ID.also {
            logcat { "PromptExposureReporter: unregistered prompt id '$promptId' sent as '$OTHER_PROMPT_ID'" }
        }

        pixel.fire(
            PromptExposurePixelName.PROMPT_EXPOSURE,
            mapOf(
                DAYS_SINCE_INSTALL to daysSinceInstall,
                NTH_IN_WEEK to nthInWeekBucket(exposure.nthInWeek),
                OPENS_PREV_WEEK to opensPrevWeekBucket(exposure.opensPrevWeek),
            ),
        )
        pixel.fire(
            PromptExposurePixelName.PROMPT_SHOWN,
            mapOf(
                DAYS_SINCE_INSTALL to daysSinceInstall,
                PROMPT_ID to boundedPromptId,
            ),
        )
    }
}
