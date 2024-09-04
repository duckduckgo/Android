/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message

import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore

class FakeVPNStore(
    onboardingShown: Boolean = false,
    appTpEnabledCtaShown: Boolean = false,
    onboardingSession: Boolean = false,
    notifyMeInAppTpDismissed: Boolean = false,
    pproUpsellBannerDismissed: Boolean = false,
) : VpnStore {
    private var _onboardingShown: Boolean = onboardingShown
    private var _appTpEnabledCtaShown: Boolean = appTpEnabledCtaShown
    private var _onboardingSession: Boolean = onboardingSession
    private var _notifyMeInAppTpDismissed: Boolean = notifyMeInAppTpDismissed
    private var _pproUpsellBannerDismissed: Boolean = pproUpsellBannerDismissed

    override fun onboardingDidShow() {
        _onboardingShown = true
    }

    override fun onboardingDidNotShow() {
        _onboardingShown = false
    }

    override fun didShowOnboarding(): Boolean = _onboardingShown

    override fun didShowAppTpEnabledCta(): Boolean = _appTpEnabledCtaShown

    override fun appTpEnabledCtaDidShow() {
        _appTpEnabledCtaShown = true
    }

    override fun getAndSetOnboardingSession(): Boolean {
        return _onboardingSession.also {
            _onboardingSession = true
        }
    }

    override fun dismissNotifyMeInAppTp() {
        _notifyMeInAppTpDismissed = true
    }

    override fun isNotifyMeInAppTpDismissed(): Boolean = _notifyMeInAppTpDismissed

    override fun dismissPproUpsellBanner() {
        _pproUpsellBannerDismissed = true
    }

    override fun isPproUpsellBannerDismised(): Boolean = _pproUpsellBannerDismissed
}
