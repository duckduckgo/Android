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

package com.duckduckgo.duckchat.impl.ui

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DuckAiContextualOnboardingViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val duckChatFeatureRepository: DuckChatFeatureRepository = mock()

    private lateinit var testee: DuckAiContextualOnboardingViewModel

    @Before
    fun setUp() = runTest {
        testee = DuckAiContextualOnboardingViewModel(
            duckChatFeatureRepository = duckChatFeatureRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenCompleteOnboardingCalledThenRepositorySetsContextualOnboardingCompletedToTrue() = runTest {
        testee.completeOnboarding()
        verify(duckChatFeatureRepository).setContextualOnboardingCompleted(true)
    }

    @Test
    fun whenCompleteOnboardingCalledThenOnboardingCompletedCommandSent() = runTest {
        testee.commands.test {
            testee.completeOnboarding()
            assertEquals(DuckAiContextualOnboardingViewModel.Command.OnboardingCompleted, awaitItem())
        }
    }
}
