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

package com.duckduckgo.networkprotection.impl.settings.geoswitching

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.configuration.WgServerDebugProvider
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository.UserPreferredLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ActivityScope::class)
class NetpGeoSwitchingViewModel @Inject constructor(
    private val egressServersProvider: NetpEgressServersProvider,
    private val netPGeoswitchingRepository: NetPGeoswitchingRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val wgServerDebugProvider: WgServerDebugProvider,
    private val networkProtectionState: NetworkProtectionState,
    private val networkProtectionPixels: NetworkProtectionPixels,
) : ViewModel(), DefaultLifecycleObserver {
    private val viewState = MutableStateFlow(ViewState())
    internal fun viewState(): Flow<ViewState> = viewState.asStateFlow()

    internal data class ViewState(
        val items: List<CountryItem> = emptyList(),
    )

    data class CountryItem(
        val countryCode: String,
        val countryEmoji: String,
        val countryName: String,
        val cities: List<String>,
    )

    private var initialPreferredLocation: UserPreferredLocation? = null
    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        networkProtectionPixels.reportGeoswitchingScreenShown()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        viewModelScope.launch(dispatcherProvider.io()) {
            initialPreferredLocation = netPGeoswitchingRepository.getUserPreferredLocation()
            val countryItems = egressServersProvider.getServerLocations().map {
                CountryItem(
                    countryEmoji = getEmojiForCountryCode(it.countryCode),
                    countryCode = it.countryCode,
                    countryName = it.countryName,
                    cities = it.cities.sorted(),
                )
            }.sortedBy { it.countryName }

            if (countryItems.isEmpty()) {
                networkProtectionPixels.reportGeoswitchingNoLocations()
            }

            viewState.emit(
                ViewState(
                    items = countryItems,
                ),
            )
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        viewModelScope.launch(dispatcherProvider.io()) {
            val newPreferredLocation = netPGeoswitchingRepository.getUserPreferredLocation()
            if (networkProtectionState.isEnabled()) {
                if (initialPreferredLocation != newPreferredLocation) {
                    networkProtectionState.clearVPNConfigurationAndRestart()
                }
            }

            if (initialPreferredLocation != newPreferredLocation) {
                if (newPreferredLocation.countryCode != null) {
                    networkProtectionPixels.reportPreferredLocationSetToCustom()
                } else {
                    networkProtectionPixels.reportPreferredLocationSetToNearest()
                }
            }
        }
    }

    private fun getSelectedCountryCode(): String? {
        return runBlocking { netPGeoswitchingRepository.getUserPreferredLocation().countryCode }
    }

    fun onCountrySelected(countryCode: String) {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (getSelectedCountryCode() != countryCode) {
                netPGeoswitchingRepository.setUserPreferredLocation(UserPreferredLocation(countryCode = countryCode))
                wgServerDebugProvider.clearSelectedServerName()
            }
        }
    }

    fun onNearestAvailableCountrySelected() {
        viewModelScope.launch(dispatcherProvider.io()) {
            netPGeoswitchingRepository.setUserPreferredLocation(UserPreferredLocation())
            wgServerDebugProvider.clearSelectedServerName()
        }
    }

    fun hasNearestAvailableSelected(): Boolean = getSelectedCountryCode().isNullOrEmpty()

    fun isLocationSelected(countryCode: String): Boolean = getSelectedCountryCode() == countryCode
}
