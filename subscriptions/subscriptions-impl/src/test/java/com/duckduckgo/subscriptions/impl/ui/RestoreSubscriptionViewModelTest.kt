package com.duckduckgo.subscriptions.impl.ui

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.Companion.SUBSCRIPTION_NOT_FOUND_ERROR
import com.duckduckgo.subscriptions.impl.SubscriptionsData
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.auth.Entitlement
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.Error
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.RestoreFromEmail
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.SubscriptionNotFound
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.Success
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RestoreSubscriptionViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private lateinit var viewModel: RestoreSubscriptionViewModel

    @Before
    fun before() {
        viewModel = RestoreSubscriptionViewModel(subscriptionsManager, coroutineTestRule.testDispatcherProvider)
    }

    @Test
    fun whenRestoreFromEmailThenSendCommand() = runTest {
        viewModel.commands().test {
            viewModel.restoreFromEmail()
            assertTrue(awaitItem() is RestoreFromEmail)
        }
    }

    @Test
    fun whenRestoreFromStoreIfFailureThenReturnError() = runTest {
        whenever(subscriptionsManager.recoverSubscriptionFromStore()).thenReturn(
            SubscriptionsData.Failure("error"),
        )

        viewModel.commands().test {
            viewModel.restoreFromStore()
            val result = awaitItem()
            assertTrue(result is Error)
            assertEquals("error", (result as Error).message)
        }
    }

    @Test
    fun whenRestoreFromStoreIfNoSubscriptionFoundThenReturnNotFound() = runTest {
        whenever(subscriptionsManager.recoverSubscriptionFromStore()).thenReturn(
            SubscriptionsData.Failure(SUBSCRIPTION_NOT_FOUND_ERROR),
        )

        viewModel.commands().test {
            viewModel.restoreFromStore()
            val result = awaitItem()
            assertTrue(result is SubscriptionNotFound)
        }
    }

    @Test
    fun whenRestoreFromStoreIfNoEntitlementsThenReturnNotFound() = runTest {
        whenever(subscriptionsManager.recoverSubscriptionFromStore()).thenReturn(
            SubscriptionsData.Success(email = null, externalId = "test", entitlements = emptyList()),
        )

        viewModel.commands().test {
            viewModel.restoreFromStore()
            val result = awaitItem()
            assertTrue(result is SubscriptionNotFound)
        }
    }

    @Test
    fun whenRestoreFromStoreIfEntitlementsThenReturnSuccess() = runTest {
        whenever(subscriptionsManager.recoverSubscriptionFromStore()).thenReturn(
            SubscriptionsData.Success(
                email = null,
                externalId = "test",
                entitlements = listOf(Entitlement(id = "test", product = "test", name = "test")),
            ),
        )

        viewModel.commands().test {
            viewModel.restoreFromStore()
            val result = awaitItem()
            assertTrue(result is Success)
        }
    }
}
