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
import app.cash.turbine.test
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
        )
    }

    @Test
    fun whenSoftwareRenderingModeEnabledThenFlowEmitsTrue() = runTest {
        fakeFeatureToggle.softwareRenderingMode().setRawStoredState(State(enable = true))

        testee.softwareRenderingModeEnabled.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSoftwareRenderingModeDisabledThenFlowEmitsFalse() = runTest {
        fakeFeatureToggle.softwareRenderingMode().setRawStoredState(State(enable = false))

        testee.softwareRenderingModeEnabled.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSoftwareRenderingModeChangesThenFlowEmitsNewValue() = runTest {
        fakeFeatureToggle.softwareRenderingMode().setRawStoredState(State(enable = true))

        testee.softwareRenderingModeEnabled.test {
            assertTrue(awaitItem())

            fakeFeatureToggle.softwareRenderingMode().setRawStoredState(State(enable = false))
            assertFalse(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
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
