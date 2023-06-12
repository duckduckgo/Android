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

package com.duckduckgo.app.appearance

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.appearance.AppearanceViewModel.Command
import com.duckduckgo.app.fire.FireAnimationLoader
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.store.ThemingDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class AppearanceViewModelTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: AppearanceViewModel

    @Mock
    private lateinit var mockThemeSettingsDataStore: ThemingDataStore

    @Mock
    private lateinit var mockAppSettingsDataStore: SettingsDataStore

    @Mock
    private lateinit var mockFireAnimationLoader: FireAnimationLoader

    @Mock
    private lateinit var mockPixel: Pixel

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        whenever(mockAppSettingsDataStore.appIcon).thenReturn(AppIcon.DEFAULT)
        whenever(mockThemeSettingsDataStore.theme).thenReturn(DuckDuckGoTheme.SYSTEM_DEFAULT)
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(FireAnimation.HeroFire)

        testee = AppearanceViewModel(
            mockThemeSettingsDataStore,
            mockAppSettingsDataStore,
            mockFireAnimationLoader,
            mockPixel,
        )
    }

    @Test
    fun whenOnStartActivityCalledThenViewStateEmittedWithDefaultValues() = runTest {
        testee.onStartActivityCalled()

        testee.viewState().test {
            val value = awaitItem()

            assertEquals(DuckDuckGoTheme.SYSTEM_DEFAULT, value.theme)
            assertEquals(AppIcon.DEFAULT, value.appIcon)
            assertEquals(FireAnimation.HeroFire, value.selectedFireAnimation)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenThemeSettingsClickedThenPixelSent() {
        testee.userRequestedToChangeTheme()
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_OPENED)
    }

    @Test
    fun whenThemeSettingsClickedThenCommandIsLaunchThemeSettingsIsSent() = runTest {
        testee.commands().test {
            testee.userRequestedToChangeTheme()

            assertEquals(Command.LaunchThemeSettings(DuckDuckGoTheme.LIGHT), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenChangeIconRequestedThenCommandIsChangeIconAndPixelSent() = runTest {
        testee.commands().test {
            testee.userRequestedToChangeIcon()

            assertEquals(Command.LaunchAppIcon, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_APP_ICON_PRESSED)

            cancelAndConsumeRemainingEvents()
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
    fun whenThemeChangedThenDataStoreIsUpdatedAndUpdateThemeCommandIsSent() = runTest {
        testee.commands().test {
            givenThemeSelected(DuckDuckGoTheme.LIGHT)
            testee.onThemeSelected(DuckDuckGoTheme.DARK)

            verify(mockThemeSettingsDataStore).theme = DuckDuckGoTheme.DARK

            assertEquals(Command.UpdateTheme, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenThemeChangedToLightThenLightThemePixelIsSent() {
        givenThemeSelected(DuckDuckGoTheme.DARK)
        testee.onThemeSelected(DuckDuckGoTheme.LIGHT)
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_LIGHT)
    }

    @Test
    fun whenThemeChangedToDarkThenDarkThemePixelIsSent() {
        givenThemeSelected(DuckDuckGoTheme.LIGHT)
        testee.onThemeSelected(DuckDuckGoTheme.DARK)
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_DARK)
    }

    @Test
    fun whenThemeChangedToSystemDefaultThenSystemDefaultThemePixelIsSent() {
        givenThemeSelected(DuckDuckGoTheme.LIGHT)
        testee.onThemeSelected(DuckDuckGoTheme.SYSTEM_DEFAULT)
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_SYSTEM_DEFAULT)
    }

    @Test
    fun whenThemeChangedButThemeWasAlreadySetThenDoNothing() = runTest {
        testee.commands().test {
            givenThemeSelected(DuckDuckGoTheme.LIGHT)
            testee.onThemeSelected(DuckDuckGoTheme.LIGHT)

            verify(mockPixel, never()).fire(AppPixelName.SETTINGS_THEME_TOGGLED_LIGHT)
            verify(mockThemeSettingsDataStore, never()).theme = DuckDuckGoTheme.LIGHT

            expectNoEvents()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenNewFireAnimationSelectedThenUpdateViewState() = runTest {
        val expectedAnimation = FireAnimation.HeroAbstract
        testee.onFireAnimationSelected(expectedAnimation)

        testee.viewState().test {
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

    private fun givenThemeSelected(theme: DuckDuckGoTheme) {
        whenever(mockThemeSettingsDataStore.theme).thenReturn(theme)
        whenever(mockThemeSettingsDataStore.isCurrentlySelected(theme)).thenReturn(true)
    }

    private fun givenSelectedFireAnimation(fireAnimation: FireAnimation) {
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(fireAnimation)
        whenever(mockAppSettingsDataStore.isCurrentlySelected(fireAnimation)).thenReturn(true)
    }
}
