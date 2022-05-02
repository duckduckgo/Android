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
    fun enableVPNFeature()
    fun removeVPNFeature()
    fun isVPNFeatureRemoved(): Boolean
    fun askRemoveVpnFeature()
    fun forgetRemoveVpnFeature()
    fun shouldRemoveVpnFeature(): Boolean
}

@ContributesBinding(AppScope::class)
class DeviceShieldOnboardingImpl @Inject constructor(
    context: Context
) : DeviceShieldOnboardingStore {
    private val preferences = context.getSharedPreferences(DEVICE_SHIELD_ONBOARDING_STORE_PREFS, Context.MODE_MULTI_PROCESS)

    override fun onboardingDidShow() {
        preferences.edit { putBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, true) }
    }

    override fun onboardingDidNotShow() {
        preferences.edit { putBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, false) }
    }

    override fun didShowOnboarding(): Boolean {
        return preferences.getBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, false)
    }

    override fun enableVPNFeature() {
        onboardingDidShow()
        preferences.edit { putBoolean(KEY_VPN_FEATURE_REMOVED, false) }
    }

    override fun removeVPNFeature() {
        preferences.edit { putBoolean(KEY_VPN_FEATURE_REMOVED, true) }
    }

    override fun isVPNFeatureRemoved(): Boolean {
        return preferences.getBoolean(KEY_VPN_FEATURE_REMOVED, false)
    }

    override fun askRemoveVpnFeature() {
        onboardingDidShow()
        preferences.edit { putBoolean(KEY_SCHEDULE_VPN_FEATURE_REMOVED, true) }
    }

    override fun forgetRemoveVpnFeature() {
        preferences.edit { putBoolean(KEY_SCHEDULE_VPN_FEATURE_REMOVED, false) }
    }

    override fun shouldRemoveVpnFeature(): Boolean {
        return preferences.getBoolean(KEY_SCHEDULE_VPN_FEATURE_REMOVED, false)
    }

    companion object {
        private const val KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED = "KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED"
        private const val KEY_VPN_FEATURE_REMOVED = "KEY_VPN_FEATURE_REMOVED"
        private const val KEY_SCHEDULE_VPN_FEATURE_REMOVED = "KEY_SCHEDULE_VPN_FEATURE_REMOVED"
        private const val DEVICE_SHIELD_ONBOARDING_STORE_PREFS = "com.duckduckgo.android.atp.onboarding.store"
    }
}
