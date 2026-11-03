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

package com.duckduckgo.autofill.impl.importing

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class ImportFromGoogleAvailabilityImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)

    private val webViewCapabilityChecker: ImportGoogleBookmarksWebViewCapabilityChecker = mock()

    private lateinit var testee: ImportFromGoogleAvailabilityImpl

    @Before
    fun setUp() {
        testee = ImportFromGoogleAvailabilityImpl(
            autofillFeature = autofillFeature,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            webViewCapabilityChecker = webViewCapabilityChecker,
        )
    }

    @Test
    fun `Import is unavailable when feature is disabled`() = runTest {
        configureFeatureState(isEnabled = false)
        assertFalse(testee.isBookmarksImportAvailable())
    }

    @Test
    fun `Import is available when feature is enabled and WebView is capable`() = runTest {
        configureFeatureState(isEnabled = true)
        whenever(webViewCapabilityChecker.webViewCapableOfImporting()).thenReturn(true)
        assertTrue(testee.isBookmarksImportAvailable())
    }

    @Test
    fun `Import is unavailable when feature is enabled but WebView is not capable`() = runTest {
        configureFeatureState(isEnabled = true)
        whenever(webViewCapabilityChecker.webViewCapableOfImporting()).thenReturn(false)
        assertFalse(testee.isBookmarksImportAvailable())
    }

    private fun configureFeatureState(isEnabled: Boolean) {
        autofillFeature.canImportBookmarksFromGoogleTakeout().setRawStoredState(State(isEnabled))
    }
}
