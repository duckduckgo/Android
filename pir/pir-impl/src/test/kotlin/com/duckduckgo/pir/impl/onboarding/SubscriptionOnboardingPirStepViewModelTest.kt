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

package com.duckduckgo.pir.impl.onboarding

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.onboarding.SubscriptionOnboardingPirStepViewModel.Companion.PIR_STEP_ID
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SubscriptionOnboardingPirStepViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockPirRepository: PirRepository = mock()
    private val mockController: SubscriptionOnboardingController = mock()

    private lateinit var testee: SubscriptionOnboardingPirStepViewModel

    private val testProfileQuery = ProfileQuery(
        id = 1L,
        firstName = "John",
        lastName = "Doe",
        city = "New York",
        state = "NY",
        addresses = emptyList(),
        birthYear = 1990,
        fullName = "John Doe",
        age = 33,
        deprecated = false,
    )

    @Before
    fun setUp() {
        testee = SubscriptionOnboardingPirStepViewModel(
            pirRepository = mockPirRepository,
            controller = mockController,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenNoUserProfileQueriesThenStepIsNotCompleted() = runTest {
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(emptyList())

        testee.completeIfScanStarted()

        verifyNoInteractions(mockController)
    }

    @Test
    fun whenAtLeastOneUserProfileQueryThenStepIsCompleted() = runTest {
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))

        testee.completeIfScanStarted()

        verify(mockController).onStepFinished(PIR_STEP_ID, COMPLETED)
    }
}
