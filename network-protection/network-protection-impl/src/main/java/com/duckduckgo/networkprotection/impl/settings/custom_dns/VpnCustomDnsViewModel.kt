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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.impl.settings.NetpVpnSettingsDataStore
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.CustomDnsEntered
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.CustomDnsSelected
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.DefaultDnsSelected
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.ForceApplyIfReset
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.Init
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.OnApply
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.State
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsViewModel.InitialState.CustomDns
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsViewModel.InitialState.DefaultDns
import com.wireguard.config.InetAddresses
import java.net.Inet4Address
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@ContributesViewModel(ActivityScope::class)
class VpnCustomDnsViewModel @Inject constructor(
    private val netpVpnSettingsDataStore: NetpVpnSettingsDataStore,
) : ViewModel() {

    private lateinit var initialState: InitialState
    private var currentState: InitialState = DefaultDns

    internal fun reduce(event: Event): Flow<State> {
        return when (event) {
            Init -> onInit()
            DefaultDnsSelected -> handleDefaultDnsSelected()
            CustomDnsSelected -> handleCustomDnsSelected()
            is CustomDnsEntered -> handleCustomDnsEntered(event)
            OnApply -> handleOnApply()
            ForceApplyIfReset -> handleforceApply()
        }
    }

    private fun handleforceApply() = flow {
        if (netpVpnSettingsDataStore.customDns != null && currentState == DefaultDns) {
            netpVpnSettingsDataStore.customDns = null
            emit(State.Done)
        }
    }

    private fun handleOnApply() = flow {
        when (val currentState = currentState) { // defensive copy
            is DefaultDns -> netpVpnSettingsDataStore.customDns = null
            is CustomDns -> netpVpnSettingsDataStore.customDns = currentState.dns
        }
        emit(State.Done)
    }

    private fun handleDefaultDnsSelected() = flow {
        currentState = DefaultDns
        emit(State.DefaultDns)
        emit(State.NeedApply(initialState != currentState))
    }

    private fun handleCustomDnsSelected() = flow {
        currentState = CustomDns(dns = null)
        emit(State.CustomDns(dns = null))
    }

    private fun handleCustomDnsEntered(event: CustomDnsEntered) = flow {
        val dns = event.dns.orEmpty()
        currentState = CustomDns(dns)
        emit(State.CustomDns(dns))
        val apply = (initialState != currentState) && dns.isValidAddress()
        emit(State.NeedApply(apply))
    }

    private fun onInit(): Flow<State> = flow {
        val customDns = netpVpnSettingsDataStore.customDns
        if (!this@VpnCustomDnsViewModel::initialState.isInitialized) {
            initialState = customDns?.let { CustomDns(it) } ?: DefaultDns
            currentState = initialState
        }
        customDns?.let {
            emit(State.CustomDns(it))
        } ?: emit(State.DefaultDns)
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
