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

import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.models.ReasoningEffort.HIGH
import com.duckduckgo.duckchat.impl.models.ReasoningEffort.LOW
import com.duckduckgo.duckchat.impl.models.ReasoningEffort.MEDIUM
import com.duckduckgo.duckchat.impl.models.ReasoningEffort.MINIMAL
import com.duckduckgo.duckchat.impl.models.ReasoningEffort.NONE
import com.duckduckgo.duckchat.impl.models.ReasoningMode.EXTENDED_REASONING
import com.duckduckgo.duckchat.impl.models.ReasoningMode.FAST
import com.duckduckgo.duckchat.impl.models.ReasoningMode.REASONING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NewChatDefaultsTest {

    private val luna = model("gpt-6-luna", efforts = listOf(NONE, LOW))
    private val terra = model("gpt-5.6-terra", efforts = listOf(NONE, LOW, HIGH))
    private val other = model("other", efforts = listOf(LOW, HIGH))

    @Test
    fun whenFreeTierThenDefaultIsLuna() {
        assertEquals("gpt-6-luna", modelId(UserTier.FREE, listOf(other, terra, luna)))
    }

    @Test
    fun whenPaidTierThenDefaultIsTerra() {
        assertEquals("gpt-5.6-terra", modelId(UserTier.PLUS, listOf(other, luna, terra)))
        assertEquals("gpt-5.6-terra", modelId(UserTier.PRO, listOf(other, luna, terra)))
    }

    @Test
    fun whenDefaultInaccessibleThenFirstAccessibleModel() {
        val lockedTerra = terra.copy(isAccessible = false)

        assertEquals("other", modelId(UserTier.PLUS, listOf(lockedTerra, other, luna)))
    }

    @Test
    fun whenDefaultMissingThenFirstAccessibleModel() {
        assertEquals("other", modelId(UserTier.FREE, listOf(other, terra)))
    }

    @Test
    fun whenNoModelsThenNoDefault() {
        assertNull(modelId(UserTier.FREE, emptyList()))
    }

    @Test
    fun whenNoServerDefaultThenTierDefaultOnFast() {
        assertEquals(NewChatDefault("gpt-6-luna", FAST), NewChatDefaults.preferred(UserTier.FREE, null))
        assertEquals(NewChatDefault("gpt-5.6-terra", FAST), NewChatDefaults.preferred(UserTier.PLUS, null))
        assertEquals(NewChatDefault("gpt-5.6-terra", FAST), NewChatDefaults.preferred(UserTier.PRO, null))
    }

    @Test
    fun whenServerDefaultHasNoModelThenTierDefaultOnFast() {
        listOf(null, "", "  ").forEach { modelId ->
            assertEquals(
                NewChatDefault("gpt-6-luna", FAST),
                NewChatDefaults.preferred(UserTier.FREE, RemoteNewChatDefault(modelId = modelId, reasoningEffort = "high")),
            )
        }
    }

    @Test
    fun whenServerDefaultSentThenItsModelOnTheModeOfferingItsEffort() {
        val expected = mapOf(
            "none" to FAST,
            "minimal" to FAST,
            "low" to REASONING,
            "medium" to EXTENDED_REASONING,
            "high" to EXTENDED_REASONING,
            "ultra" to FAST,
            null to FAST,
        )

        expected.forEach { (effort, mode) ->
            assertEquals(
                "effort $effort",
                NewChatDefault("server-model", mode),
                NewChatDefaults.preferred(UserTier.PLUS, RemoteNewChatDefault(modelId = "server-model", reasoningEffort = effort)),
            )
        }
    }

    @Test
    fun whenServerModelAccessibleThenItWinsOverTheTierDefault() {
        assertEquals("other", modelId(UserTier.FREE, listOf(luna, other), server("other")))
    }

    @Test
    fun whenServerModelLockedThenTierDefault() {
        assertEquals("gpt-6-luna", modelId(UserTier.FREE, listOf(terra, other.copy(isAccessible = false), luna), server("other")))
    }

    @Test
    fun whenServerModelMissingThenTierDefault() {
        assertEquals("gpt-5.6-terra", modelId(UserTier.PRO, listOf(luna, terra), server("gone")))
    }

    @Test
    fun whenServerModelAndTierDefaultUnusableThenFirstAccessibleModel() {
        assertEquals("gpt-5.6-terra", modelId(UserTier.FREE, listOf(luna.copy(isAccessible = false), terra), server("gone")))
    }

    @Test
    fun whenServerModelWonThenServerModeKept() {
        val default = server("other", effort = "high")

        assertEquals(EXTENDED_REASONING, NewChatDefaults.reasoningMode(default, "other", modes(LOW, HIGH)))
    }

    @Test
    fun whenServerModelWonButDoesNotOfferServerModeThenFastElseFirstAccessible() {
        val default = server("other", effort = "high")

        assertEquals(FAST, NewChatDefaults.reasoningMode(default, "other", modes(NONE, LOW)))
        assertEquals(REASONING, NewChatDefaults.reasoningMode(default, "other", modes(LOW)))
    }

    @Test
    fun whenServerModeLockedThenFastElseFirstAccessible() {
        val default = server("other", effort = "medium")
        val highLocked = listOf(ReasoningEffortAccess(HIGH, listOf("pro"), isAccessible = false))

        assertEquals(FAST, NewChatDefaults.reasoningMode(default, "other", modes(NONE, LOW, HIGH, access = highLocked)))
        assertEquals(REASONING, NewChatDefaults.reasoningMode(default, "other", modes(LOW, HIGH, access = highLocked)))
    }

    @Test
    fun whenServerModelFellBackThenServerModeNotApplied() {
        val default = server("gone", effort = "low")

        assertEquals(FAST, NewChatDefaults.reasoningMode(default, "gpt-6-luna", modes(NONE, LOW)))
        assertEquals(REASONING, NewChatDefaults.reasoningMode(default, "other", modes(LOW, HIGH)))
    }

    @Test
    fun whenEffortGivenThenModeOfferingIt() {
        assertEquals(FAST, ReasoningMode.forEffort(NONE))
        assertEquals(FAST, ReasoningMode.forEffort(MINIMAL))
        assertEquals(REASONING, ReasoningMode.forEffort(LOW))
        assertEquals(EXTENDED_REASONING, ReasoningMode.forEffort(MEDIUM))
        assertEquals(EXTENDED_REASONING, ReasoningMode.forEffort(HIGH))
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

    @Test
    fun whenNoPickThenNewChatUsesGlobalSelection() {
        val state = modelState(selected = luna)

        assertSame(state, state.forNewChat(pickedModelId = null, pickedReasoningMode = null))
    }

    @Test
    fun whenModelPickedThenNewChatUsesItOnFast() {
        val result = modelState(selected = luna).forNewChat(pickedModelId = "gpt-5.6-terra", pickedReasoningMode = null)

        assertEquals("gpt-5.6-terra", result.selectedModelId)
        assertEquals(terra.shortName, result.selectedModelShortName)
        assertEquals(FAST, result.selectedReasoningMode)
        assertEquals(listOf(FAST, REASONING, EXTENDED_REASONING), result.availableReasoningModes.map { it.mode })
    }

    @Test
    fun whenNewChatDefaultModelPickedWithoutEffortThenNewChatUsesDefaultEffort() {
        val state = modelState(selected = luna).copy(newChatDefault = server("gpt-5.6-terra", "high"))

        assertEquals(EXTENDED_REASONING, state.forNewChat(pickedModelId = "gpt-5.6-terra", pickedReasoningMode = null).selectedReasoningMode)
        assertEquals(FAST, state.forNewChat(pickedModelId = "gpt-6-luna", pickedReasoningMode = null).selectedReasoningMode)
    }

    @Test
    fun whenNewChatDefaultModelPickedWithEffortThenPickedEffortWins() {
        val state = modelState(selected = luna).copy(newChatDefault = server("gpt-5.6-terra", "high"))

        assertEquals(REASONING, state.forNewChat(pickedModelId = "gpt-5.6-terra", pickedReasoningMode = "reasoning").selectedReasoningMode)
    }

    @Test
    fun whenPickedModelInaccessibleThenNewChatUsesGlobalSelection() {
        val state = modelState(selected = luna, extra = terra.copy(isAccessible = false))

        val result = state.forNewChat(pickedModelId = "gpt-5.6-terra", pickedReasoningMode = null)

        assertEquals("gpt-6-luna", result.selectedModelId)
    }

    @Test
    fun whenPickedEffortSupportedByPickedModelThenKept() {
        val result = modelState(selected = luna).forNewChat(pickedModelId = "gpt-5.6-terra", pickedReasoningMode = "extended_reasoning")

        assertEquals(EXTENDED_REASONING, result.selectedReasoningMode)
    }

    @Test
    fun whenPickedEffortUnsupportedByPickedModelThenFastElseFirstAccessible() {
        val onFastModel = modelState(selected = luna).forNewChat(pickedModelId = "gpt-6-luna", pickedReasoningMode = "extended_reasoning")
        val onNoFastModel = modelState(selected = luna, extra = other).forNewChat(pickedModelId = "other", pickedReasoningMode = "fast")

        assertEquals(FAST, onFastModel.selectedReasoningMode)
        assertEquals(REASONING, onNoFastModel.selectedReasoningMode)
    }

    @Test
    fun whenOnlyEffortPickedThenNewChatKeepsGlobalModelAndResolvesEffortAgainstIt() {
        val global = modelState(selected = luna)

        val result = global.forNewChat(pickedModelId = null, pickedReasoningMode = "reasoning")

        assertEquals("gpt-6-luna", result.selectedModelId)
        assertEquals(REASONING, result.selectedReasoningMode)
        assertEquals(global.availableReasoningModes, result.availableReasoningModes)
    }

    @Test
    fun whenPickedModelInaccessibleThenPickedEffortResolvesAgainstGlobalModel() {
        val global = modelState(selected = luna, extra = terra.copy(isAccessible = false))

        val result = global.forNewChat(pickedModelId = "gpt-5.6-terra", pickedReasoningMode = "extended_reasoning")

        assertEquals("gpt-6-luna", result.selectedModelId)
        assertEquals(FAST, result.selectedReasoningMode)
        assertEquals(global.availableReasoningModes, result.availableReasoningModes)
    }

    @Test
    fun whenTabHasChatThenItsPickIsIgnored() {
        val state = modelState(selected = luna)
        val tab = NativeInputState.zero().copy(chatId = "chat", newChatModelId = "gpt-5.6-terra")

        assertSame(state, state.forTab(tab))
    }

    @Test
    fun whenTabHasNoChatThenItsPickApplies() {
        val tab = NativeInputState.zero().copy(newChatModelId = "gpt-5.6-terra")

        assertEquals("gpt-5.6-terra", modelState(selected = luna).forTab(tab).selectedModelId)
    }

    private fun modelState(selected: AIChatModel, extra: AIChatModel? = null): ModelState {
        val available = ReasoningResolver.availableModes(selected.supportedReasoningEfforts)
        return ModelState(
            models = listOfNotNull(luna, terra.takeIf { extra?.id != it.id }, extra),
            selectedModelId = selected.id,
            selectedModelShortName = selected.shortName,
            selectedReasoningMode = NewChatDefaults.reasoningMode(null, available),
            availableReasoningModes = available,
        )
    }

    private fun modes(vararg efforts: ReasoningEffort, access: List<ReasoningEffortAccess> = emptyList()) =
        ReasoningResolver.availableModes(efforts.toList(), access)

    private fun server(modelId: String, effort: String? = null) =
        NewChatDefaults.preferred(UserTier.FREE, RemoteNewChatDefault(modelId = modelId, reasoningEffort = effort))

    private fun modelId(tier: UserTier, models: List<AIChatModel>, preferred: NewChatDefault = NewChatDefaults.preferred(tier, null)) =
        NewChatDefaults.modelId(tier, preferred, models)

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
