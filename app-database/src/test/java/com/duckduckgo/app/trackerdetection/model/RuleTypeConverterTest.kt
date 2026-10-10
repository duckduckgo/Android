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

package com.duckduckgo.app.trackerdetection.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTypeConverterTest {

    private val testee = RuleTypeConverter()

    @Test
    fun whenRulesSerializedThenActionIsStoredAsUppercaseEnumName() {
        val json = testee.fromRules(listOf(Rule("example.com/x", Action.BLOCK, null, null, null)))

        assertTrue(json.contains("\"action\":\"BLOCK\""))
    }

    @Test
    fun whenStoredJsonDeserializedThenRuleIsRestored() {
        val stored = """[{"rule":"example.com/x","action":"BLOCK"}]"""

        val rules = testee.toRules(stored)

        assertEquals(1, rules.size)
        assertEquals("example.com/x", rules[0].rule)
        assertEquals(Action.BLOCK, rules[0].action)
    }

    @Test
    fun whenStoredActionIsLowercaseThenItStillDeserializes() {
        val rules = testee.toRules("""[{"rule":"example.com/x","action":"block"}]""")

        assertEquals(Action.BLOCK, rules[0].action)
    }

    @Test
    fun whenStoredActionIsUnknownThenItBecomesUnsupported() {
        val rules = testee.toRules("""[{"rule":"example.com/x","action":"nonsense"}]""")

        assertEquals(Action.UNSUPPORTED, rules[0].action)
    }
}
