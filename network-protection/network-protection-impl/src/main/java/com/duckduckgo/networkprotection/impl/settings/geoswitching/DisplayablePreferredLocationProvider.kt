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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.impl.configuration.WgServerDebugProvider
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface DisplayablePreferredLocationProvider {
    suspend fun getDisplayablePreferredLocation(): String?
}

@ContributesBinding(ActivityScope::class)
class RealDisplayablePreferredLocationProvider @Inject constructor(
    private val netPGeoswitchingRepository: NetPGeoswitchingRepository,
    private val wgServerDebugProvider: WgServerDebugProvider,
    private val dispatcherProvider: DispatcherProvider,
) : DisplayablePreferredLocationProvider {
    override suspend fun getDisplayablePreferredLocation(): String? {
        return withContext(dispatcherProvider.io()) {
            val currentUserPreferredLocation = netPGeoswitchingRepository.getUserPreferredLocation()
            return@withContext if (wgServerDebugProvider.getSelectedServerName() != null) {
                val server = wgServerDebugProvider.getSelectedServerName()!!
                val countryName = currentUserPreferredLocation.countryCode?.run {
                    getDisplayableCountry(this)
                }
                if (countryName.isNullOrEmpty()) {
                    server
                } else {
                    "$server (${currentUserPreferredLocation.cityName}, $countryName)"
                }
            } else if (!currentUserPreferredLocation.countryCode.isNullOrEmpty()) {
                if (!currentUserPreferredLocation.cityName.isNullOrEmpty()) {
                    "${currentUserPreferredLocation.cityName!!}, ${getDisplayableCountry(currentUserPreferredLocation.countryCode!!)}"
                } else {
                    getDisplayableCountry(currentUserPreferredLocation.countryCode!!)
                }
            } else {
                null
            }
        }
    }
}
