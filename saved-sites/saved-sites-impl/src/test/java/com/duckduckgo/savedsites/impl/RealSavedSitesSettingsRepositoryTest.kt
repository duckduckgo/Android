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

package com.duckduckgo.savedsites.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.savedsites.impl.sync.FakeSavedSitesSettingsStore
import com.duckduckgo.savedsites.store.FavoritesViewMode
import com.duckduckgo.savedsites.store.FavoritesViewMode.NATIVE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RealSavedSitesSettingsRepositoryTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private val savedSitesSettingsStore = FakeSavedSitesSettingsStore(coroutineRule.testScope)
    private val testee = RealSavedSitesSettingsRepository(savedSitesSettingsStore)

    @Test
    fun whenObserverAddedThenCurrentViewStateEmitted() = runTest {
        testee.viewModeFlow().test {
            assertEquals(NATIVE, awaitItem())
        }
    }

    @Test
    fun whenDisplayModeChangedThenViewStateIsUpdated() = runTest {
        testee.viewModeFlow().test {
            awaitItem()
            testee.favoritesDisplayMode = FavoritesViewMode.UNIFIED
            assertEquals(FavoritesViewMode.UNIFIED, awaitItem())
        }
    }
}
