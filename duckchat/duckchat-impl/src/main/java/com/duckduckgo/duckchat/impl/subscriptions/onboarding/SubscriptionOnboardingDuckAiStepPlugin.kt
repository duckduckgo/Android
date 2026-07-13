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

package com.duckduckgo.duckchat.impl.subscriptions.onboarding

import androidx.fragment.app.Fragment
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

/** Duck.ai step of the subscription onboarding, contributed from `duckchat-impl`. */
@ContributesMultibinding(AppScope::class)
@PriorityKey(300)
class SubscriptionOnboardingDuckAiStepPlugin @Inject constructor() : SubscriptionOnboardingStepPlugin {

    override val stepId: String = DUCK_AI_STEP_ID

    override val titleResId: Int = R.string.subscriptionOnboardingDuckAiTitle

    override suspend fun shouldShow(): Boolean = true

    override fun createFragment(): Fragment = SubscriptionOnboardingDuckAiFragment()

    companion object {
        const val DUCK_AI_STEP_ID = "duck_ai"
    }
}
