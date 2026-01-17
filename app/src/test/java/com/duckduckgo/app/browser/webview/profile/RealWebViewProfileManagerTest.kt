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

package com.duckduckgo.app.browser.webview.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability
import com.duckduckgo.app.fire.FireproofRepository
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.io.File

@RunWith(AndroidJUnit4::class)
class RealWebViewProfileManagerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var mockFireproofRepository: FireproofRepository

    @Mock
    private lateinit var mockCapabilityChecker: WebViewCapabilityChecker

    @Mock
    private lateinit var mockAndroidBrowserConfigFeature: AndroidBrowserConfigFeature

    @Mock
    private lateinit var mockToggle: Toggle

    private lateinit var testDataStoreFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var testee: RealWebViewProfileManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        testDataStoreFile = File.createTempFile("webview_profile_preferences_test", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = coroutineTestRule.testScope,
            produceFile = { testDataStoreFile },
        )
        testee = RealWebViewProfileManager(
            store = dataStore,
            fireproofRepository = mockFireproofRepository,
            capabilityChecker = mockCapabilityChecker,
            androidBrowserConfigFeature = mockAndroidBrowserConfigFeature,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    @After
    fun tearDown() {
        testDataStoreFile.delete()
    }

    @Test
    fun whenFeatureFlagDisabled_thenProfileSwitchingNotAvailable() = runTest {
        configureFeatureFlag(enabled = false)
        whenever(mockCapabilityChecker.isSupported(WebViewCapability.MultiProfile)).thenReturn(true)

        val result = testee.isProfileSwitchingAvailable()

        assertFalse(result)
    }

    @Test
    fun whenMultiProfileNotSupported_thenProfileSwitchingNotAvailable() = runTest {
        configureFeatureFlag(enabled = true)
        whenever(mockCapabilityChecker.isSupported(WebViewCapability.MultiProfile)).thenReturn(false)

        val result = testee.isProfileSwitchingAvailable()

        assertFalse(result)
    }

    @Test
    fun whenFeatureFlagEnabledAndMultiProfileSupported_thenProfileSwitchingAvailable() = runTest {
        configureFeatureFlag(enabled = true)
        whenever(mockCapabilityChecker.isSupported(WebViewCapability.MultiProfile)).thenReturn(true)

        val result = testee.isProfileSwitchingAvailable()

        assertTrue(result)
    }

    @Test
    fun whenNotInitialized_thenGetCurrentProfileNameReturnsEmpty() {
        val result = testee.getCurrentProfileName()

        assertEquals("", result)
    }

    @Test
    fun whenInitializedWithProfileIndex0_thenGetCurrentProfileNameReturnsEmpty() = runTest {
        testee.initialize()

        assertEquals("", testee.getCurrentProfileName())
    }

    @Test
    fun whenInitializeCalledMultipleTimes_thenOnlyInitializesOnce() = runTest {
        testee.initialize()
        testee.initialize()
        testee.initialize()

        assertEquals("", testee.getCurrentProfileName())
    }

    @Test
    fun whenSwitchToNewProfileAndProfileSwitchingNotAvailable_thenReturnsFalse() = runTest {
        configureFeatureFlag(enabled = false)

        val result = testee.switchToNewProfile()

        assertFalse(result)
    }

    private fun configureFeatureFlag(enabled: Boolean) {
        whenever(mockToggle.isEnabled()).thenReturn(enabled)
        whenever(mockAndroidBrowserConfigFeature.webViewProfiles()).thenReturn(mockToggle)
    }
}
