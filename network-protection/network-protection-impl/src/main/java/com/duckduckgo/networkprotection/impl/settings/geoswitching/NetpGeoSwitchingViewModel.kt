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
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.configuration.WgServerDebugProvider
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoswitchingListItem.CountryItem
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoswitchingListItem.DividerItem
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoswitchingListItem.HeaderItem
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoswitchingListItem.RecommendedItem
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository.UserPreferredLocation
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ActivityScope::class)
class NetpGeoSwitchingViewModel @Inject constructor(
    private val contentProvider: GeoSwitchingContentProvider,
    private val netPGeoswitchingRepository: NetPGeoswitchingRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val wgServerDebugProvider: WgServerDebugProvider,
) : ViewModel(), DefaultLifecycleObserver {
    private val viewState = MutableStateFlow(ViewState())
    internal fun viewState(): Flow<ViewState> = viewState.asStateFlow()

    internal data class ViewState(
        val items: List<GeoswitchingListItem> = emptyList(),
    )

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        viewModelScope.launch(dispatcherProvider.io()) {
            val countryItems = contentProvider.getDownloadedData().map {
                CountryItem(
                    countryEmoji = getEmojiForCountryCode(it.countryCode),
                    countryCode = it.countryCode,
                    countryName = it.countryName,
                    cities = it.cities,
                )
            }

            val completeList = mutableListOf(
                HeaderItem(R.string.netpGeoswitchingHeaderRecommended),
                RecommendedItem(
                    title = R.string.netpGeoswitchingDefaultTitle,
                    subtitle = R.string.netpGeoswitchingDefaultSubtitle,
                ),
            ).apply {
                if (countryItems.isNotEmpty()) {
                    this.add(DividerItem)
                    this.add(HeaderItem(R.string.netpGeoswitchingHeaderCustom))
                    this.addAll(countryItems)
                }
            }

            viewState.emit(
                ViewState(
                    items = completeList,
                ),
            )
        }
    }

    fun getSelectedCountryCode(): String? {
        return runBlocking { netPGeoswitchingRepository.getUserPreferredLocation().countryCode }
    }

    fun onCountrySelected(countryCode: String) {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (netPGeoswitchingRepository.getUserPreferredLocation().countryCode != countryCode) {
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
}
