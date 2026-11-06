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

package com.duckduckgo.duckchat.impl.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.duckchat.impl.feature.DuckAiModelProviderFeature
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelProvider
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Id
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class OnboardingDuckAiModelPickerDataPluginImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val duckAiModelManager: DuckAiModelManager = mock()
    private val duckAiModelProviderFeature = FakeFeatureToggleFactory.create(DuckAiModelProviderFeature::class.java)

    private val testee = OnboardingDuckAiModelPickerDataPluginImpl(
        context = context,
        duckAiModelManager = duckAiModelManager,
        duckAiModelProviderFeature = duckAiModelProviderFeature,
    )

    @Test
    fun whenIdRequestedThenItIsTheModelProviderChoice() {
        assertEquals(Id.DuckAiModelProvider, testee.id)
    }

    @Test
    fun whenPrefetchedThenModelsAreFetched() = runTest {
        testee.prefetch()

        verify(duckAiModelManager).fetchModels()
    }

    /**
     * The ids are the pixel values for the step and the key the persisted preference is resolved
     * from, so they are pinned here rather than left to follow a rename.
     */
    @Test
    fun whenOptionsOfferedThenTheirIdsAreTheShippedOnes() = runTest {
        givenModels(
            model("gpt", ModelProvider.OPENAI),
            model("claude", ModelProvider.ANTHROPIC),
            model("mistral", ModelProvider.MISTRAL),
        )

        assertEquals(listOf("openai", "anthropic", "mistral"), testee.options().map { it.id })
    }

    @Test
    fun whenModelsAvailableThenOptionsFollowTheResponseOrder() = runTest {
        givenModels(
            model("mistral", ModelProvider.MISTRAL),
            model("gpt", ModelProvider.OPENAI),
        )

        assertEquals(listOf("mistral", "openai"), testee.options().map { it.id })
    }

    @Test
    fun whenAProviderOffersSeveralModelsThenItIsListedOnce() = runTest {
        givenModels(
            model("gpt-mini", ModelProvider.OPENAI),
            model("gpt-nano", ModelProvider.OPENAI),
        )

        assertEquals(listOf("openai"), testee.options().map { it.id })
    }

    @Test
    fun whenModelsBelongToUnsupportedProvidersThenTheyAreIgnored() = runTest {
        givenModels(
            model("llama", ModelProvider.META),
            model("oss", ModelProvider.OSS),
            model("who", ModelProvider.UNKNOWN),
            model("gpt", ModelProvider.OPENAI),
        )

        assertEquals(listOf("openai"), testee.options().map { it.id })
    }

    @Test
    fun whenModelsAreNotFreeThenTheyAreIgnored() = runTest {
        givenModels(
            model("gpt", ModelProvider.OPENAI, accessTier = listOf("plus")),
            model("claude", ModelProvider.ANTHROPIC),
        )

        assertEquals(listOf("anthropic"), testee.options().map { it.id })
    }

    @Test
    fun whenOptionsOfferedThenTheyCarryTheProviderLabel() = runTest {
        givenModels(model("claude", ModelProvider.ANTHROPIC))

        assertEquals("Claude", testee.options().single().label)
    }

    @Test
    fun whenNoModelsFetchedYetThenTheShippedFallbackIsOffered() = runTest {
        givenModels()

        assertEquals(listOf("openai", "anthropic", "mistral"), testee.options().map { it.id })
    }

    @Test
    fun whenTheFallbackIsOverriddenRemotelyThenThatListIsOffered() = runTest {
        givenModels()
        givenRemoteFallback("""["mistral", "openai"]""")

        assertEquals(listOf("mistral", "openai"), testee.options().map { it.id })
    }

    @Test
    fun whenTheRemoteFallbackNamesAnUnknownProviderThenItIsDropped() = runTest {
        givenModels()
        givenRemoteFallback("""["openai", "deepseek"]""")

        assertEquals(listOf("openai"), testee.options().map { it.id })
    }

    @Test
    fun whenTheRemoteFallbackIsMalformedThenTheShippedFallbackIsOffered() = runTest {
        givenModels()
        duckAiModelProviderFeature.self().setRawStoredState(State(enable = true, settings = "not json"))

        assertEquals(listOf("openai", "anthropic", "mistral"), testee.options().map { it.id })
    }

    @Test
    fun whenAnOptionIsAppliedThenItsProviderIsSelected() = runTest {
        givenModels(
            model("gpt", ModelProvider.OPENAI),
            model("claude", ModelProvider.ANTHROPIC),
        )

        testee.apply(testee.options().last())

        verify(duckAiModelManager).selectProvider(ModelProvider.ANTHROPIC)
    }

    @Test
    fun whenAnOptionFromAnotherPluginIsAppliedThenNothingIsSelected() = runTest {
        testee.apply(
            object : Option {
                override val id: String = "openai"
                override val label: String = "ChatGPT"
                override val iconRes: Int = 0
            },
        )

        verify(duckAiModelManager, never()).selectProvider(org.mockito.kotlin.any())
    }

    private fun givenModels(vararg models: AIChatModel) {
        whenever(duckAiModelManager.modelState).thenReturn(MutableStateFlow(ModelState(models = models.toList())))
    }

    private fun givenRemoteFallback(providersJson: String) {
        duckAiModelProviderFeature.self().setRawStoredState(
            State(enable = true, settings = """{"providers": $providersJson}"""),
        )
    }

    private fun model(
        id: String,
        provider: ModelProvider,
        accessTier: List<String> = listOf("free"),
    ) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = id,
        accessTier = accessTier,
        isAccessible = true,
        provider = provider,
    )
}
