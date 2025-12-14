/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.onboarding

import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.wideevents.InputScreenOnboardingWideEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OnboardingInputScreenSelectionObserverTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockAppCoroutineScope: CoroutineScope = coroutineRule.testScope
    private val mockUserStageStore: UserStageStore = mock()
    private val mockOnboardingStore: OnboardingStore = mock()
    private val mockDuckChat: DuckChat = mock()
    private val mockInputScreenOnboardingWideEvent: InputScreenOnboardingWideEvent = mock()
    private val dispatcherProvider: DispatcherProvider = coroutineRule.testDispatcherProvider
    private val userAppStageFlow = MutableStateFlow(AppStage.NEW)
    private val inputScreenSettingFlow = MutableStateFlow(false)
    private val cosmeticInputScreenSettingFlow = MutableStateFlow(false)

    @Test
    fun whenUserStageIsEstablishedAndInputScreenSelectionIsTrueThenSetInputScreenUserSettingToTrue() =
        runTest {
            whenever(mockUserStageStore.userAppStageFlow()).thenReturn(userAppStageFlow)
            whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
            whenever(mockOnboardingStore.getInputScreenSelection()).thenReturn(true)
            whenever(mockDuckChat.observeInputScreenUserSettingEnabled()).thenReturn(inputScreenSettingFlow)
            whenever(mockDuckChat.observeCosmeticInputScreenUserSettingEnabled()).thenReturn(cosmeticInputScreenSettingFlow)

            OnboardingInputScreenSelectionObserver(
                mockAppCoroutineScope,
                dispatcherProvider,
                mockUserStageStore,
                mockOnboardingStore,
                mockDuckChat,
                mockInputScreenOnboardingWideEvent,
            )

            userAppStageFlow.value = AppStage.ESTABLISHED

            verify(mockDuckChat).setInputScreenUserSetting(true)
        }

    @Test
    fun whenUserStageIsEstablishedAndInputScreenSelectionIsFalseThenSetInputScreenUserSettingToFalse() =
        runTest {
            whenever(mockUserStageStore.userAppStageFlow()).thenReturn(userAppStageFlow)
            whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
            whenever(mockOnboardingStore.getInputScreenSelection()).thenReturn(false)
            whenever(mockDuckChat.observeInputScreenUserSettingEnabled()).thenReturn(inputScreenSettingFlow)
            whenever(mockDuckChat.observeCosmeticInputScreenUserSettingEnabled()).thenReturn(cosmeticInputScreenSettingFlow)

            OnboardingInputScreenSelectionObserver(
                mockAppCoroutineScope,
                dispatcherProvider,
                mockUserStageStore,
                mockOnboardingStore,
                mockDuckChat,
                mockInputScreenOnboardingWideEvent,
            )

            userAppStageFlow.value = AppStage.ESTABLISHED

            verify(mockDuckChat).setInputScreenUserSetting(false)
        }

    @Test
    fun whenUserStageIsEstablishedAndInputScreenSelectionIsNullThenDoNotSetInputScreenUserSetting() =
        runTest {
            whenever(mockUserStageStore.userAppStageFlow()).thenReturn(userAppStageFlow)
            whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
            whenever(mockOnboardingStore.getInputScreenSelection()).thenReturn(null)
            whenever(mockDuckChat.observeInputScreenUserSettingEnabled()).thenReturn(inputScreenSettingFlow)
            whenever(mockDuckChat.observeCosmeticInputScreenUserSettingEnabled()).thenReturn(cosmeticInputScreenSettingFlow)

            OnboardingInputScreenSelectionObserver(
                mockAppCoroutineScope,
                dispatcherProvider,
                mockUserStageStore,
                mockOnboardingStore,
                mockDuckChat,
                mockInputScreenOnboardingWideEvent,
            )

            userAppStageFlow.value = AppStage.ESTABLISHED

            verify(mockDuckChat, never()).setInputScreenUserSetting(any())
        }

    @Test
    fun whenUserStageIsNotEstablishedThenDoNotSetInputScreenUserSetting() =
        runTest {
            whenever(mockUserStageStore.userAppStageFlow()).thenReturn(userAppStageFlow)
            whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
            whenever(mockOnboardingStore.getInputScreenSelection()).thenReturn(true)
            whenever(mockDuckChat.observeInputScreenUserSettingEnabled()).thenReturn(inputScreenSettingFlow)
            whenever(mockDuckChat.observeCosmeticInputScreenUserSettingEnabled()).thenReturn(cosmeticInputScreenSettingFlow)

            OnboardingInputScreenSelectionObserver(
                mockAppCoroutineScope,
                dispatcherProvider,
                mockUserStageStore,
                mockOnboardingStore,
                mockDuckChat,
                mockInputScreenOnboardingWideEvent,
            )

            userAppStageFlow.value = AppStage.DAX_ONBOARDING

            verify(mockDuckChat, never()).setInputScreenUserSetting(any())
        }

    @Test
    fun whenUserChangesInputScreenSettingBeforeEstablishedThenMarkAsOverriddenByUser() =
        runTest {
            whenever(mockUserStageStore.userAppStageFlow()).thenReturn(userAppStageFlow)
            whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
            whenever(mockOnboardingStore.getInputScreenSelection()).thenReturn(true)
            whenever(mockOnboardingStore.isInputScreenSelectionOverriddenByUser()).thenReturn(true)
            whenever(mockDuckChat.observeInputScreenUserSettingEnabled()).thenReturn(inputScreenSettingFlow)
            whenever(mockDuckChat.observeCosmeticInputScreenUserSettingEnabled()).thenReturn(cosmeticInputScreenSettingFlow)

            OnboardingInputScreenSelectionObserver(
                mockAppCoroutineScope,
                dispatcherProvider,
                mockUserStageStore,
                mockOnboardingStore,
                mockDuckChat,
                mockInputScreenOnboardingWideEvent,
            )

            cosmeticInputScreenSettingFlow.value = true
            inputScreenSettingFlow.value = true

            verify(mockOnboardingStore).setInputScreenSelectionOverriddenByUser()
            verify(mockInputScreenOnboardingWideEvent).onInputScreenSettingEnabledBeforeInputScreenShown(enabled = true)

            userAppStageFlow.value = AppStage.ESTABLISHED

            verify(mockDuckChat, never()).setInputScreenUserSetting(any())
        }
}
