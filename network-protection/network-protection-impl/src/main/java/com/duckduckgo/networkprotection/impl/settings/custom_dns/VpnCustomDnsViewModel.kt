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

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.impl.VpnRemoteFeatures
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.impl.settings.NetpVpnSettingsDataStore
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.CustomDnsEntered
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.CustomDnsSelected
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.DefaultDnsSelected
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.ForceApplyIfReset
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.Init
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.OnApply
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.OnBlockMalwareDisabled
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.OnBlockMalwareEnabled
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.State
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsViewModel.InitialState.CustomDns
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsViewModel.InitialState.DefaultDns
import com.wireguard.config.InetAddresses
import java.net.Inet4Address
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@ContributesViewModel(ActivityScope::class)
class VpnCustomDnsViewModel @Inject constructor(
    private val netpVpnSettingsDataStore: NetpVpnSettingsDataStore,
    private val networkProtectionPixels: NetworkProtectionPixels,
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
    private val vpnRemoteFeatures: VpnRemoteFeatures,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private lateinit var initialState: InitialState
    private var currentState: InitialState = DefaultDns
    private val blockMalware: Deferred<Boolean> = viewModelScope.async(context = dispatcherProvider.io(), start = CoroutineStart.LAZY) {
        netPSettingsLocalConfig.blockMalware().isEnabled()
    }
    private val allowBlockMalware: Deferred<Boolean> = viewModelScope.async(context = dispatcherProvider.io(), start = CoroutineStart.LAZY) {
        vpnRemoteFeatures.allowBlockMalware().isEnabled()
    }

    internal fun reduce(event: Event): Flow<State> {
        return when (event) {
            is Init -> onInit(event.isPrivateDnsActive)
            DefaultDnsSelected -> handleDefaultDnsSelected()
            CustomDnsSelected -> handleCustomDnsSelected()
            is CustomDnsEntered -> handleCustomDnsEntered(event)
            OnApply -> handleOnApply()
            ForceApplyIfReset -> handleForceApply()
            OnBlockMalwareDisabled -> handleBlockMalwareState(false)
            OnBlockMalwareEnabled -> handleBlockMalwareState(true)
        }
    }

    @SuppressLint("DenyListedApi")
    private fun handleBlockMalwareState(isEnabled: Boolean) = flow {
        netPSettingsLocalConfig.blockMalware().setRawStoredState(Toggle.State(enable = isEnabled))
        netpVpnSettingsDataStore.customDns = null
        emit(State.DefaultDns(true, isEnabled, true))
        emit(State.Done(finish = false))
    }

    private fun handleForceApply() = flow {
        if (netpVpnSettingsDataStore.customDns != null && currentState == DefaultDns) {
            netpVpnSettingsDataStore.customDns = null
            networkProtectionPixels.reportDefaultDnsSet()
            emit(State.Done())
        }
    }

    private fun handleOnApply() = flow {
        when (val currentState = currentState) { // defensive copy
            is DefaultDns -> netpVpnSettingsDataStore.customDns = null
            is CustomDns -> {
                netpVpnSettingsDataStore.customDns = currentState.dns
                networkProtectionPixels.reportCustomDnsSet()
            }
        }
        emit(State.Done())
    }

    private fun handleDefaultDnsSelected() = flow {
        currentState = DefaultDns
        emit(State.DefaultDns(true, blockMalware.await(), allowBlockMalware.await()))
    }

    private fun handleCustomDnsSelected() = flow {
        currentState = CustomDns(dns = null)
        emit(State.CustomDns(dns = null, allowChange = true, applyEnabled = false))
    }

    private fun handleCustomDnsEntered(event: CustomDnsEntered) = flow {
        val dns = event.dns.orEmpty()
        currentState = CustomDns(dns)
        val apply = (initialState != currentState) && dns.isValidAddress()
        emit(State.CustomDns(dns, allowChange = true, applyEnabled = apply))
    }

    private fun onInit(isPrivateDnsActive: Boolean): Flow<State> = flow {
        val customDns = netpVpnSettingsDataStore.customDns
        if (!this@VpnCustomDnsViewModel::initialState.isInitialized) {
            initialState = customDns?.let { CustomDns(it) } ?: DefaultDns
            currentState = initialState
        }
        customDns?.let {
            emit(State.CustomDns(it, !isPrivateDnsActive, applyEnabled = false))
        } ?: emit(State.DefaultDns(!isPrivateDnsActive, blockMalware.await(), allowBlockMalware.await()))
    }

    private fun String.isValidAddress(): Boolean {
        return runCatching { InetAddresses.parse(this) }.getOrNull()?.let { inetAddress ->
            return (inetAddress is Inet4Address) && !inetAddress.isSiteLocalAddress
        } ?: false
    }

    private sealed class InitialState {
        data object DefaultDns : InitialState()
        data class CustomDns(val dns: String?) : InitialState()
    }
}
