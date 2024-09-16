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

package com.duckduckgo.networkprotection.store

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository.UserPreferredLocation
import com.duckduckgo.networkprotection.store.db.NetPGeoswitchingDao
import com.duckduckgo.networkprotection.store.db.NetPGeoswitchingLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface NetPGeoswitchingRepository {

    suspend fun getUserPreferredLocation(): UserPreferredLocation

    suspend fun setUserPreferredLocation(userPreferredLocation: UserPreferredLocation)

    fun getLocations(): List<NetPGeoswitchingLocation>

    fun getLocationsFlow(): Flow<List<NetPGeoswitchingLocation>>

    fun replaceLocations(locations: List<NetPGeoswitchingLocation>)

    data class UserPreferredLocation(
        val countryCode: String? = null,
        val cityName: String? = null,
    )
}

class RealNetPGeoswitchingRepository constructor(
    private val networkProtectionPrefs: NetworkProtectionPrefs,
    private val netPGeoswitchingDao: NetPGeoswitchingDao,
    private val dispatcherProvider: DispatcherProvider,
) : NetPGeoswitchingRepository {
    override suspend fun getUserPreferredLocation(): UserPreferredLocation {
        return withContext(dispatcherProvider.io()) {
            UserPreferredLocation(
                countryCode = networkProtectionPrefs.getString(KEY_PREFERRED_COUNTRY, null),
                cityName = networkProtectionPrefs.getString(KEY_PREFERRED_CITY, null),
            )
        }
    }

    override suspend fun setUserPreferredLocation(userPreferredLocation: UserPreferredLocation) {
        withContext(dispatcherProvider.io()) {
            networkProtectionPrefs.putString(KEY_PREFERRED_COUNTRY, userPreferredLocation.countryCode)
            networkProtectionPrefs.putString(KEY_PREFERRED_CITY, userPreferredLocation.cityName)
        }
    }

    override fun getLocations(): List<NetPGeoswitchingLocation> = netPGeoswitchingDao.getLocations()

    override fun getLocationsFlow(): Flow<List<NetPGeoswitchingLocation>> = netPGeoswitchingDao.getLocationsFlow()

    override fun replaceLocations(locations: List<NetPGeoswitchingLocation>) {
        netPGeoswitchingDao.deleteLocations()
        netPGeoswitchingDao.insertIntoLocations(locations)
    }

    companion object {
        private const val KEY_PREFERRED_COUNTRY = "wg_preferred_country"
        private const val KEY_PREFERRED_CITY = "wg_preferred_city"
    }
}
