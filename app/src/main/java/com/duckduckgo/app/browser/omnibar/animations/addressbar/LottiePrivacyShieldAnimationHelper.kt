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

package com.duckduckgo.app.browser.omnibar.animations.addressbar

import androidx.annotation.RawRes
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.animations.AddressBarTrackersAnimationManager
import com.duckduckgo.app.browser.api.OmnibarRepository
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.model.PrivacyShield.MALICIOUS
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.PrivacyShield.UNKNOWN
import com.duckduckgo.app.global.model.PrivacyShield.UNPROTECTED
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.runBlocking
import logcat.logcat
import javax.inject.Inject
import kotlin.to

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class LottiePrivacyShieldAnimationHelper @Inject constructor(
    private val appTheme: AppTheme,
    private val addressBarTrackersAnimationManager: AddressBarTrackersAnimationManager,
    private val omnibarRepository: OmnibarRepository,
) : PrivacyShieldAnimationHelper {

    override fun setAnimationView(
        holder: LottieAnimationView,
        privacyShield: PrivacyShield,
        viewMode: ViewMode,
        useLightAnimation: Boolean?,
        isAddressBarRebrandEnabled: Boolean,
    ): Boolean {
        val isLegacyCustomTab = viewMode is ViewMode.CustomTab && !omnibarRepository.isNewCustomTabEnabled
        val isLightMode = useLightAnimation ?: appTheme.isLightModeEnabled()
        val trackersAnimationEnabled = runBlocking { addressBarTrackersAnimationManager.isFeatureEnabled() }

        val (assetRes, boxed) = resolveShieldAsset(
            privacyShield,
            isLightMode,
            isLegacyCustomTab,
            isAddressBarRebrandEnabled,
            trackersAnimationEnabled,
        )
            ?: return false
        val isStatic = when (privacyShield) {
            UNPROTECTED, MALICIOUS -> isAddressBarRebrandEnabled
            PROTECTED, UNKNOWN -> false
        }

        val currentAsset = holder.tag as? Pair<*, *>
        if (currentAsset != assetRes to isStatic) {
            if (isStatic) {
                holder.cancelAnimation()
                holder.setImageResource(assetRes)
            } else {
                holder.setImageDrawable(null)
                holder.setAnimation(assetRes)
                holder.progress = if (privacyShield == UNPROTECTED) 1.0f else 0.0f
            }
            holder.tag = assetRes to isStatic
            logcat { "Shield: $privacyShield" }
        } else {
            logcat { "Shield: $privacyShield - no change" }
        }

        return boxed
    }

    internal fun resolveShieldAsset(
        privacyShield: PrivacyShield,
        isLightMode: Boolean,
        isLegacyCustomTab: Boolean,
        brandIconsEnabled: Boolean,
        trackersAnimationEnabled: Boolean,
    ): Pair<Int, Boolean>? {
        if (privacyShield == UNKNOWN) return null

        if (brandIconsEnabled) {
            return when (privacyShield) {
                PROTECTED -> R.raw.shield_color_24 to true
                UNPROTECTED -> R.drawable.shield_alert_24 to true
                MALICIOUS -> R.drawable.exclamation_recolorable_24 to true
                UNKNOWN -> null
            }
        }

        return when (privacyShield) {
            PROTECTED -> legacyProtectedShield(isLightMode, isLegacyCustomTab, trackersAnimationEnabled) to false
            UNPROTECTED -> (if (isLightMode) R.raw.unprotected_shield else R.raw.dark_unprotected_shield) to false
            MALICIOUS -> (if (isLightMode) R.raw.alert_red else R.raw.alert_red_dark) to false
            UNKNOWN -> null
        }
    }

    @RawRes
    private fun legacyProtectedShield(
        isLightMode: Boolean,
        isLegacyCustomTab: Boolean,
        trackersAnimationEnabled: Boolean,
    ): Int = when {
        isLegacyCustomTab && isLightMode -> R.raw.protected_shield_custom_tab
        isLegacyCustomTab -> R.raw.dark_protected_shield_custom_tab
        trackersAnimationEnabled -> R.raw.address_bar_trackers_animation_shield
        isLightMode -> R.raw.protected_shield
        else -> R.raw.dark_protected_shield
    }
}
