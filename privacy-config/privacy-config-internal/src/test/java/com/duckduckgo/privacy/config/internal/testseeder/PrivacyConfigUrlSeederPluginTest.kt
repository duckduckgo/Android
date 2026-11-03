/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.privacy.config.internal.testseeder

import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader.ConfigDownloadResult
import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import com.duckduckgo.privacy.config.store.PrivacyConfig
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import com.duckduckgo.testseeder.api.TestSeederKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class PrivacyConfigUrlSeederPluginTest {

    private val store = FakeDevPrivacyConfigSettingsDataStore()
    private val repository: PrivacyConfigRepository = mock()
    private val downloader: PrivacyConfigDownloader = mock()
    private val plugin = PrivacyConfigUrlSeederPlugin(store, repository, downloader)

    @Test
    fun whenValidUrlThenStoresOverrideAndDownloadsAfterVersionReset() = runTest {
        whenever(downloader.download()).thenReturn(ConfigDownloadResult.Success)

        plugin.apply(TestSeederKey.PRIVACY_CONFIG_URL.key, " $URL ")

        assertEquals(URL, store.remotePrivacyConfigUrl)
        assertTrue(store.useCustomPrivacyConfigUrl)
        inOrder(repository, downloader).run {
            verify(repository).insert(PrivacyConfig(version = -1, readme = "", eTag = "", timestamp = ""))
            verify(downloader).download()
        }
    }

    @Test
    fun whenDownloadFailsThenThrows() {
        runTest { whenever(downloader.download()).thenReturn(ConfigDownloadResult.Error("boom")) }

        assertThrows(IllegalStateException::class.java) {
            runTest { plugin.apply(TestSeederKey.PRIVACY_CONFIG_URL.key, URL) }
        }
    }

    @Test
    fun whenUrlIsInvalidThenThrowsWithoutTouchingConfig() {
        listOf("", "not a url", "ftp://example.com/config.json", "https://").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                runTest { plugin.apply(TestSeederKey.PRIVACY_CONFIG_URL.key, value) }
            }
        }

        assertNull(store.remotePrivacyConfigUrl)
        assertFalse(store.useCustomPrivacyConfigUrl)
        verifyNoInteractions(repository, downloader)
    }

    private class FakeDevPrivacyConfigSettingsDataStore : DevPrivacyConfigSettingsDataStore {
        override var remotePrivacyConfigUrl: String? = null
        override var useCustomPrivacyConfigUrl: Boolean = false
        override var canUrlBeChanged: Boolean = false
    }

    private companion object {
        const val URL = "https://privacy-test-pages.site/content-scope-scripts/infra/config/conditional-matching-experiments.json"
    }
}
