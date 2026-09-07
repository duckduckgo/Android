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

package com.duckduckgo.settings.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchAssistVisibilityTest {

    @Test
    fun `when from serp code then maps each code to its option`() {
        assertEquals(SearchAssistVisibility.NEVER, SearchAssistVisibility.fromSerpCode("0"))
        assertEquals(SearchAssistVisibility.ON_DEMAND, SearchAssistVisibility.fromSerpCode("1"))
        assertEquals(SearchAssistVisibility.SOMETIMES, SearchAssistVisibility.fromSerpCode("2"))
        assertEquals(SearchAssistVisibility.OFTEN, SearchAssistVisibility.fromSerpCode("3"))
    }

    @Test
    fun `when from serp code unknown or null then null`() {
        assertNull(SearchAssistVisibility.fromSerpCode("4"))
        assertNull(SearchAssistVisibility.fromSerpCode(""))
        assertNull(SearchAssistVisibility.fromSerpCode(null))
    }

    @Test
    fun `when serp codes then each option has unique expected code`() {
        assertEquals("0", SearchAssistVisibility.NEVER.serpCode)
        assertEquals("1", SearchAssistVisibility.ON_DEMAND.serpCode)
        assertEquals("2", SearchAssistVisibility.SOMETIMES.serpCode)
        assertEquals("3", SearchAssistVisibility.OFTEN.serpCode)
    }

    @Test
    fun `when serp key then every option carries the same key`() {
        SearchAssistVisibility.entries.forEach { assertEquals("kbe", it.serpKey) }
    }
}
