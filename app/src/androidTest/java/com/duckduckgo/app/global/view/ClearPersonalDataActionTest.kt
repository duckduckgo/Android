/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.view

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.fire.AppCacheClearer
import com.duckduckgo.app.fire.ClearDataPixel
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepositoryAPI
import com.duckduckgo.app.location.GeoLocationPermissions
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.cookies.api.DuckDuckGoCookieManager
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@Suppress("RemoveExplicitTypeArguments")
class ClearPersonalDataActionTest {

    private lateinit var testee: ClearPersonalDataAction

    private val mockDataManager: WebDataManager = mock()
    private val mockClearingUnsentForgetAllPixelStore: UnsentForgetAllPixelStore = mock()
    private val mockTabRepository: TabRepository = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockCookieManager: DuckDuckGoCookieManager = mock()
    private val mockAppCacheClearer: AppCacheClearer = mock()
    private val mockGeoLocationPermissions: GeoLocationPermissions = mock()
    private val mockThirdPartyCookieManager: ThirdPartyCookieManager = mock()
    private val mockAdClickManager: AdClickManager = mock()
    private val mockFireproofWebsiteRepository: FireproofWebsiteRepositoryAPI = mock()
    private val mockSitePermissionsManager: SitePermissionsManager = mock()
    private val mockClearDataPixel: ClearDataPixel = mock()

    private val fireproofWebsites: LiveData<List<FireproofWebsiteEntity>> = MutableLiveData()

    @Before
    fun setup() {
        testee = ClearPersonalDataAction(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            dataManager = mockDataManager,
            clearingStore = mockClearingUnsentForgetAllPixelStore,
            tabRepository = mockTabRepository,
            settingsDataStore = mockSettingsDataStore,
            cookieManager = mockCookieManager,
            appCacheClearer = mockAppCacheClearer,
            geoLocationPermissions = mockGeoLocationPermissions,
            thirdPartyCookieManager = mockThirdPartyCookieManager,
            adClickManager = mockAdClickManager,
            fireproofWebsiteRepository = mockFireproofWebsiteRepository,
            sitePermissionsManager = mockSitePermissionsManager,
            clearDataPixel = mockClearDataPixel
        )
        whenever(mockFireproofWebsiteRepository.getFireproofWebsites()).thenReturn(fireproofWebsites)
    }

    @Test
    fun whenClearCalledWithPixelIncrementSetToTrueThenPixelCountIncremented() = runTest {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = true)
        verify(mockClearingUnsentForgetAllPixelStore).incrementCount()
    }

    @Test
    fun whenClearCalledWithPixelIncrementSetToFalseThenPixelCountNotIncremented() = runTest {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockClearingUnsentForgetAllPixelStore, never()).incrementCount()
    }

    @Test
    fun whenClearCalledThenDataManagerClearsSessions() = runTest {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockDataManager).clearWebViewSessions()
    }

    @Test
    fun whenClearCalledThenDataManagerClearsData() = runTest {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockDataManager).clearData(any(), any())
    }

    @Test
    fun whenClearCalledThenAppCacheClearerClearsCache() = runTest {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockAppCacheClearer).clearCache()
    }

    @Test
    fun whenClearCalledThenTabsCleared() = runTest {
        testee.clearTabsAndAllDataAsync(false, false)
        verify(mockTabRepository).deleteAll()
    }

    @Test
    fun whenClearCalledThenGeoLocationPermissionsAreCleared() = runTest {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockGeoLocationPermissions).clearAllButFireproofed()
    }

    @Test
    fun whenClearCalledThenThirdPartyCookieSitesAreCleared() = runTest {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockThirdPartyCookieManager).clearAllData()
    }
}
