/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.whenever

@Suppress("EXPERIMENTAL_API_USAGE")
class OnboardingViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private var userStageStore: UserStageStore = mock()

    private var onboardingStore: OnboardingStore = mock()

    private val pageLayout: OnboardingPageManager = mock()

    private val testee: OnboardingViewModel by lazy {
        OnboardingViewModel(userStageStore, onboardingStore, pageLayout, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenOnboardingDoneWithNewUserThenCompleteStageNew() = runTest {
        whenever(onboardingStore.userMarkedAsReturningUser).thenReturn(false)
        testee.onOnboardingDone()
        verify(userStageStore).stageCompleted(AppStage.NEW)
    }

    @Test
    fun whenOnboardingDoneWithReturningUserThenCompleteStageDaxOnboarding() = runTest {
        whenever(onboardingStore.userMarkedAsReturningUser).thenReturn(true)
        testee.onOnboardingDone()
        verify(userStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }
}
