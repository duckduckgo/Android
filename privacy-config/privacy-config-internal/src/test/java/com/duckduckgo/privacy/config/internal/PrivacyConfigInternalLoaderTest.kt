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

package com.duckduckgo.privacy.config.internal

import com.duckduckgo.privacy.config.api.PRIVACY_REMOTE_CONFIG_URL
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader.ConfigDownloadResult
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader.ConfigDownloadResult.Success
import com.duckduckgo.privacy.config.internal.OverrideCommand.Reset
import com.duckduckgo.privacy.config.internal.OverrideCommand.SetUrl
import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import com.duckduckgo.privacy.config.store.PrivacyConfig
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PrivacyConfigInternalLoaderTest {
    private val store = object : DevPrivacyConfigSettingsDataStore {
        override var remotePrivacyConfigUrl: String? = null
        override var useCustomPrivacyConfigUrl = false
        override var canUrlBeChanged = false
    }
    private var downloads = 0
    private var downloadResult: ConfigDownloadResult = Success
    private val downloader = object : PrivacyConfigDownloader {
        override suspend fun download(force: Boolean): ConfigDownloadResult {
            assertTrue(force)
            downloads++
            return downloadResult
        }
    }
    private val repository = mock<PrivacyConfigRepository>().apply {
        whenever(get()).thenReturn(PrivacyConfig(version = 42, readme = "", eTag = "local-etag", timestamp = ""))
    }
    private val lines = mutableListOf<String>()
    private val marker = object : PrivacyConfigOverrideMarker {
        override fun emit(line: String) {
            lines.add(line)
        }
    }
    private val testee = PrivacyConfigInternalLoader(downloader, store, repository, marker)

    @Test
    fun whenSetUrlThenEnableOverrideForceLoadAndEmitAppliedLine() = runTest {
        testee.handleCommand(SetUrl(URL))
        assertEquals(URL, store.remotePrivacyConfigUrl)
        assertTrue(store.useCustomPrivacyConfigUrl)
        assertEquals(1, downloads)
        assertEquals(listOf("CONFIG_OVERRIDE_APPLIED version=42 etag=local-etag source=$URL"), lines)
    }

    @Test
    fun whenInvalidUrlThenLeaveSettingsUnchangedAndEmitFailedLine() = runTest {
        store.remotePrivacyConfigUrl = URL
        store.useCustomPrivacyConfigUrl = true
        val sources = mapOf(
            null to "",
            "" to "",
            "not a url" to "notaurl",
            "file:///tmp/config.json" to "file:///tmp/config.json",
            "HTTP://example.com/config.json" to "HTTP://example.com/config.json",
            "https:///config.json" to "https:///config.json",
            "http://" to "http://",
            "http://example.com/a b" to "http://example.com/ab",
        )
        sources.keys.forEach { testee.handleCommand(SetUrl(it)) }
        assertEquals(URL, store.remotePrivacyConfigUrl)
        assertTrue(store.useCustomPrivacyConfigUrl)
        assertEquals(0, downloads)
        assertEquals(sources.values.map { "CONFIG_OVERRIDE_FAILED action=set_url reason=invalid_url source=$it" }, lines)
    }

    @Test
    fun whenResetThenClearOverrideForceLoadAndEmitResetLine() = runTest {
        store.remotePrivacyConfigUrl = URL
        store.useCustomPrivacyConfigUrl = true
        testee.handleCommand(Reset)
        assertNull(store.remotePrivacyConfigUrl)
        assertFalse(store.useCustomPrivacyConfigUrl)
        assertEquals(1, downloads)
        assertEquals(listOf("CONFIG_OVERRIDE_RESET version=42 etag=local-etag"), lines)
    }

    @Test
    fun whenDownloadFailsThenEmitFailedLineWithTheUrlTried() = runTest {
        downloadResult = ConfigDownloadResult.Error("offline")
        testee.handleCommand(SetUrl(URL))
        testee.handleCommand(Reset)
        val expected = listOf(
            "CONFIG_OVERRIDE_FAILED action=set_url reason=download_failed source=$URL",
            "CONFIG_OVERRIDE_FAILED action=reset reason=download_failed source=$PRIVACY_REMOTE_CONFIG_URL",
        )
        assertEquals(expected, lines)
    }

    @Test
    fun whenManualLoadThenForceDownloadWithoutResultLine() = runTest {
        assertEquals(Success, testee.load())
        assertEquals(1, downloads)
        assertEquals(emptyList<String>(), lines)
    }

    private companion object {
        const val URL = "http://localhost:8080/v6/android-config.json"
    }
}
