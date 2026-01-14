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

package com.duckduckgo.app.firebutton

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import app.cash.turbine.test
import com.duckduckgo.app.fire.FireAnimationLoader
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.firebutton.DataClearingSettingsViewModel.Command
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class DataClearingSettingsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: DataClearingSettingsViewModel

    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockFireAnimationLoader: FireAnimationLoader = mock()
    private val mockPixel: Pixel = mock()
    private val mockDuckChat: DuckChat = mock()
    private val mockDuckAiFeatureState: DuckAiFeatureState = mock()
    private val mockFireproofWebsiteRepository: FireproofWebsiteRepository = mock()
    private val mockFireDataStore: FireDataStore = mock()

    private val duckAiShowClearDuckAIChatHistoryFlow = MutableStateFlow(false)
    private val fireproofWebsitesLiveData = MutableLiveData<List<FireproofWebsiteEntity>>(emptyList())
    private val automaticClearOptionsFlow = MutableStateFlow<Set<FireClearOption>>(emptySet())

    @Before
    fun before() = runTest {
        whenever(mockSettingsDataStore.selectedFireAnimation).thenReturn(FireAnimation.HeroFire)
        whenever(mockFireDataStore.isManualClearOptionSelected(FireClearOption.DUCKAI_CHATS)).thenReturn(false)
        whenever(mockDuckAiFeatureState.showClearDuckAIChatHistory).thenReturn(duckAiShowClearDuckAIChatHistoryFlow)
        whenever(mockFireproofWebsiteRepository.getFireproofWebsites()).thenReturn(fireproofWebsitesLiveData)
        whenever(mockFireDataStore.getAutomaticClearOptionsFlow()).thenReturn(automaticClearOptionsFlow)

        runBlocking {
            whenever(mockDuckChat.wasOpenedBefore()).thenReturn(false)
        }

        testee = DataClearingSettingsViewModel(
            mockSettingsDataStore,
            mockFireAnimationLoader,
            mockPixel,
            mockDuckChat,
            mockDuckAiFeatureState,
            mockFireDataStore,
            coroutineTestRule.testDispatcherProvider,
            mockFireproofWebsiteRepository,
        )
    }

    @Test
    fun whenInitialisedThenViewStateEmittedWithDefaultValues() = runTest {
        testee.viewState.test {
            val value = awaitItem()

            assertEquals(FireAnimation.HeroFire, value.selectedFireAnimation)
            assertFalse(value.clearDuckAiData)
            assertFalse(value.showClearDuckAiDataSetting)
            assertEquals(0, value.fireproofWebsitesCount)
            assertFalse(value.automaticallyClearingEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenInitialisedAndDuckAiUsedAndFFEnabledThenShowClearDuckAiDataSettingIsTrue() = runTest {
        duckAiShowClearDuckAIChatHistoryFlow.value = true
        whenever(mockDuckChat.wasOpenedBefore()).thenReturn(true)

        testee = DataClearingSettingsViewModel(
            mockSettingsDataStore,
            mockFireAnimationLoader,
            mockPixel,
            mockDuckChat,
            mockDuckAiFeatureState,
            mockFireDataStore,
            coroutineTestRule.testDispatcherProvider,
            mockFireproofWebsiteRepository,
        )

        testee.viewState.test {
            val value = awaitItem()
            assertTrue(value.showClearDuckAiDataSetting)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenInitialisedAndDuckAiUsedAndFFDisabledThenShowClearDuckAiDataSettingIsFalse() = runTest {
        duckAiShowClearDuckAIChatHistoryFlow.value = false
        whenever(mockDuckChat.wasOpenedBefore()).thenReturn(true)

        testee = DataClearingSettingsViewModel(
            mockSettingsDataStore,
            mockFireAnimationLoader,
            mockPixel,
            mockDuckChat,
            mockDuckAiFeatureState,
            mockFireDataStore,
            coroutineTestRule.testDispatcherProvider,
            mockFireproofWebsiteRepository,
        )

        testee.viewState.test {
            val value = awaitItem()
            assertFalse(value.showClearDuckAiDataSetting)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenInitialisedAndDuckAiNeverUsedAndFFEnabledThenShowClearDuckAiDataSettingIsFalse() = runTest {
        duckAiShowClearDuckAIChatHistoryFlow.value = true
        whenever(mockDuckChat.wasOpenedBefore()).thenReturn(false)

        testee = DataClearingSettingsViewModel(
            mockSettingsDataStore,
            mockFireAnimationLoader,
            mockPixel,
            mockDuckChat,
            mockDuckAiFeatureState,
            mockFireDataStore,
            coroutineTestRule.testDispatcherProvider,
            mockFireproofWebsiteRepository,
        )

        testee.viewState.test {
            val value = awaitItem()
            assertFalse(value.showClearDuckAiDataSetting)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenInitialisedWithAutomaticClearingEnabledThenAutomaticallyClearingEnabledIsTrue() = runTest {
        automaticClearOptionsFlow.value = setOf(FireClearOption.TABS, FireClearOption.DATA)

        testee.viewState.test {
            val value = awaitItem()
            assertTrue(value.automaticallyClearingEnabled)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFireproofWebsitesExistThenCountIsUpdated() = runTest {
        val websites = listOf(
            FireproofWebsiteEntity("example.com"),
            FireproofWebsiteEntity("duckduckgo.com"),
        )
        fireproofWebsitesLiveData.value = websites

        testee.viewState.test {
            val value = awaitItem()
            assertEquals(2, value.fireproofWebsitesCount)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnFireproofWebsitesClickedThenEmitCommandLaunchFireproofWebsitesAndPixelFired() = runTest {
        testee.commands.test {
            testee.onFireproofWebsitesClicked()

            assertEquals(Command.LaunchFireproofWebsites, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_FIREPROOF_WEBSITES_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAutomaticDataClearingClickedThenEmitCommandLaunchAutomaticDataClearingSettings() = runTest {
        testee.commands.test {
            testee.onAutomaticDataClearingClicked()

            assertEquals(Command.LaunchAutomaticDataClearingSettings, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserRequestedToChangeFireAnimationThenCommandIsLaunchFireAnimationSettings() = runTest {
        testee.commands.test {
            testee.userRequestedToChangeFireAnimation()

            assertEquals(Command.LaunchFireAnimationSettings(FireAnimation.HeroFire), awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserRequestedToChangeFireAnimationThenPixelSent() {
        testee.userRequestedToChangeFireAnimation()

        verify(mockPixel).fire(AppPixelName.FIRE_ANIMATION_SETTINGS_OPENED)
    }

    @Test
    fun whenNewFireAnimationSelectedThenUpdateViewState() = runTest {
        val expectedAnimation = FireAnimation.HeroWater

        testee.viewState.test {
            assertEquals(FireAnimation.HeroFire, awaitItem().selectedFireAnimation)

            testee.onFireAnimationSelected(expectedAnimation)
            assertEquals(expectedAnimation, awaitItem().selectedFireAnimation)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenNewFireAnimationSelectedThenStoreNewSelectedAnimation() {
        testee.onFireAnimationSelected(FireAnimation.HeroWater)

        verify(mockSettingsDataStore).selectedFireAnimation = FireAnimation.HeroWater
    }

    @Test
    fun whenNewFireAnimationSelectedThenPreLoadAnimation() {
        testee.onFireAnimationSelected(FireAnimation.HeroWater)

        verify(mockFireAnimationLoader).preloadSelectedAnimation()
    }

    @Test
    fun whenNewFireAnimationSelectedThenPixelSent() {
        testee.onFireAnimationSelected(FireAnimation.HeroWater)

        verify(mockPixel).fire(
            AppPixelName.FIRE_ANIMATION_NEW_SELECTED,
            mapOf(Pixel.PixelParameter.FIRE_ANIMATION to Pixel.PixelValues.FIRE_ANIMATION_WHIRLPOOL),
        )
    }

    @Test
    fun whenSameFireAnimationSelectedThenDoNotUpdateOrSendPixel() {
        whenever(mockSettingsDataStore.isCurrentlySelected(FireAnimation.HeroFire)).thenReturn(true)

        testee.onFireAnimationSelected(FireAnimation.HeroFire)

        verify(mockSettingsDataStore, never()).selectedFireAnimation = FireAnimation.HeroFire
        verify(mockFireAnimationLoader, never()).preloadSelectedAnimation()
        verify(mockPixel, never()).fire(
            AppPixelName.FIRE_ANIMATION_NEW_SELECTED,
            mapOf(Pixel.PixelParameter.FIRE_ANIMATION to Pixel.PixelValues.FIRE_ANIMATION_INFERNO),
        )
    }

    @Test
    fun whenOnLaunchedFromNotificationThenPixelFired() {
        val pixelName = "test_pixel"

        testee.onLaunchedFromNotification(pixelName)

        verify(mockPixel).fire(pixelName)
    }

    @Test
    fun whenClearDuckAiDataToggledOnThenUpdateStateAndFirePixel() = runTest {
        whenever(mockFireDataStore.isManualClearOptionSelected(FireClearOption.DUCKAI_CHATS)).thenReturn(false)

        testee.viewState.test {
            assertFalse(awaitItem().clearDuckAiData)

            testee.onClearDuckAiDataToggled(true)

            assertTrue(awaitItem().clearDuckAiData)
            verify(mockFireDataStore).addManualClearOption(FireClearOption.DUCKAI_CHATS)
            verify(mockPixel).fire(AppPixelName.SETTINGS_CLEAR_DUCK_AI_DATA_TOGGLED_ON)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenClearDuckAiDataToggledOffThenUpdateStateAndFirePixel() = runTest {
        whenever(mockFireDataStore.isManualClearOptionSelected(FireClearOption.DUCKAI_CHATS)).thenReturn(true)

        testee = DataClearingSettingsViewModel(
            mockSettingsDataStore,
            mockFireAnimationLoader,
            mockPixel,
            mockDuckChat,
            mockDuckAiFeatureState,
            mockFireDataStore,
            coroutineTestRule.testDispatcherProvider,
            mockFireproofWebsiteRepository,
        )

        testee.viewState.test {
            assertTrue(awaitItem().clearDuckAiData)

            testee.onClearDuckAiDataToggled(false)

            assertFalse(awaitItem().clearDuckAiData)
            verify(mockFireDataStore).removeManualClearOption(FireClearOption.DUCKAI_CHATS)
            verify(mockPixel).fire(AppPixelName.SETTINGS_CLEAR_DUCK_AI_DATA_TOGGLED_OFF)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnClearDataActionClickedThenEmitCommandLaunchFireDialog() = runTest {
        testee.commands.test {
            testee.onClearDataActionClicked()

            assertEquals(Command.LaunchFireDialog, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }
}
