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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.savedsites.impl.sync.SavedSiteRateLimitViewModel.Command.NavigateToBookmarks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SavedSiteRateLimitViewModelTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val realContext = InstrumentationRegistry.getInstrumentation().targetContext
    val savedSitesSyncStore = RealSavedSitesSyncStore(realContext, coroutineRule.testScope, coroutineRule.testDispatcherProvider)

    val testee = SavedSiteRateLimitViewModel(
        savedSitesSyncStore,
        coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenSyncPausedThenWarningVisible() = runTest {
        savedSitesSyncStore.isSyncPaused = true
        testee.viewState().test {
            assertTrue(awaitItem().warningVisible)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksWarningActionThenNavigateToBookmarks() = runTest {
        testee.commands().test {
            testee.onWarningActionClicked()
            assertEquals(NavigateToBookmarks, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }
}
