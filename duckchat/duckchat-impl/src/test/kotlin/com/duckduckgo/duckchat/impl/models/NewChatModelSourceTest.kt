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

class NewChatModelSourceTest {

    private val luna = model("gpt-6-luna", efforts = listOf(NONE, LOW, HIGH))
    private val terra = model("gpt-5.6-terra", efforts = listOf(NONE, LOW, HIGH))
    private val imageModel = model("image-model", efforts = listOf(NONE))
    private val noEfforts = model("no-efforts", efforts = emptyList())

    private val freeGlobal = globalSelection(UserTier.FREE, listOf(luna, terra, imageModel, noEfforts))

    @Test
    fun whenSentUnchangedThenDefault() {
        assertEquals(ModelSource.DEFAULT, sourceFor(freeGlobal))
    }

    @Test
    fun whenModelChangedAndChangedBackThenDefault() {
        assertEquals(ModelSource.DEFAULT, sourceFor(freeGlobal, pickedModelId = luna.id))
    }

    @Test
    fun whenEffortChangedAndChangedBackThenDefault() {
        assertEquals(ModelSource.DEFAULT, sourceFor(freeGlobal, pickedMode = FAST))
    }

    @Test
    fun whenModelPickedThenPicked() {
        assertEquals(ModelSource.PICKED, sourceFor(freeGlobal, pickedModelId = terra.id))
    }

    @Test
    fun whenOnlyEffortPickedThenPicked() {
        assertEquals(ModelSource.PICKED, sourceFor(freeGlobal, pickedMode = REASONING))
    }

    @Test
    fun whenImageGenerationSwitchedModelThenPicked() {
        assertEquals(ModelSource.PICKED, sourceFor(freeGlobal, pickedModelId = imageModel.id))
    }

    @Test
    fun whenNewChatHintOrUsageWarningSwitchedModelThenPicked() {
        // Both set the tab's pick like the picker does, so only the sent model distinguishes them from the default.
        assertEquals(ModelSource.PICKED, sourceFor(freeGlobal, pickedModelId = noEfforts.id))
    }

    @Test
    fun whenTierDefaultMissingThenFallback() {
        val global = globalSelection(UserTier.FREE, listOf(terra, imageModel))

        assertEquals("gpt-5.6-terra", global.selectedModelId)
        assertEquals(ModelSource.FALLBACK, sourceFor(global))
    }

    @Test
    fun whenTierDefaultLockedThenFallback() {
        val global = globalSelection(UserTier.FREE, listOf(luna.copy(isAccessible = false), terra))

        assertEquals(ModelSource.FALLBACK, sourceFor(global))
    }

    @Test
    fun whenPaidTierOnFreeTierDefaultThenFallback() {
        val global = globalSelection(UserTier.PLUS, listOf(luna, model("other")))

        assertEquals(ModelSource.FALLBACK, sourceFor(global))
    }

    @Test
    fun whenDefaultModelDoesNotOfferFastThenFallback() {
        val global = globalSelection(UserTier.FREE, listOf(model("gpt-6-luna", efforts = listOf(LOW, HIGH))))

        assertEquals(REASONING, global.selectedReasoningMode)
        assertEquals(ModelSource.FALLBACK, sourceFor(global))
    }

    @Test
    fun whenModelsNotLoadedThenHardcodedDefaultIsFallback() {
        val global = ModelState(selectedModelId = "gpt-6-luna", selectedReasoningMode = FAST)

        assertEquals(ModelSource.FALLBACK, sourceFor(global))
    }

    @Test
    fun whenDefaultModelHasNoEffortOptionsThenJudgedOnTheModelAlone() {
        val global = globalSelection(UserTier.FREE, listOf(model("gpt-6-luna", efforts = emptyList())))

        assertEquals(ModelSource.DEFAULT, sourceFor(global))
    }

    @Test
    fun whenFallbackInEffectAndUserPicksThenPicked() {
        val global = globalSelection(UserTier.FREE, listOf(terra, imageModel))

        assertEquals(ModelSource.PICKED, sourceFor(global, pickedModelId = imageModel.id))
        assertEquals(ModelSource.PICKED, sourceFor(global, pickedMode = EXTENDED_REASONING))
    }

    @Test
    fun whenOnboardingModelSentUnchangedThenOnboarding() {
        assertEquals(ModelSource.ONBOARDING, sourceFor(freeGlobal, pickedModelId = terra.id, onboardingModelId = terra.id))
    }

    @Test
    fun whenOnboardingEffortChangedThenPicked() {
        assertEquals(
            ModelSource.PICKED,
            sourceFor(freeGlobal, pickedModelId = terra.id, pickedMode = REASONING, onboardingModelId = terra.id),
        )
    }

    @Test
    fun whenOnboardingChangedToADifferentModelEvenTheTierDefaultThenPicked() {
        assertEquals(ModelSource.PICKED, sourceFor(freeGlobal, pickedModelId = luna.id, onboardingModelId = terra.id))
    }

    @Test
    fun whenOnboardingChangedAwayAndBackThenOnboarding() {
        assertEquals(
            ModelSource.ONBOARDING,
            sourceFor(freeGlobal, pickedModelId = terra.id, pickedMode = FAST, onboardingModelId = terra.id),
        )
    }

    @Test
    fun whenOnboardingModelUnusableThenJudgedAgainstTheGlobalSelection() {
        val lockedTerra = terra.copy(isAccessible = false)
        val global = globalSelection(UserTier.FREE, listOf(luna, lockedTerra))

        assertEquals(ModelSource.DEFAULT, sourceFor(global, pickedModelId = lockedTerra.id, onboardingModelId = lockedTerra.id))
    }

    @Test
    fun whenServerDefaultSentUnchangedThenDefault() {
        val global = globalSelection(UserTier.FREE, listOf(luna, terra), server = RemoteNewChatDefault("gpt-5.6-terra", "high"))

        assertEquals("gpt-5.6-terra", global.selectedModelId)
        assertEquals(EXTENDED_REASONING, global.selectedReasoningMode)
        assertEquals(ModelSource.DEFAULT, sourceFor(global))
    }

    @Test
    fun whenServerDefaultChangedThenPicked() {
        val global = globalSelection(UserTier.FREE, listOf(luna, terra), server = RemoteNewChatDefault("gpt-5.6-terra", "high"))

        assertEquals(ModelSource.PICKED, sourceFor(global, pickedMode = FAST))
        assertEquals(ModelSource.PICKED, sourceFor(global, pickedModelId = luna.id))
    }

    @Test
    fun whenServerDefaultChangedAndChangedBackThenDefault() {
        val global = globalSelection(UserTier.FREE, listOf(luna, terra), server = RemoteNewChatDefault("gpt-5.6-terra", "high"))

        assertEquals(ModelSource.DEFAULT, sourceFor(global, pickedModelId = terra.id, pickedMode = EXTENDED_REASONING))
    }

    @Test
    fun whenServerDefaultModelPickedWithoutEffortThenDefault() {
        val global = globalSelection(UserTier.FREE, listOf(luna, terra), server = RemoteNewChatDefault("gpt-5.6-terra", "high"))

        assertEquals(ModelSource.DEFAULT, sourceFor(global, pickedModelId = terra.id))
    }

    @Test
    fun whenServerModelMissingThenHardcodedDefaultIsFallback() {
        val global = globalSelection(UserTier.FREE, listOf(luna, terra), server = RemoteNewChatDefault("gone", "none"))

        assertEquals("gpt-6-luna", global.selectedModelId)
        assertEquals(ModelSource.FALLBACK, sourceFor(global))
    }

    @Test
    fun whenServerModelLockedThenHardcodedDefaultIsFallback() {
        val global = globalSelection(
            UserTier.FREE,
            listOf(luna, terra.copy(isAccessible = false)),
            server = RemoteNewChatDefault("gpt-5.6-terra", "none"),
        )

        assertEquals("gpt-6-luna", global.selectedModelId)
        assertEquals(ModelSource.FALLBACK, sourceFor(global))
    }

    @Test
    fun whenServerModelAndHardcodedDefaultUnusableThenFirstAccessibleIsFallback() {
        val global = globalSelection(UserTier.FREE, listOf(imageModel, terra), server = RemoteNewChatDefault("gone", "none"))

        assertEquals("image-model", global.selectedModelId)
        assertEquals(ModelSource.FALLBACK, sourceFor(global))
    }

    @Test
    fun whenServerModeNotOfferedByServerModelThenFallback() {
        val global = globalSelection(UserTier.FREE, listOf(luna, imageModel), server = RemoteNewChatDefault("image-model", "low"))

        assertEquals("image-model", global.selectedModelId)
        assertEquals(FAST, global.selectedReasoningMode)
        assertEquals(ModelSource.FALLBACK, sourceFor(global))
    }

    @Test
    fun whenServerDefaultLoadedThenColdStartHardcodedDefaultIsNoLongerDefault() {
        val global = globalSelection(UserTier.FREE, listOf(luna, terra), server = RemoteNewChatDefault("gpt-5.6-terra", "none"))

        assertEquals(ModelSource.PICKED, sourceFor(global, pickedModelId = luna.id))
    }

    @Test
    fun whenNoNewChatDefaultInStateThenJudgedAgainstTheHardcodedDefault() {
        val global = freeGlobal.copy(newChatDefault = null)

        assertEquals(ModelSource.DEFAULT, sourceFor(global))
    }

    @Test
    fun whenResetOffThenNull() {
        assertNull(
            modelSourceFor(
                isResetEnabled = false,
                global = freeGlobal,
                onboardingModelId = null,
                sentModelId = "gpt-6-luna",
                sentReasoningEffort = "none",
            ),
        )
    }

    @Test
    fun whenNoModelSentThenNull() {
        assertNull(
            modelSourceFor(
                isResetEnabled = true,
                global = freeGlobal,
                onboardingModelId = null,
                sentModelId = null,
                sentReasoningEffort = null,
            ),
        )
    }

    /** What a new chat sends for the tab's pick, resolved the way the input resolves it, then classified. */
    private fun sourceFor(
        global: ModelState,
        pickedModelId: String? = null,
        pickedMode: ReasoningMode? = null,
        onboardingModelId: String? = null,
    ): ModelSource? {
        val sent = global.forNewChat(pickedModelId, pickedMode?.rawValue)
        return modelSourceFor(
            isResetEnabled = true,
            global = global,
            onboardingModelId = onboardingModelId,
            sentModelId = sent.selectedModelId,
            sentReasoningEffort = ReasoningResolver.effortFor(sent.selectedReasoningMode, sent.availableReasoningModes)?.rawValue,
        )
    }

    /** The global selection the model manager publishes with `resetModelForNewChats` on. */
    private fun globalSelection(tier: UserTier, models: List<AIChatModel>, server: RemoteNewChatDefault? = null): ModelState {
        val default = NewChatDefaults.preferred(tier, server)
        val selectedId = NewChatDefaults.modelId(tier, default, models)
        val selected = models.firstOrNull { it.id == selectedId }
        val available = ReasoningResolver.availableModes(selected?.supportedReasoningEfforts.orEmpty())
        return ModelState(
            models = models,
            selectedModelId = selectedId,
            userTier = tier,
            selectedReasoningMode = NewChatDefaults.reasoningMode(default, selectedId, available),
            availableReasoningModes = available,
            newChatDefault = default,
        )
    }

    private fun model(id: String, efforts: List<ReasoningEffort> = listOf(NONE)) =
        AIChatModel(id, id, id, id, listOf("free"), isAccessible = true, supportedReasoningEfforts = efforts)
}
