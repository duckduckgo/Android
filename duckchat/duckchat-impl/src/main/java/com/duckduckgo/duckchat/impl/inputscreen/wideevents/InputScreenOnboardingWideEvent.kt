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

package com.duckduckgo.duckchat.impl.inputscreen.wideevents

import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface InputScreenOnboardingWideEvent {
    /**
     * Called when the user enables the Input Screen during onboarding
     */
    suspend fun onInputScreenEnabledDuringOnboarding()

    /**
     * Called when the user enables the Input Screen in AI Features settings before the Input Screen is shown to the user.
     */
    suspend fun onInputScreenSettingEnabledBeforeInputScreenShown()

    /**
     * Called when the Input Screen is shown to the user
     */
    suspend fun onInputScreenShown()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class InputScreenOnboardingWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val duckChatFeature: Lazy<DuckChatFeature>,
    private val dispatchers: DispatcherProvider,
) : InputScreenOnboardingWideEvent {

    private var cachedFlowId: Long? = null

    override suspend fun onInputScreenEnabledDuringOnboarding() {
        if (!isFeatureEnabled()) return

        cachedFlowId = wideEventClient
            .flowStart(
                name = INPUT_SCREEN_ONBOARDING_FEATURE_NAME,
                flowEntryPoint = "onboarding",
            )
            .getOrNull()
    }

    override suspend fun onInputScreenSettingEnabledBeforeInputScreenShown() {
        if (!isFeatureEnabled()) return
        val currentFlowId = getCurrentWideEventId() ?: return

        wideEventClient.flowFinish(
            wideEventId = currentFlowId,
            status = FlowStatus.Cancelled,
        )
        cachedFlowId = null
    }

    override suspend fun onInputScreenShown() {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.flowFinish(
            wideEventId = wideEventId,
            status = FlowStatus.Success,
        )
        cachedFlowId = null
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        duckChatFeature.get().sendInputScreenOnboardingWideEvent().isEnabled()
    }

    private suspend fun getCurrentWideEventId(): Long? {
        if (cachedFlowId == null) {
            cachedFlowId = wideEventClient
                .getFlowIds(INPUT_SCREEN_ONBOARDING_FEATURE_NAME)
                .getOrNull()
                ?.lastOrNull()
        }
        return cachedFlowId
    }

    private companion object {
        const val INPUT_SCREEN_ONBOARDING_FEATURE_NAME = "input-screen-onboarding"
    }
}
