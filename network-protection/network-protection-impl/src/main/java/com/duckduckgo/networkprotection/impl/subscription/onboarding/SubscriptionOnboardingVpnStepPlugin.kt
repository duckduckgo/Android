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

package com.duckduckgo.networkprotection.impl.subscription.onboarding

import androidx.fragment.app.Fragment
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

/** VPN step of the subscription onboarding, contributed from `network-protection-impl`. */
@ContributesMultibinding(AppScope::class)
@PriorityKey(200)
class SubscriptionOnboardingVpnStepPlugin @Inject constructor() : SubscriptionOnboardingStepPlugin {

    override val stepId: String = VPN_STEP_ID

    override val titleResId: Int = R.string.subscriptionOnboardingVpnTitle

    override suspend fun shouldShow(): Boolean = true

    override fun createFragment(): Fragment = SubscriptionOnboardingVpnFragment()

    companion object {
        const val VPN_STEP_ID = "vpn"
    }
}
