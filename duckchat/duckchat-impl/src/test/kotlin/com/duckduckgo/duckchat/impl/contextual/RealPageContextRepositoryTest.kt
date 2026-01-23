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

package com.duckduckgo.duckchat.impl.contextual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealPageContextRepositoryTest {

    private val repository = RealPageContextRepository()

    @Test
    fun whenNoEntryThenGetLatestReturnsNull() {
        assertNull(repository.getLatestPageContext("tab-1"))
    }

    @Test
    fun whenUpdateThenStoresDataForTab() {
        val before = System.currentTimeMillis()

        repository.update("tab-1", "data-1")

        val result = repository.getLatestPageContext("tab-1")
        assertEquals("data-1", result?.serializedPageData)
        assertTrue(result!!.collectedAtMs >= before)
    }

    @Test
    fun whenUpdateSameTabThenOverridesData() {
        repository.update("tab-1", "data-1")
        val firstTimestamp = repository.getLatestPageContext("tab-1")!!.collectedAtMs

        repository.update("tab-1", "data-2")

        val result = repository.getLatestPageContext("tab-1")
        assertEquals("data-2", result?.serializedPageData)
        assertTrue(result!!.collectedAtMs >= firstTimestamp)
    }

    @Test
    fun whenClearSpecificTabThenOtherTabsRemain() {
        repository.update("tab-1", "data-1")
        repository.update("tab-2", "data-2")

        repository.clear("tab-1")

        assertNull(repository.getLatestPageContext("tab-1"))
        assertEquals("data-2", repository.getLatestPageContext("tab-2")?.serializedPageData)
    }
}
