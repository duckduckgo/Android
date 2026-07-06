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

package com.duckduckgo.duckchat.impl.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchAssistVisibilityTest {

    @Test
    fun whenFromSerpCodeThenMapsEachCodeToItsOption() {
        assertEquals(SearchAssistVisibility.NEVER, SearchAssistVisibility.fromSerpCode("0"))
        assertEquals(SearchAssistVisibility.ON_DEMAND, SearchAssistVisibility.fromSerpCode("1"))
        assertEquals(SearchAssistVisibility.SOMETIMES, SearchAssistVisibility.fromSerpCode("2"))
        assertEquals(SearchAssistVisibility.OFTEN, SearchAssistVisibility.fromSerpCode("3"))
    }

    @Test
    fun whenFromSerpCodeUnknownOrNullThenNull() {
        assertNull(SearchAssistVisibility.fromSerpCode("4"))
        assertNull(SearchAssistVisibility.fromSerpCode(""))
        assertNull(SearchAssistVisibility.fromSerpCode(null))
    }

    @Test
    fun whenSerpCodesThenEachOptionHasUniqueExpectedCode() {
        assertEquals("0", SearchAssistVisibility.NEVER.serpCode)
        assertEquals("1", SearchAssistVisibility.ON_DEMAND.serpCode)
        assertEquals("2", SearchAssistVisibility.SOMETIMES.serpCode)
        assertEquals("3", SearchAssistVisibility.OFTEN.serpCode)
    }

    @Test
    fun whenFromNameThenRoundTripsForEveryOption() {
        SearchAssistVisibility.entries.forEach { option ->
            assertEquals(option, SearchAssistVisibility.fromName(option.name))
        }
    }
}
