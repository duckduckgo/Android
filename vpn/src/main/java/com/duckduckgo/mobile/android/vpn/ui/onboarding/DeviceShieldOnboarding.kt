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
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import javax.inject.Named

interface DeviceShieldOnboarding {
    /**
     * @return null if the App Tracking Protection onboarding has been shown already. Intent otherwise
     */
    fun prepare(context: Context): Intent?
}

interface DeviceShieldOnboardingStore {
    fun onboardingDidShow()
    fun onboardingDidNotShow()
    fun hasOnboardingBeenShown(): Boolean
}

@Module
@ContributesTo(AppObjectGraph::class)
class DeviceShieldOnboardingPreferencesModule {
    @Provides
    @Named("DeviceShieldOnboardingPreferences")
    fun provideDeviceShieldOnboardingPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(DeviceShieldOnboardingActivity::class.qualifiedName, Context.MODE_PRIVATE)
    }
}

@Module
@ContributesTo(AppObjectGraph::class)
abstract class DeviceShieldOnboardingModule {

    @Binds
    abstract fun bindDeviceShieldOnboardingStore(deviceShieldOnboardingImpl: DeviceShieldOnboardingImpl): DeviceShieldOnboardingStore
}

@ContributesBinding(
    scope = AppObjectGraph::class,
    boundType = DeviceShieldOnboarding::class
)
class DeviceShieldOnboardingImpl @Inject constructor(
    @Named("DeviceShieldOnboardingPreferences") private val preferences: SharedPreferences
) : DeviceShieldOnboarding, DeviceShieldOnboardingStore {

    override fun prepare(context: Context): Intent? {
        val didShowOnboarding = hasOnboardingBeenShown()

        return if (didShowOnboarding) null else Intent(context, DeviceShieldOnboardingActivity::class.java)
    }

    override fun onboardingDidShow() {
        preferences.edit { putBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, true) }
    }

    override fun onboardingDidNotShow() {
        preferences.edit { putBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, false) }
    }

    override fun hasOnboardingBeenShown(): Boolean {
        return preferences.getBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, false)
    }

    companion object {
        private const val KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED = "KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED"
    }
}
