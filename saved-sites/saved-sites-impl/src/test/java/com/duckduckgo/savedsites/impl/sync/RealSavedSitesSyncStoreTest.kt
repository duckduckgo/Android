/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.savedsites.impl.sync

import android.content.Context
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealSavedSitesSyncStoreTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val preferences = InMemorySharedPreferences()

    private val mockContext: Context = mock<Context>().apply {
        whenever(this.getSharedPreferences(anyString(), anyInt())).thenReturn(preferences)
    }

    val testee = RealSavedSitesSyncStore(mockContext, coroutineRule.testScope, coroutineRule.testDispatcherProvider)

    @Test
    fun whenNoValueIsSyncPausedThenReturnFalse() {
        assertFalse(testee.isSyncPaused)
    }

    @Test
    fun whenIsSyncPausedUpdatedThenEmitNewValue() = runTest {
        testee.isSyncPausedFlow().test {
            awaitItem()
            testee.isSyncPaused = true
            assertEquals(true, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }
}
