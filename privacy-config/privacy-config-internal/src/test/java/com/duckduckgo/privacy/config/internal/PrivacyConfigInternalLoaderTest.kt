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

package com.duckduckgo.privacy.config.internal

import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader.ConfigDownloadResult.Success
import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyConfigInternalLoaderTest {
    private val store = object : DevPrivacyConfigSettingsDataStore {
        override var remotePrivacyConfigUrl: String? = null
        override var useCustomPrivacyConfigUrl = false
        override var canUrlBeChanged = false
    }
    private var downloads = 0
    private val downloader = object : PrivacyConfigDownloader {
        override suspend fun download(force: Boolean): PrivacyConfigDownloader.ConfigDownloadResult {
            assertTrue(force)
            downloads++
            return Success
        }
    }
    private val testee = PrivacyConfigInternalLoader(downloader, store)

    @Test
    fun whenSetUrlThenEnableOverrideAndForceLoad() = runTest {
        assertTrue(testee.handleCommand("com.duckduckgo.privacy.config.internal.SET_URL", "http://10.0.2.2:8080/android-config.json"))
        assertEquals("http://10.0.2.2:8080/android-config.json", store.remotePrivacyConfigUrl)
        assertTrue(store.useCustomPrivacyConfigUrl)
        assertTrue(store.canUrlBeChanged)
        assertEquals(1, downloads)
    }

    @Test
    fun whenInvalidUrlThenLeaveSettingsAndConfigUnchanged() = runTest {
        store.remotePrivacyConfigUrl = "https://example.com/config.json"
        store.useCustomPrivacyConfigUrl = true
        listOf(null, "", "not a url", "file:///tmp/config.json", "https:///config.json", "http://", "http://example.com/a b").forEach {
            assertFalse(testee.handleCommand("com.duckduckgo.privacy.config.internal.SET_URL", it))
        }
        assertEquals("https://example.com/config.json", store.remotePrivacyConfigUrl)
        assertTrue(store.useCustomPrivacyConfigUrl)
        assertEquals(0, downloads)
    }

    @Test
    fun whenResetThenClearOverrideAndForceLoad() = runTest {
        store.remotePrivacyConfigUrl = "http://localhost/config.json"
        store.useCustomPrivacyConfigUrl = true
        store.canUrlBeChanged = true
        assertTrue(testee.handleCommand("com.duckduckgo.privacy.config.internal.RESET", null))
        assertNull(store.remotePrivacyConfigUrl)
        assertFalse(store.useCustomPrivacyConfigUrl)
        assertFalse(store.canUrlBeChanged)
        assertEquals(1, downloads)
    }

    @Test
    fun whenRepeatedCommandThenDownloadAgain() = runTest {
        repeat(2) { testee.handleCommand("com.duckduckgo.privacy.config.internal.SET_URL", "https://example.com/config.json") }
        assertEquals(2, downloads)
    }

    @Test
    fun whenManualLoadThenForceDownload() = runTest {
        assertEquals(Success, testee.load())
        assertEquals(1, downloads)
    }

    @Test
    fun whenUnknownActionThenDoNothing() = runTest {
        assertFalse(testee.handleCommand("unknown", "https://example.com/config.json"))
        assertEquals(0, downloads)
    }
}
