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

package com.duckduckgo.app.browser.animations

import android.annotation.SuppressLint
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi")
class RealAddressBarTrackersAnimationManagerTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var fakeFeatureToggle: AddressBarTrackersAnimationFeatureToggle
    private lateinit var testee: RealAddressBarTrackersAnimationManager

    @Before
    fun setup() {
        fakeFeatureToggle = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "addressBarTrackersAnimation",
        ).build().create(AddressBarTrackersAnimationFeatureToggle::class.java)

        testee = RealAddressBarTrackersAnimationManager(
            addressBarTrackersAnimationFeatureToggle = fakeFeatureToggle,
            dispatcherProvider = coroutinesTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenFeatureEnabledThenIsFeatureEnabledReturnsTrue() = runTest {
        fakeFeatureToggle.feature().setRawStoredState(State(enable = true))

        val result = testee.isFeatureEnabled()

        assertTrue(result)
    }

    @Test
    fun whenFeatureDisabledThenIsFeatureEnabledReturnsFalse() = runTest {
        fakeFeatureToggle.feature().setRawStoredState(State(enable = false))

        val result = testee.isFeatureEnabled()

        assertFalse(result)
    }

    @Test
    fun whenFetchFeatureStateCalledThenCachesFeatureState() = runTest {
        fakeFeatureToggle.feature().setRawStoredState(State(enable = true))

        testee.fetchFeatureState()

        // Change the toggle state after fetching
        fakeFeatureToggle.feature().setRawStoredState(State(enable = false))

        // Should return cached value (true), not the new value (false)
        val result = testee.isFeatureEnabled()
        assertTrue(result)
    }

    @Test
    fun whenIsFeatureEnabledCalledMultipleTimesThenUseCachedValue() = runTest {
        fakeFeatureToggle.feature().setRawStoredState(State(enable = true))

        // First call caches the value
        assertTrue(testee.isFeatureEnabled())

        // Change the toggle state
        fakeFeatureToggle.feature().setRawStoredState(State(enable = false))

        // Subsequent calls should return cached value
        assertTrue(testee.isFeatureEnabled())
        assertTrue(testee.isFeatureEnabled())
    }

    @Test
    fun whenIsFeatureEnabledCalledWithoutFetchThenFetchesAndCaches() = runTest {
        fakeFeatureToggle.feature().setRawStoredState(State(enable = false))

        val result = testee.isFeatureEnabled()

        assertFalse(result)
    }

    @Test
    fun whenCurrentUrlIsNullThenShouldShowAnimationReturnsFalse() {
        val result = testee.shouldShowAnimation(currentUrl = null, lastAnimatedUrl = null)

        assertFalse(result)
    }

    @Test
    fun whenLastAnimatedUrlIsNullThenShouldShowAnimationReturnsTrue() {
        val result = testee.shouldShowAnimation(
            currentUrl = "https://www.example.com",
            lastAnimatedUrl = null,
        )

        assertTrue(result)
    }

    @Test
    fun whenSameETldPlusOneThenShouldShowAnimationReturnsFalse() {
        val result = testee.shouldShowAnimation(
            currentUrl = "https://www.example.com/page",
            lastAnimatedUrl = "https://www.example.com",
        )

        assertFalse(result)
    }

    @Test
    fun whenDifferentETldPlusOneThenShouldShowAnimationReturnsTrue() {
        val result = testee.shouldShowAnimation(
            currentUrl = "https://www.example.com",
            lastAnimatedUrl = "https://www.different.com",
        )

        assertTrue(result)
    }

    @Test
    fun whenSubdomainOfSameETldPlusOneThenShouldShowAnimationReturnsFalse() {
        val result = testee.shouldShowAnimation(
            currentUrl = "https://video.example.com",
            lastAnimatedUrl = "https://www.example.com",
        )

        assertFalse(result)
    }

    @Test
    fun whenDifferentUserSubdomainOnPublicSuffixThenShouldShowAnimationReturnsTrue() {
        val result = testee.shouldShowAnimation(
            currentUrl = "https://user2.github.io",
            lastAnimatedUrl = "https://user1.github.io",
        )

        assertTrue(result)
    }
}
