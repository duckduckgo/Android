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

package com.duckduckgo.adblocking.impl

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.adblocking.impl.remoteconfig.RealAdBlockingExtensionConfigProvider
import com.duckduckgo.adblocking.impl.remoteconfig.ScriptletEntry
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("DenyListedApi") // setRawStoredState
@RunWith(AndroidJUnit4::class)
class AdBlockingExtensionConfigProviderTest {

    private val feature = FakeFeatureToggleFactory.create(AdBlockingExtensionFeature::class.java)
    private val provider = RealAdBlockingExtensionConfigProvider(feature)

    private val validSettingsJson = """
        {
            "version": "2026.3.9",
            "scriptlets": {
                "scriptlets/isolated/ublock-filters.js": { "url": "https://cdn.example/isolated.js", "signature": "iso-sig" },
                "scriptlets/main/ublock-filters.js": { "url": "https://cdn.example/main.js", "signature": "main-sig" }
            }
        }
    """.trimIndent()

    @Test
    fun whenSettingsAreMissingThenSettingsAreNull() = runTest {
        feature.self().setRawStoredState(Toggle.State(remoteEnableState = true, settings = null))
        provider.onPrivacyConfigDownloaded()

        assertNull(provider.scriptletsSettings.value)
    }

    @Test
    fun whenSettingsJsonIsMalformedThenSettingsAreNull() = runTest {
        feature.self().setRawStoredState(Toggle.State(remoteEnableState = true, settings = "{ not valid json"))
        provider.onPrivacyConfigDownloaded()

        assertNull(provider.scriptletsSettings.value)
    }

    @Test
    fun whenVersionIsMissingThenSettingsAreNull() = runTest {
        feature.self().setRawStoredState(
            Toggle.State(remoteEnableState = true, settings = """{"scriptlets": {}}"""),
        )
        provider.onPrivacyConfigDownloaded()

        assertNull(provider.scriptletsSettings.value)
    }

    @Test
    fun whenScriptletsIsMissingThenSettingsAreNull() = runTest {
        feature.self().setRawStoredState(
            Toggle.State(remoteEnableState = true, settings = """{"version": "1.0"}"""),
        )
        provider.onPrivacyConfigDownloaded()

        assertNull(provider.scriptletsSettings.value)
    }

    @Test
    fun whenSettingsAreValidThenScriptletsSettingsReflectsParsedConfig() = runTest {
        feature.self().setRawStoredState(Toggle.State(remoteEnableState = true, settings = validSettingsJson))
        provider.onPrivacyConfigDownloaded()

        val scriptlets = provider.scriptletsSettings.value

        assertEquals("2026.3.9", scriptlets?.version)
        assertEquals(
            setOf("scriptlets/isolated/ublock-filters.js", "scriptlets/main/ublock-filters.js"),
            scriptlets?.scriptlets?.keys,
        )
        assertEquals(
            ScriptletEntry(url = "https://cdn.example/isolated.js", signature = "iso-sig"),
            scriptlets?.scriptlets?.get("scriptlets/isolated/ublock-filters.js"),
        )
    }

    @Test
    fun whenSettingsAreValidAtConstructionThenScriptletsSettingsEmitsInitialValue() = runTest {
        feature.self().setRawStoredState(Toggle.State(remoteEnableState = true, settings = validSettingsJson))
        val freshProvider = RealAdBlockingExtensionConfigProvider(feature)

        freshProvider.scriptletsSettings.filterNotNull().test {
            assertEquals("2026.3.9", awaitItem().version)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnPrivacyConfigDownloadedFiresAfterSettingsChangeThenScriptletsSettingsEmitsNewValue() = runTest {
        feature.self().setRawStoredState(Toggle.State(remoteEnableState = true, settings = validSettingsJson))
        val freshProvider = RealAdBlockingExtensionConfigProvider(feature)

        freshProvider.scriptletsSettings.filterNotNull().test {
            assertEquals("2026.3.9", awaitItem().version)

            val newSettingsJson = validSettingsJson.replace("2026.3.9", "2026.4.0")
            feature.self().setRawStoredState(Toggle.State(remoteEnableState = true, settings = newSettingsJson))
            freshProvider.onPrivacyConfigDownloaded()

            assertEquals("2026.4.0", awaitItem().version)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnPrivacyConfigDownloadedFiresWithUnchangedSettingsThenScriptletsSettingsDoesNotEmitDuplicates() = runTest {
        feature.self().setRawStoredState(Toggle.State(remoteEnableState = true, settings = validSettingsJson))
        val freshProvider = RealAdBlockingExtensionConfigProvider(feature)

        freshProvider.scriptletsSettings.filterNotNull().test {
            assertEquals("2026.3.9", awaitItem().version)

            freshProvider.onPrivacyConfigDownloaded()
            freshProvider.onPrivacyConfigDownloaded()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
