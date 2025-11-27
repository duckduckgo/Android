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
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class OnboardingInputScreenSelectionObserver @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    dispatchers: DispatcherProvider,
    private val userStageStore: UserStageStore,
    private val onboardingStore: OnboardingStore,
    private val duckChat: DuckChat,
) : MainProcessLifecycleObserver {

    init {
        appCoroutineScope.launch(dispatchers.io()) {
            userStageStore.userAppStageFlow()
                .distinctUntilChanged()
                .filter { it == AppStage.ESTABLISHED }
                .collect {
                    onboardingStore.getInputScreenSelection()?.let { selection ->
                        duckChat.setInputScreenUserSetting(selection)
                    }
                }
        }
    }
}
