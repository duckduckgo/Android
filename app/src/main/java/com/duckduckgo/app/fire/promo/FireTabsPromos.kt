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

package com.duckduckgo.app.fire.promo

import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.onboarding.OnboardingFlowChecker
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface FireTabsPromos {
    suspend fun canShowNtpPromo(): Boolean
    suspend fun canShowTabSwitcherPromo(): Boolean
    suspend fun onNtpPromoInteracted()
    suspend fun onTabSwitcherPromoShown()
    suspend fun onUserBurned()
    suspend fun onFireModeEntered()
}

@ContributesBinding(AppScope::class)
class RealFireTabsPromos @Inject constructor(
    private val fireModeAvailability: FireModeAvailability,
    private val fireTabsPromoFeature: FireTabsPromoFeature,
    private val fireDataStore: FireDataStore,
    private val onboardingFlowChecker: OnboardingFlowChecker,
    private val browserModeStateHolder: BrowserModeStateHolder,
    private val dispatchers: DispatcherProvider,
) : FireTabsPromos {

    override suspend fun canShowNtpPromo(): Boolean = withContext(dispatchers.io()) {
        commonGate() &&
            !fireDataStore.isNtpPromoDismissed() &&
            fireDataStore.hasUserBurnedWhileBrowsing()
    }

    override suspend fun canShowTabSwitcherPromo(): Boolean = withContext(dispatchers.io()) {
        commonGate() && !fireDataStore.isTabSwitcherPromoDismissed()
    }

    override suspend fun onNtpPromoInteracted() {
        withContext(dispatchers.io()) { fireDataStore.setNtpPromoDismissed(true) }
    }

    override suspend fun onTabSwitcherPromoShown() {
        withContext(dispatchers.io()) { fireDataStore.setTabSwitcherPromoDismissed(true) }
    }

    override suspend fun onUserBurned() {
        withContext(dispatchers.io()) { fireDataStore.setUserBurnedWhileBrowsing(true) }
    }

    override suspend fun onFireModeEntered() {
        withContext(dispatchers.io()) {
            fireDataStore.setNtpPromoDismissed(true)
            fireDataStore.setTabSwitcherPromoDismissed(true)
        }
    }

    private suspend fun commonGate(): Boolean =
        fireModeAvailability.isAvailable() &&
            fireTabsPromoFeature.self().isEnabled() &&
            browserModeStateHolder.currentMode.value == BrowserMode.REGULAR &&
            onboardingFlowChecker.isOnboardingComplete()
}
