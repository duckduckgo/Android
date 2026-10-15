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

package com.duckduckgo.privacy.config.internal.plugins

import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader.ConfigDownloadResult
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalLoader
import com.duckduckgo.privacy.config.internal.PrivacyConfigOverrideMarker
import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import com.duckduckgo.privacy.config.store.PrivacyConfig
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PrivacyConfigOverrideLoggerTest {
    private val url = "http://10.0.2.2:8080/android-config.json"
    private val store = object : DevPrivacyConfigSettingsDataStore {
        override var remotePrivacyConfigUrl: String? = null
        override var useCustomPrivacyConfigUrl = false
        override var canUrlBeChanged = false
        override var lastSeenConfig: String? = null
    }
    private var stored: PrivacyConfig? = PrivacyConfig(version = 1, readme = "", eTag = "etag-1", timestamp = "t0")
    private val repository = object : PrivacyConfigRepository {
        override fun insert(privacyConfig: PrivacyConfig) {
            stored = privacyConfig
        }

        override fun get() = stored

        override fun delete() {
            stored = null
        }
    }
    private val lines = mutableListOf<String>()
    private val marker = object : PrivacyConfigOverrideMarker {
        override fun emit(line: String) {
            lines.add(line)
        }
    }
    private val testee = PrivacyConfigOverrideLogger(store, repository, marker)

    private var servedVersion = 1L
    private var servedETag = "etag-1"
    private var persists = 0

    // Mirrors the real downloader and persister: persist only a newer version, but always notify callbacks.
    private val downloader = object : PrivacyConfigDownloader {
        override suspend fun download(force: Boolean): ConfigDownloadResult {
            if (force) repository.insert(PrivacyConfig(version = -1, readme = "", eTag = "", timestamp = ""))
            if (servedVersion > repository.get()!!.version) {
                repository.insert(PrivacyConfig(version = servedVersion, readme = "", eTag = servedETag, timestamp = "t${++persists}"))
            }
            testee.onPrivacyConfigPersisted()
            return ConfigDownloadResult.Success
        }
    }
    private val loader = PrivacyConfigInternalLoader(downloader, store, repository, marker)

    @Before
    fun setUp() {
        testee.onPrivacyConfigPersisted()
        store.remotePrivacyConfigUrl = url
        store.useCustomPrivacyConfigUrl = true
    }

    @Test
    fun whenHourlyRefreshPersistsNewerConfigWhileOverriddenThenLogOneAppliedLine() = runTest {
        servedVersion = 2
        servedETag = "etag-2"
        downloader.download(force = false)
        assertEquals(listOf("CONFIG_OVERRIDE_APPLIED stage=config version=2 etag=etag-2 source=$url"), lines)
    }

    @Test
    fun whenNoOverrideThenLogNothing() = runTest {
        store.useCustomPrivacyConfigUrl = false
        servedVersion = 2
        downloader.download(force = false)
        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun whenVersionNotNewerThenLogNothing() = runTest {
        downloader.download(force = false)
        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun whenSetUrlThenLogExactlyOneAppliedLine() = runTest {
        servedVersion = 2
        servedETag = "etag-2"
        loader.handleCommand(PrivacyConfigInternalLoader.ACTION_SET_URL, url)
        assertEquals(listOf("CONFIG_OVERRIDE_APPLIED stage=config version=2 etag=etag-2 source=$url"), lines)
    }

    @Test
    fun whenSetUrlServesSameVersionThenStillLogOneAppliedLine() = runTest {
        loader.handleCommand(PrivacyConfigInternalLoader.ACTION_SET_URL, url)
        assertEquals(listOf("CONFIG_OVERRIDE_APPLIED stage=config version=1 etag=etag-1 source=$url"), lines)
    }

    @Test
    fun whenResetThenLogResetAndNoAppliedLine() = runTest {
        servedVersion = 3
        servedETag = "etag-3"
        loader.handleCommand(PrivacyConfigInternalLoader.ACTION_RESET, null)
        assertEquals(listOf("CONFIG_OVERRIDE_RESET stage=config version=3 etag=etag-3"), lines)
    }

    @Test
    fun whenFirstCallbackAfterInstallThenLogNothing() = runTest {
        store.lastSeenConfig = null
        servedVersion = 2
        downloader.download(force = false)
        assertEquals(emptyList<String>(), lines)
    }
}
