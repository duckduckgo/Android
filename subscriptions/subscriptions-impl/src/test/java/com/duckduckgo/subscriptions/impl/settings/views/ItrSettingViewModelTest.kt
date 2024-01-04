package com.duckduckgo.subscriptions.impl.settings.views

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.api.Subscriptions.EntitlementStatus.Found
import com.duckduckgo.subscriptions.api.Subscriptions.EntitlementStatus.NotFound
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingViewModel.Command.OpenItr
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class ItrSettingViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptions: Subscriptions = mock()
    private lateinit var viewModel: ItrSettingViewModel

    @Before
    fun before() {
        viewModel = ItrSettingViewModel(subscriptions, coroutineTestRule.testDispatcherProvider)
    }

    @Test
    fun whenOnItrThenCommandSent() = runTest {
        viewModel.commands().test {
            viewModel.onItr()
            assertTrue(awaitItem() is OpenItr)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnResumeIfSubscriptionEmitViewState() = runTest {
        whenever(subscriptions.getEntitlementStatus("Identity Theft Restoration")).thenReturn(Result.success(Found))

        viewModel.onResume(mock())
        viewModel.viewState.test {
            assertTrue(awaitItem().hasSubscription)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnResumeIfNotSubscriptionEmitViewState() = runTest {
        whenever(subscriptions.getEntitlementStatus("Identity Theft Restoration")).thenReturn(Result.success(NotFound))

        viewModel.onResume(mock())
        viewModel.viewState.test {
            assertFalse(awaitItem().hasSubscription)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnResumeEntitlementCheckFailsEmitViewState() = runTest {
        whenever(subscriptions.getEntitlementStatus("Identity Theft Restoration")).thenReturn(Result.failure(RuntimeException()))

        viewModel.onResume(mock())
        viewModel.viewState.test {
            assertFalse(awaitItem().hasSubscription)
            cancelAndConsumeRemainingEvents()
        }
    }
}
