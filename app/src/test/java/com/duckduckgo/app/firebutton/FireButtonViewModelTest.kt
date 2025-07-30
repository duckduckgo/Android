/*
 * Copyright (c) 2023 DuckDuckGo
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
import app.cash.turbine.test
import com.duckduckgo.app.fire.FireAnimationLoader
import com.duckduckgo.app.firebutton.FireButtonViewModel.Command
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class FireButtonViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: FireButtonViewModel

    @Mock
    private lateinit var mockAppSettingsDataStore: SettingsDataStore

    @Mock
    private lateinit var mockFireAnimationLoader: FireAnimationLoader

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var mockDuckChat: DuckChat

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        whenever(mockAppSettingsDataStore.automaticallyClearWhenOption).thenReturn(ClearWhenOption.APP_EXIT_ONLY)
        whenever(mockAppSettingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_NONE)
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(FireAnimation.HeroFire)
        runBlocking {
            whenever(mockDuckChat.wasOpenedBefore()).thenReturn(false)
        }

        testee = FireButtonViewModel(
            mockAppSettingsDataStore,
            mockFireAnimationLoader,
            mockPixel,
            mockDuckChat,
        )
    }

    @Test
    fun whenInitialisedThenViewStateEmittedWithDefaultValues() = runTest {
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(FireAnimation.HeroFire)
        val expectedClearData = FireButtonViewModel.AutomaticallyClearData(
            clearWhatOption = ClearWhatOption.CLEAR_NONE,
            clearWhenOption = ClearWhenOption.APP_EXIT_ONLY,
            clearWhenOptionEnabled = false,
        )

        testee.viewState().test {
            val value = awaitItem()

            assertEquals(expectedClearData, value.automaticallyClearData)
            assertEquals(FireAnimation.HeroFire, value.selectedFireAnimation)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnFireproofWebsitesClickedThenEmitCommandLaunchFireproofWebsitesAndPixelFired() = runTest {
        testee.commands().test {
            testee.onFireproofWebsitesClicked()

            assertEquals(Command.LaunchFireproofWebsites, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_FIREPROOF_WEBSITES_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAutomaticallyClearWhatClickedEmitCommandShowClearWhatDialogAndPixelFired() = runTest {
        testee.commands().test {
            testee.onAutomaticallyClearWhatClicked()

            assertEquals(Command.ShowClearWhatDialog(ClearWhatOption.CLEAR_NONE), awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_AUTOMATICALLY_CLEAR_WHAT_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAutomaticallyClearWhenClickedEmitCommandShowClearWhenDialogAndPixelFired() = runTest {
        testee.commands().test {
            testee.onAutomaticallyClearWhenClicked()

            assertEquals(Command.ShowClearWhenDialog(ClearWhenOption.APP_EXIT_ONLY), awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_AUTOMATICALLY_CLEAR_WHEN_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAutomaticallyWhatOptionSelectedWithNewOptionThenDataStoreIsUpdatedAndPixelSent() = runTest {
        whenever(mockAppSettingsDataStore.isCurrentlySelected(ClearWhatOption.CLEAR_TABS_AND_DATA)).thenReturn(false)

        testee.commands().test {
            testee.onAutomaticallyWhatOptionSelected(ClearWhatOption.CLEAR_TABS_AND_DATA)

            verify(mockAppSettingsDataStore).automaticallyClearWhatOption = ClearWhatOption.CLEAR_TABS_AND_DATA
            verify(mockPixel).fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_OPTION_TABS_AND_DATA)
        }
    }

    @Test
    fun whenOnAutomaticallyWhatOptionSelectedWithSameOptionThenDataStoreIsNotUpdatedAndPixelNotSent() = runTest {
        whenever(mockAppSettingsDataStore.isCurrentlySelected(ClearWhatOption.CLEAR_NONE)).thenReturn(true)

        testee.commands().test {
            testee.onAutomaticallyWhatOptionSelected(ClearWhatOption.CLEAR_NONE)

            verify(mockAppSettingsDataStore, never()).automaticallyClearWhatOption
            verify(mockPixel, never()).fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_OPTION_NONE)
        }
    }

    @Test
    fun whenOnAutomaticallyWhenOptionSelectedWithNewOptionThenDataStoreIsUpdatedAndPixelSent() = runTest {
        whenever(mockAppSettingsDataStore.isCurrentlySelected(ClearWhenOption.APP_EXIT_ONLY)).thenReturn(false)

        testee.commands().test {
            testee.onAutomaticallyWhenOptionSelected(ClearWhenOption.APP_EXIT_ONLY)

            verify(mockAppSettingsDataStore).automaticallyClearWhenOption = ClearWhenOption.APP_EXIT_ONLY
            verify(mockPixel).fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_ONLY)
        }
    }

    @Test
    fun whenOnAutomaticallyWhenOptionSelectedWithSameOptionThenDataStoreIsNotUpdatedAndPixelNotSent() = runTest {
        whenever(mockAppSettingsDataStore.isCurrentlySelected(ClearWhenOption.APP_EXIT_ONLY)).thenReturn(true)

        testee.commands().test {
            testee.onAutomaticallyWhenOptionSelected(ClearWhenOption.APP_EXIT_ONLY)

            verify(mockAppSettingsDataStore, never()).automaticallyClearWhenOption
            verify(mockPixel, never()).fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_OPTION_NONE)
        }
    }

    @Test
    fun whenFireAnimationSettingClickedThenCommandIsLaunchFireAnimationSettings() = runTest {
        testee.commands().test {
            testee.userRequestedToChangeFireAnimation()

            assertEquals(Command.LaunchFireAnimationSettings(FireAnimation.HeroFire), awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFireAnimationSettingClickedThenPixelSent() {
        testee.userRequestedToChangeFireAnimation()

        verify(mockPixel).fire(AppPixelName.FIRE_ANIMATION_SETTINGS_OPENED)
    }

    @Test
    fun whenNewFireAnimationSelectedThenUpdateViewState() = runTest {
        val expectedAnimation = FireAnimation.HeroWater

        testee.viewState().test {
            // expect HeroFire as a default which will happen when view state flow is created
            assertEquals(FireAnimation.HeroFire, awaitItem().selectedFireAnimation)

            testee.onFireAnimationSelected(expectedAnimation)
            assertEquals(expectedAnimation, awaitItem().selectedFireAnimation)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenNewFireAnimationSelectedThenStoreNewSelectedAnimation() {
        testee.onFireAnimationSelected(FireAnimation.HeroWater)

        verify(mockAppSettingsDataStore).selectedFireAnimation = FireAnimation.HeroWater
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
    fun whenSameFireAnimationSelectedThenDoNotSendPixel() {
        givenSelectedFireAnimation(FireAnimation.HeroFire)

        testee.onFireAnimationSelected(FireAnimation.HeroFire)

        verify(mockPixel, times(0)).fire(
            AppPixelName.FIRE_ANIMATION_NEW_SELECTED,
            mapOf(Pixel.PixelParameter.FIRE_ANIMATION to Pixel.PixelValues.FIRE_ANIMATION_INFERNO),
        )
    }

    private fun givenSelectedFireAnimation(fireAnimation: FireAnimation) {
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(fireAnimation)
        whenever(mockAppSettingsDataStore.isCurrentlySelected(fireAnimation)).thenReturn(true)
    }
}
