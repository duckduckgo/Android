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

package com.duckduckgo.privacy.config.internal.plugins

import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import logcat.LogPriority
import logcat.LogcatLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PrivacyConfigOverrideLoggerTest {
    private val store = object : DevPrivacyConfigSettingsDataStore {
        override var remotePrivacyConfigUrl: String? = "http://10.0.2.2:8080/android-config.json"
        override var useCustomPrivacyConfigUrl = true
        override var canUrlBeChanged = true
    }
    private val lines = mutableListOf<String>()
    private val testee = PrivacyConfigOverrideLogger(store)

    @Before
    fun setUp() {
        LogcatLogger.install(object : LogcatLogger {
            override fun isLoggable(priority: LogPriority) = true
            override fun log(priority: LogPriority, tag: String, message: String) { lines.add(message) }
        })
    }

    @After
    fun tearDown() { LogcatLogger.uninstall() }

    @Test
    fun whenOverridePersistedThenLogOneLineWithPersistedMetadata() {
        testee.onPrivacyConfigPersisted(42, "local-etag", "http://10.0.2.2:8080/android-config.json")
        assertEquals(
            listOf("CONFIG_OVERRIDE_APPLIED stage=config version=42 etag=local-etag source=http://10.0.2.2:8080/android-config.json"),
            lines,
        )
    }

    @Test
    fun whenEtagAbsentThenLogEmptyField() {
        testee.onPrivacyConfigPersisted(42, null, "http://10.0.2.2:8080/android-config.json")
        assertEquals(
            listOf("CONFIG_OVERRIDE_APPLIED stage=config version=42 etag= source=http://10.0.2.2:8080/android-config.json"),
            lines,
        )
    }

    @Test
    fun whenDefaultConfigOrInvalidOverrideThenDoNotLog() {
        store.useCustomPrivacyConfigUrl = false
        testee.onPrivacyConfigPersisted(42, "etag", "http://10.0.2.2:8080/android-config.json")
        store.useCustomPrivacyConfigUrl = true
        store.remotePrivacyConfigUrl = "invalid"
        testee.onPrivacyConfigPersisted(42, "etag", "http://10.0.2.2:8080/android-config.json")
        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun whenDefaultResponseFinishesAfterOverrideEnabledThenDoNotLog() {
        testee.onPrivacyConfigPersisted(42, "etag", "https://staticcdn.duckduckgo.com/trackerblocking/config/v5/android-config.json")
        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun whenDownloadedOrLegacyCallbackThenDoNotLog() {
        testee.onPrivacyConfigDownloaded()
        testee.onPrivacyConfigPersisted()
        assertEquals(emptyList<String>(), lines)
    }
}
