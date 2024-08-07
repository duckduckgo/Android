package com.duckduckgo.duckplayer.impl

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.UserPreferences
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.impl.DuckPlayerSettingsViewModel.Command.OpenPlayerModeSelector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class DuckPlayerSettingsViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val duckPlayer: DuckPlayer = mock()
    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository = mock()
    private lateinit var viewModel: DuckPlayerSettingsViewModel

    @Before
    fun setUp() {
        runTest {
            whenever(duckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            viewModel = DuckPlayerSettingsViewModel(duckPlayer, duckPlayerFeatureRepository)
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
    }

    @Test
    fun whenViewModelIsCreatedAndPrivatePlayerModeIsDisabledThenEmitDisabled() = runTest {
        whenever(duckPlayer.observeUserPreferences()).thenReturn(flowOf(UserPreferences(overlayInteracted = true, privatePlayerMode = Disabled)))
        viewModel = DuckPlayerSettingsViewModel(duckPlayer, duckPlayerFeatureRepository)

        viewModel.viewState.test {
            assertEquals(Disabled, awaitItem().privatePlayerMode)
        }
    }
}
