/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.webtrackingprotection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.webtrackingprotection.list.FeatureGridItem
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class WebTrackingProtectionViewModel @Inject constructor(
    private val gpc: Gpc,
    private val featureToggle: FeatureToggle,
    private val pixel: Pixel,
) : ViewModel() {

    data class ViewState(
        val globalPrivacyControlEnabled: Boolean = false,
        val protectionItems: List<FeatureGridItem> = emptyList(),
    )

    sealed class Command {
        class LaunchLearnMoreWebPage(val url: String = LEARN_MORE_URL) : Command()
        data object LaunchGlobalPrivacyControl : Command()
        data object LaunchAllowList : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun viewState(): Flow<ViewState> = viewState.onStart {
        viewModelScope.launch {
            viewState.emit(
                ViewState(
                    globalPrivacyControlEnabled = gpc.isEnabled() && featureToggle.isFeatureEnabled(PrivacyFeatureName.GpcFeatureName.value),
                    protectionItems = getProtectionItems(),
                ),
            )
        }
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onLearnMoreSelected() {
        viewModelScope.launch { command.send(Command.LaunchLearnMoreWebPage()) }
    }

    fun onGlobalPrivacyControlClicked() {
        viewModelScope.launch { command.send(Command.LaunchGlobalPrivacyControl) }
        pixel.fire(AppPixelName.SETTINGS_GPC_PRESSED)
    }

    fun onManageAllowListSelected() {
        viewModelScope.launch { command.send(Command.LaunchAllowList) }
        pixel.fire(AppPixelName.SETTINGS_MANAGE_ALLOWLIST)
    }

    private fun getProtectionItems(): List<FeatureGridItem> {
        return listOf(
            FeatureGridItem(
                iconRes = R.drawable.ic_shield_protection,
                titleRes = R.string.webTrackingProtectionThirdPartyTrackersTitle,
                descriptionRes = R.string.webTrackingProtectionThirdPartyTrackersDescription,
            ),
            FeatureGridItem(
                iconRes = R.drawable.ic_ads_tracking_blocked,
                titleRes = R.string.webTrackingProtectionTargetedAdsTitle,
                descriptionRes = R.string.webTrackingProtectionTargetedAdsDescription,
            ),
            FeatureGridItem(
                iconRes = R.drawable.ic_fingerprint,
                titleRes = R.string.webTrackingProtectionFingerprintTrackingTitle,
                descriptionRes = R.string.webTrackingProtectionFingerprintTrackingDescription,
            ),
            FeatureGridItem(
                iconRes = R.drawable.ic_link_blocked,
                titleRes = R.string.webTrackingProtectionLinkTrackingTitle,
                descriptionRes = R.string.webTrackingProtectionLinkTrackingDescription,
            ),
            FeatureGridItem(
                iconRes = R.drawable.ic_profile_secure,
                titleRes = R.string.webTrackingProtectionReferrerTrackingTitle,
                descriptionRes = R.string.webTrackingProtectionReferrerTrackingDescription,
            ),
            FeatureGridItem(
                iconRes = R.drawable.ic_cookie_blocked,
                titleRes = R.string.webTrackingProtectionFirstPartyCookiesTitle,
                descriptionRes = R.string.webTrackingProtectionFirstPartyCookiesDescription,
            ),
            FeatureGridItem(
                iconRes = R.drawable.ic_device_laptop_secure,
                titleRes = R.string.webTrackingProtectionDnsCnameCloakingTitle,
                descriptionRes = R.string.webTrackingProtectionDnsCnameCloakingDescription,
            ),
            FeatureGridItem(
                iconRes = R.drawable.ic_eye_blocked,
                titleRes = R.string.webTrackingProtectionGoogleAmpTrackingTitle,
                descriptionRes = R.string.webTrackingProtectionGoogleAmpTrackingDescription,
            ),
            FeatureGridItem(
                iconRes = R.drawable.ic_popup_blocked,
                titleRes = R.string.webTrackingProtectionGoogleSigninPopupsTitle,
                descriptionRes = R.string.webTrackingProtectionGoogleSigninPopupsDescription,
            ),
        )
    }

    companion object {
        const val LEARN_MORE_URL = "https://help.duckduckgo.com/duckduckgo-help-pages/privacy/web-tracking-protections/"
    }
}
