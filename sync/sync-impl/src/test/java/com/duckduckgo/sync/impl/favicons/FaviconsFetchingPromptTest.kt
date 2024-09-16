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

package com.duckduckgo.sync.impl.favicons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.api.favicons.FaviconsFetchingPrompt
import com.duckduckgo.sync.api.favicons.FaviconsFetchingStore
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class FaviconsFetchingPromptTest {

    private var faviconsFetchingStore: FaviconsFetchingStore = mock()
    private var syncAccountRepository: SyncAccountRepository = mock()

    private lateinit var faviconsFetchingPrompt: FaviconsFetchingPrompt

    @Before
    fun setUp() {
        faviconsFetchingPrompt = SyncFaviconsFetchingPrompt(faviconsFetchingStore, syncAccountRepository)
    }

    @Test
    fun whenPromptAlreadyShownThenShouldNotShowAgain() {
        whenever(faviconsFetchingStore.promptShown).thenReturn(true)

        val shouldShow = faviconsFetchingPrompt.shouldShow()

        assertFalse(shouldShow)
    }

    @Test
    fun whenFaviconsFetchingEnabledThenShouldNotShow() {
        whenever(faviconsFetchingStore.promptShown).thenReturn(false)
        whenever(faviconsFetchingStore.isFaviconsFetchingEnabled).thenReturn(true)

        val shouldShow = faviconsFetchingPrompt.shouldShow()

        assertFalse(shouldShow)
    }

    @Test
    fun whenSyncNotEnabledThenShouldNotShow() {
        whenever(faviconsFetchingStore.promptShown).thenReturn(false)
        whenever(faviconsFetchingStore.isFaviconsFetchingEnabled).thenReturn(false)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        val shouldShow = faviconsFetchingPrompt.shouldShow()

        assertFalse(shouldShow)
    }

    @Test
    fun whenSyncOnlyHasOnceDeviceConnectedThenShouldNotShow() {
        whenever(faviconsFetchingStore.promptShown).thenReturn(false)
        whenever(faviconsFetchingStore.isFaviconsFetchingEnabled).thenReturn(false)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        val connectedDevices = listOf(TestSyncFixtures.connectedDevice)
        whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Result.Success(connectedDevices))

        val shouldShow = faviconsFetchingPrompt.shouldShow()

        assertFalse(shouldShow)
    }

    @Test
    fun whenSyncHasMoreThenOnceDevicesConnectedThenShouldShow() {
        whenever(faviconsFetchingStore.promptShown).thenReturn(false)
        whenever(faviconsFetchingStore.isFaviconsFetchingEnabled).thenReturn(false)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        val connectedDevices = listOf(TestSyncFixtures.connectedDevice, TestSyncFixtures.connectedDevice)
        whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Result.Success(connectedDevices))

        val shouldShow = faviconsFetchingPrompt.shouldShow()

        assertTrue(shouldShow)
    }

    @Test
    fun whenPromptNotShownThenShouldShow() {
        whenever(faviconsFetchingStore.promptShown).thenReturn(false)

        val shouldShow = faviconsFetchingPrompt.shouldShow()

        assertFalse(shouldShow)
    }
}
