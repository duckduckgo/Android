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

package com.duckduckgo.duckchat.impl.ui

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelProvider
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.models.UserTier
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.ModelPickerViewModel
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.ModelPickerViewModel.PickerSurface
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ModelPickerViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val modelManager: DuckAiModelManager = mock()
    private val stateFlow = MutableStateFlow(ModelState())

    private lateinit var testee: ModelPickerViewModel

    @Before
    fun setUp() {
        whenever(modelManager.modelState).thenReturn(stateFlow)
        testee = ModelPickerViewModel(modelManager)
    }

    @Test
    fun whenGetSelectedModelIdThenDelegatesToModelManager() {
        whenever(modelManager.getSelectedModelId()).thenReturn("id")

        assertEquals("id", testee.getSelectedModelId())
    }

    @Test
    fun whenGetSelectedModelIdAndNoneSelectedThenReturnsNull() {
        whenever(modelManager.getSelectedModelId()).thenReturn(null)

        assertNull(testee.getSelectedModelId())
    }

    @Test
    fun whenFetchModelsThenDelegatesToModelManager() = runTest {
        testee.fetchModels()

        verify(modelManager).fetchModels()
    }

    @Test
    fun whenMenuShowingDefaultThenFalse() {
        assertFalse(testee.menuShowing)
    }

    @Test
    fun whenFreeUserThenSectionsAreFreePlusPro() = runTest {
        val state = ModelState(
            models = listOf(freeModel("f"), plusModel("p"), proModel("pr")),
            userTier = UserTier.FREE,
        )

        val sections = testee.buildSections(state)

        assertEquals(3, sections.size)
        assertNull(sections[0].headerRes)
        assertEquals(R.string.duckAiModelPickerPlusModels, sections[1].headerRes)
        assertEquals(R.string.duckAiModelPickerProModels, sections[2].headerRes)
    }

    @Test
    fun whenPlusUserThenSectionsAreFreePlusPro() = runTest {
        val state = ModelState(
            models = listOf(freeModel("f"), plusModel("p", accessible = true), proModel("pr")),
            userTier = UserTier.PLUS,
        )

        val sections = testee.buildSections(state)

        assertEquals(3, sections.size)
        assertNull(sections[0].headerRes)
        assertEquals(R.string.duckAiModelPickerPlusModels, sections[1].headerRes)
        assertEquals(R.string.duckAiModelPickerProModels, sections[2].headerRes)
    }

    @Test
    fun whenProUserThenSectionsAreFreePlusPro() = runTest {
        val state = ModelState(
            models = listOf(freeModel("f"), plusModel("p", accessible = true), proModel("pr", accessible = true)),
            userTier = UserTier.PRO,
        )

        val sections = testee.buildSections(state)

        assertEquals(3, sections.size)
        assertNull(sections[0].headerRes)
        assertEquals(R.string.duckAiModelPickerPlusModels, sections[1].headerRes)
        assertEquals(R.string.duckAiModelPickerProModels, sections[2].headerRes)
    }

    @Test
    fun whenModelIsFreeTierThenPlacedInFreeSection() = runTest {
        val state = ModelState(
            models = listOf(freeModel("f1"), freeModel("f2")),
            userTier = UserTier.FREE,
        )

        val sections = testee.buildSections(state)

        assertEquals(1, sections.size)
        assertNull(sections[0].headerRes)
        assertEquals(listOf("f1", "f2"), sections[0].models.map { it.id })
    }

    @Test
    fun whenModelAccessTierContainsBothPlusAndProThenPlacedInPlusSection() = runTest {
        val state = ModelState(
            models = listOf(plusModel("p1")),
            userTier = UserTier.FREE,
        )

        val sections = testee.buildSections(state)

        assertEquals(1, sections.size)
        assertEquals(R.string.duckAiModelPickerPlusModels, sections[0].headerRes)
        assertEquals(listOf("p1"), sections[0].models.map { it.id })
    }

    @Test
    fun whenModelAccessTierIsProOnlyThenPlacedInProSection() = runTest {
        val state = ModelState(
            models = listOf(proModel("pr1")),
            userTier = UserTier.FREE,
        )

        val sections = testee.buildSections(state)

        assertEquals(1, sections.size)
        assertEquals(R.string.duckAiModelPickerProModels, sections[0].headerRes)
        assertEquals(listOf("pr1"), sections[0].models.map { it.id })
    }

    @Test
    fun whenModelAccessTierIsEmptyAndAccessibleThenPlacedInFreeSection() = runTest {
        val ubiquitous = AIChatModel(
            id = "u",
            name = "u",
            displayName = "u",
            shortName = "u",
            accessTier = emptyList(),
            isAccessible = true,
        )
        val state = ModelState(models = listOf(ubiquitous), userTier = UserTier.FREE)

        val sections = testee.buildSections(state)

        assertEquals(1, sections.size)
        assertNull(sections[0].headerRes)
    }

    @Test
    fun whenNoModelsThenNoSections() = runTest {
        val state = ModelState(models = emptyList(), userTier = UserTier.FREE)

        val sections = testee.buildSections(state)

        assertTrue(sections.isEmpty())
    }

    @Test
    fun whenAccessibleModelTappedThenSelectModelInvokedAndNoCommandEmitted() = runTest {
        val model = freeModel("f")

        testee.commands.test {
            testee.onModelTapped(model, PickerSurface.ADDRESS_BAR)
            runCurrent()

            verify(modelManager).selectModel(model)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFreeUserTapsPlusModelFromAddressBarThenLaunchPurchaseCommandEmittedWithAddressBarOrigin() = runTest {
        stateFlow.value = ModelState(userTier = UserTier.FREE)
        val model = plusModel("p")

        testee.commands.test {
            testee.onModelTapped(model, PickerSurface.ADDRESS_BAR)

            assertEquals(
                ModelPickerViewModel.Command.LaunchPurchase("funnel_nativeinput_androidapp__modelpicker"),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFreeUserTapsProModelFromDuckAiTabThenLaunchPurchaseCommandEmittedWithDuckAiOrigin() = runTest {
        stateFlow.value = ModelState(userTier = UserTier.FREE)
        val model = proModel("pr")

        testee.commands.test {
            testee.onModelTapped(model, PickerSurface.DUCK_AI_TAB)

            assertEquals(
                ModelPickerViewModel.Command.LaunchPurchase("funnel_duckai_androidapp__modelpicker"),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPlusUserTapsProModelFromAddressBarThenLaunchUpgradeCommandEmittedWithAddressBarOrigin() = runTest {
        stateFlow.value = ModelState(userTier = UserTier.PLUS)
        val model = proModel("pr")

        testee.commands.test {
            testee.onModelTapped(model, PickerSurface.ADDRESS_BAR)

            assertEquals(
                ModelPickerViewModel.Command.LaunchUpgrade("funnel_nativeinput_androidapp__modelpicker"),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPlusUserTapsProModelFromDuckAiTabThenLaunchUpgradeCommandEmittedWithDuckAiOrigin() = runTest {
        stateFlow.value = ModelState(userTier = UserTier.PLUS)
        val model = proModel("pr")

        testee.commands.test {
            testee.onModelTapped(model, PickerSurface.DUCK_AI_TAB)

            assertEquals(
                ModelPickerViewModel.Command.LaunchUpgrade("funnel_duckai_androidapp__modelpicker"),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenInaccessibleModelHasNoPublicTierThenNoCommandEmitted() = runTest {
        stateFlow.value = ModelState(userTier = UserTier.FREE)
        val ghost = AIChatModel(
            id = "ghost",
            name = "ghost",
            displayName = "ghost",
            shortName = "ghost",
            accessTier = emptyList(),
            isAccessible = false,
        )

        testee.commands.test {
            testee.onModelTapped(ghost, PickerSurface.ADDRESS_BAR)

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFreeUserTapsModelWithNonPublicAccessTierThenNoCommandEmitted() = runTest {
        stateFlow.value = ModelState(userTier = UserTier.FREE)
        val internalModel = AIChatModel(
            id = "internal-1",
            name = "internal-1",
            displayName = "internal-1",
            shortName = "internal-1",
            accessTier = listOf("internal"),
            isAccessible = false,
        )

        testee.commands.test {
            testee.onModelTapped(internalModel, PickerSurface.ADDRESS_BAR)

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPlusUserTapsModelWithNonPublicAccessTierThenNoCommandEmitted() = runTest {
        stateFlow.value = ModelState(userTier = UserTier.PLUS)
        val internalModel = AIChatModel(
            id = "internal-1",
            name = "internal-1",
            displayName = "internal-1",
            shortName = "internal-1",
            accessTier = listOf("internal"),
            isAccessible = false,
        )

        testee.commands.test {
            testee.onModelTapped(internalModel, PickerSurface.DUCK_AI_TAB)

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenModelAccessTierIsNonPublicOnlyThenNotPlacedInAnySection() = runTest {
        val internalModel = AIChatModel(
            id = "internal-1",
            name = "internal-1",
            displayName = "internal-1",
            shortName = "internal-1",
            accessTier = listOf("internal"),
            isAccessible = false,
        )
        val state = ModelState(
            models = listOf(freeModel("f1"), internalModel),
            userTier = UserTier.FREE,
        )

        val sections = testee.buildSections(state)

        assertEquals(1, sections.size)
        assertEquals(listOf("f1"), sections[0].models.map { it.id })
    }

    @Test
    fun whenModelProviderIsAnthropicThenClaudeIcon() {
        val model = freeModel(id = "claude-3-haiku", shortName = "Claude", provider = ModelProvider.ANTHROPIC)

        assertEquals(R.drawable.ic_ai_model_claude_16, testee.getIconResForModel(model))
    }

    @Test
    fun whenModelProviderIsOpenAiThenOpenAiIcon() {
        val model = freeModel(id = "gpt-5-mini", shortName = "GPT 5 mini", provider = ModelProvider.OPENAI)

        assertEquals(R.drawable.ic_ai_model_openai_16, testee.getIconResForModel(model))
    }

    @Test
    fun whenModelProviderIsMistralThenMistralIcon() {
        val model = freeModel(id = "mistralai/Mistral-Small", shortName = "Mistral Small", provider = ModelProvider.MISTRAL)

        assertEquals(R.drawable.ic_ai_model_mistral_16, testee.getIconResForModel(model))
    }

    @Test
    fun whenModelProviderIsMetaThenLlamaIcon() {
        val model = freeModel(id = "meta-llama/Llama-3", shortName = "Llama 3", provider = ModelProvider.META)

        assertEquals(R.drawable.ic_ai_model_llama_16, testee.getIconResForModel(model))
    }

    @Test
    fun whenModelProviderIsOssThenOssIcon() {
        val model = freeModel(id = "openai/gpt-oss-120b", shortName = "GPT-OSS", provider = ModelProvider.OSS)

        assertEquals(R.drawable.ic_ai_model_oss_16, testee.getIconResForModel(model))
    }

    @Test
    fun whenModelProviderIsUnknownThenNoIcon() {
        val model = freeModel(id = "some-other-model", shortName = "Other", provider = ModelProvider.UNKNOWN)

        assertNull(testee.getIconResForModel(model))
    }

    @Test
    fun whenNoModelSelectedThenGetSelectedModelReturnsNull() {
        stateFlow.value = ModelState(models = listOf(freeModel("id1", "model1")), selectedModelId = null)

        assertNull(testee.getSelectedModel())
    }

    @Test
    fun whenModelSelectedThenGetSelectedModelReturnsIt() {
        val model = freeModel("id1", "model1")
        stateFlow.value = ModelState(models = listOf(model), selectedModelId = "id1")

        assertEquals(model, testee.getSelectedModel())
    }

    @Test
    fun whenSelectedModelSupportsImageGenerationThenIsImageGenerationSupportedIsTrue() {
        stateFlow.value = ModelState(
            models = listOf(freeModel("id1", "model1", supportedTools = listOf(Tool.IMAGE_GENERATION))),
            selectedModelId = "id1",
        )

        assertTrue(testee.isImageGenerationSupported())
    }

    @Test
    fun whenSelectedModelDoesNotSupportImageGenerationThenIsImageGenerationSupportedIsFalse() {
        stateFlow.value = ModelState(
            models = listOf(freeModel("id1", "model1", supportedTools = emptyList())),
            selectedModelId = "id1",
        )

        assertFalse(testee.isImageGenerationSupported())
    }

    @Test
    fun whenNoModelSelectedThenIsImageGenerationSupportedDefaultsToTrue() {
        stateFlow.value = ModelState(models = emptyList(), selectedModelId = null)

        assertTrue(testee.isImageGenerationSupported())
    }

    @Test
    fun whenSelectedModelSupportsWebSearchThenIsWebSearchSupportedIsTrue() {
        stateFlow.value = ModelState(
            models = listOf(freeModel("id1", "model1", supportedTools = listOf(Tool.WEB_SEARCH))),
            selectedModelId = "id1",
        )

        assertTrue(testee.isWebSearchSupported())
    }

    @Test
    fun whenSelectedModelDoesNotSupportWebSearchThenIsWebSearchSupportedIsFalse() {
        stateFlow.value = ModelState(
            models = listOf(freeModel("id1", "model1", supportedTools = emptyList())),
            selectedModelId = "id1",
        )

        assertFalse(testee.isWebSearchSupported())
    }

    @Test
    fun whenNoModelSelectedThenIsWebSearchSupportedDefaultsToTrue() {
        stateFlow.value = ModelState(models = emptyList(), selectedModelId = null)

        assertTrue(testee.isWebSearchSupported())
    }

    private fun freeModel(
        id: String,
        shortName: String = id,
        provider: ModelProvider = ModelProvider.UNKNOWN,
        supportedTools: List<Tool> = emptyList(),
    ) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = shortName,
        accessTier = listOf("free"),
        isAccessible = true,
        provider = provider,
        supportedTools = supportedTools,
    )

    private fun plusModel(id: String, shortName: String = id, accessible: Boolean = false) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = shortName,
        accessTier = listOf("plus", "pro"),
        isAccessible = accessible,
    )

    private fun proModel(id: String, shortName: String = id, accessible: Boolean = false) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = shortName,
        accessTier = listOf("pro"),
        isAccessible = accessible,
    )
}
