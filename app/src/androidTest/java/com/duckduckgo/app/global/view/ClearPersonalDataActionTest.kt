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

import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.fire.DuckDuckGoCookieManager
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Captor

@ExperimentalCoroutinesApi
class ClearPersonalDataActionTest {

    private lateinit var testee: ClearPersonalDataAction

    private val mockDataManager: WebDataManager = mock()
    private val mockClearingUnsentForgetAllPixelStore: UnsentForgetAllPixelStore = mock()
    private val mockTabRepository: TabRepository = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockCookieManager: DuckDuckGoCookieManager = mock()

    @Captor
    private val clearDataCallbackCaptor = argumentCaptor<() -> Unit>()

    @Before
    fun setup() {
        testee = ClearPersonalDataAction(
            InstrumentationRegistry.getInstrumentation().targetContext,
            mockDataManager,
            mockClearingUnsentForgetAllPixelStore,
            mockTabRepository,
            mockSettingsDataStore
        )
    }

    @UiThreadTest
    @Test
    fun whenClearCalledThenPixelCountIncremented() = runBlocking {
        testee.clearTabsAndAllDataAsync(false)
        verify(mockClearingUnsentForgetAllPixelStore).incrementCount()
    }

    @UiThreadTest
    @Test
    fun whenClearCalledThenDataManagerClearsSessions() = runBlocking {
        testee.clearTabsAndAllDataAsync(false)
        verify(mockDataManager).clearWebViewSessions()
    }

    @UiThreadTest
    @Test
    fun whenClearCalledThenDataManagerClearsData() = runBlocking {
        testee.clearTabsAndAllDataAsync(false)
        verify(mockDataManager).clearData(any(), any(), any())
    }

    @UiThreadTest
    @Test
    fun whenClearCalledThenDataManagerClearsCookies() = runBlocking {
        testee.clearTabsAndAllDataAsync(false)
        verify(mockDataManager).clearExternalCookies()
    }

    @UiThreadTest
    @Test
    fun whenClearCalledThenTabsCleared() = runBlocking {
        testee.clearTabsAndAllDataAsync(false)
        verify(mockTabRepository).deleteAll()
    }
}