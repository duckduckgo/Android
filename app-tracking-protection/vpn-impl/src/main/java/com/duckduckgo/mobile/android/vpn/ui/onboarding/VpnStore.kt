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

package com.duckduckgo.mobile.android.vpn.ui.onboarding

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import org.threeten.bp.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface VpnStore {
    fun onboardingDidShow()
    fun onboardingDidNotShow()
    fun didShowOnboarding(): Boolean
    fun resetAppTPManuallyEnablesCounter()
    fun onAppTPManuallyEnabled()
    fun getAppTPManuallyEnables(): Int
    fun onForgetPromoteAlwaysOn()
    fun userAllowsShowPromoteAlwaysOn(): Boolean
    suspend fun setAlwaysOn(enabled: Boolean)
    fun isAlwaysOnEnabled(): Boolean
    fun didShowAppTpEnabledCta(): Boolean
    fun appTpEnabledCtaDidShow()
    fun onOnboardingSessionSet()
    fun isOnboardingSession(): Boolean

    companion object {
        const val ALWAYS_ON_PROMOTION_DELTA = 3
    }
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesVpnStore @Inject constructor(
    private val sharedPreferencesProvider: VpnSharedPreferencesProvider,
    private val dispatcherProvider: DispatcherProvider,
) : VpnStore {

    private val preferences: SharedPreferences
        get() = sharedPreferencesProvider.getSharedPreferences(DEVICE_SHIELD_ONBOARDING_STORE_PREFS, multiprocess = true, migrate = true)

    override fun onboardingDidShow() {
        preferences.edit { putBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, true) }
    }

    override fun onboardingDidNotShow() {
        preferences.edit { putBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, false) }
    }

    override fun didShowOnboarding(): Boolean {
        return preferences.getBoolean(KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED, false)
    }

    override fun resetAppTPManuallyEnablesCounter() {
        preferences.edit(commit = true) { putInt(KEY_DEVICE_SHIELD_MANUALLY_ENABLED, 0) }
    }

    override fun onAppTPManuallyEnabled() {
        preferences.edit(commit = true) { putInt(KEY_DEVICE_SHIELD_MANUALLY_ENABLED, getAppTPManuallyEnables() + 1) }
    }

    override fun getAppTPManuallyEnables(): Int {
        return preferences.getInt(KEY_DEVICE_SHIELD_MANUALLY_ENABLED, 0)
    }

    override fun onForgetPromoteAlwaysOn() {
        preferences.edit(commit = true) { putBoolean(KEY_PROMOTE_ALWAYS_ON_DIALOG_ALLOWED, false) }
    }

    override fun userAllowsShowPromoteAlwaysOn(): Boolean {
        return preferences.getBoolean(KEY_PROMOTE_ALWAYS_ON_DIALOG_ALLOWED, true)
    }

    override suspend fun setAlwaysOn(enabled: Boolean) = withContext(dispatcherProvider.io()) {
        preferences.edit(commit = true) { putBoolean(KEY_ALWAYS_ON_MODE_ENABLED, enabled) }
    }

    override fun isAlwaysOnEnabled(): Boolean {
        return preferences.getBoolean(KEY_ALWAYS_ON_MODE_ENABLED, false)
    }

    override fun didShowAppTpEnabledCta(): Boolean {
        return preferences.getBoolean(KEY_APP_TP_ONBOARDING_VPN_ENABLED_CTA_SHOWN, false)
    }

    override fun appTpEnabledCtaDidShow() {
        preferences.edit { putBoolean(KEY_APP_TP_ONBOARDING_VPN_ENABLED_CTA_SHOWN, true) }
    }

    override fun onOnboardingSessionSet() {
        val now = Instant.now().toEpochMilli()
        val expiryTimestamp = now.plus(TimeUnit.HOURS.toMillis(WINDOW_INTERVAL_HOURS))
        preferences.edit { putLong(KEY_APP_TP_ONBOARDING_BANNER_EXPIRY_TIMESTAMP, expiryTimestamp) }
    }

    override fun isOnboardingSession(): Boolean {
        val expiryTimestamp = preferences.getLong(KEY_APP_TP_ONBOARDING_BANNER_EXPIRY_TIMESTAMP, -1)
        return Instant.now().toEpochMilli() < expiryTimestamp
    }

    companion object {
        private const val DEVICE_SHIELD_ONBOARDING_STORE_PREFS = "com.duckduckgo.android.atp.onboarding.store"

        private const val KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED = "KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED"
        private const val KEY_DEVICE_SHIELD_MANUALLY_ENABLED = "KEY_DEVICE_SHIELD_MANUALLY_ENABLED"
        private const val KEY_PROMOTE_ALWAYS_ON_DIALOG_ALLOWED = "KEY_PROMOTE_ALWAYS_ON_DIALOG_ALLOWED"
        private const val KEY_ALWAYS_ON_MODE_ENABLED = "KEY_ALWAYS_ON_MODE_ENABLED"
        private const val KEY_APP_TP_ONBOARDING_VPN_ENABLED_CTA_SHOWN = "KEY_APP_TP_ONBOARDING_VPN_ENABLED_CTA_SHOWN"
        private const val KEY_APP_TP_ONBOARDING_BANNER_EXPIRY_TIMESTAMP = "KEY_APP_TP_ONBOARDING_BANNER_EXPIRY_TIMESTAMP"

        private const val WINDOW_INTERVAL_HOURS = 24L
    }
}
