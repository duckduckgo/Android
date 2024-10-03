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
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.store.NetPManualExclusionListRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@ContributesViewModel(FragmentScope::class)
class VpnAutoExcludePromptViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val packageManager: PackageManager,
    private val localConfig: NetPSettingsLocalConfig,
    private val networkProtectionState: NetworkProtectionState,
    private val netPManualExclusionListRepository: NetPManualExclusionListRepository,
) : ViewModel() {
    private val _viewState = MutableStateFlow(ViewState(emptyList()))
    private val appsToExclude: MutableMap<String, Boolean> = mutableMapOf()

    fun onPromptShown(
        appPackages: List<String>,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            appsToExclude.putAll(appPackages.associateWith { true })
            _viewState.emit(
                ViewState(
                    incompatibleApps = appPackages.map { packageName ->
                        ItemInfo(
                            name = packageManager.getAppName(packageName),
                            packageName = packageName,
                        )
                    }.sortedBy { it.name },
                ),
            )
        }
    }

    fun viewState(): Flow<ViewState> = _viewState.asStateFlow()

    @SuppressLint("DenyListedApi")
    fun onAddExclusionsSelected(shouldEnableAutoExclude: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            var shouldRestart = false

            if (shouldEnableAutoExclude) {
                localConfig.autoExcludeBrokenApps().setRawStoredState(State(enable = true))
                shouldRestart = true
            } else {
                appsToExclude.filter { it.value }
                    .keys
                    .toList()
                    .also {
                        if (it.isNotEmpty()) {
                            netPManualExclusionListRepository.manuallyExcludeApps(it)
                            shouldRestart = true
                        }
                    }
            }

            if (shouldRestart) {
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

    data class ViewState(
        val incompatibleApps: List<ItemInfo>,
    )

    data class ItemInfo(
        val packageName: String,
        val name: String,
    )
}
