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

package com.duckduckgo.app.onboarding

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DuckAiOnboardingAvailabilityTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val mockDuckChat: DuckChat = mock()

    private val testee = RealDuckAiOnboardingAvailability(
        duckChat = mockDuckChat,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenDuckChatEnabledThenEnabled() = runTest {
        whenever(mockDuckChat.isEnabled()).thenReturn(true)

        assertTrue(testee.isDuckAiOnboardingEnabled())
    }

    @Test
    fun whenDuckChatDisabledThenNotEnabled() = runTest {
        whenever(mockDuckChat.isEnabled()).thenReturn(false)

        assertFalse(testee.isDuckAiOnboardingEnabled())
    }
}
