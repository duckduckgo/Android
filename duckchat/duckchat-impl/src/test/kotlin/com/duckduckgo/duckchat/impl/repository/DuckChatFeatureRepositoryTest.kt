/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.repository

import android.content.Context
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckChatFeatureRepositoryTest {
    private val mockDataStore: DuckChatDataStore = mock()

    private val mockContext: Context = mock()

    private val testee = RealDuckChatFeatureRepository(mockDataStore, mockContext)

    @Test
    fun whenSetDuckChatUserEnabledThenSetInDataStore() = runTest {
        testee.setDuckChatUserEnabled(true)

        verify(mockDataStore).setDuckChatUserEnabled(true)
    }

    @Test
    fun whenSetShowInBrowserMenuThenSetInDataStore() = runTest {
        testee.setShowInBrowserMenu(true)

        verify(mockDataStore).setShowInBrowserMenu(true)
    }

    @Test
    fun whenSetShowInAddressBarThenSetInDataStore() = runTest {
        testee.setShowInAddressBar(false)

        verify(mockDataStore).setShowInAddressBar(false)
    }

    @Test
    fun `when setInputScreenUserSetting then set in data store`() = runTest {
        testee.setInputScreenUserSetting(false)

        verify(mockDataStore).setInputScreenUserSetting(false)
    }

    @Test
    fun whenObserveDuckChatUserEnabledThenObserveDataStore() = runTest {
        whenever(mockDataStore.observeDuckChatUserEnabled()).thenReturn(flowOf(true, false))

        val results = testee.observeDuckChatUserEnabled().take(2).toList()
        assertTrue(results[0])
        assertFalse(results[1])
    }

    @Test
    fun whenObserveShowInBrowserMenuThenObserveDataStore() = runTest {
        whenever(mockDataStore.observeShowInBrowserMenu()).thenReturn(flowOf(true, false))

        val results = testee.observeShowInBrowserMenu().take(2).toList()
        assertTrue(results[0])
        assertFalse(results[1])
    }

    @Test
    fun whenObserveShowInAddressBarThenObserveDataStore() = runTest {
        whenever(mockDataStore.observeShowInAddressBar()).thenReturn(flowOf(false, true))

        val results = testee.observeShowInAddressBar().take(2).toList()
        assertFalse(results[0])
        assertTrue(results[1])
    }

    @Test
    fun `when observeInputScreenUserSettingEnabled then observe data store`() = runTest {
        whenever(mockDataStore.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(false, true))

        val results = testee.observeInputScreenUserSettingEnabled().take(2).toList()
        assertFalse(results[0])
        assertTrue(results[1])
    }

    @Test
    fun whenIsDuckChatUserEnabledThenGetFromDataStore() = runTest {
        whenever(mockDataStore.isDuckChatUserEnabled()).thenReturn(false)
        assertFalse(testee.isDuckChatUserEnabled())
    }

    @Test
    fun whenShouldShowInBrowserMenuThenGetFromDataStore() = runTest {
        whenever(mockDataStore.getShowInBrowserMenu()).thenReturn(true)

        assertTrue(testee.shouldShowInBrowserMenu())
    }

    @Test
    fun whenShouldShowInAddressBarThenGetFromDataStore() = runTest {
        whenever(mockDataStore.getShowInAddressBar()).thenReturn(true)
        assertTrue(testee.shouldShowInAddressBar())
    }

    @Test
    fun `when isInputScreenUserSettingEnabled called, then get from data store`() = runTest {
        whenever(mockDataStore.isInputScreenUserSettingEnabled()).thenReturn(true)
        assertTrue(testee.isInputScreenUserSettingEnabled())
    }

    @Test
    fun whenRegisterDuckChatOpenedThenDataStoreCalled() = runTest {
        whenever(mockDataStore.wasOpenedBefore()).thenReturn(false)
        testee.registerOpened()

        verify(mockDataStore).registerOpened()
    }

    @Test
    fun whenRegisterDuckChatOpenedFirstTimeThenWidgetsUpdated() = runTest {
        whenever(mockDataStore.wasOpenedBefore()).thenReturn(false)

        testee.registerOpened()

        verify(mockContext).sendBroadcast(
            argThat { intent -> true },
        )
        verify(mockDataStore).registerOpened()
    }

    @Test
    fun whenRegisterDuckChatOpenedNotFirstTimeThenWidgetsNotUpdated() = runTest {
        whenever(mockDataStore.wasOpenedBefore()).thenReturn(true)

        testee.registerOpened()

        verify(mockContext, never()).sendBroadcast(
            argThat { intent -> true },
        )
        verify(mockDataStore).registerOpened()
    }

    @Test
    fun whenWasOpenedBeforeCheckedThenReturnDataFromTheStore() = runTest {
        whenever(mockDataStore.wasOpenedBefore()).thenReturn(true)

        val result = testee.wasOpenedBefore()

        assertTrue(result)
    }
}
