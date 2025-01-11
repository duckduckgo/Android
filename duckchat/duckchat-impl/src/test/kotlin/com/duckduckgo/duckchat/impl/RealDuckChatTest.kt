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

package com.duckduckgo.duckchat.impl

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealDuckChatTest {

    @get:org.junit.Rule
    var coroutineRule = com.duckduckgo.common.test.CoroutineTestRule()

    private val mockDuckPlayerFeatureRepository: DuckChatFeatureRepository =
        mock()
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val moshi: Moshi = Moshi.Builder().build()
    private val dispatcherProvider = coroutineRule.testDispatcherProvider
    private val mockGlobalActivityStarter: GlobalActivityStarter = mock()
    private val mockContext: Context = mock()

    private val testee = RealDuckChat(
        mockDuckPlayerFeatureRepository,
        duckChatFeature,
        moshi,
        dispatcherProvider,
        mockGlobalActivityStarter,
        mockContext,
        true,
        coroutineRule.testScope,
    )

    @Before
    fun setup() = runTest {
        whenever(mockDuckPlayerFeatureRepository.shouldShowInBrowserMenu()).thenReturn(true)
        setFeatureToggle(true)
    }

    @Test
    fun whenDuckChatIsEnabled_isEnabledReturnsTrue() = runTest {
        val result = testee.isEnabled()
        assertTrue(result)
    }

    @Test
    fun whenDuckChatIsDisabled_isEnabledReturnsFalse() = runTest {
        setFeatureToggle(false)

        val result = testee.isEnabled()
        assertFalse(result)
    }

    @Test
    fun observeShowInBrowserMenuUserSetting_emitsCorrectValues() = runTest {
        whenever(mockDuckPlayerFeatureRepository.observeShowInBrowserMenu()).thenReturn(flowOf(true, false))

        val results = testee.observeShowInBrowserMenuUserSetting().take(2).toList()
        assertTrue(results[0])
        assertFalse(results[1])
    }

    @Test
    fun whenFeatureEnabled_showInBrowserMenuReturnsValueFromRepository() {
        val result = testee.showInBrowserMenu()
        assertTrue(result)
    }

    @Test
    fun whenFeatureDisabled_showInBrowserMenuReturnsFalse() {
        setFeatureToggle(false)

        val result = testee.showInBrowserMenu()
        assertFalse(result)
    }

    private fun setFeatureToggle(enabled: Boolean) {
        duckChatFeature.self().setRawStoredState(State(enabled))
        testee.onPrivacyConfigDownloaded()
    }
}
