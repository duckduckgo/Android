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

package com.duckduckgo.mobile.android.vpn.onboarding

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.di.scopes.DeviceShieldOnboardingObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides

interface DeviceShieldOnboarding {
    /**
     * @return null if the Device Shield onboarding has been shown already. Intent otherwise
     */
    fun prepare(context: Context): Intent?
}

interface DeviceShieldOnboardingStore {
    fun onboardingDidShow()

    fun onboardingDidNotShow()
}

private fun Context.deviceShieldOnboardingSharePrefs(): SharedPreferences {
    return getSharedPreferences(DeviceShieldOnboardingActivity::class.qualifiedName, Context.MODE_PRIVATE)
}

@Module
@ContributesTo(AppObjectGraph::class)
class DeviceShieldOnboardingModule {
    @Provides
    fun provideDeviceShieldOnboarding(context: Context): DeviceShieldOnboarding {
        return DeviceShieldOnboardingImpl(context.deviceShieldOnboardingSharePrefs())
    }
}

@Module
@ContributesTo(DeviceShieldOnboardingObjectGraph::class)
class DeviceShieldOnboardingStoreModule {
    @Provides
    fun provideDeviceShieldOnboardingStore(context: Context): DeviceShieldOnboardingStore {
        return DeviceShieldOnboardingImpl(context.deviceShieldOnboardingSharePrefs())
    }
}

private class DeviceShieldOnboardingImpl(
    private val preferences: SharedPreferences
): DeviceShieldOnboarding, DeviceShieldOnboardingStore {

    override fun prepare(context: Context): Intent? {
        val didShowOnboarding = preferences.getBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, false)

        return if (didShowOnboarding) null else Intent(context, DeviceShieldOnboardingActivity::class.java)
    }

    override fun onboardingDidShow() {
        preferences.edit { putBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, true) }
    }

    override fun onboardingDidNotShow() {
        preferences.edit { putBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, false) }
    }

    companion object {
        private const val KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED = "KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED"
    }
}
