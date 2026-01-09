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

package com.duckduckgo.remote.messaging.impl.modal.evaluator

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.impl.store.ModalSurfaceStore
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ModalSurfaceLifecycleObserverTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockModalSurfaceStore: ModalSurfaceStore = mock()
    private val mockLifecycleOwner: LifecycleOwner = mock()

    private lateinit var testee: ModalSurfaceLifecycleObserver

    @Before
    fun setUp() {
        testee = ModalSurfaceLifecycleObserver(
            appCoroutineScope = coroutinesTestRule.testScope,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            modalSurfaceStore = mockModalSurfaceStore,
        )
    }

    @Test
    fun whenOnStopCalledThenBackgroundedTimestampIsRecorded() = runTest {
        testee.onStop(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockModalSurfaceStore).recordBackgroundedTimestamp()
    }
}
