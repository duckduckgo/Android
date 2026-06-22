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

package com.duckduckgo.app.onboarding

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.AppUserStageStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

enum class CustomAiOnboardingPixelName(override val pixelName: String) : Pixel.PixelName {
    PLAN_STARTED("onboarding_ai_started"),
    PLAN_FINISHED("onboarding_ai_finished"),
    AI_COMPARISON_SCREEN_SHOW("onboarding_ai_ai-comparison-screen-shown"),
    RETURNING_SYNC_USER_IGNORED("onboarding_ai_returning-sync-user-ignored"),
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class CustomAiOnboardingPixels @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    appUserStageStore: AppUserStageStore,
    private val customAiOnboardingStore: CustomAiOnboardingStore,
    private val pixel: Pixel,
) : MainProcessLifecycleObserver {
    init {
        appUserStageStore.userAppStageFlow().onEach {
            if (it == AppStage.ESTABLISHED && customAiOnboardingStore.isEnabled()) {
                pixel.fire(CustomAiOnboardingPixelName.PLAN_FINISHED, type = Pixel.PixelType.Unique())
            }
        }.launchIn(coroutineScope)
    }
}

@ContributesMultibinding(AppScope::class)
class CustomAiOnboardingPixelRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            CustomAiOnboardingPixelName.PLAN_STARTED.pixelName to PixelParameter.removeAtb(),
            CustomAiOnboardingPixelName.PLAN_FINISHED.pixelName to PixelParameter.removeAtb(),
            CustomAiOnboardingPixelName.AI_COMPARISON_SCREEN_SHOW.pixelName to PixelParameter.removeAtb(),
            CustomAiOnboardingPixelName.RETURNING_SYNC_USER_IGNORED.pixelName to PixelParameter.removeAtb(),
        )
    }
}
