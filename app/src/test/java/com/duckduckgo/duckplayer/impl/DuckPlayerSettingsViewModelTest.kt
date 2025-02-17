package com.duckduckgo.duckplayer.impl

import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckplayer.api.DuckPlayer.OpenDuckPlayerInNewTab.Off
import com.duckduckgo.duckplayer.api.DuckPlayer.OpenDuckPlayerInNewTab.On
import com.duckduckgo.duckplayer.api.DuckPlayer.UserPreferences
import com.duckduckgo.duckplayer.api.DuckPlayerSettingsLaunchSource.Other
import com.duckduckgo.duckplayer.api.DuckPlayerSettingsLaunchSource.Settings
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_ALWAYS_SETTINGS
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_BACK_TO_DEFAULT
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_NEVER_SETTINGS
import com.duckduckgo.duckplayer.impl.DuckPlayerSettingsViewModel.Command.OpenPlayerModeSelector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class DuckPlayerSettingsViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val duckPlayer: DuckPlayerInternal = mock()
    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository = mock()
    private val pixel: Pixel = mock()
    private lateinit var viewModel: DuckPlayerSettingsViewModel
    private val userPreferencesFlow: MutableSharedFlow<UserPreferences> = MutableSharedFlow()

    @Before
    fun setUp() {
        runTest {
            whenever(duckPlayer.observeUserPreferences()).thenReturn(userPreferencesFlow)
            userPreferencesFlow.emit(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            whenever(duckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            whenever(duckPlayer.shouldOpenDuckPlayerInNewTab()).thenReturn(Off)
            viewModel = DuckPlayerSettingsViewModel(duckPlayer, duckPlayerFeatureRepository, pixel)
        }
    }

    @Test
    fun whenDuckPlayerModeSelectorIsClichedThenEmitOpenPlayerModeSelector() = runTest {
        viewModel.duckPlayerModeSelectorClicked()

        viewModel.commands.test {
            assertEquals(OpenPlayerModeSelector(AlwaysAsk), awaitItem())
        }
    }

    @Test
    fun whenPrivatePlayerModeIsSelectedThenUpdateUserPreferences() = runTest {
        viewModel.onPlayerModeSelected(Disabled)

        verify(duckPlayer).setUserPreferences(overlayInteracted = false, privatePlayerMode = Disabled.value)
        verify(pixel).fire(DUCK_PLAYER_SETTINGS_NEVER_SETTINGS)
    }

    @Test
    fun whenPrivatePlayerModeIsSelectedToAlwaysAskThenUpdateUserPreferences() = runTest {
        viewModel.onPlayerModeSelected(AlwaysAsk)

        verify(duckPlayer).setUserPreferences(overlayInteracted = false, privatePlayerMode = AlwaysAsk.value)
        verify(pixel).fire(DUCK_PLAYER_SETTINGS_BACK_TO_DEFAULT)
    }

    @Test
    fun whenPrivatePlayerModeIsSelectedToAlwaysThenUpdateUserPreferences() = runTest {
        viewModel.onPlayerModeSelected(Enabled)

        verify(duckPlayer).setUserPreferences(overlayInteracted = false, privatePlayerMode = Enabled.value)
        verify(pixel).fire(DUCK_PLAYER_SETTINGS_ALWAYS_SETTINGS)
    }

    @Test
    fun whenViewModelIsCreatedAndPrivatePlayerModeIsDisabledThenEmitDisabled() = runTest {
        whenever(duckPlayer.observeUserPreferences()).thenReturn(flowOf(UserPreferences(overlayInteracted = true, privatePlayerMode = Disabled)))
        whenever(duckPlayer.observeShouldOpenInNewTab()).thenReturn(flowOf(On))
        userPreferencesFlow.emit(UserPreferences(overlayInteracted = true, privatePlayerMode = Disabled))
        viewModel = DuckPlayerSettingsViewModel(duckPlayer, duckPlayerFeatureRepository, pixel)

        viewModel.viewState.test {
            assertEquals(Disabled, awaitItem().privatePlayerMode)
        }
    }

    @Test
    fun whenViewModelIsCreatedAndPrivatePlayerModeIsAlwaysAndOpenInNewTabThenEmitAlwaysAndOpenInNewTab() = runTest {
        whenever(duckPlayer.observeUserPreferences()).thenReturn(flowOf(UserPreferences(overlayInteracted = true, privatePlayerMode = Enabled)))
        whenever(duckPlayer.observeShouldOpenInNewTab()).thenReturn(flowOf(On))
        userPreferencesFlow.emit(UserPreferences(overlayInteracted = true, privatePlayerMode = Enabled))
        viewModel = DuckPlayerSettingsViewModel(duckPlayer, duckPlayerFeatureRepository, pixel)

        viewModel.viewState.test {
            awaitItem().let {
                assertEquals(Enabled, it.privatePlayerMode)
                assertEquals(On, it.openDuckPlayerInNewTab)
            }
        }
    }

    @Test
    fun whenOpenInNewTabIsSetToTrueThenUpdatePreferences() = runTest {
        viewModel.onOpenDuckPlayerInNewTabToggled(true)

        verify(duckPlayer).setOpenInNewTab(true)
    }

    @Test
    fun `when screen opened, source settings and wasn't used before, then send count pixel`() = runTest {
        whenever(duckPlayer.wasUsedBefore()).thenReturn(false)

        viewModel.onScreenOpened(launchSource = Settings)

        verify(pixel).fire(DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_PRESSED, mapOf("was_used_before" to "0"))
    }

    @Test
    fun `when screen opened, source settings and was used before, then send count pixel`() = runTest {
        whenever(duckPlayer.wasUsedBefore()).thenReturn(true)

        viewModel.onScreenOpened(launchSource = Settings)

        verify(pixel).fire(DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_PRESSED, mapOf("was_used_before" to "1"))
    }

    @Test
    fun `when screen opened, source settings, then send unique pixel`() = runTest {
        whenever(duckPlayer.wasUsedBefore()).thenReturn(false)

        viewModel.onScreenOpened(launchSource = Settings)

        verify(pixel).fire(DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_PRESSED_UNIQUE, type = Pixel.PixelType.Unique())
    }

    @Test
    fun `when screen opened, source other, then don't send count pixel`() = runTest {
        whenever(duckPlayer.wasUsedBefore()).thenReturn(true)

        viewModel.onScreenOpened(launchSource = Other)

        verify(pixel, never()).fire(eq(DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_PRESSED), any(), any(), any())
    }

    @Test
    fun `when screen opened, source other, then don't send unique pixel`() = runTest {
        whenever(duckPlayer.wasUsedBefore()).thenReturn(false)

        viewModel.onScreenOpened(launchSource = Other)

        verify(pixel, never()).fire(eq(DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_PRESSED_UNIQUE), any(), any(), any())
    }
}
