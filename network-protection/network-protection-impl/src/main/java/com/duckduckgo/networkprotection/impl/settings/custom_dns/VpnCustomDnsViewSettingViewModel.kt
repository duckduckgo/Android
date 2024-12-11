/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.settings.custom_dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.networkprotection.impl.VpnRemoteFeatures
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.impl.settings.NetpVpnSettingsDataStore
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsSettingView.Event
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsSettingView.Event.Init
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsSettingView.State
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class VpnCustomDnsViewSettingViewModel(
    private val netpVpnSettingsDataStore: NetpVpnSettingsDataStore,
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
    private val vpnRemoteFeatures: VpnRemoteFeatures,
) : ViewModel() {

    internal fun reduce(event: Event): Flow<State> {
        return when (event) {
            Init -> onInit()
        }
    }

    private fun onInit(): Flow<State> = flow {
        netpVpnSettingsDataStore.customDns?.let {
            emit(State.CustomDns(it))
        } ?: if (netPSettingsLocalConfig.blockMalware().isEnabled() && vpnRemoteFeatures.allowBlockMalware().isEnabled()) {
            emit(State.DefaultBlockMalware)
        } else {
            emit(State.Default)
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val store: NetpVpnSettingsDataStore,
        private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
        private val vpnRemoteFeatures: VpnRemoteFeatures,
    ) : ViewModelProvider.NewInstanceFactory() {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return with(modelClass) {
                when {
                    isAssignableFrom(VpnCustomDnsViewSettingViewModel::class.java) -> VpnCustomDnsViewSettingViewModel(
                        store,
                        netPSettingsLocalConfig,
                        vpnRemoteFeatures,
                    )

                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            } as T
        }
    }
}
