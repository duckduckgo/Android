/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.onboarding

import android.content.Context
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DeviceShieldOnboardingStore {
    fun onboardingDidShow()
    fun onboardingDidNotShow()
    fun didShowOnboarding(): Boolean

    fun onFeatureEnabled()
    fun onFeatureDisabled()
    fun isFeatureEnabled(): Boolean

    fun onAppTPManuallyEnabled()
    fun shouldPromoteAlwaysOn(): Boolean
    fun onForgetPromoteAlwaysOn()

    fun setAlwaysOn(enabled: Boolean)
    fun isAlwaysOnEnabled(): Boolean
}

@ContributesBinding(AppScope::class)
class DeviceShieldOnboardingImpl @Inject constructor(
    context: Context
) : DeviceShieldOnboardingStore {
    private val preferences = context.getSharedPreferences(DEVICE_SHIELD_ONBOARDING_STORE_PREFS, Context.MODE_PRIVATE)

    override fun onboardingDidShow() {
        preferences.edit { putBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, true) }
    }

    override fun onboardingDidNotShow() {
        preferences.edit { putBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, false) }
    }

    override fun didShowOnboarding(): Boolean {
        return preferences.getBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, false)
    }

    override fun onFeatureEnabled() {
        onboardingDidShow()
        preferences.edit { putBoolean(KEY_DEVICE_SHIELD_FEATURE_ENABLED, true) }
    }

    override fun onFeatureDisabled() {
        preferences.edit { putBoolean(KEY_DEVICE_SHIELD_FEATURE_ENABLED, false) }
    }

    override fun isFeatureEnabled(): Boolean {
        return preferences.getBoolean(KEY_DEVICE_SHIELD_FEATURE_ENABLED, false)
    }

    override fun onAppTPManuallyEnabled() {
        preferences.edit { putInt(KEY_DEVICE_SHIELD_MANUALLY_ENABLED, getTotalEnabled() + 1) }
    }

    private fun getTotalEnabled(): Int {
        return preferences.getInt(KEY_DEVICE_SHIELD_MANUALLY_ENABLED, 0)
    }

    override fun shouldPromoteAlwaysOn(): Boolean {
        return preferences.getInt(KEY_DEVICE_SHIELD_MANUALLY_ENABLED, 0) >= ALWAYS_ON_PROMOTION_DELTA && canShowPromoteAlwaysOnDialog()
    }

    override fun onForgetPromoteAlwaysOn() {
        preferences.edit { putBoolean(KEY_PROMOTE_ALWAYS_ON_DIALOG_ALLOWED, false) }
    }

    override fun setAlwaysOn(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ALWAYS_ON_ENABLED, enabled) }
    }

    override fun isAlwaysOnEnabled(): Boolean {
        return preferences.getBoolean(KEY_ALWAYS_ON_ENABLED, false)
    }

    private fun canShowPromoteAlwaysOnDialog(): Boolean {
        return preferences.getBoolean(KEY_PROMOTE_ALWAYS_ON_DIALOG_ALLOWED, true)
    }

    companion object {
        private const val DEVICE_SHIELD_ONBOARDING_STORE_PREFS = "com.duckduckgo.android.atp.onboarding.store"

        private const val KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED = "KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED"
        private const val KEY_DEVICE_SHIELD_FEATURE_ENABLED = "KEY_DEVICE_SHIELD_FEATURE_ENABLED"

        private const val KEY_DEVICE_SHIELD_MANUALLY_ENABLED = "KEY_DEVICE_SHIELD_MANUALLY_ENABLED"
        private const val KEY_PROMOTE_ALWAYS_ON_DIALOG_ALLOWED = "KEY_PROMOTE_ALWAYS_ON_DIALOG_ALLOWED"
        private const val ALWAYS_ON_PROMOTION_DELTA = 5

        private const val KEY_ALWAYS_ON_ENABLED = "KEY_ALWAYS_ON_ENABLED"
    }
}
