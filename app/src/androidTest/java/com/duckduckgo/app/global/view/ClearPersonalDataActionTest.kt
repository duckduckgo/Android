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
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.fire.AppCacheClearer
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedRepository
import com.duckduckgo.cookies.api.DuckDuckGoCookieManager
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.sync.api.DeviceSyncState
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ClearPersonalDataActionTest {

    private lateinit var testee: ClearPersonalDataAction

    private val mockDataManager: WebDataManager = mock()
    private val mockClearingUnsentForgetAllPixelStore: UnsentForgetAllPixelStore = mock()
    private val mockTabRepository: TabRepository = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockCookieManager: DuckDuckGoCookieManager = mock()
    private val mockAppCacheClearer: AppCacheClearer = mock()
    private val mockThirdPartyCookieManager: ThirdPartyCookieManager = mock()
    private val mockFireproofWebsiteRepository: FireproofWebsiteRepository = mock()
    private val mockDeviceSyncState: DeviceSyncState = mock()
    private val mockSavedSitesRepository: SavedSitesRepository = mock()
    private val mockSitePermissionsManager: SitePermissionsManager = mock()
    private val mockNavigationHistory: NavigationHistory = mock()
    private val mockWebTrackersBlockedRepository: WebTrackersBlockedRepository = mock()

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
            thirdPartyCookieManager = mockThirdPartyCookieManager,
            fireproofWebsiteRepository = mockFireproofWebsiteRepository,
            deviceSyncState = mockDeviceSyncState,
            savedSitesRepository = mockSavedSitesRepository,
            sitePermissionsManager = mockSitePermissionsManager,
            navigationHistory = mockNavigationHistory,
            webTrackersBlockedRepository = mockWebTrackersBlockedRepository,
        )
        whenever(mockFireproofWebsiteRepository.getFireproofWebsites()).thenReturn(fireproofWebsites)
        whenever(mockDeviceSyncState.isUserSignedInOnDevice()).thenReturn(true)
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
        verify(mockSitePermissionsManager).clearAllButFireproof(any())
    }

    @Test
    fun whenClearCalledThenThirdPartyCookieSitesAreCleared() = runTest {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockThirdPartyCookieManager).clearAllData()
    }

    @Test
    fun whenClearCalledAndSyncEnabledThenSavedSitesDoesNotPruneDeleted() = runTest {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verifyNoInteractions(mockSavedSitesRepository)
    }

    @Test
    fun whenClearCalledAndSyncDisabledThenSavedSitesDoesNotPruneDeleted() = runTest {
        whenever(mockDeviceSyncState.isUserSignedInOnDevice()).thenReturn(false)
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockSavedSitesRepository).pruneDeleted()
    }

    @Test
    fun whenClearCalledThenWebTrackersAreCleared() = runTest {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockWebTrackersBlockedRepository).deleteAll()
    }

    @Test
    fun whenClearTabsAndAllDataAsyncCalledThenNavigationHistoryCleared() = runTest {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockNavigationHistory).clearHistory()
    }

    @Test
    fun whenClearTabsAndAllDataAsyncCalledThenCookieManagerFlushed() = runTest {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockCookieManager).flush()
    }

    @Test
    fun whenClearTabsOnlyCalledThenTabsAndSessionsCleared() = runTest {
        testee.clearTabsAsync(appInForeground = false)
        verify(mockTabRepository).deleteAll()
    }

    @Test
    fun whenClearTabsAsyncCalledWithAppInForegroundThenAppUsedFlagSetToTrue() = runTest {
        testee.clearTabsAsync(appInForeground = true)
        verify(mockSettingsDataStore).appUsedSinceLastClear = true
    }

    @Test
    fun whenClearTabsAsyncCalledWithAppNotInForegroundThenAppUsedFlagSetToFalse() = runTest {
        testee.clearTabsAsync(appInForeground = false)
        verify(mockSettingsDataStore).appUsedSinceLastClear = false
    }

    @Test
    fun whenClearTabsOnlyCalledThenTabsCleared() = runTest {
        testee.clearTabsOnly()
        verify(mockTabRepository).deleteAll()
    }

    @Test
    fun whenClearTabsOnlyCalledThenNoBrowserDataCleared() = runTest {
        testee.clearTabsOnly()
        verify(mockDataManager, never()).clearData(any(), any())
        verify(mockDataManager, never()).clearData(any(), any(), any(), any())
        verifyNoInteractions(mockAppCacheClearer)
        verifyNoInteractions(mockCookieManager)
        verifyNoInteractions(mockThirdPartyCookieManager)
        verifyNoInteractions(mockSitePermissionsManager)
        verifyNoInteractions(mockNavigationHistory)
        verifyNoInteractions(mockWebTrackersBlockedRepository)
        verifyNoInteractions(mockClearingUnsentForgetAllPixelStore)
    }

    @Test
    fun whenClearBrowserDataOnlyCalledWithPixelIncrementSetToTrueThenPixelCountIncremented() = runTest {
        testee.clearBrowserDataOnly(shouldFireDataClearPixel = true)
        verify(mockClearingUnsentForgetAllPixelStore).incrementCount()
    }

    @Test
    fun whenClearBrowserDataOnlyCalledWithPixelIncrementSetToFalseThenPixelCountNotIncremented() = runTest {
        testee.clearBrowserDataOnly(shouldFireDataClearPixel = false)
        verify(mockClearingUnsentForgetAllPixelStore, never()).incrementCount()
    }

    @Test
    fun whenClearBrowserDataOnlyCalledThenDataManagerClearsDataWithCorrectFlags() = runTest {
        testee.clearBrowserDataOnly(shouldFireDataClearPixel = false)
        verify(mockDataManager).clearData(any(), any(), shouldClearBrowserData = eq(true), shouldClearDuckAiData = eq(false))
    }

    @Test
    fun whenClearBrowserDataOnlyCalledThenAppCacheClearerClearsCache() = runTest {
        testee.clearBrowserDataOnly(shouldFireDataClearPixel = false)
        verify(mockAppCacheClearer).clearCache()
    }

    @Test
    fun whenClearBrowserDataOnlyCalledThenGeoLocationPermissionsAreCleared() = runTest {
        testee.clearBrowserDataOnly(shouldFireDataClearPixel = false)
        verify(mockSitePermissionsManager).clearAllButFireproof(any())
    }

    @Test
    fun whenClearBrowserDataOnlyCalledThenThirdPartyCookieSitesAreCleared() = runTest {
        testee.clearBrowserDataOnly(shouldFireDataClearPixel = false)
        verify(mockThirdPartyCookieManager).clearAllData()
    }

    @Test
    fun whenClearBrowserDataOnlyCalledAndSyncEnabledThenSavedSitesDoesNotPruneDeleted() = runTest {
        testee.clearBrowserDataOnly(shouldFireDataClearPixel = false)
        verifyNoInteractions(mockSavedSitesRepository)
    }

    @Test
    fun whenClearBrowserDataOnlyCalledAndSyncDisabledThenSavedSitesPruneDeleted() = runTest {
        whenever(mockDeviceSyncState.isUserSignedInOnDevice()).thenReturn(false)
        testee.clearBrowserDataOnly(shouldFireDataClearPixel = false)
        verify(mockSavedSitesRepository).pruneDeleted()
    }

    @Test
    fun whenClearBrowserDataOnlyCalledThenWebTrackersAreCleared() = runTest {
        testee.clearBrowserDataOnly(shouldFireDataClearPixel = false)
        verify(mockWebTrackersBlockedRepository).deleteAll()
    }

    @Test
    fun whenClearBrowserDataOnlyCalledThenNavigationHistoryCleared() = runTest {
        testee.clearBrowserDataOnly(shouldFireDataClearPixel = false)
        verify(mockNavigationHistory).clearHistory()
    }

    @Test
    fun whenClearBrowserDataOnlyCalledThenTabsNotCleared() = runTest {
        testee.clearBrowserDataOnly(shouldFireDataClearPixel = false)
        verify(mockTabRepository, never()).deleteAll()
    }

    @Test
    fun whenClearBrowserDataOnlyCalledThenCookieManagerFlushed() = runTest {
        testee.clearBrowserDataOnly(shouldFireDataClearPixel = false)
        verify(mockCookieManager).flush()
    }

    @Test
    fun whenClearDuckAiChatsOnlyCalledThenDataManagerClearsDataWithCorrectFlagsAndInteractions() = runTest {
        testee.clearDuckAiChatsOnly()
        verify(mockDataManager).clearData(any(), any(), shouldClearBrowserData = eq(false), shouldClearDuckAiData = eq(true))
        verifyNoInteractions(mockTabRepository)
        verifyNoInteractions(mockAppCacheClearer)
        verifyNoInteractions(mockCookieManager)
        verifyNoInteractions(mockThirdPartyCookieManager)
        verifyNoInteractions(mockSitePermissionsManager)
        verifyNoInteractions(mockNavigationHistory)
        verifyNoInteractions(mockClearingUnsentForgetAllPixelStore)
    }

    @Test
    fun whenSetAppUsedSinceLastClearFlagCalledWithTrueThenFlagSetToTrue() = runTest {
        testee.setAppUsedSinceLastClearFlag(true)
        verify(mockSettingsDataStore).appUsedSinceLastClear = true
    }

    @Test
    fun whenSetAppUsedSinceLastClearFlagCalledWithFalseThenFlagSetToFalse() = runTest {
        testee.setAppUsedSinceLastClearFlag(false)
        verify(mockSettingsDataStore).appUsedSinceLastClear = false
    }
}
