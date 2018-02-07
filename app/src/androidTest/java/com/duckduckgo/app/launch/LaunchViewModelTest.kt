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

package com.duckduckgo.app.launch

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import com.duckduckgo.app.launch.LaunchViewModel.Command.*
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.any


class LaunchViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private var onboardingStore: OnboardingStore = mock()
    private var mockCommandObserver: Observer<LaunchViewModel.Command> = mock()

    private val testee: LaunchViewModel by lazy {
        LaunchViewModel(onboardingStore)
    }

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenOnboardingShouldShowThenCommandIsOnboarding() {
        whenever(onboardingStore.shouldShow).thenReturn(true)
        testee.command.observeForever(mockCommandObserver)
        verify(mockCommandObserver).onChanged(any(Onboarding::class.java))
    }

    @Test
    fun whenOnboardingShouldNotShowThenCommandIsHome() {
        whenever(onboardingStore.shouldShow).thenReturn(false)
        testee.command.observeForever(mockCommandObserver)
        verify(mockCommandObserver).onChanged(any(Home::class.java))
    }

    @Test
    fun whenOnboardingDoneThenCommandIsHome() {
        whenever(onboardingStore.shouldShow).thenReturn(true)
        testee.command.observeForever(mockCommandObserver)
        verify(mockCommandObserver).onChanged(any(Onboarding::class.java))

        testee.onOnboardingDone()
        verify(mockCommandObserver).onChanged(any(Home::class.java))
    }

}