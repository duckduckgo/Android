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

package com.duckduckgo.app.statistics.user_segments

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UsageHistoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var usageHistory: UsageHistory

    @Before
    fun setup() {
        usageHistory = SegmentStoreModule().provideSegmentStore(
            FakeSharedPreferencesProvider(),
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenAddingDuckAiUsage_thenOnlyDuckAiHistoryUpdated() = runTest {
        usageHistory.addSearchUsage("v123-1")
        usageHistory.addAppUsage("v123-2")
        usageHistory.addDuckAiUsage("v123-3")
        usageHistory.addDuckAiUsage("v123-4")

        assertEquals(listOf("v123-1"), usageHistory.getSearchUsageHistory())
        assertEquals(listOf("v123-2"), usageHistory.getAppUsageHistory())
        assertEquals(listOf("v123-3", "v123-4"), usageHistory.getDuckAiHistory())
    }

    @Test
    fun whenAddingAppUsage_thenOnlyAppHistoryUpdated() = runTest {
        usageHistory.addSearchUsage("v123-1")
        usageHistory.addDuckAiUsage("v123-2")
        usageHistory.addAppUsage("v123-3")
        usageHistory.addAppUsage("v123-4")

        assertEquals(listOf("v123-1"), usageHistory.getSearchUsageHistory())
        assertEquals(listOf("v123-3", "v123-4"), usageHistory.getAppUsageHistory())
        assertEquals(listOf("v123-2"), usageHistory.getDuckAiHistory())
    }

    @Test
    fun whenAddingSearchUsage_thenOnlySearchHistoryUpdated() = runTest {
        usageHistory.addAppUsage("v123-1")
        usageHistory.addDuckAiUsage("v123-2")
        usageHistory.addSearchUsage("v123-3")
        usageHistory.addSearchUsage("v123-4")

        assertEquals(listOf("v123-3", "v123-4"), usageHistory.getSearchUsageHistory())
        assertEquals(listOf("v123-1"), usageHistory.getAppUsageHistory())
        assertEquals(listOf("v123-2"), usageHistory.getDuckAiHistory())
    }
}
