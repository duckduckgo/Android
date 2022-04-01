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

package com.duckduckgo.mobile.android.vpn.feature

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.remote_config.*
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@ContributesBinding(
    scope = AppScope::class,
    boundType = AppTpFeatureConfig::class
)
@SingleInstanceIn(AppScope::class)
class AppTpFeatureConfigImpl @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val appBuildConfig: AppBuildConfig,
    vpnRemoteConfigDatabase: VpnRemoteConfigDatabase,
    dispatcherProvider: DispatcherProvider,
) : AppTpFeatureConfig, AppTpFeatureConfig.Editor {

    private val togglesDao = vpnRemoteConfigDatabase.vpnConfigTogglesDao()
    private val togglesCache = ConcurrentHashMap<String, Boolean>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            togglesDao.getConfigToggles().forEach {
                togglesCache[it.name] = it.enabled
            }
        }
    }

    override fun isEnabled(settingName: SettingName): Boolean {
        return togglesCache[settingName.value] ?: settingName.defaultValue
    }

    override fun edit(): AppTpFeatureConfig.Editor {
        return this
    }

    override fun setEnabled(settingName: SettingName, enabled: Boolean, isManualOverride: Boolean) {
        val toggle = togglesDao.getConfigToggles().firstOrNull { it.name == settingName.value }
        if (toggle == null || !shouldSkipInsert(toggle.isManualOverride, isManualOverride)) {
            togglesCache[settingName.value] = enabled
            persistToggle(VpnConfigToggle(settingName.value, enabled, isManualOverride))
        } else {
            Timber.d("Skip setEnabled($settingName, $enabled, $isManualOverride)")
        }
    }

    private fun shouldSkipInsert(oldManualOverride: Boolean, newManualOverride: Boolean): Boolean {
        return appBuildConfig.flavor == BuildFlavor.INTERNAL && (oldManualOverride && !newManualOverride)
    }

    private fun persistToggle(toggle: VpnConfigToggle) {
        coroutineScope.launch {
            // Remote configs will not override any value that has isManualOverride = true
            // But this is only for INTERNAL builds, because we have internal settings
            // In any other build that is not internal isManualOverride is alwyas false
            togglesDao.insert(toggle.copy(isManualOverride = appBuildConfig.flavor == BuildFlavor.INTERNAL && toggle.isManualOverride))
        }
    }
}
