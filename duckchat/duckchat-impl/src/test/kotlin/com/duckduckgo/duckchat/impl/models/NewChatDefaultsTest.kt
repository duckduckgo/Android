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

import com.duckduckgo.duckchat.impl.models.ReasoningEffort.HIGH
import com.duckduckgo.duckchat.impl.models.ReasoningEffort.LOW
import com.duckduckgo.duckchat.impl.models.ReasoningEffort.NONE
import com.duckduckgo.duckchat.impl.models.ReasoningMode.EXTENDED_REASONING
import com.duckduckgo.duckchat.impl.models.ReasoningMode.FAST
import com.duckduckgo.duckchat.impl.models.ReasoningMode.REASONING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewChatDefaultsTest {

    private val luna = model("gpt-6-luna", efforts = listOf(NONE, LOW))
    private val terra = model("gpt-5.6-terra", efforts = listOf(NONE, LOW, HIGH))
    private val other = model("other", efforts = listOf(LOW, HIGH))

    @Test
    fun whenFreeTierThenDefaultIsLuna() {
        assertEquals("gpt-6-luna", NewChatDefaults.modelId(UserTier.FREE, listOf(other, terra, luna)))
    }

    @Test
    fun whenPaidTierThenDefaultIsTerra() {
        assertEquals("gpt-5.6-terra", NewChatDefaults.modelId(UserTier.PLUS, listOf(other, luna, terra)))
        assertEquals("gpt-5.6-terra", NewChatDefaults.modelId(UserTier.PRO, listOf(other, luna, terra)))
    }

    @Test
    fun whenDefaultInaccessibleThenFirstAccessibleModel() {
        val lockedTerra = terra.copy(isAccessible = false)

        assertEquals("other", NewChatDefaults.modelId(UserTier.PLUS, listOf(lockedTerra, other, luna)))
    }

    @Test
    fun whenDefaultMissingThenFirstAccessibleModel() {
        assertEquals("other", NewChatDefaults.modelId(UserTier.FREE, listOf(other, terra)))
    }

    @Test
    fun whenNoModelsThenNoDefault() {
        assertNull(NewChatDefaults.modelId(UserTier.FREE, emptyList()))
    }

    @Test
    fun whenNoPreferenceThenFast() {
        assertEquals(FAST, NewChatDefaults.reasoningMode(null, modes(NONE, LOW)))
    }

    @Test
    fun whenFastUnsupportedThenFirstAccessibleMode() {
        assertEquals(REASONING, NewChatDefaults.reasoningMode(null, modes(LOW, HIGH)))
    }

    @Test
    fun whenPreferredSupportedThenKept() {
        assertEquals(EXTENDED_REASONING, NewChatDefaults.reasoningMode(EXTENDED_REASONING, modes(NONE, LOW, HIGH)))
    }

    @Test
    fun whenPreferredUnsupportedThenFast() {
        assertEquals(FAST, NewChatDefaults.reasoningMode(EXTENDED_REASONING, modes(NONE, LOW)))
    }

    private fun modes(vararg efforts: ReasoningEffort) = ReasoningResolver.availableModes(efforts.toList())

    private fun model(id: String, efforts: List<ReasoningEffort>) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = "short-$id",
        accessTier = listOf("free"),
        isAccessible = true,
        supportedReasoningEfforts = efforts,
    )
}
