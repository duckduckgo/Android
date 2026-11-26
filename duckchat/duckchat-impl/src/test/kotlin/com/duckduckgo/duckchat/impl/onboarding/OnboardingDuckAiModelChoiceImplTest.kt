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
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.feature.DuckAiModelProviderFeature
import com.duckduckgo.duckchat.impl.helper.RealPendingNativePromptStore
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelProvider
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class OnboardingDuckAiModelChoiceImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val duckAiModelManager: DuckAiModelManager = mock()
    private val duckAiModelProviderFeature = FakeFeatureToggleFactory.create(DuckAiModelProviderFeature::class.java)
    private val duckChat: DuckChat = mock {
        on { getDuckChatUrl(any(), any(), any()) } doReturn CHAT_URL
    }
    private val pendingNativePromptStore = RealPendingNativePromptStore()

    private val testee = OnboardingDuckAiModelChoiceImpl(
        context = context,
        duckChat = duckChat,
        duckAiModelManager = duckAiModelManager,
        duckAiModelProviderFeature = duckAiModelProviderFeature,
        pendingNativePromptStore = pendingNativePromptStore,
    )

    @Test
    fun whenPrefetchedThenModelsAreFetched() = runTest {
        testee.prefetch()

        verify(duckAiModelManager).fetchModels()
    }

    /** The ids are the pixel values for the step, so they are pinned here rather than left to follow a rename. */
    @Test
    fun whenOptionsOfferedThenTheirIdsAreTheShippedOnes() = runTest {
        givenModels(
            model("gpt", ModelProvider.OPENAI),
            model("claude", ModelProvider.ANTHROPIC),
            model("tinfoil/gemma4-31b", ModelProvider.OSS),
        )

        assertEquals(listOf("openai", "anthropic", "gemma"), testee.options().map { it.id })
    }

    @Test
    fun whenModelsAvailableThenOptionsFollowTheResponseOrder() = runTest {
        givenModels(
            model("tinfoil/gemma4-31b", ModelProvider.OSS),
            model("gpt", ModelProvider.OPENAI),
        )

        assertEquals(listOf("gemma", "openai"), testee.options().map { it.id })
    }

    @Test
    fun whenOnlyOtherOpenModelsAreOfferedThenGemmaIsNot() = runTest {
        givenModels(
            model("tinfoil/gpt-oss-120b", ModelProvider.OSS),
            model("mistral-small", ModelProvider.MISTRAL),
            model("gpt", ModelProvider.OPENAI),
        )

        assertEquals(listOf("openai"), testee.options().map { it.id })
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
            model("mistral", ModelProvider.MISTRAL),
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

        assertEquals(listOf("openai", "anthropic", "gemma"), testee.options().map { it.id })
    }

    @Test
    fun whenTheFallbackIsOverriddenRemotelyThenThatListIsOffered() = runTest {
        givenModels()
        givenRemoteFallback("""["gemma", "openai"]""")

        assertEquals(listOf("gemma", "openai"), testee.options().map { it.id })
    }

    @Test
    fun whenTheRemoteFallbackNamesAnUnknownProviderThenItIsDropped() = runTest {
        givenModels()
        givenRemoteFallback("""["openai", "mistral"]""")

        assertEquals(listOf("openai"), testee.options().map { it.id })
    }

    @Test
    fun whenTheRemoteFallbackIsMalformedThenTheShippedFallbackIsOffered() = runTest {
        givenModels()
        duckAiModelProviderFeature.self().setRawStoredState(State(enable = true, settings = "not json"))

        assertEquals(listOf("openai", "anthropic", "gemma"), testee.options().map { it.id })
    }

    @Test
    fun whenChatUrlRequestedForAnOptionThenThePromptCarriesItsFirstAccessibleModel() = runTest {
        givenModels(
            model("gpt", ModelProvider.OPENAI),
            model("tinfoil/gpt-oss-120b", ModelProvider.OSS),
            model("tinfoil/gemma4-31b", ModelProvider.OSS),
        )

        val url = testee.chatUrl("hi", testee.options().last())

        assertEquals(CHAT_URL, url)
        verify(duckChat).getDuckChatUrl("hi", autoPrompt = true)
        val pending = pendingNativePromptStore.consume()
        assertEquals("hi", pending?.prompt)
        assertEquals("tinfoil/gemma4-31b", pending?.modelId)
    }

    @Test
    fun whenChatUrlRequestedThenThePromptDoesNotAcceptTheTerms() = runTest {
        givenModels(model("claude", ModelProvider.ANTHROPIC))

        testee.chatUrl("hi", testee.options().single())

        assertFalse(pendingNativePromptStore.consume()!!.carriesTermsConsent)
    }

    @Test
    fun whenChatUrlRequestedThenTheUsersModelSelectionIsLeftAlone() = runTest {
        givenModels(model("claude", ModelProvider.ANTHROPIC))

        testee.chatUrl("hi", testee.options().single())

        verify(duckAiModelManager, never()).selectModel(any())
    }

    @Test
    fun whenChatUrlRequestedThenInaccessibleModelsAreSkipped() = runTest {
        givenModels(
            model("claude-sonnet", ModelProvider.ANTHROPIC, accessTier = listOf("plus"), isAccessible = false),
            model("claude-haiku", ModelProvider.ANTHROPIC),
        )

        testee.chatUrl("hi", testee.options().single())

        assertEquals("claude-haiku", pendingNativePromptStore.consume()?.modelId)
    }

    @Test
    fun whenChatUrlRequestedForAFallbackOptionThenModelsAreFetchedFirst() = runTest {
        val state = MutableStateFlow(ModelState())
        whenever(duckAiModelManager.modelState).thenReturn(state)
        whenever(duckAiModelManager.fetchModels()).then {
            state.value = ModelState(models = listOf(model("claude", ModelProvider.ANTHROPIC)))
            Unit
        }
        val option = testee.options().first { it.id == "anthropic" }

        testee.chatUrl("hi", option)

        assertEquals("claude", pendingNativePromptStore.consume()?.modelId)
    }

    @Test
    fun whenNoModelMatchesTheOptionThenThePromptRunsOnTheDefault() = runTest {
        givenModels()
        val option = testee.options().first { it.id == "gemma" }

        val url = testee.chatUrl("hi", option)

        assertEquals(CHAT_URL, url)
        assertNull(pendingNativePromptStore.consume())
    }

    @Test
    fun whenChatUrlRequestedWithoutAnOptionThenThePromptRunsOnTheDefault() = runTest {
        givenModels(model("gpt", ModelProvider.OPENAI))

        val url = testee.chatUrl("hi", null)

        assertEquals(CHAT_URL, url)
        assertNull(pendingNativePromptStore.consume())
    }

    @Test
    fun whenChatUrlRequestedWithAForeignOptionThenThePromptRunsOnTheDefault() = runTest {
        givenModels(model("gpt", ModelProvider.OPENAI))

        testee.chatUrl(
            "hi",
            object : Option {
                override val id: String = "openai"
                override val label: String = "ChatGPT"
                override val iconRes: Int = 0
            },
        )

        assertNull(pendingNativePromptStore.consume())
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
        isAccessible: Boolean = true,
    ) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = id,
        accessTier = accessTier,
        isAccessible = isAccessible,
        provider = provider,
    )

    private companion object {
        const val CHAT_URL = "https://duck.ai/?q=hi&prompt=1"
    }
}
