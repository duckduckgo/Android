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
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.adblocking.impl.remoteconfig.ContingencyMessageStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("DenyListedApi") // setRawStoredState
@RunWith(AndroidJUnit4::class)
class RealContingencyMessageHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(AdBlockingExtensionFeature::class.java)

    private val shownFlow = MutableStateFlow(false)
    private val store = object : ContingencyMessageStore {
        override val shown: StateFlow<Boolean> = shownFlow
        override suspend fun setShown() {
            shownFlow.value = true
        }

        override suspend fun reset() {
            shownFlow.value = false
        }
    }

    private val handler = RealContingencyMessageHandler(
        feature = feature,
        store = store,
        appScope = coroutineRule.testScope,
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    private fun setToggles(uxImprovements: Boolean, contingency: Boolean) {
        feature.adBlockingUXImprovements().setRawStoredState(Toggle.State(remoteEnableState = uxImprovements))
        feature.enableContingencyMode().setRawStoredState(Toggle.State(remoteEnableState = contingency))
    }

    @Test
    fun whenAllGatesOpenAndYouTubeAndNotShownThenShouldShow() {
        setToggles(uxImprovements = true, contingency = true)

        assertTrue(handler.shouldShow("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun whenYouTubeSubdomainThenShouldShow() {
        setToggles(uxImprovements = true, contingency = true)

        assertTrue(handler.shouldShow("https://m.youtube.com/watch?v=abc"))
    }

    @Test
    fun whenUxImprovementsDisabledThenShouldNotShow() {
        setToggles(uxImprovements = false, contingency = true)

        assertFalse(handler.shouldShow("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun whenContingencyModeDisabledThenShouldNotShow() {
        setToggles(uxImprovements = true, contingency = false)

        assertFalse(handler.shouldShow("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun whenUrlIsNotYouTubeThenShouldNotShow() {
        setToggles(uxImprovements = true, contingency = true)

        assertFalse(handler.shouldShow("https://example.com/watch?v=abc"))
    }

    @Test
    fun whenUrlIsNullThenShouldNotShow() {
        setToggles(uxImprovements = true, contingency = true)

        assertFalse(handler.shouldShow(null))
    }

    @Test
    fun whenAlreadyShownThenShouldNotShow() {
        setToggles(uxImprovements = true, contingency = true)
        shownFlow.value = true

        assertFalse(handler.shouldShow("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun whenContingencyModeDisabledThenShownIsReset() = runTest {
        shownFlow.value = true

        handler.onContingencyModeChanged(contingencyEnabled = false)

        assertFalse(shownFlow.value)
    }

    @Test
    fun whenContingencyModeEnabledThenShownIsNotReset() = runTest {
        shownFlow.value = true

        handler.onContingencyModeChanged(contingencyEnabled = true)

        assertTrue(shownFlow.value)
    }
}
