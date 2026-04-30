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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelProvider
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.UserTier
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun whenSelectModelThenDelegatesToModelManager() = runTest {
        val model = freeModel("id", "model")

        testee.selectModel(model)

        verify(modelManager).selectModel(model)
    }

    @Test
    fun whenMenuShowingDefaultThenFalse() {
        assertFalse(testee.menuShowing)
    }

    @Test
    fun whenFreeUserThenFreeSectionHasNoHeader() {
        val state = ModelState(
            models = listOf(freeModel("id1", "model1"), premiumModel("id2", "model2")),
            userTier = UserTier.FREE,
        )

        val sections = testee.buildSections(state)

        assertNull(sections[0].headerRes)
        assertEquals(listOf("id1"), sections[0].models.map { it.id })
    }

    @Test
    fun whenFreeUserThenPremiumSectionHasHeader() {
        val state = ModelState(
            models = listOf(freeModel("id1", "model1"), premiumModel("id2", "model2")),
            userTier = UserTier.FREE,
        )

        val sections = testee.buildSections(state)

        assertEquals(2, sections.size)
        assertNull(sections[1].headerRes)
        assertEquals(listOf("id2"), sections[1].models.map { it.id })
    }

    @Test
    fun whenFreeUserWithOnlyFreeModelsThenOnlyFreeSectionWithNoHeader() {
        val state = ModelState(
            models = listOf(freeModel("id1", "model1"), freeModel("id2", "model2")),
            userTier = UserTier.FREE,
        )

        val sections = testee.buildSections(state)

        assertEquals(1, sections.size)
        assertNull(sections[0].headerRes)
    }

    @Test
    fun whenFreeUserWithOnlyPremiumModelsThenOnlyPremiumSectionWithHeader() {
        val state = ModelState(
            models = listOf(premiumModel("id1", "model1")),
            userTier = UserTier.FREE,
        )

        val sections = testee.buildSections(state)

        assertEquals(1, sections.size)
        assertNull(sections[0].headerRes)
    }

    @Test
    fun whenFreeUserWithNoModelsThenNoSections() {
        val state = ModelState(models = emptyList(), userTier = UserTier.FREE)

        val sections = testee.buildSections(state)

        assertTrue(sections.isEmpty())
    }

    @Test
    fun whenSubscriberThenAdvancedSectionFirst() {
        val state = ModelState(
            models = listOf(advancedModel("id1", "model1"), basicModel("id2", "model2")),
            userTier = UserTier.PLUS,
        )

        val sections = testee.buildSections(state)

        assertEquals(2, sections.size)
        assertEquals(R.string.duckAiModelPickerAdvancedModels, sections[0].headerRes)
        assertEquals(listOf("id1"), sections[0].models.map { it.id })
    }

    @Test
    fun whenSubscriberThenBasicSectionSecond() {
        val state = ModelState(
            models = listOf(advancedModel("id1", "model1"), basicModel("id2", "model2")),
            userTier = UserTier.PLUS,
        )

        val sections = testee.buildSections(state)

        assertEquals(R.string.duckAiModelPickerBasicModels, sections[1].headerRes)
        assertEquals(listOf("id2"), sections[1].models.map { it.id })
    }

    @Test
    fun whenSubscriberWithOnlyAdvancedModelsThenOnlyAdvancedSection() {
        val state = ModelState(
            models = listOf(advancedModel("id1", "model1")),
            userTier = UserTier.PLUS,
        )

        val sections = testee.buildSections(state)

        assertEquals(1, sections.size)
        assertEquals(R.string.duckAiModelPickerAdvancedModels, sections[0].headerRes)
    }

    @Test
    fun whenSubscriberWithOnlyBasicModelsThenOnlyBasicSection() {
        val state = ModelState(
            models = listOf(basicModel("id1", "model1")),
            userTier = UserTier.PLUS,
        )

        val sections = testee.buildSections(state)

        assertEquals(1, sections.size)
        assertEquals(R.string.duckAiModelPickerBasicModels, sections[0].headerRes)
    }

    @Test
    fun whenSubscriberWithNoModelsThenNoSections() {
        val state = ModelState(models = emptyList(), userTier = UserTier.PLUS)

        val sections = testee.buildSections(state)

        assertTrue(sections.isEmpty())
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

    private fun freeModel(id: String, shortName: String, provider: ModelProvider = ModelProvider.UNKNOWN) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = shortName,
        accessTier = listOf("free"),
        isAccessible = true,
        provider = provider,
    )

    private fun premiumModel(id: String, shortName: String) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = shortName,
        accessTier = listOf("plus"),
        isAccessible = false,
    )

    private fun advancedModel(id: String, shortName: String) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = shortName,
        accessTier = listOf("plus"),
        isAccessible = true,
    )

    private fun basicModel(id: String, shortName: String) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = shortName,
        accessTier = listOf("free"),
        isAccessible = true,
    )
}
