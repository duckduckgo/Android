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

package com.duckduckgo.duckchat.impl.models

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AIChatModelDecodingTest {

    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter: JsonAdapter<RemoteAIChatModel> = moshi.adapter(RemoteAIChatModel::class.java)

    @Test
    fun whenSupportedReasoningEffortMissingThenFieldIsNull() {
        val json = """{"id":"m","name":"n"}"""
        val parsed = adapter.fromJson(json)!!
        assertNull(parsed.supportedReasoningEffort)
    }

    @Test
    fun whenSupportedReasoningEffortNullThenFieldIsNull() {
        val json = """{"id":"m","name":"n","supportedReasoningEffort":null}"""
        val parsed = adapter.fromJson(json)!!
        assertNull(parsed.supportedReasoningEffort)
    }

    @Test
    fun whenSupportedReasoningEffortPopulatedThenFieldDecoded() {
        val json = """{"id":"m","name":"n","supportedReasoningEffort":["none","low","medium"]}"""
        val parsed = adapter.fromJson(json)!!
        assertEquals(listOf("none", "low", "medium"), parsed.supportedReasoningEffort)
    }

    @Test
    fun whenSupportedReasoningEffortContainsUnknownThenStillDecodesAsRawStrings() {
        val json = """{"id":"m","name":"n","supportedReasoningEffort":["low","mystery","high"]}"""
        val parsed = adapter.fromJson(json)!!
        assertEquals(listOf("low", "mystery", "high"), parsed.supportedReasoningEffort)
    }

    @Test
    fun whenReasoningEffortAccessMissingThenFieldIsNull() {
        val json = """{"id":"m","name":"n"}"""
        val parsed = adapter.fromJson(json)!!
        assertNull(parsed.reasoningEffortAccess)
    }

    @Test
    fun whenReasoningEffortAccessNullThenFieldIsNull() {
        val json = """{"id":"m","name":"n","reasoningEffortAccess":null}"""
        val parsed = adapter.fromJson(json)!!
        assertNull(parsed.reasoningEffortAccess)
    }

    @Test
    fun whenReasoningEffortAccessPopulatedThenFieldDecoded() {
        val json = """
            {
              "id":"m",
              "name":"n",
              "reasoningEffortAccess":[
                {"id":"low","accessTier":["free","plus","pro"],"entityHasAccess":true},
                {"id":"medium","accessTier":["pro"],"entityHasAccess":false}
              ]
            }
        """.trimIndent()
        val parsed = adapter.fromJson(json)!!
        assertEquals(
            listOf(
                RemoteReasoningEffortAccess(id = "low", accessTier = listOf("free", "plus", "pro"), entityHasAccess = true),
                RemoteReasoningEffortAccess(id = "medium", accessTier = listOf("pro"), entityHasAccess = false),
            ),
            parsed.reasoningEffortAccess,
        )
    }

    @Test
    fun whenReasoningEffortAccessContainsUnknownIdThenStillDecodesAsRawString() {
        val json = """
            {
              "id":"m",
              "name":"n",
              "reasoningEffortAccess":[
                {"id":"very_high","accessTier":["pro"],"entityHasAccess":false}
              ]
            }
        """.trimIndent()
        val parsed = adapter.fromJson(json)!!
        assertEquals(
            listOf(RemoteReasoningEffortAccess(id = "very_high", accessTier = listOf("pro"), entityHasAccess = false)),
            parsed.reasoningEffortAccess,
        )
    }

    @Test
    fun whenReasoningEffortAccessEntryOmitsAccessTierThenFieldIsNull() {
        val json = """
            {
              "id":"m",
              "name":"n",
              "reasoningEffortAccess":[{"id":"low","entityHasAccess":true}]
            }
        """.trimIndent()
        val parsed = adapter.fromJson(json)!!
        val entry = parsed.reasoningEffortAccess!!.single()
        assertEquals("low", entry.id)
        assertNull(entry.accessTier)
        assertEquals(true, entry.entityHasAccess)
    }

    @Test
    fun whenReasoningEffortAccessEntryHasUnknownFieldsThenIgnoredAndKnownFieldsDecoded() {
        val json = """
            {
              "id":"m",
              "name":"n",
              "reasoningEffortAccess":[
                {"id":"low","accessTier":["free"],"entityHasAccess":true,"futureField":"ignoreMe"}
              ]
            }
        """.trimIndent()
        val parsed = adapter.fromJson(json)!!
        assertEquals(
            listOf(RemoteReasoningEffortAccess(id = "low", accessTier = listOf("free"), entityHasAccess = true)),
            parsed.reasoningEffortAccess,
        )
    }
}
