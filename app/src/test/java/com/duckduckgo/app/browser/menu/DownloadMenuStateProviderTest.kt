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

package com.duckduckgo.app.browser.menu

import com.duckduckgo.app.settings.db.SettingsDataStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DownloadMenuStateProviderTest {

    private val settingsDataStore: SettingsDataStore = mock()
    private lateinit var provider: RealDownloadMenuStateProvider

    @Before
    fun setUp() {
        provider = RealDownloadMenuStateProvider(settingsDataStore)
    }

    @Test
    fun whenHasNewDownloadIsFalse_thenHasNewDownloadReturnsFalse() {
        whenever(settingsDataStore.hasNewDownload).thenReturn(false)

        assertFalse(provider.hasNewDownload())
    }

    @Test
    fun whenHasNewDownloadIsTrue_thenHasNewDownloadReturnsTrue() {
        whenever(settingsDataStore.hasNewDownload).thenReturn(true)

        assertTrue(provider.hasNewDownload())
    }

    @Test
    fun whenOnDownloadComplete_thenSetsHasNewDownloadToTrue() {
        provider.onDownloadComplete()

        verify(settingsDataStore).hasNewDownload = true
    }

    @Test
    fun whenOnDownloadsScreenViewed_thenSetsHasNewDownloadToFalse() {
        provider.onDownloadsScreenViewed()

        verify(settingsDataStore).hasNewDownload = false
    }
}
