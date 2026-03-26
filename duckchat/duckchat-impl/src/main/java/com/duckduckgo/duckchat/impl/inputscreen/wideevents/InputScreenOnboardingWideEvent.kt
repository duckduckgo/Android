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

import android.annotation.SuppressLint
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import javax.inject.Inject

interface InputScreenOnboardingWideEvent {
    /**
     * Called when the user enables the Input Screen during onboarding
     * @param reinstallUser true if the user tapped "I've been here before", false otherwise
     */
    fun onInputScreenEnabledDuringOnboarding(reinstallUser: Boolean = false)

    /**
     * Called when the user enables the Input Screen in AI Features settings before the Input Screen is shown to the user.
     * @param enabled true if the Input Screen is enabled, false otherwise
     */
    fun onInputScreenSettingEnabledBeforeInputScreenShown(enabled: Boolean)

    /**
     * Called when the Input Screen is shown to the user
     */
    fun onInputScreenShown()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class InputScreenOnboardingWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val duckChatFeature: Lazy<DuckChatFeature>,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : InputScreenOnboardingWideEvent {

    // This is to ensure modifications of the wide event are serialized
    @SuppressLint("AvoidComputationUsage")
    private val coroutineScope = CoroutineScope(
        context = appCoroutineScope.coroutineContext +
            dispatchers.computation().limitedParallelism(1),
    )

    private var cachedFlowId: Long? = null

    override fun onInputScreenEnabledDuringOnboarding(reinstallUser: Boolean) {
        coroutineScope.launch {
            if (!isFeatureEnabled()) return@launch

            cachedFlowId = wideEventClient
                .flowStart(
                    name = INPUT_SCREEN_ONBOARDING_FEATURE_NAME,
                    flowEntryPoint = "onboarding",
                    metadata = mapOf("reinstallUser" to reinstallUser.toString()),
                    cleanupPolicy = CleanupPolicy.OnTimeout(
                        duration = Duration.ofDays(30),
                        flowStatus = FlowStatus.Unknown,
                    ),
                )
                .getOrNull()
        }
    }

    override fun onInputScreenSettingEnabledBeforeInputScreenShown(enabled: Boolean) {
        coroutineScope.launch {
            if (!isFeatureEnabled()) return@launch
            val currentFlowId = getCurrentWideEventId() ?: return@launch

            wideEventClient.flowFinish(
                wideEventId = currentFlowId,
                status = FlowStatus.Cancelled,
                metadata = mapOf("enabled" to enabled.toString()),
            )
            cachedFlowId = null
        }
    }

    override fun onInputScreenShown() {
        coroutineScope.launch {
            if (!isFeatureEnabled()) return@launch
            val wideEventId = getCurrentWideEventId() ?: return@launch

            wideEventClient.flowFinish(
                wideEventId = wideEventId,
                status = FlowStatus.Success,
            )
            cachedFlowId = null
        }
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
