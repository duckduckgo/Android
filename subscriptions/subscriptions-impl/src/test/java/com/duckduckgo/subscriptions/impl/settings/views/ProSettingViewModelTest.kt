package com.duckduckgo.subscriptions.impl.settings.views

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class ProSettingViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private lateinit var viewModel: ProSettingViewModel

    @Before
    fun before() {
        viewModel = ProSettingViewModel(subscriptionsManager, coroutineTestRule.testDispatcherProvider)
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
    fun whenOnResumeIfSubscriptionEmitViewState() = runTest {
        whenever(subscriptionsManager.hasSubscription).thenReturn(flowOf(true))

        viewModel.onResume(mock())
        viewModel.viewState.test {
            assertTrue(awaitItem().hasSubscription)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnResumeIfNotSubscriptionEmitViewState() = runTest {
        whenever(subscriptionsManager.hasSubscription).thenReturn(flowOf(false))

        viewModel.onResume(mock())
        viewModel.viewState.test {
            assertFalse(awaitItem().hasSubscription)
            cancelAndConsumeRemainingEvents()
        }
    }
}
