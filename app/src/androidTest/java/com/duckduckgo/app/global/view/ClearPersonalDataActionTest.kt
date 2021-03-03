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

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.fire.AppCacheClearer
import com.duckduckgo.app.fire.DatabaseCleaner
import com.duckduckgo.app.fire.DuckDuckGoCookieManager
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.location.GeoLocationPermissions
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

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
    private val mockDatabaseCleaner: DatabaseCleaner = mock()

    @Before
    fun setup() {
        testee = ClearPersonalDataAction(
            InstrumentationRegistry.getInstrumentation().targetContext,
            mockDataManager,
            mockClearingUnsentForgetAllPixelStore,
            mockTabRepository,
            mockSettingsDataStore,
            mockCookieManager,
            mockAppCacheClearer,
            mockGeoLocationPermissions,
            mockDatabaseCleaner
        )
    }

    @Test
    fun whenClearCalledWithPixelIncrementSetToTrueThenPixelCountIncremented() = runBlocking<Unit> {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = true)
        verify(mockClearingUnsentForgetAllPixelStore).incrementCount()
    }

    @Test
    fun whenClearCalledWithPixelIncrementSetToFalseThenPixelCountNotIncremented() = runBlocking<Unit> {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockClearingUnsentForgetAllPixelStore, never()).incrementCount()
    }

    @Test
    fun whenClearCalledThenDataManagerClearsSessions() = runBlocking<Unit> {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockDataManager).clearWebViewSessions()
    }

    @Test
    fun whenClearCalledThenDataManagerClearsData() = runBlocking<Unit> {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockDataManager).clearData(any(), any())
    }

    @Test
    fun whenClearCalledThenAppCacheClearerClearsCache() = runBlocking<Unit> {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockAppCacheClearer).clearCache()
    }

    @Test
    fun whenClearCalledThenTabsCleared() = runBlocking<Unit> {
        testee.clearTabsAndAllDataAsync(false, false)
        verify(mockTabRepository).deleteAll()
    }

    @Test
    fun whenClearCalledThenGeoLocationPermissionsAreCleared() = runBlocking<Unit> {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockGeoLocationPermissions).clearAllButFireproofed()
    }

    @Test
    fun whenClearCalledThenThenCleanDatabaseCalled() = runBlocking<Unit> {
        testee.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
        verify(mockDatabaseCleaner).cleanDatabase()
    }
}
