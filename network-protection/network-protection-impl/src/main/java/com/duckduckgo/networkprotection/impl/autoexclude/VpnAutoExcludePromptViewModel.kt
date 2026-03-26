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

package com.duckduckgo.networkprotection.impl.autoexclude

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptFragment.Companion.Source
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptFragment.Companion.Source.VPN_SCREEN
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.PromptState.ALL_INCOMPATIBLE_APPS
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.PromptState.NEW_INCOMPATIBLE_APP
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.PromptState.UNKNOWN
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.store.NetPManualExclusionListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class VpnAutoExcludePromptViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val packageManager: PackageManager,
    private val localConfig: NetPSettingsLocalConfig,
    private val networkProtectionState: NetworkProtectionState,
    private val netPManualExclusionListRepository: NetPManualExclusionListRepository,
    private val networkProtectionPixels: NetworkProtectionPixels,
) : ViewModel() {
    private val _viewState = MutableStateFlow(ViewState(emptyList(), UNKNOWN))
    private val appsToExclude = mutableMapOf<String, Boolean>()

    fun onPromptShown(
        appPackages: List<String>,
        source: Source,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (source == VPN_SCREEN) {
                networkProtectionPixels.reportAutoExcludePromptShownInVPNScreen()
            } else {
                networkProtectionPixels.reportAutoExcludePromptShownInExclusionList()
            }

            appsToExclude.putAll(appPackages.associateWith { true })
            _viewState.emit(
                ViewState(
                    incompatibleApps = appPackages.map { packageName ->
                        ItemInfo(
                            name = packageManager.getAppName(packageName),
                            packageName = packageName,
                        )
                    }.sortedBy { it.name },
                    promptState = if (source == VPN_SCREEN) NEW_INCOMPATIBLE_APP else ALL_INCOMPATIBLE_APPS,
                ),
            )
        }
    }

    fun viewState(): Flow<ViewState> = _viewState.asStateFlow()

    @SuppressLint("DenyListedApi")
    fun onAddExclusionsSelected(shouldEnableAutoExclude: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            var shouldRestartVpn = false
            val appsToManuallyExclude = mutableListOf<String>()
            val appsToManuallyInclude = mutableListOf<String>()

            val checkedApps = appsToExclude.filter { it.value }.keys.toList()
            if (checkedApps.isNotEmpty()) {
                networkProtectionPixels.reportAutoExcludePromptExcludeApps()
            }

            if (shouldEnableAutoExclude) {
                shouldRestartVpn = true

                // enable auto exclude
                localConfig.autoExcludeBrokenApps().setRawStoredState(State(enable = true))
                networkProtectionPixels.reportAutoExcludePromptEnable()

                // Manually exclude checked apps that are part of user's manual exclusion list since they will not be excluded via auto-exclude
                val manuallyEnabledApps = netPManualExclusionListRepository.getManualAppExclusionList()
                    .filter { it.isProtected } // We only care about protected apps as unprotected apps are already in the correct state
                    .map { it.packageId }

                checkedApps.filter {
                    manuallyEnabledApps.contains(it) // If checked app is manually protected, we manually exclude it
                }.also {
                    appsToManuallyExclude.addAll(it)
                }

                // Manually protect apps that are unchecked as they will be likely excluded via auto-exclude
                appsToExclude.filter {
                    !it.value
                }.keys.toList().also {
                    appsToManuallyInclude.addAll(it)
                }
            } else {
                // exclude checked apps
                appsToManuallyExclude.addAll(checkedApps)
            }

            if (appsToManuallyExclude.isNotEmpty()) {
                shouldRestartVpn = true
                netPManualExclusionListRepository.manuallyExcludeApps(appsToManuallyExclude)
            }

            if (appsToManuallyInclude.isNotEmpty()) {
                shouldRestartVpn = true
                netPManualExclusionListRepository.manuallyEnableApps(appsToManuallyInclude)
            }

            if (shouldRestartVpn) {
                networkProtectionState.restart()
            }
        }
    }

    fun updateAppExcludeState(
        packageName: String,
        exclude: Boolean,
    ) {
        appsToExclude[packageName] = exclude
    }

    fun onCancelPrompt() {
        networkProtectionPixels.reportAutoExcludePromptNoAction()
    }

    data class ViewState(
        val incompatibleApps: List<ItemInfo>,
        val promptState: PromptState,
    )

    enum class PromptState {
        NEW_INCOMPATIBLE_APP,
        ALL_INCOMPATIBLE_APPS,
        UNKNOWN,
    }

    data class ItemInfo(
        val packageName: String,
        val name: String,
    )
}
