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

package com.duckduckgo.app.cta.ui

import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Decides whether the Privacy Pro promo modal is currently eligible to be shown, and with which
 * flow and copy. This is the single source of truth shared by the modal coordinator's
 * [com.duckduckgo.app.cta.ui.SubscriptionPromoModalEvaluator] and by [CtaViewModel] (which uses it
 * to know when a pending promo should suppress other onboarding surfaces).
 *
 * The eligibility rules mirror the historical `getPromoCtaOnForeground` logic exactly.
 */
interface SubscriptionPromoModalDecider {

    /**
     * @return the promo to show, or null when no promo is currently eligible.
     */
    suspend fun decide(): SubscriptionPromoModalDecision?
}

data class SubscriptionPromoModalDecision(
    val flow: SubscriptionPromoFlow,
    val isFreeTrialCopy: Boolean,
)

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSubscriptionPromoModalDecider @Inject constructor(
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles,
    private val appInstallStore: AppInstallStore,
    private val settingsDataStore: SettingsDataStore,
    private val dismissedCtaDao: DismissedCtaDao,
    private val subscriptions: Subscriptions,
    private val dispatchers: DispatcherProvider,
) : SubscriptionPromoModalDecider {

    override suspend fun decide(): SubscriptionPromoModalDecision? = withContext(dispatchers.io()) {
        when {
            canShowSubscriptionCtaForSkippedOnboarding() -> SubscriptionPromoModalDecision(
                flow = SubscriptionPromoFlow.SKIPPED_ONBOARDING,
                isFreeTrialCopy = freeTrialCopyAvailable(),
            )

            canShowSubscriptionPromoCta() -> SubscriptionPromoModalDecision(
                flow = SubscriptionPromoFlow.NUDGE,
                isFreeTrialCopy = freeTrialCopyAvailable(),
            )

            else -> null
        }
    }

    private suspend fun canShowSubscriptionCtaForSkippedOnboarding(): Boolean =
        extendedOnboardingFeatureToggles.subscriptionPromoModalCta().isEnabled() &&
            settingsDataStore.hideTips &&
            appInstallStore.daysInstalled() >= SUBSCRIPTION_SKIPPED_ONBOARDING_MIN_DAYS &&
            !subscriptionPromoModalShown() &&
            isSubscriptionCtaAvailable()

    private suspend fun canShowSubscriptionPromoCta(): Boolean =
        extendedOnboardingFeatureToggles.subscriptionPromoModalCtaExistingUsers().isEnabled() &&
            appInstallStore.daysInstalled() >= SUBSCRIPTION_SKIPPED_ONBOARDING_MIN_DAYS &&
            !subscriptionPromoModalShown() &&
            isSubscriptionCtaAvailable()

    private fun subscriptionPromoModalShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_INTRO_PRIVACY_PRO)

    private suspend fun isSubscriptionCtaAvailable(): Boolean =
        subscriptions.isEligible() &&
            subscriptions.getSubscriptionStatus() == SubscriptionStatus.UNKNOWN &&
            extendedOnboardingFeatureToggles.privacyProCta().isEnabled()

    private suspend fun freeTrialCopyAvailable(): Boolean =
        extendedOnboardingFeatureToggles.freeTrialCopy().isEnabled() && subscriptions.isFreeTrialEligible()

    companion object {
        private const val SUBSCRIPTION_SKIPPED_ONBOARDING_MIN_DAYS = 7L
    }
}
