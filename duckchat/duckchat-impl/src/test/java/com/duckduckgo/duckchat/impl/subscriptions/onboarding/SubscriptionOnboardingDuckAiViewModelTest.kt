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

package com.duckduckgo.duckchat.impl.subscriptions.onboarding

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatEntryPoint.ONBOARDING
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelProvider
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.UserTier
import com.duckduckgo.duckchat.impl.subscriptiononboarding.SubscriptionOnboardingDuckAiStepPlugin.Companion.DUCK_AI_STEP_ID
import com.duckduckgo.duckchat.impl.subscriptiononboarding.SubscriptionOnboardingDuckAiViewModel
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.SKIPPED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionOnboardingDuckAiViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val controller: SubscriptionOnboardingController = mock()
    private val duckChat: DuckChat = mock()
    private val modelManager: DuckAiModelManager = mock()

    @Test
    fun whenModelsLoadedThenPaidModelsFirstAndFirstIsPreselected() = runTest {
        val testee = createViewModel(
            models = listOf(
                model(id = "free1", name = "GPT-5.4 nano", tiers = listOf("free")),
                model(id = "plus1", name = "GPT-5.4", tiers = listOf("plus")),
            ),
        )

        testee.viewState().test {
            val state = awaitItem()
            assertEquals(listOf("plus1", "free1"), state.models.map { it.id })
            assertEquals(UserTier.PLUS, state.models.first().tier)
            assertEquals("plus1", state.selectedModelId)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserIsPlusThenProModelsAreHidden() = runTest {
        val testee = createViewModel(
            userTier = UserTier.PLUS,
            models = listOf(
                model(id = "free1", name = "GPT-5.4 nano", tiers = listOf("free")),
                model(id = "plus1", name = "GPT-5.4", tiers = listOf("plus")),
                model(id = "pro1", name = "Claude Opus", tiers = listOf("pro")),
            ),
        )

        testee.viewState().test {
            val state = awaitItem()
            assertEquals(listOf("plus1", "free1"), state.models.map { it.id })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserIsProThenAllModelsAreVisibleOrderedProThenPlusThenFree() = runTest {
        val testee = createViewModel(
            userTier = UserTier.PRO,
            models = listOf(
                model(id = "free1", name = "GPT-5.4 nano", tiers = listOf("free")),
                model(id = "pro1", name = "Claude Opus", tiers = listOf("pro")),
                model(id = "plus1", name = "GPT-5.4", tiers = listOf("plus")),
                model(id = "pro2", name = "GPT-5.4 Pro", tiers = listOf("pro")),
            ),
        )

        testee.viewState().test {
            val state = awaitItem()
            assertEquals(listOf("pro1", "pro2", "plus1", "free1"), state.models.map { it.id })
            assertEquals(UserTier.PRO, state.models.first().tier)
            assertEquals("pro1", state.selectedModelId)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserIsFreeThenOnlyFreeModelsAreVisible() = runTest {
        val testee = createViewModel(
            userTier = UserTier.FREE,
            models = listOf(
                model(id = "plus1", name = "GPT-5.4", tiers = listOf("plus")),
                model(id = "free1", name = "GPT-5.4 nano", tiers = listOf("free")),
                model(id = "pro1", name = "Claude Opus", tiers = listOf("pro")),
            ),
        )

        testee.viewState().test {
            val state = awaitItem()
            assertEquals(listOf("free1"), state.models.map { it.id })
            assertEquals("free1", state.selectedModelId)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenModelHasNoPublicTierThenItIsHidden() = runTest {
        val testee = createViewModel(
            userTier = UserTier.PRO,
            models = listOf(
                model(id = "internal1", name = "Internal", tiers = listOf("internal")),
                model(id = "free1", name = "GPT-5.4 nano", tiers = listOf("free")),
            ),
        )

        testee.viewState().test {
            val state = awaitItem()
            assertEquals(listOf("free1"), state.models.map { it.id })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenModelSelectedThenSelectionUpdated() = runTest {
        val testee = createViewModel(
            models = listOf(
                model(id = "plus1", name = "GPT-5.4", tiers = listOf("plus")),
                model(id = "free1", name = "GPT-5.4 nano", tiers = listOf("free")),
            ),
        )

        testee.onModelSelected("free1")

        testee.viewState().test {
            assertEquals("free1", awaitItem().selectedModelId)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenNotNowClickedThenStepSkipped() = runTest {
        val testee = createViewModel()

        testee.onNotNowClicked()

        verify(controller).onStepFinished(DUCK_AI_STEP_ID, SKIPPED)
    }

    @Test
    fun whenStartClickedThenSelectedModelPersistedDuckChatOpenedAndOnboardingExited() = runTest {
        val plus = model(id = "plus1", name = "GPT-5.4", tiers = listOf("plus"))
        val testee = createViewModel(models = listOf(plus))

        testee.onStartClicked()

        verify(modelManager).selectModel(plus)
        verify(duckChat).openDuckChat(ONBOARDING)
        verify(controller).exitOnboarding()
    }

    private fun createViewModel(
        models: List<AIChatModel> = emptyList(),
        userTier: UserTier = UserTier.PLUS,
    ): SubscriptionOnboardingDuckAiViewModel {
        whenever(modelManager.modelState).thenReturn(MutableStateFlow(ModelState(models = models, userTier = userTier)))
        return SubscriptionOnboardingDuckAiViewModel(
            controller,
            duckChat,
            modelManager,
            coroutineRule.testDispatcherProvider,
        )
    }

    private fun model(
        id: String,
        name: String,
        tiers: List<String>,
        provider: ModelProvider = ModelProvider.UNKNOWN,
    ): AIChatModel = AIChatModel(
        id = id,
        name = name,
        displayName = name,
        shortName = name,
        accessTier = tiers,
        isAccessible = true,
        provider = provider,
    )
}
