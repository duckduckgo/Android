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

package com.duckduckgo.subscriptions.impl.onboarding.experiment

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature.SubscriptionOnboardingCohorts.TREATMENT
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface SubscriptionOnboardingExperiments {

    /**
     * Enrolls the experiment matching the purchase type, assigning a cohort.
     */
    suspend fun enroll(isFreeTrial: Boolean)

    /** Whether the user is already enrolled in the treatment cohort of either experiment (no new enrollment). */
    suspend fun isTreatment(): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSubscriptionOnboardingExperiments @Inject constructor(
    private val subscriptionsFeature: SubscriptionsFeature,
    private val dispatcherProvider: DispatcherProvider,
) : SubscriptionOnboardingExperiments {

    override suspend fun enroll(isFreeTrial: Boolean) {
        withContext(dispatcherProvider.io()) {
            experimentFor(isFreeTrial).enroll()
        }
    }

    override suspend fun isTreatment(): Boolean = withContext(dispatcherProvider.io()) {
        subscriptionsFeature.subscriptionOnboardingFreeTrialsOct2026().isEnrolledAndEnabled(TREATMENT) ||
            subscriptionsFeature.subscriptionOnboardingPaidSubsOct2026().isEnrolledAndEnabled(TREATMENT)
    }

    private fun experimentFor(isFreeTrial: Boolean): Toggle =
        if (isFreeTrial) {
            subscriptionsFeature.subscriptionOnboardingFreeTrialsOct2026()
        } else {
            subscriptionsFeature.subscriptionOnboardingPaidSubsOct2026()
        }
}
