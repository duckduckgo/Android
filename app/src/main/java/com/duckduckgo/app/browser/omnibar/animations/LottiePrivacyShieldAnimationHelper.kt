/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar.animations

import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.model.PrivacyShield.MALICIOUS
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.PrivacyShield.UNKNOWN
import com.duckduckgo.app.global.model.PrivacyShield.UNPROTECTED
import com.duckduckgo.common.ui.experiments.visual.AppPersonalityFeature
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import timber.log.Timber

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class LottiePrivacyShieldAnimationHelper @Inject constructor(
    private val appTheme: AppTheme,
    private val appPersonalityFeature: AppPersonalityFeature,
) : PrivacyShieldAnimationHelper {

    override fun setAnimationView(
        holder: LottieAnimationView,
        privacyShield: PrivacyShield,
    ) {
        val protectedShield: Int
        val protectedShieldDark: Int
        val unprotectedShield: Int
        val unprotectedShieldDark: Int
        if (appPersonalityFeature.self().isEnabled() && !appPersonalityFeature.variant1().isEnabled()) {
            protectedShield = R.raw.protected_shield_experiment
            protectedShieldDark = R.raw.protected_shield_experiment
            unprotectedShield = R.raw.unprotected_shield_experiment
            unprotectedShieldDark = R.raw.unprotected_shield_experiment_dark
        } else {
            protectedShield = R.raw.protected_shield
            protectedShieldDark = R.raw.dark_protected_shield
            unprotectedShield = R.raw.unprotected_shield
            unprotectedShieldDark = R.raw.dark_unprotected_shield
        }

        val currentAnimation = holder.tag as? Int
        val newAnimation = when (privacyShield) {
            PROTECTED -> if (appTheme.isLightModeEnabled()) protectedShield else protectedShieldDark
            UNPROTECTED -> if (appTheme.isLightModeEnabled()) unprotectedShield else unprotectedShieldDark
            UNKNOWN -> null
            MALICIOUS -> if (appTheme.isLightModeEnabled()) R.raw.alert_red else R.raw.alert_red_dark
        }

        if (newAnimation != null && newAnimation != currentAnimation) {
            holder.setAnimation(newAnimation)
            holder.tag = newAnimation
            holder.progress = if (privacyShield == UNPROTECTED) 1.0f else 0.0f
            Timber.d("Shield: $privacyShield")
        } else {
            Timber.d("Shield: $privacyShield - no change")
        }
    }
}
