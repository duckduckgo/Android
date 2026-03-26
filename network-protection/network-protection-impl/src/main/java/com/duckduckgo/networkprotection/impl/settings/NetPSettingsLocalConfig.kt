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

package com.duckduckgo.networkprotection.impl.settings

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.networkprotection.impl.di.ProdNetPConfigTogglesDao
import com.duckduckgo.networkprotection.store.remote_config.NetPConfigToggle
import com.duckduckgo.networkprotection.store.remote_config.NetPConfigTogglesDao
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Local feature/settings - they will never be in remote config
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "netpSettingsLocalConfig",
    // we define store because we need something multi-process
    toggleStore = NetPSettingsLocalConfigStore::class,
)
interface NetPSettingsLocalConfig {
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun vpnNotificationAlerts(): Toggle

    /**
     * When `true` the VPN routes will exclude local networks
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun vpnExcludeLocalNetworkRoutes(): Toggle

    /**
     * When `true` the VPN will automatically pause when a call is started and will automatically restart after.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun vpnPauseDuringCalls(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun excludeSystemAppsCommunication(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun excludeSystemAppsNetworking(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun excludeSystemAppsMedia(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun excludeSystemAppsOthers(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun blockMalware(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun permanentRemoveExcludeAppPrompt(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun autoExcludeBrokenApps(): Toggle
}

@ContributesBinding(AppScope::class)
@RemoteFeatureStoreNamed(NetPSettingsLocalConfig::class)
@SingleInstanceIn(AppScope::class)
class NetPSettingsLocalConfigStore @Inject constructor(
    @ProdNetPConfigTogglesDao private val togglesDao: NetPConfigTogglesDao,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : Toggle.Store {

    override fun set(key: String, state: Toggle.State) {
        persistToggle(state.asNetPConfigToggle(key))
    }

    override fun get(key: String): Toggle.State? {
        return togglesDao.getConfigToggles().firstOrNull { it.name == key }?.asToggleState()
    }

    private fun NetPConfigToggle.asToggleState(): Toggle.State {
        return Toggle.State(
            enable = this.enabled,
        )
    }

    private fun Toggle.State.asNetPConfigToggle(key: String): NetPConfigToggle {
        return NetPConfigToggle(
            name = key,
            enabled = enable,
        )
    }

    private fun persistToggle(toggle: NetPConfigToggle) {
        coroutineScope.launch(dispatcherProvider.io()) {
            togglesDao.insert(toggle.copy(isManualOverride = true))
        }
    }
}
