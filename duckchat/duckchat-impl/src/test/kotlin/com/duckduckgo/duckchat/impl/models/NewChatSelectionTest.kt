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

import org.junit.Assert.assertEquals
import org.junit.Test

class NewChatSelectionTest {

    private val state = ModelState(
        models = listOf(
            model("default", listOf(ReasoningEffort.NONE, ReasoningEffort.LOW)),
            model("picked", listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH)),
            model("gated", listOf(ReasoningEffort.NONE), isAccessible = false),
        ),
        selectedModelId = "default",
        selectedReasoningMode = ReasoningMode.FAST,
        availableReasoningModes = ReasoningResolver.availableModes(listOf(ReasoningEffort.NONE, ReasoningEffort.LOW)),
    )

    @Test
    fun whenNothingPickedThenGlobalSelection() {
        val selection = state.newChatSelection(pickedModelId = null, pickedReasoningMode = null)

        assertEquals("default", selection.modelId)
        assertEquals(ReasoningMode.FAST, selection.mode)
        assertEquals(ReasoningEffort.NONE, selection.effort)
    }

    @Test
    fun whenPickedModelLacksTheDefaultModeThenFirstAccessibleModeOfThatModel() {
        val selection = state.newChatSelection(pickedModelId = "picked", pickedReasoningMode = null)

        assertEquals("picked", selection.modelId)
        assertEquals(ReasoningMode.REASONING, selection.mode)
        assertEquals(ReasoningEffort.LOW, selection.effort)
    }

    @Test
    fun whenPickedModeIsAvailableThenItWins() {
        val selection = state.newChatSelection(pickedModelId = "picked", pickedReasoningMode = ReasoningMode.EXTENDED_REASONING.rawValue)

        assertEquals(ReasoningEffort.HIGH, selection.effort)
    }

    @Test
    fun whenPickedModeIsNotAvailableOnTheModelThenDefaultMode() {
        val selection = state.newChatSelection(pickedModelId = null, pickedReasoningMode = ReasoningMode.EXTENDED_REASONING.rawValue)

        assertEquals(ReasoningMode.FAST, selection.mode)
    }

    @Test
    fun whenPickedModelIsNotAccessibleThenGlobalSelection() {
        val selection = state.newChatSelection(pickedModelId = "gated", pickedReasoningMode = null)

        assertEquals("default", selection.modelId)
    }

    @Test
    fun whenPickedModelIsGoneThenGlobalSelection() {
        val selection = state.newChatSelection(pickedModelId = "gone", pickedReasoningMode = null)

        assertEquals("default", selection.modelId)
    }

    private fun model(id: String, supported: List<ReasoningEffort>, isAccessible: Boolean = true) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = id,
        accessTier = listOf("free", "plus", "pro"),
        isAccessible = isAccessible,
        supportedReasoningEfforts = supported,
    )
}
