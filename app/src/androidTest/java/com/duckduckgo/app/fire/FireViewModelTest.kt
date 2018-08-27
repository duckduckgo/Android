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

package com.duckduckgo.app.fire

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.support.test.annotation.UiThreadTest
import com.duckduckgo.app.InstantSchedulersRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test


class FireViewModelTest {

    @Rule
    @JvmField
    val schedulers = InstantSchedulersRule()

    @Rule
    @JvmField
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewState: FireViewModel.ViewState
    private lateinit var testee: FireViewModel

    @UiThreadTest
    @Before
    fun setup() {
        testee = FireViewModel()
        testee.viewState.observeForever { viewState = it!! }
    }

    @Test
    fun whenViewModelInitialisedThenAutoStartEnabled() {
        assertTrue(viewState.autoStart)
    }

    @Test
    fun whenViewModelInitialisedThenAnimationEnabled() {
        assertTrue(viewState.animate)
    }

    @Test
    fun whenTimerReachedThenAnimationDisabled() {
        testee.startDeathClock()
        assertFalse(viewState.animate)
    }

    @Test
    fun whenUserLeftActivityThenAutoStartDisabled() {
        testee.onViewStopped()
        assertFalse(viewState.autoStart)
    }

    @Test
    fun whenUserLeftActivityThenReturnedThenAutoStartEnabled() {
        testee.onViewStopped()
        assertFalse(viewState.autoStart)

        testee.onViewRestarted()
        assertTrue(viewState.autoStart)
    }
}