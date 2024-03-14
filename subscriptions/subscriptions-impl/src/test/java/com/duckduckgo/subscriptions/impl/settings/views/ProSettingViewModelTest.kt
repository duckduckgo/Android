package com.duckduckgo.subscriptions.impl.settings.views

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenBuyScreen
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenRestoreScreen
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenSettings
import com.duckduckgo.subscriptions.impl.settings.views.SettingsStateProvider.SettingsState.SubscriptionOfferAvailable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ProSettingViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val settingsStateProvider: SettingsStateProvider = mock()
    private lateinit var viewModel: ProSettingViewModel

    @Before
    fun before() {
        viewModel = ProSettingViewModel(settingsStateProvider)
    }

    @Test
    fun whenOnSettingsThenCommandSent() = runTest {
        viewModel.commands().test {
            viewModel.onSettings()
            assertTrue(awaitItem() is OpenSettings)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnBuyThenCommandSent() = runTest {
        viewModel.commands().test {
            viewModel.onBuy()
            assertTrue(awaitItem() is OpenBuyScreen)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnRestoreThenCommandSent() = runTest {
        viewModel.commands().test {
            viewModel.onRestore()
            assertTrue(awaitItem() is OpenRestoreScreen)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnResumeEmitViewState() = runTest {
        whenever(settingsStateProvider.getSettingsState()).thenReturn(flowOf(SubscriptionOfferAvailable))

        viewModel.onResume(mock())
        viewModel.viewState.test {
            assertEquals(SubscriptionOfferAvailable, awaitItem().settingsState)
            cancelAndConsumeRemainingEvents()
        }
    }
}
