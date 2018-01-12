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

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test


class OnboardingViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private var onboardingStore: OnboardingStore = mock()
    private var viewStateObserver: Observer<OnboardingViewModel.ViewState> = mock()

    private val testee: OnboardingViewModel by lazy {
        val model = OnboardingViewModel(onboardingStore)
        model.viewState.observeForever(viewStateObserver)
        model
    }

    @Test
    fun whenStartedThenStoreNotifiedThatOnboardingShown() {
        testee
        verify(onboardingStore).onboardingShown()
    }

    @Test
    fun whenOnboardingShouldShowThenShowHomeIsFalse() {
        whenever(onboardingStore.shouldShow).thenReturn(true)
        assertFalse(testee.viewState.value!!.showHome)
    }

    @Test
    fun whenOnboardingShouldNotShowThenShowHomeIsTrue() {
        whenever(onboardingStore.shouldShow).thenReturn(false)
        assertTrue(testee.viewState.value!!.showHome)
    }

    @Test
    fun whenOnboardingDoneThenShowHomeIsTrue() {
        whenever(onboardingStore.shouldShow).thenReturn(true)
        testee.onOnboardingDone()
        assertTrue(testee.viewState.value!!.showHome)
    }

}