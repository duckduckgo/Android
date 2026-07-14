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

package com.duckduckgo.common.utils.edgetoedge

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RealEdgeToEdgeProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(EdgeToEdgeFeature::class.java)
    private val testee = RealEdgeToEdgeProvider(
        edgeToEdgeFeature = feature,
        dispatchers = coroutineRule.testDispatcherProvider,
        appScope = coroutineRule.testScope,
    )

    @Test
    fun whenMasterDisabledThenBucketDisabledEvenIfBucketToggleEnabled() {
        feature.self().setRawStoredState(State(enable = false))
        feature.settings().setRawStoredState(State(enable = true))

        assertFalse(testee.isEnabled(EdgeToEdgeBucket.SETTINGS))
    }

    @Test
    fun whenMasterEnabledButBucketDisabledThenBucketDisabled() {
        feature.self().setRawStoredState(State(enable = true))
        feature.settings().setRawStoredState(State(enable = false))

        assertFalse(testee.isEnabled(EdgeToEdgeBucket.SETTINGS))
    }

    @Test
    fun whenMasterAndBucketEnabledThenBucketEnabled() {
        feature.self().setRawStoredState(State(enable = true))
        feature.browser().setRawStoredState(State(enable = true))

        assertTrue(testee.isEnabled(EdgeToEdgeBucket.BROWSER))
    }

    @Test
    fun whenMasterAndOneBucketEnabledThenOtherBucketsRemainDisabled() {
        feature.self().setRawStoredState(State(enable = true))
        feature.browser().setRawStoredState(State(enable = true))
        feature.settings().setRawStoredState(State(enable = false))

        assertTrue(testee.isEnabled(EdgeToEdgeBucket.BROWSER))
        assertFalse(testee.isEnabled(EdgeToEdgeBucket.SETTINGS))
    }

    @Test
    fun whenMasterDisabledThenWebviewDisabledEvenIfWebviewToggleEnabled() {
        feature.self().setRawStoredState(State(enable = false))
        feature.webview().setRawStoredState(State(enable = true))

        assertFalse(testee.isEnabled(EdgeToEdgeBucket.WEBVIEW))
    }

    @Test
    fun whenMasterEnabledButWebviewDisabledThenWebviewDisabled() {
        feature.self().setRawStoredState(State(enable = true))
        feature.webview().setRawStoredState(State(enable = false))

        assertFalse(testee.isEnabled(EdgeToEdgeBucket.WEBVIEW))
    }

    @Test
    fun whenMasterAndWebviewEnabledThenWebviewEnabled() {
        feature.self().setRawStoredState(State(enable = true))
        feature.webview().setRawStoredState(State(enable = true))

        assertTrue(testee.isEnabled(EdgeToEdgeBucket.WEBVIEW))
    }

    @Test
    fun whenMasterDisabledThenOnboardingDisabledEvenIfOnboardingToggleEnabled() {
        feature.self().setRawStoredState(State(enable = false))
        feature.onboarding().setRawStoredState(State(enable = true))

        assertFalse(testee.isEnabled(EdgeToEdgeBucket.ONBOARDING))
    }

    @Test
    fun whenMasterEnabledButOnboardingDisabledThenOnboardingDisabled() {
        feature.self().setRawStoredState(State(enable = true))
        feature.onboarding().setRawStoredState(State(enable = false))

        assertFalse(testee.isEnabled(EdgeToEdgeBucket.ONBOARDING))
    }

    @Test
    fun whenMasterAndOnboardingEnabledThenOnboardingEnabled() {
        feature.self().setRawStoredState(State(enable = true))
        feature.onboarding().setRawStoredState(State(enable = true))

        assertTrue(testee.isEnabled(EdgeToEdgeBucket.ONBOARDING))
    }

    @Test
    fun whenMasterDisabledThenMiscDisabledEvenIfMiscToggleEnabled() {
        feature.self().setRawStoredState(State(enable = false))
        feature.misc().setRawStoredState(State(enable = true))

        assertFalse(testee.isEnabled(EdgeToEdgeBucket.MISC))
    }

    @Test
    fun whenMasterEnabledButMiscDisabledThenMiscDisabled() {
        feature.self().setRawStoredState(State(enable = true))
        feature.misc().setRawStoredState(State(enable = false))

        assertFalse(testee.isEnabled(EdgeToEdgeBucket.MISC))
    }

    @Test
    fun whenMasterAndMiscEnabledThenMiscEnabled() {
        feature.self().setRawStoredState(State(enable = true))
        feature.misc().setRawStoredState(State(enable = true))

        assertTrue(testee.isEnabled(EdgeToEdgeBucket.MISC))
    }

    @Test
    fun whenMasterDisabledThenBottomSheetsDisabledEvenIfBottomSheetsToggleEnabled() {
        feature.self().setRawStoredState(State(enable = false))
        feature.bottomSheets().setRawStoredState(State(enable = true))

        assertFalse(testee.isEnabled(EdgeToEdgeBucket.BOTTOM_SHEETS))
    }

    @Test
    fun whenMasterEnabledButBottomSheetsDisabledThenBottomSheetsDisabled() {
        feature.self().setRawStoredState(State(enable = true))
        feature.bottomSheets().setRawStoredState(State(enable = false))

        assertFalse(testee.isEnabled(EdgeToEdgeBucket.BOTTOM_SHEETS))
    }

    @Test
    fun whenMasterAndBottomSheetsEnabledThenBottomSheetsEnabled() {
        feature.self().setRawStoredState(State(enable = true))
        feature.bottomSheets().setRawStoredState(State(enable = true))

        assertTrue(testee.isEnabled(EdgeToEdgeBucket.BOTTOM_SHEETS))
    }
}
