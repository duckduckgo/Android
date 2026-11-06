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

package com.duckduckgo.eventhub.impl.pixels

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CanonicalJsonTest {

    @Test
    fun `object keys are sorted`() {
        assertEquals("""{"a":2,"b":1}""", canonicalJson(JSONObject("""{"b": 1, "a": 2}""")))
    }

    @Test
    fun `nested object keys are sorted recursively`() {
        val value = JSONObject("""{"z": {"y": 1, "x": {"d": 4, "c": 3}}, "a": 0}""")

        assertEquals("""{"a":0,"z":{"x":{"c":3,"d":4},"y":1}}""", canonicalJson(value))
    }

    @Test
    fun `objects with the same members in different order are equal`() {
        val first = JSONObject().put("reason", "overlay").put("stage", 1)
        val second = JSONObject().put("stage", 1).put("reason", "overlay")

        assertEquals(canonicalJson(first), canonicalJson(second))
    }

    @Test
    fun `array element order is kept`() {
        assertEquals("[2,1]", canonicalJson(JSONArray("[2, 1]")))
        assertNotEquals(canonicalJson(JSONObject("""{"seen": [1, 2]}""")), canonicalJson(JSONObject("""{"seen": [2, 1]}""")))
    }

    @Test
    fun `objects inside arrays are canonicalised`() {
        assertEquals("""[{"a":1,"b":2}]""", canonicalJson(JSONArray("""[{"b": 2, "a": 1}]""")))
    }

    @Test
    fun `strings are quoted and escaped`() {
        assertEquals("\"say \\\"hi\\\"\"", canonicalJson("say \"hi\""))
    }

    @Test
    fun `numbers booleans and null are written as JSON literals`() {
        assertEquals("1", canonicalJson(1))
        assertEquals("1.5", canonicalJson(1.5))
        assertEquals("true", canonicalJson(true))
        assertEquals("null", canonicalJson(JSONObject.NULL))
    }

    @Test
    fun `integral and decimal forms of the same number are equal`() {
        assertEquals(canonicalJson(1), canonicalJson(1.0))
        assertEquals(canonicalJson(1), canonicalJson(1L))
    }

    @Test
    fun `explicit null is not an empty object`() {
        assertNotEquals(canonicalJson(JSONObject()), canonicalJson(JSONObject.NULL))
    }

    @Test
    fun `a string is not the same value as the number it spells`() {
        assertNotEquals(canonicalJson("1"), canonicalJson(1))
    }
}
