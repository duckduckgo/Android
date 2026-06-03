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

package com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.NewAddressBarOptionV2Prompt.Command
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.modalcoordinator.api.ModalEvaluator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NewAddressBarOptionV2ModalEvaluatorTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val duckChat: DuckChat = mock()
    private val duckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)

    private lateinit var testee: NewAddressBarOptionV2ModalEvaluator

    @Before
    fun setUp() = runTest {
        whenever(duckChat.isEnabled()).thenReturn(true)
        whenever(duckChatFeatureRepository.isInputScreenEverEnabled()).thenReturn(false)
        whenever(duckChatFeatureRepository.wasNewAddressBarOptionV2Shown()).thenReturn(false)
        duckChatFeature.showAIChatAddressBarChoiceScreenV2().setRawStoredState(State(enable = true))

        testee = NewAddressBarOptionV2ModalEvaluator(
            duckChat,
            duckChatFeature,
            duckChatFeatureRepository,
            coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when all conditions met then ModalShown persists shown and emits ShowPicker`() = runTest {
        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.ModalShown, result)
        verify(duckChatFeatureRepository).setNewAddressBarOptionV2Shown()
        assertEquals(Command.ShowPicker, testee.commands.first())
    }

    @Test
    fun `when v2 flag disabled then Skipped`() = runTest {
        duckChatFeature.showAIChatAddressBarChoiceScreenV2().setRawStoredState(State(enable = false))

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
        verify(duckChatFeatureRepository, never()).setNewAddressBarOptionV2Shown()
    }

    @Test
    fun `when duck ai disabled then Skipped`() = runTest {
        whenever(duckChat.isEnabled()).thenReturn(false)

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
        verify(duckChatFeatureRepository, never()).setNewAddressBarOptionV2Shown()
    }

    @Test
    fun `when input screen was ever enabled then Skipped`() = runTest {
        whenever(duckChatFeatureRepository.isInputScreenEverEnabled()).thenReturn(true)

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
    }

    @Test
    fun `when already shown then Skipped`() = runTest {
        whenever(duckChatFeatureRepository.wasNewAddressBarOptionV2Shown()).thenReturn(true)

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
    }

    @Test
    fun `when confirmed with search and ai then enables input screen toggle`() = runTest {
        testee.onConfirmed(searchAndAiSelected = true)

        verify(duckChat).setInputScreenUserSetting(true)
    }

    @Test
    fun `when confirmed with search only then does not enable toggle`() = runTest {
        testee.onConfirmed(searchAndAiSelected = false)

        verify(duckChat, never()).setInputScreenUserSetting(true)
    }
}
