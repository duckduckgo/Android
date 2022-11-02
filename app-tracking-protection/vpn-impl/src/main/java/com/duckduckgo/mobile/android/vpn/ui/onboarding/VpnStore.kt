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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.dao.VpnHeartBeatDao
import com.duckduckgo.mobile.android.vpn.dao.VpnServiceStateStatsDao
import com.duckduckgo.mobile.android.vpn.heartbeat.VpnServiceHeartbeatMonitor
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface VpnStore {
    fun onboardingDidShow()
    fun onboardingDidNotShow()
    fun didShowOnboarding(): Boolean
    suspend fun isAlwaysOnEnabled(): Boolean
    suspend fun vpnLastDisabledByAndroid(): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesVpnStore @Inject constructor(
    private val sharedPreferencesProvider: VpnSharedPreferencesProvider,
    private val vpnHeartBeatDao: VpnHeartBeatDao,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val vpnServiceStateDao: VpnServiceStateStatsDao,
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

    override suspend fun isAlwaysOnEnabled(): Boolean {
        return vpnServiceStateDao.getLastStateStats()?.alwaysOnState?.alwaysOnEnabled ?: false
    }

    override suspend fun vpnLastDisabledByAndroid(): Boolean {
        fun vpnUnexpectedlyDisabled(): Boolean {
            return vpnServiceStateDao.getLastStateStats()?.let {
                (
                    it.state == VpnServiceState.DISABLED &&
                        it.stopReason != VpnStoppingReason.SELF_STOP &&
                        it.stopReason != VpnStoppingReason.REVOKED
                    )
            } ?: false
        }

        fun vpnKilledBySystem(): Boolean {
            val lastHeartBeat = vpnHeartBeatDao.hearBeats().maxByOrNull { it.timestamp }
            return lastHeartBeat?.type == VpnServiceHeartbeatMonitor.DATA_HEART_BEAT_TYPE_ALIVE &&
                !vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)
        }

        return vpnUnexpectedlyDisabled() || vpnKilledBySystem()
    }

    companion object {
        private const val DEVICE_SHIELD_ONBOARDING_STORE_PREFS = "com.duckduckgo.android.atp.onboarding.store"

        private const val KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED = "KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED"
    }
}
