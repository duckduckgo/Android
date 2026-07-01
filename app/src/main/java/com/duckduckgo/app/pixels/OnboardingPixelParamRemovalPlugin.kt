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

package com.duckduckgo.app.pixels

import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class OnboardingPixelParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {

    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            OnboardingPixelName.ONBOARDING_QUICK_SETUP.pixelName to PixelParameter.removeAtb(),
            OnboardingPixelName.ONBOARDING_WELCOME.pixelName to PixelParameter.removeAtb(),
            OnboardingPixelName.ONBOARDING_SET_DEFAULT.pixelName to PixelParameter.removeAtb(),
            OnboardingPixelName.ONBOARDING_ADDRESS_BAR_POSITION.pixelName to PixelParameter.removeAtb(),
            OnboardingPixelName.ONBOARDING_SEARCH_EXPERIENCE.pixelName to PixelParameter.removeAtb(),
            OnboardingPixelName.ONBOARDING_SKIP_ONBOARDING.pixelName to PixelParameter.removeAtb(),
            OnboardingPixelName.ONBOARDING_NOTIFICATIONS.pixelName to PixelParameter.removeAtb(),
            OnboardingPixelName.ONBOARDING_SEARCH_CHAT_TOGGLE.pixelName to PixelParameter.removeAtb(),
            OnboardingPixelName.ONBOARDING_AI_INTRO.pixelName to PixelParameter.removeAtb(),
            OnboardingPixelName.ONBOARDING_FIRE_BUTTON.pixelName to PixelParameter.removeAtb(),
        )
    }
}
