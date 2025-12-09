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

package com.duckduckgo.app.onboarding

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.wideevents.InputScreenOnboardingWideEvent
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class OnboardingInputScreenSelectionObserver @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val userStageStore: UserStageStore,
    private val onboardingStore: OnboardingStore,
    private val duckChat: DuckChat,
    private val inputScreenOnboardingWideEvent: InputScreenOnboardingWideEvent,
) : MainProcessLifecycleObserver {

    init {
        observeInputScreenSetting()
        setSelectionWhenEstablished()
    }

    private fun observeInputScreenSetting() {
        combine(
            duckChat.observeCosmeticInputScreenUserSettingEnabled(),
            duckChat.observeInputScreenUserSettingEnabled(),
        ) { isCosmeticallyEnabled, isInputScreenEnabled ->
            isCosmeticallyEnabled to isInputScreenEnabled
        }
            .distinctUntilChanged()
            .drop(1)
            .onEach { (isInputScreenCosmeticallyEnabled, isInputScreenEnabled) ->
                if (isInputScreenCosmeticallyEnabled != isInputScreenEnabled) return@onEach

                if (userStageStore.getUserAppStage() != AppStage.ESTABLISHED && onboardingStore.getInputScreenSelection() != null) {
                    onboardingStore.setInputScreenSelectionOverriddenByUser()
                    inputScreenOnboardingWideEvent.onInputScreenSettingEnabledBeforeInputScreenShown(enabled = isInputScreenEnabled)
                }
            }
            .flowOn(dispatchers.io())
            .launchIn(appCoroutineScope)
    }

    private fun setSelectionWhenEstablished() {
        userStageStore.userAppStageFlow()
            .distinctUntilChanged()
            .filter { it == AppStage.ESTABLISHED }
            .onEach {
                val selection = onboardingStore.getInputScreenSelection()
                if (!onboardingStore.isInputScreenSelectionOverriddenByUser() && selection != null) {
                    duckChat.setInputScreenUserSetting(selection)
                }
            }
            .flowOn(dispatchers.io())
            .launchIn(appCoroutineScope)
    }
}
